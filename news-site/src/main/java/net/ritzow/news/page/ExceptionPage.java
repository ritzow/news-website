package net.ritzow.news.page;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.ritzow.news.NewsSite;
import net.ritzow.news.component.CommonComponents;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.a;
import static j2html.TagCreator.p;
import static net.ritzow.news.PageTemplate.context;
import static net.ritzow.news.ResponseUtil.doGetHtmlStreamed;

public class ExceptionPage {
	public static void exceptionPageHandler(Request request, NewsSite site) {
		Optional.ofNullable(request.getSession(false)).ifPresent(HttpSession::invalidate);
		doGetHtmlStreamed(request, HttpStatus.INTERNAL_SERVER_ERROR_500, List.of(),
			context(request, site.translator, Map.of(),
				CommonComponents.page(request, site, "Error", Locale.forLanguageTag("en-US"),
					CommonComponents.headerlessContent(
						p("Sorry, there was an unexpected error!"),
						a("Go home").withHref("/")
					)
				)
			)
		);
	}
}
