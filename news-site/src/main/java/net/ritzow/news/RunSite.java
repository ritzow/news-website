package net.ritzow.news;

import j2html.TagCreator;
import j2html.tags.DomContent;
import j2html.tags.specialized.BodyTag;
import j2html.tags.specialized.ButtonTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import net.ritzow.jetstart.JettySetup;
import net.ritzow.jetstart.StaticPathHandler;
import net.ritzow.jetstart.Translator;
import net.ritzow.news.ContentManager.Article;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.MultiPartParser;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.jetstart.JettyHandlers.newPath;
import static net.ritzow.news.PageTemplate.*;
import static net.ritzow.news.ResourceUtil.open;
import static net.ritzow.news.ResourceUtil.properties;
import static net.ritzow.news.ResponseUtil.doGetHtmlStreamed;
import static net.ritzow.news.ResponseUtil.generatedHandler;

/* TODO use integrity attribute to verify content if delivered via CDN and use crossorigin="anonymous" */
//TODO set loading="lazy" on img HTML elements.
//TODO look into using noscript if ever using javascript
//TODO use interactive elements https://developer.mozilla.org/en-US/docs/Web/HTML/Element#interactive_elements
//TODO use <details> for accordion items
//TODO could use <button> with form submission for the home button to prevent link dragging? not very idiomatic
//TODO create Handler that handles language-switch POST requests and wrap newPath call

public class RunSite {
	
	public static void main(String[] args) throws Exception {
		var server = new RunSite(Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"));
		server.start();
		server.await();
		server.stop();
	}
	
	public static RunSite startServer(Path keyStore, String keyStorePassword) throws Exception {
		var server = new RunSite(keyStore, keyStorePassword);
		server.start();
		return server;
	}
	
	private final Server server;
	private final ContentManager CONTENT_MANAGER;
	private final Translator<String> TRANSLATIONS;
	
	private final CountDownLatch shutdownLock = new CountDownLatch(1);
	
	private RunSite(Path keyStore, String keyStorePassword) throws
			CertificateException,
			IOException,
			KeyStoreException,
			NoSuchAlgorithmException,
			SQLException {
		server = createServer(keyStore, keyStorePassword);
		CONTENT_MANAGER = ContentManager.ofMemoryDatabase();
		ContentUtil.genArticles(CONTENT_MANAGER);
		TRANSLATIONS = Translator.ofProperties(properties("/lang/welcome.properties"));
	}
	
	public void start() throws Exception {
		server.start();
	}
	
	public void await() throws InterruptedException {
		shutdownLock.await();
	}
	
	public void stop() throws Exception {
		server.stop();
		CONTENT_MANAGER.shutdown();
		server.join();
	}
	
	public Server createServer(Path keyStore, String keyStorePassword)
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
		return JettySetup.newStandardServer(
			keyStore, keyStorePassword,
			newPath(
				generatedHandler(this::mainPageGenerator),
				entry("/article", generatedHandler(this::articlePageProcessor)),
				entry("/upload", generatedHandler(this::uploadGenerator)),
				entry("/shutdown", generatedHandler(this::shutdownPage)),
				entry("/opensearch", new StaticContentHandler(open("/xml/opensearch.xml"),
					"application/opensearchdescription+xml")),
				entry("/style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("/icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml"))
			),
			generatedHandler(RunSite::errorGenerator)
		);
	}
	
	private static void processSql() {
		//TODO create a webpage to submit sql commands
//		try(var reader = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
//			try(var db = cm.getConnection()) {
//				/* TODO set interactive to false and create a loop, create a StringReader after creating a query string */
//				SqlFile file = new SqlFile(reader, "<stdin>", System.out, "UTF-8", true, (URL)null);
//				file.setConnection(db);
//				file.execute();
//			}
//		} catch(IOException | SQLException e) {
//			e.printStackTrace();
//		}
	}
	
	@RequiresNamedHtml({"full-content", "time", "heap"})
	private static final BodyTag PAGE_BODY_HTML = body().withId("top").with(
		named("full-content"),
		div().withClass("page-separator"),
		footer().withClasses("page-footer").with(
			a().withClasses("left-footer", "foreground").withHref("/shutdown").with(
				rawHtml("Shutdown")
			),
			span().withClasses("right-footer", "foreground").with(
				text("Server Time: "),
				time(named("time")),
				text(" Heap: "),
				named("heap")
			)
		)
	);

	@RequiresDynamicHtml
	private static DomContent page(String title, Locale locale, DomContent fullContent) {
		return html().withLang(locale.toLanguageTag()).with(
			PageTemplate.head(title),
			dynamic(PAGE_BODY_HTML, Map.of(
				"full-content", fullContent,
				"time", rawHtml(serverTime(locale)),
				"heap", dynamic(state -> {
					long kbUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000;
					NumberFormat format = NumberFormat.getIntegerInstance(locale);
					return text(format.format(kbUsed) + " KB");
				})
			))
		);
	}
	
	private static String serverTime(Locale locale) {
		return ZonedDateTime.now().format(
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale));
	}
	
	public Locale pageLocale(Request request) throws SQLException {
		return HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
	}
	
	private void shutdownPage(Request request) {
		doGetHtmlStreamed(request, HttpStatus.OK_200,
			html().with(
				body().with(
					p("Shutting down...")
				)
			)
		);
		
		request.getResponse().getHttpOutput().complete(new Callback() {
			@Override
			public void succeeded() {
				shutdownLock.countDown();
			}
			
			@Override
			public void failed(Throwable x) {
				x.printStackTrace();
			}
		});
	}
	
	/* HTML should used "named" content when a lage chunk of HTML has a small number of dynamic elements */
	@RequiresDynamicHtml
	@RequiresNamedHtml({"header-content", "content"})
	private static final DomContent CONTENT_HTML = each(
		nav().withClasses("header", "foreground").with(named("header-content")),
		div().withClasses("page-body").with(
			div().withClass("content-left").with(
			
			),
			mainBox(
				named("content")
			),
			freeze(
				div().withClass("content-right").with(
					a().withClasses("jump-top", "foreground").withHref("#top").with(
						rawHtml("Return to top")
					)
				)
			)
		)
	);
	
	@RequiresDynamicHtml
	private static DomContent content(DomContent header, DomContent mainContent) {
		return dynamic(CONTENT_HTML, Map.of("header-content", header, "content", mainContent));
	}
	
	private DomContent header(Request request) throws SQLException {
		List<Locale> locales = CONTENT_MANAGER.getSupportedLocales();
		Locale bestCurrent = HttpUser.bestLocale(request, locales);
		return each(
			logo("/icon.svg"),
			form()
				.withClasses("lang-selector")
				.withCondAutocomplete(false)
				.withMethod("post")
				.withAction(request.getHttpURI().getDecodedPath())
				.withEnctype("multipart/form-data")
				.with(
					each(locales.stream().map(locale -> langButton(locale, bestCurrent)))
				)
		);
	}
	
	private static ButtonTag langButton(Locale locale, Locale pageLocale) {
		var button = button()
			.withType("submit")
			.withName("lang-select")
			.withValue(locale.toLanguageTag())
			.withClass("lang-button").with(
				span(locale.getDisplayLanguage(locale))
			);
		
		if(locale.equals(pageLocale)) {
			button.withCondDisabled(true);
		}
		return button;
	}
	
	private void doPostLang(Request request) {
		HttpField contentType = request.getHttpFields().getField(HttpHeader.CONTENT_TYPE);
		Objects.requireNonNull(contentType, "No content type specified by client");
		String contentTypeStr = contentType.getValue();
		var map = new HashMap<String, String>(1);
		HttpField.getValueParameters(contentTypeStr, map);
		String boundary = map.get("boundary");
		
		try {
			var handler = new MultiPartParser.Handler() {
				private String name;
				
				@Override
				public void parsedField(String name, String value) {
					if(name.equalsIgnoreCase("Content-Disposition")) {
						var map = new HashMap<String, String>(1);
						String disposition = HttpField.getValueParameters(value, map);
						if(!disposition.equals("form-data")) {
							throw new RuntimeException("received non-form-data");
						}
						/* TODO validate "name" field to make sure it is proper locale */
						this.name = map.get("name");
					}
				}
				
				@Override
				public boolean content(ByteBuffer item, boolean last) {
					switch(name) {
						case "lang-select" -> {
							/* Skip over 0-length separator */
							if(item.remaining() > 0) {
								storeLocale(request, item);
							}
						}
						default -> throw new RuntimeException("what???");
					}
					return false;
				}
				
				@Override
				public void earlyEOF() {
					throw new RuntimeException("early EOF");
				}
			};
			
			/* Read and parse data in chunks */
			parse(new MultiPartParser(handler, boundary), request.getHttpInput());
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
		request.getResponse().setHeader(HttpHeader.LOCATION, request.getHttpURI().getDecodedPath());
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	private void storeLocale(Request request, ByteBuffer content) {
		try {
			String locale = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(content)
				.toString();
			Locale selected = Locale.forLanguageTag(locale);
			if(CONTENT_MANAGER.getSupportedLocales().stream().noneMatch(selected::equals)) {
				throw new RuntimeException("Invalid selected locale \"" + locale + "\"");
			}
			HttpUser.session(request).locale(selected);
		} catch(CharacterCodingException | SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static void parse(MultiPartParser parser, HttpInput in) throws IOException {
		ByteBuffer buffer = null;
		while(true) {
			int available = in.available();
			if(buffer == null) {
				buffer = ByteBuffer.wrap(new byte[Math.max(available, 2048)]);
			} else if(available > buffer.capacity()) {
				buffer = ByteBuffer.wrap(new byte[available]);
			}
			int count = in.read(buffer.array());
			if(count != -1) {
				parser.parse(buffer.clear().limit(count), false);
			} else {
				parser.parse(buffer.clear().limit(0), true);
				break;
			}
		}
	}
	
	private void mainPageGenerator(Request request) {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			doPostLang(request);
			return;
		}
		
		if(StaticPathHandler.peekComponent(request).isPresent()) {
			doGeneric404(request);
			return;
		}
		
		try {
			Locale bestLocale = HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
			doGetHtmlStreamed(request, HttpStatus.OK_200,
				context(
					request,
					TRANSLATIONS,
					Map.of(),
					page("RedNet", bestLocale,
						content(
							header(request),
							div().withClass("article-list").with(
								h1(translated("greeting")).withClass("title"),
								each(
									CONTENT_MANAGER.getArticlesForLocale(bestLocale).stream().map(
										article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
									)
								)
							)
						)
					)
				)
			);
		} catch(SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void uploadGenerator(Request request) throws SQLException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			throw new RuntimeException("Not implemented");
		}
		
		if(StaticPathHandler.peekComponent(request).isPresent()) {
			doGeneric404(request);
			return;
		}
		
		Locale locale = HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
		
		throw new UnsupportedOperationException("not implemented");
		
//		doGetHtmlStreamed(request, HttpStatus.OK_200, page("RedNet", locale),
//			entry("full-content",
//				CONTENT_HTML
//			),
//			entry("header-content",
//				header(request)
//			),
//			entry("content",
//				mainForm()
//			)
//		);
	}
	
	private void articlePageProcessor(Request request) throws SQLException, IOException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			doPostLang(request);
			return;
		}
		
		Optional<String> name = StaticPathHandler.peekComponent(request);
		Locale mainLocale = HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
		
		//TODO make sure there isn't an extra component in the path
		
		if(name.isEmpty()) {
			doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404,
				context(request, TRANSLATIONS, Map.of(),
					page("Error", mainLocale,
						mainBox(
							p("Please specify an article URL component: ").with(
								span(request.getHttpURI().toURI().normalize() + "<article-path>")
							),
							a("Go home").withHref("/")
						)
					)
				)
			);
			return;
		}
		
		String urlname = name.get();
		List<Locale> supported = CONTENT_MANAGER.getArticleLocales(urlname);
		
		if(supported.isEmpty()) {
			doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404,
				page("Error", mainLocale,
					content(
						header(request),
						p("No such article " + urlname)
					)
				)
			);
			return;
		}
		
		Locale articleLocale = HttpUser.bestLocale(request, supported);
		Optional<Article<MarkdownContent>> article = CONTENT_MANAGER.getLatestArticle(urlname, articleLocale, reader -> {
			try {
				return new MarkdownContent(reader);
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		
		if(article.isEmpty()) {
			doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404, page("Error", mainLocale,
				content(
					header(request),
					p("No such article " + urlname)
				)
			));
			return;
		}
		
		doGetHtmlStreamed(request, HttpStatus.OK_200,
			context(
				request,
				TRANSLATIONS,
				Map.of(),
				page(article.get().title(), mainLocale,
					content(
						header(request),
						TagCreator.main().withLang(articleLocale.toLanguageTag()).with(
							article(
								h1(article.get().title()).withClass("title-article"),
								articleLocale.equals(mainLocale) ? null : h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
								div().withClass("markdown").with(article.get().content())
							)
						)
					)
				)
			)
		);
	}
	
	//TODO standardize error page
	private static void doGeneric404(Request request) {
		doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404,
			page("Error", Locale.forLanguageTag("en-US"),
				mainBox(
					p("\"" + request.getHttpURI() + "\" does not exist."),
					a("Go home").withHref("/")
				)
			)
		);
	}
	
	private static void errorGenerator(Request request) {
		doGetHtmlStreamed(request, HttpStatus.INTERNAL_SERVER_ERROR_500,
			page("Error", Locale.forLanguageTag("en-US"),
				mainBox(
					p("Sorry, there was an unexpected error!"),
					a("Go home").withHref("/")
				)
			)
		);
	}
}
