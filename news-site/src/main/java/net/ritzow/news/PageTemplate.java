package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import j2html.tags.specialized.FormTag;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.io.UncheckedIOException;
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
		return rawHtml(html.output().toString());
	}
	
	public static DomContent dynamic(RequestHandler handler) {
		return new RequestHandlerContent(handler);
	}
	
	public static DomContent translated(String name) {
		return new RequestHandlerContent(state ->
			rawHtml(state.translator().forPrioritized(name, HttpUser.localesForUser(state.request()))));
	}
	
	public static DomContent mainBox(DomContent... content) {
		return div().withClasses("main-box", "foreground").with(content);
	}
	
	public static DomContent articleBox(String title, String url) {
		return a().withClasses("foreground", "article-box").withHref(url).with(
			span(title)
		);
	}
	
	public static DomContent logo(String logoPath) {
		return a().withClass("logo").withHref("/").with(
			img().withClass("logo-img").withWidth("50px").withHeight("50px").withAlt("RedNet Logo Red Circle").withSrc(logoPath),
			span("RedNet!").withClass("logo-text")
		);
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
				.withHref("/icon.svg")
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
			meta().withCharset("UTF-8"),
			meta().withName("referrer").withContent("no-referrer")
		));
	}
	
	private static FormTag mainForm() {
		return form().withId("main").withAction("/form/main")
			.withMethod("POST").withEnctype("multipart/form-data").with(
			p("Username:"),
			input()
				.withCondRequired(true)
				.withClass("form-element")
				.withType("text")
				.withId("username")
				.withPlaceholder("Username"),
			p("Password:"),
			input()
				.withCondRequired(true)
				.withClass("form-element")
				.withType("password")
				.withId("password")
				.withPlaceholder("Password"),
			p().with(
				label("File upload: ").withFor("upload-field"),
				input()
					.withType("file")
					.withId("upload-field")
			),
			p("Echo:"),
			textarea()
				.withClass("form-element")
				.withId("comment")
				.withPlaceholder("Type some text here."),
			p(input().withType("submit")),
			a(
				button("This is a link button")
			).withHref("blah")
		);
	}
}
