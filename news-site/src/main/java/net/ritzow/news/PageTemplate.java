package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import j2html.tags.specialized.HeadTag;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import net.ritzow.jetstart.RequestHandlerContent;
import net.ritzow.jetstart.RequestHandlerContent.RequestHandler;

import static j2html.TagCreator.*;

public class PageTemplate {

	//TODO make a method similar to freeze that takes a predicate to determine whether to use a
	// cached UnescapedText depending on the value of 'model'.
	/** Pre-render child elements to a string instead of traversing DomContent **/
	public static DomContent freeze(DomContent... content) {
		var html = FlatHtml.inMemory();
		for(var c : content) {
			try {
				c.render(html);
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return new UnescapedText(html.output().toString());
	}
	
	public static DomContent dynamic(RequestHandler handler) {
		return new RequestHandlerContent(handler);
	}
	
	public static HtmlTag fullPage(String title, DomContent... body) {
		return html(
			baseHead(title),
			body().withClass("page").with(body)
		);
	}
	
	public static DomContent baseHead(String title) {
		return freeze(head(
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
		));
	}
	
	public static DomContent generatedText(Supplier<String> text) {
		return new DynamicTextContent(text);
	}
}
