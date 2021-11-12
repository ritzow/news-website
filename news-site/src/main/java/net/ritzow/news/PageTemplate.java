package net.ritzow.news;

import j2html.tags.DomContent;
import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.HtmlTag;
import java.util.function.Supplier;

import static j2html.TagCreator.*;

public class PageTemplate {
	
	public static HtmlTag fullPage(String title, DomContent... body) {
		return html(
			baseHead(title),
			body().withClass("page").with(body)
		);
	}
	
	public static HeadTag baseHead(String title) {
		return head(
			title(title),
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
		);
	}
	
	public static DomContent generatedText(Supplier<String> text) {
		return new DynamicTextContent(text);
	}
}
