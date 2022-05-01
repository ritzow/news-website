package net.ritzow.news;

import j2html.tags.DomContent;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import net.ritzow.news.component.CommonComponents;
import net.ritzow.news.database.ContentManager;
import net.ritzow.news.page.ArticlePage;
import net.ritzow.news.page.ExceptionPage;
import net.ritzow.news.page.MainPage;
import net.ritzow.news.page.SessionPage;
import net.ritzow.news.response.ContentSource;
import net.ritzow.news.response.NamedResourceConsumer;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.JettySetup.newStandardServer;
import static net.ritzow.news.PageTemplate.context;
import static net.ritzow.news.ResourceUtil.properties;
import static net.ritzow.news.ResponseUtil.*;

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
	
	public static NewsSite start(boolean requireSni, Path keyStore, String keyStorePassword, 
			Set<String> peers, InetAddress... bind) throws Exception {
		var server = new NewsSite(requireSni, keyStore, keyStorePassword, peers, bind);
		server.server.start();
		return server;
	}
	
	public static final NamedResourceConsumer<NewsSite> 
		RES_GLOBAL_CSS = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/css/global.css", "text/css")),
		RES_ICON = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/image/icon.svg", "image/svg+xml")),
		RES_OPENSEARCH = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/xml/opensearch.xml", "application/opensearchdescription+xml")),
		RES_FONT = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/font/OpenSans-Regular.ttf", "font/ttf"));
	
	public static final NamedResourceConsumer<NewsSite>
		RES_FONT_FACE = NamedResourceConsumer.ofHashed(
			ContentSource.ofString("""
				@font-face {
					font-family: "Open Sans";
					src: url("URLHERE") format("truetype");
					font-display: swap;
				}
				""".replace("URLHERE", contentPath(RES_FONT)),
				"text/css"
			)
		);
	
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
		RequestConsumer<NewsSite> route = matchStaticPaths(
			rootNoMatchOrNext(
				MainPage::mainPageGenerator,
				NewsSite::doGeneric404,
				entry("article", ArticlePage::articlePageProcessor),
				entry("content", rootNoMatchOrNext(
					null,
					NewsSite::doGeneric404,
					RES_ICON,
					RES_GLOBAL_CSS,
					RES_OPENSEARCH,
					RES_FONT,
					RES_FONT_FACE
				)),
				entry("session", SessionPage::sessionPage)
			)
		);
		
		server = newStandardServer(
			requireSni,
			keyStore, 
			keyStorePassword,
			consumer(this, route), 
			request -> ExceptionPage.exceptionPageHandler(request, this), 
			bind
		);
	}
	
	public static void doSessionInitResponse(Request request) throws IOException {
		/* Shared session handler between connectors */
		var session = request.getSessionHandler().getSession(request.getParameter("id"));
		if(session != null) {
			request.setSession(session);
			var cookie = request.getSessionHandler().getSessionCookie(session, "/", true);
			request.getResponse().addCookie(cookie);
		}
		doEmptyResponse(request, HttpStatus.NO_CONTENT_204);
	}
	
	public static String serverTime(Locale locale) {
		return ZonedDateTime.now().format(
			DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale));
	}
	
	public static DomContent memoryUsage(Locale locale) {
		long kbUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1000;
		NumberFormat format = NumberFormat.getIntegerInstance(locale);
		return text(format.format(kbUsed) + " KB");
	}
	
	public static Locale pageLocale(Request request, NewsSite site) {
		return HttpUser.bestLocale(request, site.cm.getSupportedLocales());
	}
	
	public static void storeLocale(Request request, NewsSite site, String languageTag) {
		Locale selected = Locale.forLanguageTag(languageTag);
		Optional<Locale> existing = site.cm.getSupportedLocales().stream().filter(selected::equals).findAny();
		HttpUser.session(request).locale(existing.orElseThrow(() -> new RuntimeException("Invalid selected locale \"" + languageTag + "\"")));
	}
	
	public static void doDecoratedPage(int status, Request request, NewsSite site, Locale mainLocale, String title, DomContent body) {
		doGetHtmlStreamed(request, status, List.of(mainLocale),
			context(request, site.translator, Map.of(),
				CommonComponents.page(request, site, title, 
					contentPath(RES_ICON),
					contentPath(RES_OPENSEARCH),
					contentPath(RES_GLOBAL_CSS),
					mainLocale,
					CommonComponents.content(
						CommonComponents.header(request, site),
						body
					)
				)
			)
		);
	}
	
	public static String prettyUrl(Request request) {
		return "\"" + request.getHttpURI().getHost() + request.getHttpURI().getDecodedPath() + "\"";
	}
	
	/* TODO translate error pages */
	
	public static void doNoSuchArticle(Request request, NewsSite site, Locale mainLocale, String urlname) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "No such article",
			p("No such article \"" + urlname + "\"")
		);
	}
	
	public static void doWrongArticlePath(Request request, NewsSite site, Locale mainLocale) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "Not an article",
			each(
				p("Please specify an article URL component: ").with(
					span(request.getHttpURI().toURI().normalize() + "<article-path>")
				),
				a("Go home").withHref("/")
			)
		);
	}
	
	private static void doGeneric404(Request request, NewsSite site, @SuppressWarnings("Unused") Iterator<String> path) {
		doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, pageLocale(request, site), "No Such Path",
			p("No such path " + prettyUrl(request))
		);
	}
	
}
