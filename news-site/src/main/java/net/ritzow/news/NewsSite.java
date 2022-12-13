package net.ritzow.news;

import j2html.tags.DomContent;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.ritzow.news.database.ContentManager;
import net.ritzow.news.page.*;
import net.ritzow.news.response.CachingImmutableRequestConsumer;
import net.ritzow.news.response.ContentSource;
import net.ritzow.news.response.NamedResourceConsumer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;

import static j2html.TagCreator.text;
import static java.util.Map.entry;
import static net.ritzow.news.JettySetup.newStandardServer;
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
	public final ContentManager cm; //TODO use intellij to refactor cm into getter method
	public final Translator<String> translator;
	public final Set<String> peers;
	
	public static NewsSite start(boolean requireSni, KeyStore keyStore, String keyStorePassword, 
			Set<String> peers, InetAddress... bind) throws Exception {
		var server = new NewsSite(requireSni, keyStore, keyStorePassword, peers, bind);
		server.server.start();
		return server;
	}
	
	public void waitForExit() throws InterruptedException {
		server.join();
	}
	
	public static final NamedResourceConsumer<NewsSite> 
		RES_GLOBAL_CSS = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/css/global.css", "text/css")),
		RES_ICON = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/image/icon.svg", "image/svg+xml")),
		RES_FONT = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/font/OpenSans-Regular.ttf", "font/ttf"));
	
	private static final ContentSource 
		RES_OPENSEARCH = ContentSource.ofString(OpenSearch.generateOpensearchXml(), "application/opensearchdescription+xml");
	
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
	
	private NewsSite(boolean requireSni, KeyStore keyStore, String keyStorePassword, Set<String> peers, InetAddress... bind) throws
		SQLException {
		cm = ContentManager.ofMemoryDatabase();
		ContentUtil.genArticles(cm);
		translator = Translator.ofProperties(properties("/lang/welcome.properties"));
		this.peers = peers;
		RequestConsumer<NewsSite> route = matchStaticPaths(
			rootNoMatchOrNext(
				MainPage::mainPageGenerator,
				ErrorPages::doGeneric404,
				entry("article", ArticlePage::articlePageProcessor),
				entry("opensearch", new CachingImmutableRequestConsumer<>(RES_OPENSEARCH, Duration.ZERO)),
				entry("search", SearchPage::searchPage),
				entry("content", rootNoMatchOrNext(
					ErrorPages::doGeneric404,
					ErrorPages::doGeneric404,
					/* Content hashes */
					RES_ICON,
					RES_GLOBAL_CSS,
					RES_FONT,
					RES_FONT_FACE
				)),
				entry("session", SessionPage::sessionPage),
				entry("kill", ShutdownPage::shutdownPage),
				entry("streamtest", NewsSite::streamingPage)
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
	
	private static void streamingPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		ByteBuffer buf = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap("test message 123\n"));
		request.getResponse().getHttpFields().add(HttpHeader.CONTENT_TYPE, "text/plain; charset=utf-8");
		while(!request.getResponse().getHttpOutput().isClosed()) {
			request.getResponse().getHttpOutput().write(buf);
			request.getResponse().flushBuffer();
			buf.flip();
			try {
				Thread.sleep(1000);
			} catch(InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
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

	public static String prettyUrl(Request request) {
		return "\"" + request.getHttpURI().getHost() + request.getHttpURI().getDecodedPath() + "\"";
	}

	public static String websiteTitle() {
		return System.getProperties().getProperty("title", System.getProperties().getProperty("hostname"));
	}
}
