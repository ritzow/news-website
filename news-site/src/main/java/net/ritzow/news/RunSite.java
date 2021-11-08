package net.ritzow.news;

import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import java.nio.file.Path;
import java.util.Map;
import net.ritzow.jetstart.HtmlGeneratorHandler;
import net.ritzow.jetstart.JettyHandlers;
import net.ritzow.jetstart.JettySetup;
import net.ritzow.jetstart.StaticPathHandler;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.resource.Resource;

import static j2html.TagCreator.*;

public class RunSite {
	public static void main(String[] args) throws Exception {
		
		var base = Resource.newClassPathResource("base");
		var osxml = base.getResource("opensearch.xml");
		var index = base.getResource("index.html");
		var favicon = base.getResource("icon.svg");
		var style = base.getResource("style.css");
		
		Handler handler = StaticPathHandler.newPath(
			new HtmlGeneratorHandler(index()),
			Map.entry("opensearch", JettyHandlers.newResource(osxml, "application/opensearchdescription+xml")),
			Map.entry("style.css", JettyHandlers.newResource(style, "text/css"))
		);
		
		JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"),
			handler
		).start();
	}
	
	private static HtmlTag index() {
		return pageTemplate(
			div(
				h1("Welcome to R Net!").withId("title"),
				//img().withId("site-icon").withSrc("/favicon.ico"),
				a("Map").withHref("/map"),
				p().withId("time"),
				p().withId("connection"),
				form(
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
				).withName("main")
					.withAction("/form/main")
					.withMethod("POST")
					.withEnctype("multipart/form-data")
			).withId("content")
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
			body(body)
		);
	}
}
