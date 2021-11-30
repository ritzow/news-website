package net.ritzow.news;

import j2html.TagCreator;
import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Stream;
import net.ritzow.jetstart.*;
import net.ritzow.news.ContentManager.Article;
import org.eclipse.jetty.http.*;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.MultiPartParser;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.hsqldb.cmdline.SqlFile;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.jetstart.JettyHandlers.newPath;
import static net.ritzow.news.PageTemplate.*;

public class RunSite {
	
	private static ContentManager cm;
	
	public static void main(String[] args) throws Exception {
		System.out.println(Runtime.version());
		
		cm = ContentManager.ofMemoryDatabase();
		var content = resourceAsString("/content/Sandbox2D.md");
		cm.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme", content);
		cm.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme v2", content + "\n\nAND SOME EXTRA.");
		cm.newArticle("sandbox2d", Locale.forLanguageTag("es"), "Sandbox2D Readme v2 Spanish", content);
		cm.newArticle("blahblah", Locale.forLanguageTag("es"), "blahblah! Español", "***HELLO!!!ñ***");
		
		RandomGenerator random = RandomGeneratorFactory.getDefault().create(0);
		
		for(int i = 0; i < 25; i++) {
			int length = random.nextInt(200, 1000);
			String title = generateGibberish(random, 3, 5);
			for(Locale locale : cm.getAvailableLocales()) {
				if(random.nextFloat() < 0.7) {
					cm.newArticle(Integer.toHexString(i),
						locale, title + " " + locale.getDisplayLanguage(locale), generateGibberish(random, length, 6));
				}
			}
		}
		
		/* TODO use integrity attribute to verify content if delivered via CDN */
		/* TODO and use crossorigin="anonymous" */
		//TODO set loading="lazy" on img HTML elements.
		//TODO deliver article content using svg elements
		//TODO look into using noscript if ever using javascript
		//TODO use interactive elements https://developer.mozilla.org/en-US/docs/Web/HTML/Element#interactive_elements
		//TODO use <details> for accordion items
		
		var server = JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")), System.getProperty("net.ritzow.pass"),
			newPath(
				generatedHandler(RunSite::mainPageGenerator),
				entry("/opensearch", new StaticContentHandler(open("/xml/opensearch.xml"), "application/opensearchdescription+xml")),
				entry("/style.css", new StaticContentHandler(open("/css/global.css"), "text/css")),
				entry("/article", generatedHandler(RunSite::articlePageProcessor)),
				entry("/icon.svg", new StaticContentHandler(open("/image/icon.svg"), "image/svg+xml"))
			),
			generatedHandler(RunSite::errorGenerator)
		);
		
		server.start();
		
//		JmDNS mdns = JmDNS.create(InetAddress.getLocalHost(), "news.local");
		
		/* Shutdown on user input */
		try(var reader = new InputStreamReader(System.in, StandardCharsets.UTF_8)) {
			try(var db = cm.getConnection()) {
				/* TODO set interactive to false and create a loop, create a StringReader after creating a query string */
				SqlFile file = new SqlFile(reader, "<stdin>", System.out, "UTF-8", true, (URL)null);
				file.setConnection(db);
				file.execute();
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
	
	//TODO html tag itself needs to have lang
	//TODO need a readable way of nesting named elements
	private static final HtmlTag PAGE_OUTLINE = fullPage("RedNet",
		named("full-content"),
		div().withClass("page-separator"),
		footer().withClasses("page-footer", "footer", "foreground").with(
			span(
				text("Server Time: "),
				dynamic(state -> time(rawHtml(ZonedDateTime.now().format(
					DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(Locale.ROOT))))),
				text(" Heap: "),
				dynamic(state -> text(NumberFormat.getIntegerInstance(HttpUser.localesForUser(state.request()).get(0))
					.format((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/1000) + " KB"))
			)
		)
	);
	
	private static DomContent content(Locale lang) {
		return div().withLang(lang.toLanguageTag()).withClass("page-body").with(
			nav().withClasses("header", "foreground").with(named("header-content")),
			mainBox(
				named("content")
			)
		);
	}
	
	private static final Translator<String> TRANSLATIONS =
		Translator.ofProperties(properties("/lang/welcome.properties"));
	
	private static void doGetHtmlStreamed(Request request, HtmlResult result, Translator<String> translations) {
		try {
			setBasicStreamingHeaders(request.getResponse(), result.status(), "text/html; charset=utf-8");
			Writer body = new OutputStreamWriter(request.getResponse().getHttpOutput(), StandardCharsets.UTF_8);
			Object model = new HtmlSessionState(request, translations, result.named());
			result.html().render(FlatHtml.into(body).appendUnescapedText("<!DOCTYPE html>"), model);
			body.flush();
			request.setHandled(true);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static void doError(Request request, int status, String contentType) {
		setBasicStreamingHeaders(request.getResponse(), status, contentType);
	}
	
	private static void setBasicStreamingHeaders(Response response, int status, String contentType) {
		response.setStatus(status);
		response.setHeader(HttpHeader.TRANSFER_ENCODING, "chunked");
		response.setContentType(contentType);
		response.setHeader(HttpHeader.CACHE_CONTROL, "no-store");
		response.setHeader(HttpHeader.REFERER, "no-referrer");
	}
	
	private static Handler generatedHandler(Consumer<Request> function) {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest,
					HttpServletRequest request, HttpServletResponse response) {
				function.accept(baseRequest);
			}
		};
	}
	
	private static DomContent header(Request request) throws SQLException {
		List<Locale> locales = cm.getAvailableLocales();
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
						String text = locale.getDisplayName(locale) + (locale.equals(bestCurrent)
							? "" : " (" + locale.getDisplayLanguage(bestCurrent) + ")");
						
						var button = button()
							.withType("submit")
							.withName(locale.toLanguageTag());
						
						if(locale.equals(bestCurrent)) {
							button.withCondDisabled(true);
							button.withClasses("current-locale", "header-content");
						} else {
							button.withClasses("header-content");
						}
						
						return button.withText(text);
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
		
		var handler = new MultiPartParser.Handler() {
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
		};
		
		try {
			//TODO instead of readAllBytes, read as much as possible at a time and parse progressively.
			new MultiPartParser(handler, boundary)
				.parse(ByteBuffer.wrap(request.getHttpInput().readAllBytes()), true);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
		request.getResponse().setHeader(HttpHeader.LOCATION, request.getHttpURI().getDecodedPath());
		request.getResponse().setStatus(/*HttpStatus.OK_200*/HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	private static void mainPageGenerator(Request request) {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			doPostLang(request);
			return;
		}
		
		Optional<String> next = StaticPathHandler.peekComponent(request);
		if(next.isEmpty()) {
			try {
				Locale bestLocale = HttpUser.bestLocale(request, cm.getAvailableLocales());
				doGetHtmlStreamed(request, new HtmlResult(PAGE_OUTLINE, Map.of(
					"full-content", content(bestLocale),
					"header-content", header(request),
					"content", div().withClass("article-list").with(
						Stream.concat(
							Stream.of(
								h1(translated("greeting")).withClass("title")
							),
							cm.getArticlesForLocale(bestLocale).stream().map(
								article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
							)
						).toArray(DomContent[]::new)
					)
				)), TRANSLATIONS);
			} catch(SQLException | IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			var html = fullPage("Error",
				mainBox(
					p("\"" + next.get() + "\" does not exist."),
					a("Go home").withHref("/")
				)
			);
			doGetHtmlStreamed(request, new HtmlResult(html, Map.of(), HttpStatus.NOT_FOUND_404), TRANSLATIONS);
		}
	}
	
	private static void articlePageProcessor(Request request) {
		if(HttpMethod.fromString(request.getMethod()) == HttpMethod.POST) {
			doPostLang(request);
			return;
		}
		
		Optional<String> name = StaticPathHandler.peekComponent(request);
		if(name.isPresent()) {
			try {
				String urlname = name.get();
				List<Locale> supported = cm.getArticleLocales(urlname);
				Locale mainLocale = HttpUser.bestLocale(request, cm.getAvailableLocales());
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
						content = TagCreator.main().withLang(lang.toLanguageTag()).with(
							article(
								h1(article.get().title()).withClass("title-article"),
								div().withClass("markdown").with(article.get().content())
							)
						);
					} else {
						content = p("No such article " + urlname);
					}
				}
				doGetHtmlStreamed(request, new HtmlResult(PAGE_OUTLINE, Map.of(
					"full-content", content(mainLocale),
					"header-content", header(request),
					"content", content
				)), TRANSLATIONS);
			} catch(SQLException | IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			var html = fullPage("Error",
				mainBox(
					p("Please specify an article URL component: ").with(
						span(request.getHttpURI().toURI().normalize() + "<article-path>")
					),
					a("Go home").withHref("/")
				)
			);
			doGetHtmlStreamed(request, new HtmlResult(html,  Map.of(), HttpStatus.NOT_FOUND_404), TRANSLATIONS);
		}
	}
	
	private static void errorGenerator(Request request) {
		var html = fullPage("Error",
			mainBox(
				p("Sorry, there was an unexpected error!"),
				a("Go home").withHref("/")
			)
		);
		
		doGetHtmlStreamed(request, new HtmlResult(html, Map.of(), HttpStatus.INTERNAL_SERVER_ERROR_500), TRANSLATIONS);
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
	
	private static String generateGibberish(RandomGenerator random, int words, int maxWordSize) {
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
