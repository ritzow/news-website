package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.ritzow.jetstart.*;
import net.ritzow.news.ContentManager.Article;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hsqldb.cmdline.SqlFile;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.jetstart.JettyHandlers.newPath;
import static net.ritzow.news.PageTemplate.*;

public class RunSite {
	
	private static ContentManager cm;
	
	public static void main(String[] args) throws Exception {
		cm = ContentManager.ofMemoryDatabase();
		var content = resourceAsString("/content/Sandbox2D.md");
		cm.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme", content);
		cm.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme v2", content + "\n\nAND SOME EXTRA.");
		//cm.newArticle("sandbox2d", Locale.forLanguageTag("es-US"), "Sandbox2D Readme v2 Spanish", content);
		cm.newArticle("blahblah", Locale.forLanguageTag("es"), "Sandbox2D Readme Spanish", "***HELLO!!!***");
		
		SplittableRandom random = new SplittableRandom(0);
		
		for(int i = 0; i < 25; i++) {
			cm.newArticle(Integer.toHexString(i),
				Locale.forLanguageTag("en-US"), generateGibberish(random, 3, 5), generateGibberish(random, random.nextInt(200, 1000), 6));
		}
		
		/* TODO use integrity attribute to verify content if delivered via CDN */
		/* TODO and use crossorigin="anonymous" */
		//TODO set loading="lazy" on img HTML elements.
		
		var server = JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")), System.getProperty("net.ritzow.pass"),
			newPath(
				dynamicHandler(RunSite::mainPageGenerator, TRANSLATIONS),
				entry("/opensearch", new StaticContentHandler(open("/xml/opensearch.xml"), "application/opensearchdescription+xml")),
				entry("/style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("/article", dynamicHandler(RunSite::articlePageGenerator, TRANSLATIONS)),
				entry("/icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml"))
			),
			dynamicHandler(RunSite::errorGenerator, TRANSLATIONS)
		);
		
		server.start();
		
//		JmDNS mdns = JmDNS.create(InetAddress.getLocalHost(), "news.local");
		
		/* Shutdown on user input */
		try(var reader = new InputStreamReader(System.in);
		    var scan = new Scanner(reader)) {
			SqlFile file = new SqlFile(reader, "<stdin>", System.out, "UTF-8", true, (URL)null);
			while(true) {
				try(var db = cm.getConnection()) {
					file.setConnection(db);
					file.execute();
				}
			}
		} catch(IOException | SQLException e) {
			e.printStackTrace();
		}

//		mdns.close();
		server.stop();
		cm.shutdown();
		server.join();
	}
	
	private static String resourceAsString(String resource) throws IOException {
		try(var in = RunSite.class.getResourceAsStream(resource)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
	
	private static Supplier<InputStream> open(String resource) {
		return () -> RunSite.class.getResourceAsStream(resource);
	}
	
	private static final HtmlTag PAGE_OUTLINE = fullPage("RedNet",
		div()/*.withLang("es")*/.withClass("page-body").with(
			freeze(
				div().withClasses("header", "foreground").with(logo("/icon.svg"))
			),
			mainBox(
				dynamic(state -> state.named("content"))
			)
		),
		div().withClass("page-separator"),
		footer().withClasses("page-footer", "footer", "foreground").with(
			span(
				text("Server Time: "),
				dynamic(state -> rawHtml(ZonedDateTime.now().format(
					DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(Locale.ROOT)))),
				text(" Heap: "),
				dynamic(state -> text((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000 + " KB"))
			)
		)
	);
	
	private static final Translator<String> TRANSLATIONS =
		Translator.ofProperties(properties("/lang/welcome.properties"));
	
	public static void doGet(Request request, Translator<String> translations,
		Function<Request, HtmlResult> onRequest) throws IOException {
		
		/* Generate result first to catch errors early */
		HtmlResult result = onRequest.apply(request);
		request.getResponse().setStatus(result.status());
		request.getResponse().setHeader(HttpHeader.TRANSFER_ENCODING, "chunked");
		request.getResponse().setContentType("text/html; charset=utf-8");
		request.getResponse().setHeader(HttpHeader.CACHE_CONTROL, "no-store");
		request.getResponse().setHeader(HttpHeader.REFERER, "no-referrer");
		Writer body = request.getResponse().getWriter();
		Object model = new HtmlSessionState(request, translations, result.named());
		result.html().render(FlatHtml.into(body).appendUnescapedText("<!DOCTYPE html>"), model);
		body.flush();
		request.setHandled(true);
	}
	
	private static Handler dynamicHandler(Function<Request, HtmlResult> function, Translator<String> translator) {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest
					request, HttpServletResponse response) throws IOException {
				doGet(baseRequest, translator, function);
			}
		};
	}
	
	private static HtmlResult mainPageGenerator(Request request) {
		Optional<String> next = StaticPathHandler.peekComponent(request);
		if(next.isEmpty()) {
			try {
				return new HtmlResult(PAGE_OUTLINE, Map.of(
					"content", div().withClass("article-list").with(
						Stream.concat(
							Stream.of(
								h1(translated("greeting")).withClass("title")
							),
							cm.getArticlesForLocale(HttpUser.localesForUser(request).get(0)).stream().map(
								article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
							)
						).toArray(DomContent[]::new)
					)
				));
			} catch(SQLException | IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			//errorGenerator(request);
			//return new HtmlResult(null, Map.of(), 404);
			//TODO allow error returns, create better error handling framework
			throw new RuntimeException("invalid page " + next.get());
		}
	}
	
	private static HtmlResult articlePageGenerator(Request request) {
		Optional<String> name = StaticPathHandler.peekComponent(request);
		if(name.isPresent()) {
			try {
				String urlname = name.get();
				List<Locale> supported = cm.getArticleLocales(urlname);
				DomContent content;
				if(supported.isEmpty()) {
					content = p("No such article " + urlname);
				} else {
					Locale lang = HttpUser.bestLocale(request, supported);
					Optional<Article<MarkdownContent>> article = cm.getLatestArticle(urlname, lang, reader -> {
						try {
							return new MarkdownContent(reader);
						} catch(IOException e) {
							throw new UncheckedIOException(e);
						}
					});
					if(article.isPresent()) {
						content = div().withLang(lang.toLanguageTag()).with(
							h1(article.get().title()).withClass("title-article"),
							div().withClass("markdown").with(article.get().content())
						);
					} else {
						content = p("No such article " + urlname);
					}
				}
				return new HtmlResult(PAGE_OUTLINE, Map.of("content", content));
			} catch(SQLException | IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("Invalid path");
		}
	}
	
	private static HtmlResult errorGenerator(Request request) {
		var html = fullPage("Error",
			mainBox(
				p("Sorry, there was an error!"),
				a("Go home").withHref("/")
			)
		);
		
		return new HtmlResult(html, Map.of(), HttpStatus.NOT_FOUND_404);
	}
	
	private static Properties properties(String path) {
		try(var in = new InputStreamReader(RunSite.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			var p = new Properties();
			p.load(in);
			return p;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz";
	
	private static String generateGibberish(SplittableRandom random, int words, int maxWordSize) {
		StringBuilder builder = new StringBuilder(words * maxWordSize/2);
		for(int i = 0; i < words; i++) {
			char[] word = new char[random.nextInt(maxWordSize + 1) + 1];
			for(int j = 0; j < word.length; j++) {
				word[j] = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length()));
			}
			if(random.nextBoolean()) {
				word[0] = Character.toUpperCase(word[0]);
			}
			builder.append(word).append(' ');
		}
		return builder.toString();
	}
	
}
