package net.ritzow.news.page;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import net.ritzow.news.NewsSite;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.eclipse.jetty.server.Request;

public class SearchPage {
	public static void searchPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		if(path.hasNext()) {
			throw new RuntimeException(path.next());
		}
		request.getResponse().getHttpOutput().write("Results:\n".getBytes(StandardCharsets.UTF_8));
		try {
			site.cm.search(request.getParameterMap().get("q")[0]).forEachOrdered(result -> {
				try {
					request.getResponse().getHttpOutput().write(result.getBytes(StandardCharsets.UTF_8));
					request.getResponse().getHttpOutput().write('\n');
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			});
		} catch(QueryNodeException e) {
			throw new RuntimeException(e);
		} catch(
			ParseException e) {
			throw new RuntimeException(e);
		}
		request.setHandled(true);
	}
}
