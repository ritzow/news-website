package net.ritzow.news;

import j2html.tags.DomContent;
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
import net.ritzow.news.component.CommonComponents;
import net.ritzow.news.page.ArticlePage;
import net.ritzow.news.page.ExceptionPage;
import net.ritzow.news.page.MainPage;
import net.ritzow.news.page.SessionPage;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.JettySetup.newStandardServer;
import static net.ritzow.news.PageTemplate.context;
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
				MainPage::mainPageGenerator,
				NewsSite::doGeneric404,
				entry("article", ArticlePage::articlePageProcessor),
//				entry("shutdown", this::shutdownPage),
				entry("opensearch", staticContent(jarResourceOpener("/xml/opensearch.xml"),
					"application/opensearchdescription+xml")),
				entry("style.css", staticContent(jarResourceOpener("/css/global.css"), "text/css")),
				entry("icon.svg", staticContent(jarResourceOpener("/image/icon.svg"), "image/svg+xml")),
				entry("opensans.ttf", staticContent(jarResourceOpener("/font/OpenSans-Regular.ttf"), "font/ttf")),
				entry("session", SessionPage::sessionPage)
			)
		);
		
		server = newStandardServer(
			requireSni,
			keyStore, 
			keyStorePassword,
			request -> route.accept(request, this), 
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
	
	public static void storeLocale(Request request, NewsSite site, String languageTag) {
		Locale selected = Locale.forLanguageTag(languageTag);
		Optional<Locale> existing = site.cm.getSupportedLocales().stream().filter(selected::equals).findAny();
		HttpUser.session(request).locale(existing.orElseThrow(() -> new RuntimeException("Invalid selected locale \"" + languageTag + "\"")));
	}
	
	public static void doDecoratedPage(int status, Request request, NewsSite site, Locale mainLocale, String title, DomContent body) {
		doGetHtmlStreamed(request, status, List.of(mainLocale),
			context(request, site.translator, Map.of(),
				CommonComponents.page(request, site, title, mainLocale,
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
