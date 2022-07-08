package net.ritzow.news.page;

import j2html.tags.DomContent;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Locale;
import java.util.stream.Stream;
import net.ritzow.news.HttpUser;
import net.ritzow.news.NewsSite;
import net.ritzow.news.component.LangSelectComponent;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
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
				try {
					var locale = HttpUser.bestLocale(request, site.cm.getSupportedLocales());
					
					doDecoratedPage(HttpStatus.OK_200, request, site, locale, 
						"Search \"" + query + "\"", 
						main().withClasses("main-box", "foreground")
							.with(eachStreamed(content(site, query, locale)))
					);
				} catch(QueryNodeException | ParseException e) {
					throw new RuntimeException(e);
				}		
			}
			
			case POST -> {
				doFormResponse(request,
					entry(LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
					entry(LangSelectComponent.LANG_SELECT_FORM, values -> LangSelectComponent.doLangSelectForm(request, site, values)),
					entry(Login.LOGGED_IN_FORM, values -> Login.doLoggedInForm(request ,values))
				);
			}
		}
	}

	private static Stream<DomContent> content(NewsSite site, String query, Locale locale) throws QueryNodeException, IOException, ParseException {
		return site.cm.search(query, locale, SearchPage::firstSentence)
			.stream().map(article -> {
				return div().withClasses("foreground").with(
					h2(article.title()),
					p().withText(article.content())
				);
			});
	}

	private static String firstSentence(Reader reader) {
		/*var parser = Parser.builder().postProcessor(node -> {
			return node instanceof Paragraph ? node : null;
		}).build();
		var root = parser.parseReader(reader);
		while(root.)*/
		return "blah blah";
	}
}
