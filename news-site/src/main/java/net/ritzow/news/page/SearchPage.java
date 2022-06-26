package net.ritzow.news.page;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.server.Request;

public class SearchPage {
	public static void searchPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		if(path.hasNext()) {
			throw new RuntimeException(path.next());
		}
		
		request.getResponse().getHttpOutput().write(request.getParameterMap().get("q")[0].getBytes(StandardCharsets.UTF_8));
		request.setHandled(true);
	}
}
