package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import j2html.tags.Renderable;
import j2html.tags.specialized.ButtonTag;
import j2html.tags.specialized.FormTag;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import net.ritzow.news.component.CommonComponents;
import net.ritzow.news.database.ContentManager.Article3;
import net.ritzow.news.internal.SiteResources;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;
import static net.ritzow.news.ResponseUtil.contentPath;
import static net.ritzow.news.ResponseUtil.doGetHtmlStreamed;

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
				try(content) {
					return builder.output();
				}
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

	public static void doDecoratedPage(int status, Request request, NewsSite site, Locale mainLocale, String title, DomContent body) {
		doGetHtmlStreamed(request, status, List.of(mainLocale),
			context(request, site.translator, Map.of(),
				CommonComponents.page(title, 
					contentPath(SiteResources.RES_ICON),
					"/opensearch",
					contentPath(SiteResources.RES_GLOBAL_CSS),
					mainLocale,
					CommonComponents.content(
						CommonComponents.header(request, site),
						body
					)
				)
			)
		);
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
	
	public static DomContent articleBox(Article3 article, Locale locale) {
		return a().withClasses("foreground", "article-box").withHref("/article/" + article.urlname()).with(
			span(article.title()),
			time(article.published().atZone(ZoneId.systemDefault() /* TODO time stuff? */).format(
				DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withLocale(locale)))
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
				.withAlt(NewsSite.websiteTitle() + " Logo Red Circle"),
			span(NewsSite.websiteTitle()).withClass("logo-text")
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
