package net.ritzow.news.page;

import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import net.ritzow.news.Forms;
import net.ritzow.news.Forms.FormField;
import net.ritzow.news.Forms.FormWidget;
import net.ritzow.news.NewsSite;
import net.ritzow.news.component.LangSelectComponent;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.p;
import static j2html.TagCreator.rawHtml;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.doFormResponse;

public class MainPage {
	public static final FormWidget LOGIN_FORM = FormWidget.of(
		FormField.required("username", Forms::stringReader),
		FormField.required("password", Forms::secretBytesReader),
		FormField.optional("login-remember", Forms::stringReader),
		FormField.required("login-action", Forms::stringReader)
	);
	
	public static void mainPageGenerator(Request request, NewsSite site) {
		mainPageGenerator(request, site, Collections.emptyIterator());
	}
	
	private static void mainPageGenerator(Request request, NewsSite site, Iterator<String> path) {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				if(path.hasNext()) {
					NewsSite.doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, NewsSite.pageLocale(request, site),
						"No such path",
						p(rawHtml("No such path " + NewsSite.prettyUrl(request)))
					);
					return;
				}
				
				Locale bestLocale = NewsSite.pageLocale(request, site);
				NewsSite.doDecoratedPage(HttpStatus.OK_200, request, site, bestLocale, "RedNet!",
					ArticlePage.generateArticlesList(bestLocale, site)
				);
			}
			
			/* TODO check for vulnerability from cross-site POST request. https://stackoverflow.com/a/19322811/2442171 */
			/* Don't use Referer header for this purpose https://stackoverflow.com/a/6023980/2442171 */
			/* https://security.stackexchange.com/a/133945 */
			case POST -> doFormResponse(request,
				entry(LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
				entry(LangSelectComponent.LANG_SELECT_FORM, values -> LangSelectComponent.doLangSelectForm(request, site, values)),
				entry(Login.LOGGED_IN_FORM, values -> Login.doLoggedInForm(request ,values))
			);
		}
	}
}
