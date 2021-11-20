package net.ritzow.news;

import j2html.Config;
import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import j2html.tags.specialized.HtmlTag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Function;
import net.ritzow.jetstart.*;
import net.ritzow.news.ContentManager.Article;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.jetstart.JettyHandlers.newPath;
import static net.ritzow.jetstart.JettyHandlers.newResource;
import static net.ritzow.news.PageTemplate.*;

public class RunSite {
	
	private static ContentManager cm;
	
	private static final String WEBSITE_LOGO = "/favicon.ico";
	
	public static void main(String[] args) throws Exception {
		cm = ContentManager.ofMemoryDatabase();
		cm.newArticle("sandbox2d", Locale.forLanguageTag("en-US"), "Sandbox2D Readme", TempContent.markdown);
		cm.newArticle("blahblah", Locale.forLanguageTag("es"), "Sandbox2D Readme Spanish", "***HELLO!!!***");
		
		/* TODO use integrity attribute to verify content if delivered via CDN */
		/* TODO and use crossorigin="anonymous" */
		var server = JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")), System.getProperty("net.ritzow.pass"),
			newPath(
				genericPage(PAGE_OUTLINE, Map.of(
					"content", p("PLACEHOLDER!!!")
				)),
				entry("/opensearch", newResource(resource("/xml/opensearch.xml"), "application/opensearchdescription+xml")),
				entry("/style.css", newResource(resource("/css/global.css"), "text/css")),
				entry(WEBSITE_LOGO, newResource(resource("/image/icon.svg"), "image/svg+xml")),
				entry("/article", articlePages(TRANSLATIONS, cm))
			),
			genericPage(PAGE_OUTLINE, Map.of("content", p("Sorry! There was an error!")))
		);
		
		server.start();
		
		/* Shutdown on user input */
		try(var in = new Scanner(System.in)) {
			while(!in.next().equals("stop"));
		}
		
		System.out.println("Stopping server...");
		
		server.stop();
		cm.shutdown();
		server.join();
	}
	
	private static final HtmlTag PAGE_OUTLINE = fullPage("RedNet",
		div()/*.withLang("es")*/.withClass("page-body").with(
			freeze(
				div().withClasses("header", "foreground").with(
					//span("RedNet!")
					logo(WEBSITE_LOGO)
				)
			),
			div().withClasses("main-box", "foreground").with(
				freeze(
					h1("Welcome to RedNet!").withClass("title")
				),
				dynamic(state -> state.named("content"))
			)
		),
		div().withClass("page-separator"),
		footer().withClasses("page-footer", "footer", "foreground").with(
			span(text("Server Time: "), dynamic(state -> new UnescapedText(ZonedDateTime.now().format(
				DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(Locale.ROOT)))))
		)
	);
	
	private static final Translator<String> TRANSLATIONS =
		Translator.ofProperties(properties("/lang/welcome.properties"));
	
	/** Returns SHA-512 of response content **/
	public static byte[] doGet(Request request, Translator<String> translations,
		Function<Request, HtmlResult> onRequest) throws IOException {
		MessageDigest hasher;
		try {
			//TODO use this for the other resources, not the html
			hasher = MessageDigest.getInstance("SHA-512");
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		Writer body = new OutputStreamWriter(
			new DigestOutputStream(
				request.getResponse().getOutputStream(), hasher
			), StandardCharsets.UTF_8
		);
		body.append(document().render()).append('\n');
		HtmlResult result = onRequest.apply(request);
		result.html().render(FlatHtml.into(body, Config.global()), new HtmlSessionState(request, translations, result.named()));
		body.flush();
		request.getResponse().setContentType("text/html");
		//TODO allow other status codes
		request.getResponse().setStatus(HttpStatus.OK_200);
		request.getResponse().getHttpFields().add(HttpHeader.CACHE_CONTROL, "no-store");
		//request.getResponse().getHttpFields().add(HttpHeader.ETAG, ContentUtils.generateTimeEtag());
		/* TODO generate etag value from content written to body outputstream */
		request.setHandled(true);
		return hasher.digest();
	}
	
	private static Handler articlePages(Translator<String> translator, ContentManager cm) {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest
					request, HttpServletResponse response) throws IOException {
				byte[] out = doGet(baseRequest, translator, RunSite::blah);
				System.out.println(baseRequest.getHttpURI() + " sha512-" + Base64.getEncoder().withoutPadding().encodeToString(out));
			}
		};
	}
	
	private static HtmlResult blah(Request request) {
		Optional<String> name = StaticPathHandler.nextComponent(request);
		if(name.isPresent()) {
			try {
				/* TODO if has session, see if language is overriden first */
				String urlname = name.get();
				List<Locale> supported = cm.getArticleLocales(urlname);
				DomContent content;
				if(supported.isEmpty()) {
					content = p("No such article " + urlname);
				} else {
					Locale lang = HttpUser.bestLocale(request, supported);
					Optional<Article> article = cm.getLatestArticle(urlname, lang);
					if(article.isPresent()) {
						content = div().withLang(lang.toLanguageTag()).with(
							h1(article.get().title()).withClass("title-article"),
							div().withClass("markdown").with(
								new DomContent() {
									@Override
									public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) {
										convertMarkdownToHtml(article.get().markdown(), builder.output());
										return builder.output();
									}
								}
							)
						);
					} else {
						content = p("No such article " + urlname);
					}
				}
				return new HtmlResult(PAGE_OUTLINE, Map.of("content", content));
			} catch(SQLException | IOException e) {
				//e.printStackTrace();
				//content = p("An internal error occurred, sorry for the inconvenience.");
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("Invalid path");
		}
	}
	
	private static Handler genericPage(HtmlTag page, Map<String, DomContent> map) {
		var result = new HtmlResult(page, map);
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request,
					HttpServletResponse response) throws IOException, ServletException {
				doGet(baseRequest, TRANSLATIONS, req -> result);
			}
		};
	}
	
	private static URL resource(String path) {
		return RunSite.class.getResource(path);
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
	
	private static void convertMarkdownToHtml(String markdown, Appendable output)  {
		HtmlRenderer.builder().escapeHtml(true).build().render(Parser.builder().build().parse(markdown), output);
	}
	
	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz";
	
	private static String generateSomeGibberish() {
		return generateGibberish(100 + new Random().nextInt(500), 8);
	}
	
	private static String generateGibberish(int words, int maxWordSize) {
		SplittableRandom random = new SplittableRandom();
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
