package net.ritzow.news.page;

import j2html.TagCreator;
import java.util.Iterator;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.p;

public class ErrorPages {
	public static void doGeneric404(Request request, NewsSite site, @SuppressWarnings("Unused") Iterator<String> path) {
		NewsSite.doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, NewsSite.pageLocale(request, site), "No Such Path",
			TagCreator.p("No such path " + NewsSite.prettyUrl(request))
		);
	}
}
