package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.Renderable;
import j2html.tags.specialized.ButtonTag;
import j2html.tags.specialized.FormTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
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
	
	public static <T extends Renderable> DomContent eachStreamed(Stream<T> content) {
		return new DomContent() {
			@Override
			public <U extends Appendable> U render(HtmlBuilder<U> builder, Object model) throws IOException {
				var it = content.iterator();
				while(it.hasNext()) {
					it.next().render(builder, model);
				}
				return builder.output();
			}
		};
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
	
	public interface RequestHandler {
		DomContent handle(HtmlSessionState request) throws IOException;
	}
	
	public static DomContent dynamic(RequestHandler handler) {
		return new DomContent() {
			@Override
			public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
				handler.handle((HtmlSessionState)model).render(builder, model);
				return builder.output();
			}
		};
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
	}
	
	public static FormTag postForm() {
		return form().withMethod("post").withEnctype("multipart/form-data");
	}
	
	public static ButtonTag postButton() {
		return button().withType("submit");
	}
	
	public static DomContent mainBox(DomContent... content) {
		return div().withClasses("content-center").with(content);
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

	public static FormTag mainForm() {
		return form()
			.withId("main")
			.withMethod("post")
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
			p().with(
				label("File upload: ").withFor("upload-field"),
				input()
					.withType("file")
					.withName("upload")
			),
			p("Echo:"),
			textarea()
				.withClass("form-element")
				.withName("comment")
				.withPlaceholder("Type some text here."),
			p(input().withType("submit"))
		);
	}
}
