package net.ritzow.news;

import j2html.TagCreator;
import j2html.tags.DomContent;
import j2html.tags.specialized.BodyTag;
import j2html.tags.specialized.ButtonTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
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
import java.util.function.Function;
import java.util.random.RandomGenerator;
import net.ritzow.jetstart.JettySetup;
import net.ritzow.jetstart.StaticPathHandler;
import net.ritzow.jetstart.Translator;
import net.ritzow.news.ContentManager.Article;
import net.ritzow.news.Forms.FieldReader;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.MultiPartParser;
import org.eclipse.jetty.server.MultiPartParser.Handler;
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
//TODO use Link header to prefetch stylesheet and font, add parameter to doGetHtmlStreamed

public class RunSite {
	
	public static void main(String[] args) throws Exception {
		var server = new RunSite(
			InetAddress.getByName("::1"), false,
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass")
		);
		server.start();
		server.await();
		server.stop();
	}
	
	public static RunSite startServer(InetAddress bind, boolean requireSni, Path keyStore, String keyStorePassword) throws Exception {
		var server = new RunSite(bind, requireSni, keyStore, keyStorePassword);
		server.start();
		return server;
	}
	
	private final Server server;
	private final ContentManager cm;
	private final Translator<String> translator;
	
	private final CountDownLatch shutdownLock = new CountDownLatch(1);
	
	private RunSite(InetAddress bind, boolean requireSni, Path keyStore, String keyStorePassword) throws
			CertificateException,
			IOException,
			KeyStoreException,
			NoSuchAlgorithmException,
			SQLException {
		server = createServer(bind, requireSni, keyStore, keyStorePassword);
		cm = ContentManager.ofMemoryDatabase();
		ContentUtil.genArticles(cm);
		translator = Translator.ofProperties(properties("/lang/welcome.properties"));
	}
	
	public void start() throws Exception {
		server.start();
	}
	
	public void await() throws InterruptedException {
		shutdownLock.await();
	}
	
	public void stop() throws Exception {
		server.stop();
		cm.shutdown();
		server.join();
	}
	
	public Server createServer(InetAddress bind, boolean requireSni, Path keyStore, String keyStorePassword)
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
		return JettySetup.newStandardServer(
			bind, requireSni, keyStore, keyStorePassword,
			newPath(
				generatedHandler(this::mainPageGenerator),
				entry("/article", generatedHandler(this::articlePageProcessor)),
				entry("/upload", generatedHandler(this::uploadGenerator)),
				entry("/shutdown", generatedHandler(this::shutdownPage)),
				entry("/opensearch", new StaticContentHandler(open("/xml/opensearch.xml"),
					"application/opensearchdescription+xml")),
				entry("/style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("/icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml")),
				entry("/opensans.ttf", new StaticContentHandler(open("/font/OpenSans-Regular.ttf"), "font/ttf"))
			),
			generatedHandler(this::errorGenerator)
		);
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
		return HttpUser.bestLocale(request, cm.getSupportedLocales());
	}
	
	private void shutdownPage(Request request) {
		doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(),
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
	
	private static final DomContent LOGO_HTML = logo("/icon.svg");
	
	private DomContent header(Request request) throws SQLException {
		List<Locale> locales = cm.getSupportedLocales();
		Locale bestCurrent = HttpUser.bestLocale(request, locales);
		return each(
			LOGO_HTML,
			form()
				.withClasses("lang-selector")
				.attr("autocomplete", "off")
				.withMethod("post")
				.withEnctype("multipart/form-data")
				.with(
					locales.stream().map(locale -> langButton(locale, bestCurrent))
				),
			loginForm()
		);
	}
	
	private static ButtonTag langButton(Locale locale, Locale pageLocale) {
		var button = button()
			.withType("submit")
			.withName("lang-select")
			.withValue(locale.toLanguageTag() /*+ ContentUtil.generateGibberish(RandomGenerator.getDefault(), false, 1000, 10)*/)
			.withClass("lang-button").with(
				span(locale.getDisplayLanguage(locale))
			);
		
		if(locale.equals(pageLocale)) {
			button.withCondDisabled(true);
		}
		return button;
	}
	
	/* Cool checkboxes https://stackoverflow.com/questions/4148499/how-to-style-a-checkbox-using-css */
	/* Custom checkbox https://stackoverflow.com/questions/44299150/set-text-inside-a-check-box/44299305 */
	private static DomContent loginForm() {
		return form().withClass("login-form").withMethod("post").withEnctype("multipart/form-data").with(
			/* TODO the username should be prefilled in "value" on the next page if the user clicks "Sign up" */
			input().withName("username").withType("text").attr("autocomplete", "username").withPlaceholder("Username"),
			input().withName("password").withType("password").attr("autocomplete", "current-password").withPlaceholder("Password"),
			label(input().withType("checkbox"), rawHtml("Remember me")),
			button("Login").withName("login-action").withValue("login"),
			button("Sign up").withName("login-action").withValue("signup")
		);
	}
	
	private static <T> Function<String, Optional<? extends T>> doProcessForms(Request request,
			Function<String, FieldReader<? extends T>> actions) {
		HttpField contentType = request.getHttpFields().getField(HttpHeader.CONTENT_TYPE);
		Objects.requireNonNull(contentType, "No content type specified by client");
		String contentTypeStr = contentType.getValue();
		var map = new HashMap<String, String>(1);
		HttpField.getValueParameters(contentTypeStr, map);
		String boundary = map.get("boundary");
		try {
			Map<String, FieldReader<? extends T>> storage = new TreeMap<>();
			parse(request.getHttpInput(), boundary, new Handler() {
				private FieldReader<? extends T> reader;
				private String name, filename;
				
				@Override
				public void parsedField(String name, String value) {
					if(name.equalsIgnoreCase("Content-Disposition")) {
						var map = new TreeMap<String, String>();
						String disposition = HttpField.getValueParameters(value, map);
						if(!disposition.equals("form-data")) {
							/* Only form-data is valid https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition */
							throw new RuntimeException("received non-form-data");
						}
						
						this.name = map.get("name");
						this.filename = map.get("filename");
						reader = null;
					}
				}
				
				@Override
				public boolean content(ByteBuffer item, boolean last) {
					if(reader == null) {
						reader = Objects.requireNonNull(actions.apply(name));
						/* Overrride existing values */
						storage.put(name, reader);
					}
					if(item.hasRemaining()) reader.read(item, last, filename);
					return false;
				}
				
				@Override
				public void earlyEOF() {
					throw new RuntimeException("early EOF");
				}
			});
			return name -> {
				var field = storage.get(name);
				if(field == null) {
					return Optional.empty();
				} else {
					return Optional.ofNullable(field.result());
				}
			};
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	/** Respond to {@code request} with a 303 redirect to the page, and set the language. **/
	private void processLangForm(Request request, String langTag) {
		storeLocale(request, langTag);
		request.getResponse().setHeader(HttpHeader.LOCATION, request.getHttpURI().getDecodedPath());
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	private void storeLocale(Request request, String languageTag) {
		try {
			Locale selected = Locale.forLanguageTag(languageTag);
			Optional<Locale> existing = cm.getSupportedLocales().stream().filter(selected::equals).findAny();
			HttpUser.session(request).locale(existing.orElseThrow(() -> new RuntimeException("Invalid selected locale \"" + languageTag + "\"")));
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	/** Parse {@code in} incrementally using the provided {@code handler}. **/
	private static void parse(HttpInput in, String boundary, Handler handler) throws IOException {
		MultiPartParser parser = new MultiPartParser(handler, boundary);
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
			var result = doProcessForms(request, name -> switch(name) {
				case "lang-select" -> Forms.stringReader();
				default -> throw new RuntimeException("Unknown form field \"" + name + "\"");
			});
			result.apply("lang-select").ifPresent(lang -> processLangForm(request, lang));
			return;
		}
		
		if(StaticPathHandler.peekComponent(request).isPresent()) {
			doGeneric404(request);
			return;
		}
		
		try {
			Locale bestLocale = HttpUser.bestLocale(request, cm.getSupportedLocales());
			doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(bestLocale),
				context(
					request,
					translator,
					Map.of(),
					page("RedNet", bestLocale,
						content(
							header(request),
							each(
								h1(translated("greeting")).withClass("title"),
								each(
									cm.getArticlesForLocale(bestLocale).stream().map(
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
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				Locale locale = HttpUser.bestLocale(request, cm.getSupportedLocales());
				
				doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(locale),
					context(request, translator, Map.of(),
						page("Upload", locale,
							content(
								header(request),
								mainForm()
							)
						)
					)
				);
			}
			case POST -> {
				var result = doProcessForms(request, name -> switch(name) {
					case "lang-select", "username", "comment" -> Forms.stringReader();
					case "password" -> Forms.secretBytesReader(); //content.get(passwordHolder[0] = new byte[content.remaining()]);
					case "upload" -> Forms.fileReader(); //content.get(uploadHolder[0] = new byte[content.remaining()]);
					case "login-action" -> Forms.stringReader();
					default -> throw new RuntimeException("Unknown form field \"" + name + "\"");
				});
				
				result.apply("lang-select").ifPresent(lang -> processLangForm(request, (String)lang));
				result.apply("password").ifPresent(data -> Arrays.fill((byte[])data, (byte)0));
				
				var locale = HttpUser.bestLocale(request, cm.getSupportedLocales());
				
				@SuppressWarnings("unchecked") Optional<String> loginAction = (Optional<String>)result.apply("login-action");
				
				switch(loginAction.orElse("other")) {
					case "login" -> {
						@SuppressWarnings("unchecked") Optional<String> username = (Optional<String>)result.apply("username");
						doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(locale),
							context(request, translator, Map.of(),
								page("Upload", locale,
									content(
										header(request),
										p(username.orElse("No username provided"))
									)
								)
							)
						);
					}
					
					case "signup" -> throw new RuntimeException("not implemented");
					
					default -> doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(locale),
						context(request, translator, Map.of(),
							page("Upload", locale,
								content(
									header(request),
									mainForm()
								)
							)
						)
					);
				}
			}
			
			default -> throw new RuntimeException("Unsupported HTTP method");
		}
	}
	
	private void articlePageProcessor(Request request) throws SQLException, IOException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			var result = doProcessForms(request, name -> switch(name) {
				case "lang-select" -> Forms.stringReader();
				default -> throw new RuntimeException();
			});
			result.apply("lang-select").ifPresent(lang -> processLangForm(request, lang));
			//TODO also apply other stuff here
			return;
		}
		
		Optional<String> name = StaticPathHandler.peekComponent(request);
		Locale mainLocale = HttpUser.bestLocale(request, cm.getSupportedLocales());
		
		//TODO make sure there isn't an extra component in the path
		
		if(name.isEmpty()) {
			doWrongArticlePath(request, mainLocale);
			return;
		}
		
		String urlname = name.get();
		List<Locale> supported = cm.getArticleLocales(urlname);
		
		if(supported.isEmpty()) {
			doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404, List.of(mainLocale),
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
		Optional<Article<MarkdownContent>> article = cm.getLatestArticle(urlname, articleLocale, MarkdownContent::new);
		
		if(article.isEmpty()) {
			doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404, List.of(mainLocale, articleLocale),
				page("Error", mainLocale,
					content(
						header(request),
						p("No such article " + urlname)
					)
				)
			);
			return;
		}
		
		doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(mainLocale),
			context(request, translator, Map.of(),
				page(article.get().title(), mainLocale,
					content(
						header(request),
						TagCreator.main().withLang(articleLocale.toLanguageTag()).with(
							article(
								h1(article.get().title()).withClass("title-article"),
								articleLocale.equals(mainLocale) ? null :
									h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
								div().withClass("markdown").with(article.get().content())
							)
						)
					)
				)
			)
		);
	}
	
	private void doWrongArticlePath(Request request, Locale mainLocale) {
		doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404, List.of(mainLocale),
			context(request, translator, Map.of(),
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
	}
	
	private void doGeneric404(Request request) {
		doGetHtmlStreamed(request, HttpStatus.NOT_FOUND_404, List.of(),
			context(request, translator, Map.of(),
				page("Error", Locale.forLanguageTag("en-US"),
					mainBox(
						p("\"" + request.getHttpURI() + "\" does not exist."),
						a("Go home").withHref("/")
					)
				)
			)
		);
	}
	
	private void errorGenerator(Request request) {
		doGetHtmlStreamed(request, HttpStatus.INTERNAL_SERVER_ERROR_500, List.of(),
			context(request, translator, Map.of(),
				page("Error", Locale.forLanguageTag("en-US"),
					mainBox(
						p("Sorry, there was an unexpected error!"),
						a("Go home").withHref("/")
					)
				)
			)
		);
	}
}
