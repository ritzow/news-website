package net.ritzow.news;

import j2html.tags.DomContent;
import j2html.tags.specialized.BodyTag;
import j2html.tags.specialized.ButtonTag;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.InetAddress;
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
import net.ritzow.news.ContentManager.Article;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.*;
import static net.ritzow.news.PageTemplate.*;
import static net.ritzow.news.ResourceUtil.open;
import static net.ritzow.news.ResourceUtil.properties;
import static net.ritzow.news.ResponseUtil.*;

/* TODO use integrity attribute to verify content if delivered via CDN and use crossorigin="anonymous" */
//TODO set loading="lazy" on img HTML elements.
//TODO look into using noscript if ever using javascript
//TODO use interactive elements https://developer.mozilla.org/en-US/docs/Web/HTML/Element#interactive_elements
//TODO use <details> for accordion items
//TODO could use <button> with form submission for the home button to prevent link dragging? not very idiomatic
//TODO create Handler that handles language-switch POST requests and wrap newPath call
//TODO use Link header to prefetch stylesheet and font, add parameter to doGetHtmlStreamed

public final class NewsSite {
	private final Server server;
	private final ContentManager cm;
	private final Translator<String> translator;
	
	public static NewsSite start(InetAddress bind, boolean requireSni, Path keyStore, String keyStorePassword) throws Exception {
		var server = new NewsSite(bind, requireSni, keyStore, keyStorePassword);
		server.server.start();
		return server;
	}
	
	private NewsSite(InetAddress bind, boolean requireSni, Path keyStore, String keyStorePassword) throws
			CertificateException,
			IOException,
			KeyStoreException,
			NoSuchAlgorithmException,
			SQLException {
		cm = ContentManager.ofMemoryDatabase();
		ContentUtil.genArticles(cm);
		translator = Translator.ofProperties(properties("/lang/welcome.properties"));
		server = JettySetup.newStandardServer(
			bind,
			requireSni,
			keyStore,
			keyStorePassword,
			route()::accept,
			this::exceptionPageHandler
		);
	}
	
	private RequestConsumer<Exception> route() {
		return matchStaticPaths(
			rootNoMatchOrNext(
				this::mainPageGenerator,
				this::doGeneric404,
				entry("article", this::articlePageProcessor),
				entry("upload", this::uploadGenerator),
				entry("shutdown", this::shutdownPage),
				entry("opensearch", new StaticContentHandler(open("/xml/opensearch.xml"),
					"application/opensearchdescription+xml")),
				entry("style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml")),
				entry("opensans.ttf", new StaticContentHandler(open("/font/OpenSans-Regular.ttf"), "font/ttf"))
			)
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
	
	private void shutdownPage(Request request, Iterator<String> path) {
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
				try {
					server.stop();
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
			}
			
			@Override
			public void failed(Throwable x) {
				x.printStackTrace();
			}
		});
	}
	
	/* HTML should use "named" content when a lage chunk of HTML has a small number of dynamic elements */
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
			postForm()
				.withClasses("lang-selector")
				.attr("autocomplete", "off")
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
	
	private static final DomContent LOGIN_FORM_CONTENT = freeze(
		input().withName("username").withType("text").attr("autocomplete", "username").withPlaceholder("Username"),
		input().withName("password").withType("password").attr("autocomplete", "current-password").withPlaceholder("Password"),
		label(input().withName("login-remember").withType("checkbox"), rawHtml("Remember me")),
		button("Login").withName("login-action").withValue("login"),
		button("Sign up").withName("login-action").withValue("signup")
	);
	
	/* Cool checkboxes https://stackoverflow.com/questions/4148499/how-to-style-a-checkbox-using-css */
	/* Custom checkbox https://stackoverflow.com/questions/44299150/set-text-inside-a-check-box/44299305 */
	/* TODO the username should be prefilled in "value" on the next page if the user clicks "Sign up" */
	private static DomContent loginForm() {
		return postForm().withClass("login-form").with(LOGIN_FORM_CONTENT);
	}
	
	/** Respond to {@code request} with a 303 redirect to the page, and set the language. **/
	private void processLangForm(Request request, String langTag) {
		storeLocale(request, langTag);
		doSeeOther(request, request.getHttpURI().getDecodedPath());
		request.setHandled(true);
	}
	
	private static void doSeeOther(Request request, String location) {
		request.getResponse().setHeader(HttpHeader.LOCATION, location);
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
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
	
	private void mainPageGenerator(Request request) {
		mainPageGenerator(request, Collections.emptyIterator());
	}
	
	private void mainPageGenerator(Request request, Iterator<String> path) {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			var result = doProcessForms(request, name -> switch(name) {
				case "lang-select" -> stringReader();
				default -> throw new RuntimeException("Unknown form field \"" + name + "\"");
			});
			result.apply("lang-select").ifPresent(lang -> processLangForm(request, lang));
			return;
		}
		
		if(path.hasNext()) {
			try {
				doDecoratedPage(HttpStatus.NOT_FOUND_404, request,pageLocale(request), 
					"No such path", 
					p(rawHtml("No such path " + prettyUrl(request)))
				);
			} catch(SQLException e) {
				throw new RuntimeException(e);
			}
			return;
		}
		
		try {
			Locale bestLocale = pageLocale(request);
			
			doDecoratedPage(HttpStatus.OK_200, request, bestLocale, "RedNet!",
				each(
					h1(translated("greeting")).withClass("title"),
					dynamic(state -> {
						try {
							return eachStreamed(
								cm.getArticlesForLocale(bestLocale).stream().map(
									article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
								)
							);
						} catch(SQLException e) {
							throw new RuntimeException(e);
						}
					})
				)	
			);
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	private void uploadGenerator(Request request, Iterator<String> path) throws SQLException {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> doGetUploadPage(request);
			case POST -> {
				var result = doProcessForms(request, name -> switch(name) {
					case "lang-select", "username", "comment", "login-action", "login-form" -> stringReader();
					case "password" -> secretBytesReader();
					case "upload" -> fileReader();
					default -> throw new RuntimeException("Unknown form field \"" + name + "\"");
				});
				
				result.apply("lang-select").ifPresent(lang -> processLangForm(request, (String)lang));
				result.apply("password").ifPresent(data -> Arrays.fill((byte[])data, (byte)0));
				
				Locale locale = pageLocale(request);
				
				@SuppressWarnings("unchecked") Optional<String> loginAction = (Optional<String>)result.apply("login-action");
				
				switch(loginAction.orElse("other")) {
					case "login" -> {
						@SuppressWarnings("unchecked") Optional<String> username = (Optional<String>)result.apply("username");
						doDecoratedPage(HttpStatus.OK_200, request, locale, "Upload", p(username.orElse("No username provided")));
					}
					
					default -> throw new RuntimeException("not implemented");
					
					case "other" -> doGetUploadPage(request);
				}
			}
			
			default -> throw new RuntimeException("Unsupported HTTP method");
		}
	}
	
	private void doGetUploadPage(Request request) throws SQLException {
		doDecoratedPage(HttpStatus.OK_200, request, pageLocale(request), "Upload", mainForm());
	}
	
	private void articlePageProcessor(Request request, Iterator<String> path) throws SQLException, IOException {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			var result = doProcessForms(request, name -> switch(name) {
				case "lang-select" -> stringReader();
				default -> throw new RuntimeException("Unknown form field");
			});
			result.apply("lang-select").ifPresent(lang -> processLangForm(request, lang));
			//TODO also apply other stuff here
			return;
		}
		
		Optional<String> name = Optional.ofNullable(path.hasNext() ? path.next() : null);
		Locale mainLocale = pageLocale(request);
		
		//TODO make sure there isn't an extra component in the path
		
		if(name.isEmpty()) {
			doWrongArticlePath(request, mainLocale);
			return;
		}
		
		String urlname = name.get();
		List<Locale> supported = cm.getArticleLocales(urlname);
		
		if(path.hasNext()) {
			doDecoratedPage(HttpStatus.NOT_FOUND_404, request, mainLocale, "Not Found", 
				p(
					rawHtml("There is no such page " + prettyUrl(request))
				)
			);
		}
		
		if(supported.isEmpty()) {
			doNoSuchArticle(request, mainLocale, urlname);
			return;
		}
		
		Locale articleLocale = HttpUser.bestLocale(request, supported);
		Optional<Article<MarkdownContent>> article = cm.getLatestArticle(urlname, articleLocale, MarkdownContent::new);
		
		if(article.isEmpty()) {
			doNoSuchArticle(request, mainLocale, urlname);
			return;
		}
		
		doDecoratedPage(HttpStatus.OK_200, request, mainLocale, article.get().title(),
			main().withLang(articleLocale.toLanguageTag()).with(
				article(
					h1(article.get().title()).withClass("title-article"),
					articleLocale.equals(mainLocale) ? null :
						h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
					div().withClass("markdown").with(article.get().content())
				)
			)	
		);
	}
	
	private void doDecoratedPage(int status, Request request, Locale mainLocale, String title, DomContent body) throws SQLException {
		doGetHtmlStreamed(request, status, List.of(mainLocale),
			context(request, translator, Map.of(),
				page(title, mainLocale,
					content(
						header(request),
						body
					)
				)
			)
		);
	}
	
	private static String prettyUrl(Request request) {
		return "\"" + request.getHttpURI().getHost() + request.getHttpURI().getDecodedPath() + "\"";
	}
	
	/* TODO translate error pages */
	
	private void doNoSuchArticle(Request request, Locale mainLocale, String urlname) throws SQLException {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, mainLocale, "No such article",
			p("No such article \"" + urlname + "\"")
		);
	}
	
	private void doWrongArticlePath(Request request, Locale mainLocale) throws SQLException {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, mainLocale, "Not an article",
			each(
				p("Please specify an article URL component: ").with(
					span(request.getHttpURI().toURI().normalize() + "<article-path>")
				),
				a("Go home").withHref("/")
			)
		);
	}
	
	@RequiresDynamicHtml
	@RequiresNamedHtml({"content"})
	private static final DomContent STATIC_CENTERED_CONTENT = each(
		div().withClasses("page-body", "headerless-content").with(
			div().withClass("content-left"),
			mainBox(
				named("content")
			),
			div().withClass("content-right")
		)
	);
	
	private static DomContent staticContent(DomContent... content) {
		return dynamic(STATIC_CENTERED_CONTENT, Map.of("content", each(content)));
	}
	
	private void doGeneric404(Request request, Iterator<String> path) throws SQLException {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, pageLocale(request), "No Such Path",
			p("No such path " + prettyUrl(request))
		);
	}
	
	private void exceptionPageHandler(Request request) {
		Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
		doGetHtmlStreamed(request, HttpStatus.INTERNAL_SERVER_ERROR_500, List.of(),
			context(request, translator, Map.of(),
				page("Error", Locale.forLanguageTag("en-US"),
					staticContent(
						p("Sorry, there was an unexpected error!"),
						a("Go home").withHref("/")
					)
				)
			)
		);
	}
}
