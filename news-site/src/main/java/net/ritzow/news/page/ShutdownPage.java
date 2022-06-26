package net.ritzow.news.page;

import java.util.Iterator;
import java.util.List;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.util.Callback;

import static j2html.TagCreator.*;
import static net.ritzow.news.ResponseUtil.doGetHtmlStreamed;

public class ShutdownPage {
	public static void shutdownPage(Request request, NewsSite site, Iterator<String> path) {
		doGetHtmlStreamed(request, HttpStatus.OK_200, List.of(),
			html().with(
				body().with(
					p("Shutting down...")
				)
			)
		);

		request.getResponse().getHttpOutput().complete(new Callback() {
			@Override
			public void succeeded() {
				try {
					Runtime.getRuntime().halt(0);
				} catch(RuntimeException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void failed(Throwable x) {
				x.printStackTrace();
			}
		});
	}
}
