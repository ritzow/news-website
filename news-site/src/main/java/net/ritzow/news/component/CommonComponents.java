package net.ritzow.news.component;

import j2html.tags.DomContent;
import j2html.tags.specialized.BodyTag;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.ritzow.news.*;
import net.ritzow.news.page.Login;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;
import static net.ritzow.news.PageTemplate.*;
import static net.ritzow.news.PageTemplate.head;

public class CommonComponents {
	@RequiresNamedHtml({"full-content", "time", "heap"})
	private static final BodyTag PAGE_BODY_HTML = body().withId("top").with(
		named("full-content"),
		div().withClass("page-separator"),
		footer().withClasses("page-footer").with(
			/*a().withClasses("left-footer", "foreground").withHref("/shutdown").with(
				rawHtml("Shutdown")
			),*/
			span().withClasses("right-footer", "foreground").with(
				text("Server Time: "),
				time(named("time")),
				text(" Heap: "),
				named("heap")
			)
		)
	);
	/* HTML should use "named" content when a lage chunk of HTML has a small number of dynamic elements */
	@RequiresDynamicHtml
	@RequiresNamedHtml({"header-content", "content"})
	private static final DomContent CONTENT_HTML = each(
		nav().withClasses("header", "foreground").with(named("header-content")),
		div().withClasses("page-body").with(
			div().withClass("content-left").with(
			
			),
			mainBox(
				named("content")
			),
			freeze(
				div().withClass("content-right").with(
					a().withClasses("jump-top", "foreground").withHref("#top").with(
						rawHtml("Return to top")
					)
				)
			)
		)
	);
	
	private static final DomContent LOGO_HTML = logo("/content/" + NewsSite.RES_ICON.fileName() /*"/icon.svg"*/);
	
	@RequiresDynamicHtml
	@RequiresNamedHtml({"content"})
	private static final DomContent STATIC_CENTERED_CONTENT = each(
		div().withClasses("page-body", "headerless-content").with(
			div().withClass("content-left"),
			mainBox(
				named("content")
			),
			div().withClass("content-right")
		)
	);
	
	@RequiresDynamicHtml
	public static DomContent page(Request request, NewsSite site, String title, String iconPath,
			String opensearchPath, String stylePath, Locale locale, DomContent fullContent) {
		return html().withLang(locale.toLanguageTag()).with(
			head(
				request, 
				title,
				iconPath,
				opensearchPath,
				stylePath,
				sites(site, request)
			),
			dynamic(PAGE_BODY_HTML, Map.of(
				"full-content", fullContent,
				"time", rawHtml(NewsSite.serverTime(locale)),
				"heap", dynamic(state -> NewsSite.memoryUsage(locale))
			))
		);
	}
	
	private static Set<String> sites(NewsSite site, Request request) {
		return site.peers.stream().filter(host -> !host.equals(request.getHttpURI().getHost())).collect(Collectors.toSet());
	}
	
	@RequiresDynamicHtml
	public static DomContent content(DomContent header, DomContent mainContent) {
		return dynamic(CONTENT_HTML, Map.of("header-content", header, "content", mainContent));
	}
	
	public static DomContent header(Request request, NewsSite site) {
		List<Locale> locales = site.cm.getSupportedLocales();
		Locale bestCurrent = HttpUser.bestLocale(request, locales);
		return each(
			LOGO_HTML,
			postForm()
				.withClasses("lang-selector")
				.attr("autocomplete", "off")
				.with(
					locales.stream().map(locale -> LangSelectComponent.langButton(locale, bestCurrent))
				),
				accountHeader(request)
		);
	}
	
	private static DomContent accountHeader(Request request) {
		return HttpUser.getExistingSession(request)
			.flatMap(SessionData::user)
			.map(Login::loggedInForm)
			.orElseGet(Login::loginForm);
	}
	
	public static DomContent headerlessContent(DomContent... content) {
		return dynamic(STATIC_CENTERED_CONTENT, Map.of("content", each(content)));
	}
}
