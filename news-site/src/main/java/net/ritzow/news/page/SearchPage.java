package net.ritzow.news.page;

import j2html.tags.DomContent;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import net.ritzow.news.HttpUser;
import net.ritzow.news.NewsSite;
import net.ritzow.news.component.LangSelectComponent;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.doFormResponse;
import static net.ritzow.news.PageTemplate.doDecoratedPage;
import static net.ritzow.news.PageTemplate.eachStreamed;
import static net.ritzow.news.page.MainPage.LOGIN_FORM;

public class SearchPage {
	public static void searchPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		if(path.hasNext()) {
			throw new RuntimeException(path.next());
		}
		
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET -> {
				//TODO handle standard forms
				String query = request.getParameterMap().get("q")[0];
				var locale = HttpUser.bestLocale(request, site.cm.getSupportedLocales());

				doDecoratedPage(HttpStatus.OK_200, request, site, locale,
					"Search \"" + query + "\"",
					main().withClasses("main-box", "foreground")
						.with(eachStreamed(content(site, query, locale)))
				);	
			}
			
			case POST ->
				doFormResponse(request,
					entry(LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
					entry(LangSelectComponent.LANG_SELECT_FORM, values -> LangSelectComponent.doLangSelectForm(request, site, values)),
					entry(Login.LOGGED_IN_FORM, values -> Login.doLoggedInForm(request ,values))
				);
		}
	}

	private static Stream<DomContent> content(NewsSite site, String query, Locale locale) throws IOException {
		return site.cm.searcher().search(query, locale)
			.map(site.cm.searchLookup())
			.map(article -> div().withClasses("foreground").with(
				h2(article.title()),
				p().withText(firstSentence(article.content())))
			);
	}
	
	private static String firstSentence(Reader reader) {
		try {
			var texts = Parser.builder()
				.build()
				.parseReader(reader);
			return Objects.requireNonNullElse(firstText(texts), "");
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static String firstText(Node node) {
		for(var n = node; n != null; n = node.getNext()) {
			var text = firstText(n.getFirstChild());
			if(text != null) {
				return text;
			}
			if(n instanceof Text t) {
				return t.getLiteral();
			}
		}
		return null;
	}
}
