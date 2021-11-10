package net.ritzow.news;

import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.specialized.FormTag;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Supplier;
import net.ritzow.jetstart.HtmlGeneratorHandler;
import net.ritzow.jetstart.JettyHandlers;
import net.ritzow.jetstart.JettySetup;
import net.ritzow.jetstart.StaticPathHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.resource.Resource;

import static j2html.TagCreator.*;

public class RunSite {
	public static void main(String[] args) throws Exception {
		System.out.println("Starting server...");
		
		Resource osxml = Resource.newResource(resource("/xml/opensearch.xml"));
		Resource favicon = Resource.newResource(resource("/image/icon.svg"));
		Resource style = Resource.newResource(resource("/css/global.css"));
		
		Handler handler = StaticPathHandler.newPath(
			new HtmlGeneratorHandler(index()),
			Map.entry("opensearch", JettyHandlers.newResource(osxml, "application/opensearchdescription+xml")),
			Map.entry("style.css", JettyHandlers.newResource(style, "text/css")),
			Map.entry("favicon.ico", JettyHandlers.newResource(favicon, "image/svg+xml"))
		);
		
		var server = JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"),
			handler
		);
		
		server.start();
		
		/* Shutdown on user input */
		try(var in = new Scanner(System.in)) {
			while(!in.nextLine().equals("stop"));
		}
		
		System.out.println("Stopping server...");
		
		server.stop();
		server.join();
	}
	
	private static URL resource(String path) {
		return RunSite.class.getResource(path);
	}

	private static final class DynamicTextContent extends DomContent {
		private final Supplier<String> sup;
		
		DynamicTextContent(Supplier<String> sup) {
			this.sup = sup;
		}
		
		@Override
		public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
			builder.appendEscapedText(sup.get());
			return builder.output();
		}
	}
	
	private static HtmlTag index() {
		return pageTemplate(
			div().withClass("content").with(
				h1("Welcome to R Net!").withClass("title"),
				p(new DynamicTextContent(() -> generateGibberish(100 + new Random().nextInt(500), 8)))
			),
			div().withClass("separator"),
			footer().withClass("footer").with(
				span(text("Server Time: "), new DynamicTextContent(() -> ZonedDateTime.now().format(
					DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(Locale.ROOT))))
			)
		);
	}
	
	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz";
	
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
	
	private static FormTag mainForm() {
		return form().withName("main")
			.withAction("/form/main")
			.withMethod("POST")
			.withEnctype("multipart/form-data").with(
				p("Username:"),
				input()
					.withCondRequired(true)
					.withClass("form-element")
					.withType("text")
					.withName("username")
					.withPlaceholder("Username"),
				p("Password:"),
				input()
					.withCondRequired(true)
					.withClass("form-element")
					.withType("password")
					.withName("password")
					.withPlaceholder("Password"),
				p("File upload: ").with(
					input()
						.withType("file")
						.withName("upload")
				),
				p("Echo:"),
				textarea()
					.withClass("form-element")
					.withName("comment")
					.withPlaceholder("Type some text here."),
				p(input().withType("submit")),
				a(
					button("This is a link button")
				).withHref("blah")
		);
	}
	
	private static HtmlTag pageTemplate(DomContent... body) {
		return html(
			head(
				title("R Net"),
				link()
					.withRel("shortcut icon")
					.withHref("/favicon.ico")
					.withType("image/svg+xml"),
				link()
					.withRel("search")
					.withHref("/opensearch")
					.withType("application/opensearchdescription+xml")
					.withTitle("Ritzow Net"),
				link().withRel("stylesheet").withHref("/style.css"),
				meta().withName("robots").withContent("noindex"),
				meta().withName("viewport")
					.withContent("width=device-width,initial-scale=1"),
				meta().withCharset("UTF-8")
			),
			body(body).withClass("page")
		);
	}
}
