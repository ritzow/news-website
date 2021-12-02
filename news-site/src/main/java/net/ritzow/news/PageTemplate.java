package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.specialized.FormTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.ritzow.news.RequestHandlerContent.RequestHandler;

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
	
	public static DomContent named(String name) {
		return new RequestHandlerContent(state -> state.named(name));
	}
	
	public static DomContent translated(String name) {
		return new RequestHandlerContent(state ->
			rawHtml(state.translator().forPrioritized(name, HttpUser.localesForUser(state.request()))));
	}
	
	public static DomContent mainBox(DomContent... content) {
		return div().withClasses("main-box", "foreground")
			.with(content).with(
			a().withClasses("jump-top", "foreground").withHref("#top").with(
				rawHtml("Return to top")
			)
		);
	}
	
	public static DomContent articleBox(String title, String url) {
		return a().withClasses("foreground", "article-box").withHref(url).with(
			span(title)
		);
	}
	
	public static DomContent logo(String logoPath) {
		return a().withCondDraggable(false).withClass("logo").withHref("/").with(
			img()
				.withSrc(logoPath)
				.withWidth("50px")
				.withHeight("50px")
				.withCondDraggable(false)
				.withClass("logo-img")
				.withAlt("RedNet Logo Red Circle"),
			span("RedNet!").withClass("logo-text")
		);
	}
	
	public static DomContent inName(DomContent existing, String name, DomContent named) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				((HtmlSessionState)model).insert(name, named);
				existing.render(builder, model);
				return builder.output();
			}
		};
	}
	
	public static DomContent baseHead(String title) {
		return freeze(
			head(
				title(title),
				link()
					.withRel("icon")
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
				meta().withCharset("utf-8"),
				meta().withName("referrer").withContent("no-referrer")
			)
		);
	}
	
	public static FormTag mainForm() {
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
