package net.ritzow.news;

import j2html.TagCreator;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import net.ritzow.jetstart.HtmlResult;
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
import org.eclipse.jetty.util.Callback;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.jetstart.JettyHandlers.newPath;
import static net.ritzow.news.PageTemplate.*;
import static net.ritzow.news.ResourceUtil.*;

public class RunSite {
	
	private static final Lock shutdownLock = new ReentrantLock();
	private static final Condition shutdownCond = shutdownLock.newCondition();
	
	private static ContentManager CONTENT_MANAGER;
	
	static final Translator<String> TRANSLATIONS =
		Translator.ofProperties(properties("/lang/welcome.properties"));
	
	public static void main(String[] args) throws Exception {
		CONTENT_MANAGER = ContentManager.ofMemoryDatabase();
		var content = resourceAsString("/content/Sandbox2D.md");
		CONTENT_MANAGER.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme", content);
		CONTENT_MANAGER.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme v2", content + "\n\nAND SOME EXTRA.");
		CONTENT_MANAGER.newArticle("sandbox2d", Locale.forLanguageTag("es"), "Sandbox2D Readme v2 Spanish", content);
		
		ContentUtil.genArticles(CONTENT_MANAGER);
		
		/* TODO use integrity attribute to verify content if delivered via CDN and use crossorigin="anonymous" */
		//TODO set loading="lazy" on img HTML elements.
		//TODO deliver article content using svg elements
		//TODO look into using noscript if ever using javascript
		//TODO use interactive elements https://developer.mozilla.org/en-US/docs/Web/HTML/Element#interactive_elements
		//TODO use <details> for accordion items
		//TODO could use <button> with form submission for the home button to prevent link dragging? not very idiomatic
		
		//TODO create Handler that handles language-switch POST requests and wrap newPath call
		var server = JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")), System.getProperty("net.ritzow.pass"),
			newPath(
				ResponseUtil.generatedHandler(RunSite::mainPageGenerator),
				entry("/article", ResponseUtil.generatedHandler(RunSite::articlePageProcessor)),
				entry("/upload", ResponseUtil.generatedHandler(RunSite::uploadGenerator)),
				entry("/shutdown", ResponseUtil.generatedHandler(RunSite::shutdownPage)),
				entry("/opensearch", new StaticContentHandler(open("/xml/opensearch.xml"), "application/opensearchdescription+xml")),
				entry("/style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("/icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml"))
			),
			ResponseUtil.generatedHandler(RunSite::errorGenerator)
		);
		
		server.start();
		
		lock.await();
		
		server.stop();
		CONTENT_MANAGER.shutdown();
		server.join();
	}
	
	private static final CountDownLatch lock = new CountDownLatch(1);
	
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

	private static HtmlTag page(String title, Locale locale) {
		return html().withLang(locale.toLanguageTag()).with(
			baseHead(title),
			body().withId("top").with(
				named("full-content"),
				div().withClass("page-separator"),
				footer().withClasses("page-footer").with(
					a().withClasses("left-footer", "foreground").withHref("/shutdown").with(
						rawHtml("Shutdown")
					),
					span().withClasses("right-footer", "foreground").with(
						text("Server Time: "),
						time(rawHtml(serverTime(locale))),
						text(" Heap: "),
						dynamic(state -> {
							long kbUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000;
							NumberFormat format = NumberFormat.getIntegerInstance(locale);
							return text(format.format(kbUsed) + " KB");
						})
					)
				)
			)
		);
	}
	
	private static String serverTime(Locale locale) {
		return ZonedDateTime.now().format(
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale));
	}
	
	public static Locale pageLocale(Request request) throws SQLException {
		return HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
	}
	
	private static void shutdownPage(Request request) {
		
		ResponseUtil.doGetHtmlStreamed(request,
			html().with(
				body().with(
					p("Shutting down...")
				)
			)
		);
		
		request.getResponse().getHttpOutput().complete(new Callback() {
			@Override
			public void succeeded() {
				lock.countDown();
			}
			
			@Override
			public void failed(Throwable x) {
				x.printStackTrace();
			}
		});
	}
	
	private static final DomContent CONTENT_HTML = each(
		nav().withClasses("header", "foreground").with(named("header-content")),
		mainBox(
			named("content")
		)
	);
	
	private static DomContent header(Request request) throws SQLException {
		List<Locale> locales = CONTENT_MANAGER.getSupportedLocales();
		Locale bestCurrent = HttpUser.bestLocale(request, locales);
		return each(
			logo("/icon.svg"),
			form()
				.withClasses("lang-selector")
				.withCondAutocomplete(false)
				.withMethod("post")
				.withAction(request.getHttpURI().getDecodedPath())
				.withEnctype("multipart/form-data").with(
				each(
					locales.stream().map(locale -> {
						var button = button()
							.withType("submit")
							.withName(locale.toLanguageTag())
							.withClass("lang-button").with(
								span(locale.getDisplayLanguage(locale))
							);
						
						if(locale.equals(bestCurrent)) {
							button.withCondDisabled(true);
							/*.with(
								span(locale.getDisplayCountry(locale))
							);*/
						}
						return button;
					})
				)
			)
		);
	}
	
	private static void doPostLang(Request request) {
		HttpField contentType = request.getHttpFields().getField(HttpHeader.CONTENT_TYPE);
		Objects.requireNonNull(contentType, "No content type specified by client");
		String contentTypeStr = contentType.getValue();
		var map = new HashMap<String, String>(1);
		HttpField.getValueParameters(contentTypeStr, map);
		String boundary = map.get("boundary");
		
		try {
			//TODO instead of readAllBytes, read as much as possible at a time and parse progressively.
			var parser = new MultiPartParser(new MultiPartParser.Handler() {
				@Override
				public void parsedField(String name, String value) {
					if(name.equalsIgnoreCase("Content-Disposition")) {
						var map = new HashMap<String, String>(1);
						HttpField.getValueParameters(value, map);
						/* TODO validate "name" field to make sure it is proper locale */
						HttpUser.session(request).locale(Locale.forLanguageTag(map.get("name")));
					}
				}
				
				@Override
				public boolean content(ByteBuffer item, boolean last) {
					//System.out.println("\"" + StandardCharsets.UTF_8.decode(item) + "\"");
					return false;
				}
				
				@Override
				public void earlyEOF() {
					throw new RuntimeException("early EOF");
				}
			}, boundary);
			HttpInput in = request.getHttpInput();
			parser.parse(ByteBuffer.wrap(in.readAllBytes()), true);
			
			//TODO read in chunks so parsing can happen while receiving packets.
//			boolean finished = in.isFinished();
//			ByteBuffer buffer = null;
//			while(!finished) {
//				request.getHttpInput()
//				int available = in.available();
//				if(buffer == null || available > buffer.capacity()) {
//					buffer = ByteBuffer.wrap(new byte[available]);
//				} else if(buffer) {
//
//				} else {
//					buffer = ByteBuffer.wrap(new byte[2048]);
//				}
//				buffer = (buffer == null || available > buffer.length) ? new byte[available] : new byte[2048];
//				int count = in.read(buffer);
//				parser.parse(buffer, finished = in.isFinished());
//			}
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
		request.getResponse().setHeader(HttpHeader.LOCATION, request.getHttpURI().getDecodedPath());
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	private static void mainPageGenerator(Request request) {
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
			ResponseUtil.doGetHtmlStreamed(request, new HtmlResult(page("RedNet", bestLocale), Map.of(
				"full-content", CONTENT_HTML,
				"header-content", header(request),
				"content", div().withClass("article-list").with(
					Stream.concat(
						Stream.of(
							h1(translated("greeting")).withClass("title")
						),
						CONTENT_MANAGER.getArticlesForLocale(bestLocale).stream().map(
							article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
						)
					).toArray(DomContent[]::new)
				)
			)), TRANSLATIONS);
		} catch(SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static void uploadGenerator(Request request) throws SQLException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			throw new RuntimeException("Not implemented");
		}
		
		if(StaticPathHandler.peekComponent(request).isPresent()) {
			doGeneric404(request);
			return;
		}
		
		Locale locale = HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
		
		ResponseUtil.doGetHtmlStreamed(request, page("RedNet", locale),
			entry("full-content",
				CONTENT_HTML
			),
			entry("header-content",
				header(request)
			),
			entry("content",
				mainForm()
			)
		);
	}
	
	private static void articlePageProcessor(Request request) throws SQLException, IOException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			doPostLang(request);
			return;
		}
		
		Optional<String> name = StaticPathHandler.peekComponent(request);
		Locale mainLocale = HttpUser.bestLocale(request, CONTENT_MANAGER.getSupportedLocales());
		
		//TODO make sure there isn't an extra component in the path
		
		if(name.isEmpty()) {
			var html = page("Error", mainLocale);
			ResponseUtil.doGetHtmlStreamed(request, new HtmlResult(html,  Map.ofEntries(
				entry("full-content",
					mainBox(
						p("Please specify an article URL component: ").with(
							span(request.getHttpURI().toURI().normalize() + "<article-path>")
						),
						a("Go home").withHref("/")
					)
				)
			), HttpStatus.NOT_FOUND_404), TRANSLATIONS);
			return;
		}
		
		String urlname = name.get();
		List<Locale> supported = CONTENT_MANAGER.getArticleLocales(urlname);
		
		if(supported.isEmpty()) {
			ResponseUtil.doGetHtmlStreamed(request, page("Error", mainLocale),
				entry("full-content", CONTENT_HTML),
				entry("header-content", header(request)),
				entry("content",
					p("No such article " + urlname)
				)
			);
			return;
		}
		
		Locale lang = HttpUser.bestLocale(request, supported);
		Optional<Article<MarkdownContent>> article = CONTENT_MANAGER.getLatestArticle(urlname, lang, reader -> {
			try {
				return new MarkdownContent(reader);
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		
		if(article.isEmpty()) {
			ResponseUtil.doGetHtmlStreamed(request, page("Error", mainLocale),
				entry("full-content", CONTENT_HTML),
				entry("header-content", header(request)),
				entry("content",
					p("No such article " + urlname)
				)
			);
			return;
		}
		
		ResponseUtil.doGetHtmlStreamed(request, page(article.get().title(), mainLocale),
			entry("full-content", CONTENT_HTML),
			entry("header-content", header(request)),
			entry("content", TagCreator.main().withLang(lang.toLanguageTag()).with(
				article(
					h1(article.get().title()).withClass("title-article"),
					lang.equals(mainLocale) ? null : h3(lang.getDisplayName(mainLocale)).withClass("article-lang"),
					div().withClass("markdown").with(article.get().content())
				)
			))
		);
	}
	
	//TODO standardize error page
	private static void doGeneric404(Request request) {
		var html = page("Error", Locale.forLanguageTag("en-US"));
		ResponseUtil.doGetHtmlStreamed(request, new HtmlResult(html,  Map.ofEntries(
			entry("full-content",
				mainBox(
					p().with(
						span("\"" + request.getHttpURI() + "\" does not exist.")
					),
					a("Go home").withHref("/")
				)
			)
		), HttpStatus.NOT_FOUND_404), TRANSLATIONS);
	}
	
	private static void errorGenerator(Request request) {
		var html = page("Error", Locale.forLanguageTag("en-US"));
		
		ResponseUtil.doGetHtmlStreamed(request, new HtmlResult(html, Map.ofEntries(
			entry("full-content",
				mainBox(
					p("Sorry, there was an unexpected error!"),
					a("Go home").withHref("/")
				)
			)
		), HttpStatus.INTERNAL_SERVER_ERROR_500), TRANSLATIONS);
	}
}
