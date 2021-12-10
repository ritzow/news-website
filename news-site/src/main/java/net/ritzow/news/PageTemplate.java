package net.ritzow.news;

import j2html.TagCreator;
import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.specialized.FormTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import net.ritzow.jetstart.Translator;
import net.ritzow.news.RequestHandlerContent.RequestHandler;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;

public class PageTemplate {

	//TODO make a method similar to freeze that takes a predicate to determine whether to use a
	// cached UnescapedText depending on the value of 'model'.
	/** Pre-render child elements to a string instead of traversing DomContent **/
	public static DomContent freeze(DomContent... content) {
		try {
			return rawHtml(each(content).render(FlatHtml.inMemory()).toString());
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	/** Initialize a context for dynamic HTML elements **/
	public static DomContent context(Request request, Translator<String> translator, Map<String, DomContent> context, DomContent content) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				content.render(builder, new HtmlSessionState(request, translator, context));
				return builder.output();
			}
		};
	}
	
	//TODO improve efficiency
	@RequiresDynamicHtml
	public static DomContent dynamic(DomContent content, Map<String, DomContent> template) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				var current = (HtmlSessionState)model;
				//TODO this could probably be more efficient
				var map = new HashMap<>(current.named());
				map.putAll(template);
				var state = new HtmlSessionState(current.request(), current.translator(), map);
				content.render(builder, state);
				return builder.output();
			}
		};
	}
	
	public static DomContent dynamic(RequestHandler handler) {
		return new RequestHandlerContent(handler);
	}
	
	public static DomContent named(String name) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				((HtmlSessionState)model).named(name).render(builder, model);
				return builder.output();
			}
		};
	}
	
	public static DomContent translated(String name) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				var state = ((HtmlSessionState)model);
				builder.appendUnescapedText(state.translator().forPrioritized(name, HttpUser.localesForUser(state.request())));
				return builder.output();
			}
		};
		
//		return new RequestHandlerContent(state ->
//			rawHtml(state.translator().forPrioritized(name, HttpUser.localesForUser(state.request()))));
	}
	
	public static DomContent mainBox(DomContent... content) {
		return div().withClasses("content-center", "foreground").with(content);
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
	
//	public static DomContent inName(DomContent existing, String name, DomContent named) {
//		return new DomContent() {
//			@Override
//			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
//				((HtmlSessionState)model).insert(name, named);
//				existing.render(builder, model);
//				return builder.output();
//			}
//		};
//	}
	
	private static final DomContent HEAD_HTML = TagCreator.head(
		named("title"),
		freeze(
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
	
	@RequiresDynamicHtml
	public static DomContent head(String title) {
		return dynamic(HEAD_HTML, Map.of("title", title(title)));
	}
	
	public static FormTag mainForm() {
		return form()
			.withId("main")
			//.withAction("/upload")
			.withMethod("post")
			.withEnctype("multipart/form-data").with(
			p("Username:"),
			input()
				.withCondRequired(true)
				.withClass("form-element")
				.withType("text")
				//.withId("username")
				.withName("username")
				.withPlaceholder("Username"),
			p("Password:"),
			input()
				.withCondRequired(true)
				.withClass("form-element")
				.withType("password")
				//.withId("password")
				.withName("password")
				.withPlaceholder("Password"),
			p().with(
				label("File upload: ").withFor("upload-field"),
				input()
					.withType("file")
					.withName("upload")
					//.withId("upload-field")
			),
			p("Echo:"),
			textarea()
				.withClass("form-element")
				//.withId("comment")
				.withName("comment")
				.withPlaceholder("Type some text here."),
			p(input().withType("submit"))
		);
	}
}
