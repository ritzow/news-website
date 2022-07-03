package net.ritzow.news.page;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import net.ritzow.news.HttpUser;
import net.ritzow.news.NewsSite;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.main;
import static j2html.TagCreator.p;
import static net.ritzow.news.PageTemplate.doDecoratedPage;
import static net.ritzow.news.PageTemplate.eachStreamed;

public class SearchPage {
	public static void searchPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		if(path.hasNext()) {
			throw new RuntimeException(path.next());
		}
		
		//TODO handle standard forms
		
		String query = request.getParameterMap().get("q")[0];
		try {
			doDecoratedPage(HttpStatus.OK_200, request, site, Locale.ENGLISH, 
				"Search \"" + query + "\"", main().withClasses("main-box", "foreground")
					.with(eachStreamed(site.cm.search(query, HttpUser.bestLocale(request, site.cm.getSupportedLocales())).stream().map(i -> p(i.toString())))));
		} catch(QueryNodeException | ParseException e) {
			throw new RuntimeException(e);
		}
	}
}
