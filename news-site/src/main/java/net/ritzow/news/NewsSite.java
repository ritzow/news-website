package net.ritzow.news;

import j2html.tags.DomContent;
import j2html.tags.specialized.BodyTag;
import j2html.tags.specialized.ButtonTag;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import net.ritzow.news.ContentManager.Article;
import net.ritzow.news.page.Login;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.*;
import static net.ritzow.news.JettySetup.newStandardServer;
import static net.ritzow.news.PageTemplate.head;
import static net.ritzow.news.PageTemplate.*;
import static net.ritzow.news.ResourceUtil.jarResourceOpener;
import static net.ritzow.news.ResourceUtil.properties;
import static net.ritzow.news.ResponseUtil.*;
import static net.ritzow.news.StaticContentHandler.staticContent;

// TODO use integrity attribute to verify content if delivered via CDN and use crossorigin="anonymous"
// TODO set loading="lazy" on img HTML elements.
// TODO look into using noscript if ever using javascript
// TODO use interactive elements https://developer.mozilla.org/en-US/docs/Web/HTML/Element#interactive_elements
// TODO use <details> for accordion items
// TODO use Link header to prefetch font, add parameter to doGetHtmlStreamed

public final class NewsSite {
	public final Server server;
	public final ContentManager cm;
	public final Translator<String> translator;
	public final Set<String> peers;
	
	public static NewsSite start(boolean requireSni, Path keyStore, String keyStorePassword, Set<String> peers, InetAddress... bind) throws Exception {
		var server = new NewsSite(requireSni, keyStore, keyStorePassword, peers, bind);
		server.server.start();
		return server;
	}
	
	private NewsSite(boolean requireSni, Path keyStore, String keyStorePassword, Set<String> peers, InetAddress... bind) throws
			CertificateException,
			IOException,
			KeyStoreException,
			NoSuchAlgorithmException,
			SQLException {
		cm = ContentManager.ofMemoryDatabase();
		ContentUtil.genArticles(cm);
		translator = Translator.ofProperties(properties("/lang/welcome.properties"));
		this.peers = peers;
		var route = matchStaticPaths(
			rootNoMatchOrNext(
				NewsSite::mainPageGenerator,
				NewsSite::doGeneric404,
				entry("article", NewsSite::articlePageProcessor),
//				entry("shutdown", this::shutdownPage),
				entry("opensearch", staticContent(jarResourceOpener("/xml/opensearch.xml"),
					"application/opensearchdescription+xml")),
				entry("style.css", staticContent(jarResourceOpener("/css/global.css"), "text/css")),
				entry("icon.svg", staticContent(jarResourceOpener("/image/icon.svg"), "image/svg+xml")),
				entry("opensans.ttf", staticContent(jarResourceOpener("/font/OpenSans-Regular.ttf"), "font/ttf")),
				entry("session", NewsSite::sessionPage)
			)
		);
		
		server = newStandardServer(
			requireSni,
			keyStore, 
			keyStorePassword,
			request -> route.accept(request, this), 
			request -> exceptionPageHandler(request, this), 
			bind
		);
	}
	
	//TODO this only works if privacy mode is off, because it blocks cross-site cookies.
	//TODO maybe use redirects instead?
	private static void sessionPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				String origin = request.getHttpFields().get(HttpHeader.ORIGIN);
				if(origin != null) {
					URI originUrl = URI.create(origin);
					if(HttpScheme.HTTPS.is(originUrl.getScheme()) && site.peers.contains(originUrl.getHost())) {
						request.getResponse().getHttpFields()
							.add("Access-Control-Allow-Methods", "GET")
							.add("Access-Control-Allow-Origin", origin)
							.add("Access-Control-Allow-Credentials", "true")
							.add("Access-Control-Allow-Headers", HttpHeader.COOKIE.asString())
							.add(HttpHeader.VARY, HttpHeader.ORIGIN.asString());
						doSessionInitResponse(request);
					} else {
						doEmptyResponse(request, HttpStatus.UNAUTHORIZED_401);
					}
				} else {
					doEmptyResponse(request, HttpStatus.UNAUTHORIZED_401);		
				}
			}
		}
	}
	
	private static void doSessionInitResponse(Request request) throws IOException {
		/* Shared session handler between connectors */
		var session = request.getSessionHandler().getSession(request.getParameter("id"));
		if(session != null) {
			request.setSession(session);
			var cookie = request.getSessionHandler().getSessionCookie(session, "/", true);
			request.getResponse().addCookie(cookie);
		}
		doEmptyResponse(request, HttpStatus.NO_CONTENT_204);
	}
	
	@RequiresNamedHtml({"full-content", "time", "heap"})
	private static final BodyTag PAGE_BODY_HTML = body().withId("top").with(
		named("full-content"),
		div().withClass("page-separator"),
		footer().withClasses("page-footer").with(
			/*a().withClasses("left-footer", "foreground").withHref("/shutdown").with(
				rawHtml("Shutdown")
			),*/
			span().withClasses("right-footer", "foreground").with(
				text("Server Time: "),
				time(named("time")),
				text(" Heap: "),
				named("heap")
			)
		)
	);

	@RequiresDynamicHtml
	private static DomContent page(Request request, NewsSite site, String title, Locale locale, DomContent fullContent) {
		return html().withLang(locale.toLanguageTag()).with(
			head(request, title, site.peers.stream().filter(host -> !host.equals(request.getHttpURI().getHost())).collect(Collectors.toSet())),
			dynamic(PAGE_BODY_HTML, Map.of(
				"full-content", fullContent,
				"time", rawHtml(serverTime(locale)),
				"heap", dynamic(state -> memoryUsage(locale))
			))
		);
	}
	
	private static String serverTime(Locale locale) {
		return ZonedDateTime.now().format(
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale));
	}
	
	private static DomContent memoryUsage(Locale locale) {
		long kbUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000;
		NumberFormat format = NumberFormat.getIntegerInstance(locale);
		return text(format.format(kbUsed) + " KB");
	}
	
	public static Locale pageLocale(Request request, NewsSite site) {
		return HttpUser.bestLocale(request, site.cm.getSupportedLocales());
	}
	
	/*private void shutdownPage(Request request, Iterator<String> path) {
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
	}*/
	
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
	
	private static DomContent header(Request request, NewsSite site) {
		List<Locale> locales = site.cm.getSupportedLocales();
		Locale bestCurrent = HttpUser.bestLocale(request, locales);
		return each(
			LOGO_HTML,
			postForm()
				.withClasses("lang-selector")
				.attr("autocomplete", "off")
				.with(
					locales.stream().map(locale -> langButton(locale, bestCurrent))
				),
				accountHeader(request)
		);
	}
	
	private static DomContent accountHeader(Request request) {
		return HttpUser.getExistingSession(request)
			.flatMap(SessionData::user)
			.map(Login::loggedInForm)
			.orElseGet(Login::loginForm);
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
	
	/** Respond to {@code request} with a 303 redirect to the page, and set the language. **/
	private static void processLangForm(Request request, NewsSite site, String langTag) {
		storeLocale(request, site, langTag);
		doRefreshPage(request);
	}
	
	private static void storeLocale(Request request, NewsSite site, String languageTag) {
		Locale selected = Locale.forLanguageTag(languageTag);
		Optional<Locale> existing = site.cm.getSupportedLocales().stream().filter(selected::equals).findAny();
		HttpUser.session(request).locale(existing.orElseThrow(() -> new RuntimeException("Invalid selected locale \"" + languageTag + "\"")));
	}
	
	private static final FormWidget LOGIN_FORM = FormWidget.of(
		FormField.required("username", Forms::stringReader),
		FormField.required("password", Forms::secretBytesReader),
		FormField.optional("login-remember", Forms::stringReader),
		FormField.required("login-action", Forms::stringReader)
	);
	
	private static final FormWidget LANG_SELECT_FORM = FormWidget.of(
		FormField.required("lang-select", Forms::stringReader)
	);
	
	private static final FormWidget LOGGED_IN_FORM = FormWidget.of(
		FormField.required("logout", Forms::stringReader)
	);
	
	private static void mainPageGenerator(Request request, NewsSite site) {
		mainPageGenerator(request, site, Collections.emptyIterator());
	}
	
	private static void mainPageGenerator(Request request, NewsSite site, Iterator<String> path) {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				if(path.hasNext()) {
					doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, pageLocale(request, site),
						"No such path",
						p(rawHtml("No such path " + prettyUrl(request)))
					);
					return;
				}
				
				Locale bestLocale = pageLocale(request, site);
				doDecoratedPage(HttpStatus.OK_200, request, site, bestLocale, "RedNet!",
					generateArticlesList(bestLocale, site)
				);
			}
			
			/* TODO check for vulnerability from cross-site POST request. https://stackoverflow.com/a/19322811/2442171 */
			/* Don't use Referer header for this purpose https://stackoverflow.com/a/6023980/2442171 */
			/* https://security.stackexchange.com/a/133945 */
			case POST -> doFormResponse(request,
				entry(LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
				entry(LANG_SELECT_FORM, values -> doLangSelectForm(request, site, values)),
				entry(LOGGED_IN_FORM, values -> Login.doLoggedInForm(request ,values))
			);
		}
	}
	
	private static URI doLangSelectForm(Request request, NewsSite site, Function<String, Optional<Object>> values) {
		values.apply("lang-select").ifPresent(lang -> processLangForm(request, site, (String)lang));
		return request.getHttpURI().toURI();
	}
	
	private static DomContent generateArticlesList(Locale bestLocale, NewsSite site) {
		return each(
			h1(translated("greeting")).withClass("title"),
			dynamic(state -> eachStreamed(
				site.cm.getArticlesForLocale(bestLocale).stream().map(
					article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
				)
			))
		);
	}
	
	private static void articlePageProcessor(Request request, NewsSite site, Iterator<String> path) {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				Optional<String> name = Optional.ofNullable(path.hasNext() ? path.next() : null);
				Locale mainLocale = pageLocale(request, site);
				
				//TODO make sure there isn't an extra component in the path
				
				if(name.isEmpty()) {
					doWrongArticlePath(request, site, mainLocale);
					return;
				}
				
				String urlname = name.orElseThrow();
				List<Locale> supported = site.cm.getArticleLocales(urlname);
				
				if(path.hasNext()) {
					doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "Not Found",
						p(
							rawHtml("There is no such page " + prettyUrl(request))
						)
					);
				}
				
				if(supported.isEmpty()) {
					doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				Locale articleLocale = HttpUser.bestLocale(request, supported);
				Optional<Article<MarkdownContent>> article = site.cm.getLatestArticle(urlname, articleLocale, MarkdownContent::new);
				
				if(article.isEmpty()) {
					doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				doDecoratedPage(HttpStatus.OK_200, request, site, mainLocale, article.orElseThrow().title(),
					generateArticlePage(articleLocale, mainLocale, article.orElseThrow())
				);
			}
			
			case POST -> doFormResponse(request, 
				entry(LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
				entry(LANG_SELECT_FORM, values -> doLangSelectForm(request, site, values)),
				entry(LOGGED_IN_FORM, values -> Login.doLoggedInForm(request, values))
			);
		}
	}
	
	private static DomContent generateArticlePage(Locale articleLocale, Locale mainLocale, Article<MarkdownContent> article) {
		return main().withLang(articleLocale.toLanguageTag()).with(
			article(
				h1(article.title()).withClass("title-article"),
				articleLocale.equals(mainLocale) ? null :
					h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
				div().withClass("markdown").with(article.content())
			)
		);	
	}
	
	private static void doDecoratedPage(int status, Request request, NewsSite site, Locale mainLocale, String title, DomContent body) {
		doGetHtmlStreamed(request, status, List.of(mainLocale),
			context(request, site.translator, Map.of(),
				page(request, site, title, mainLocale,
					content(
						header(request, site),
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
	
	private static void doNoSuchArticle(Request request, NewsSite site, Locale mainLocale, String urlname) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "No such article",
			p("No such article \"" + urlname + "\"")
		);
	}
	
	private static void doWrongArticlePath(Request request, NewsSite site, Locale mainLocale) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "Not an article",
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
	
	private static DomContent headerlessContent(DomContent... content) {
		return dynamic(STATIC_CENTERED_CONTENT, Map.of("content", each(content)));
	}
	
	private static void doGeneric404(Request request, NewsSite site, @SuppressWarnings("Unused") Iterator<String> path) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, pageLocale(request, site), "No Such Path",
			p("No such path " + prettyUrl(request))
		);
	}
	
	private static void exceptionPageHandler(Request request, NewsSite site) {
		Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
		doGetHtmlStreamed(request, HttpStatus.INTERNAL_SERVER_ERROR_500, List.of(),
			context(request, site.translator, Map.of(),
				page(request, site, "Error", Locale.forLanguageTag("en-US"),
					headerlessContent(
						p("Sorry, there was an unexpected error!"),
						a("Go home").withHref("/")
					)
				)
			)
		);
	}
}
