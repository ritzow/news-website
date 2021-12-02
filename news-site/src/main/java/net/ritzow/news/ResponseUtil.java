package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import net.ritzow.jetstart.HtmlResult;
import net.ritzow.jetstart.Translator;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class ResponseUtil {
	@SafeVarargs
	static void doGetHtmlStreamed(Request request, HtmlTag html, Entry<String, DomContent>... map) {
		doGetHtmlStreamed(request, new HtmlResult(html, Map.ofEntries(map)), RunSite.TRANSLATIONS);
	}
	
	static void doGetHtmlStreamed(Request request, HtmlResult result, Translator<String> translations) {
		try {
			setBasicStreamingHeaders(request.getResponse(), result.status(), "text/html; charset=utf-8");
			Writer body = new OutputStreamWriter(request.getResponse().getHttpOutput(), StandardCharsets.UTF_8);
			Object model = new HtmlSessionState(request, translations, result.named());
			result.html().render(FlatHtml.into(body).appendUnescapedText("<!DOCTYPE html>"), model);
			body.flush();
			request.setHandled(true);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static void setBasicStreamingHeaders(Response response, int status, String contentType) {
		response.setStatus(status);
		response.setHeader(HttpHeader.TRANSFER_ENCODING, "chunked");
		response.setContentType(contentType);
		response.setHeader(HttpHeader.CACHE_CONTROL, "no-store");
		response.setHeader(HttpHeader.REFERER, "no-referrer");
	}
	
	/** RequestState contains all per-request session state, providing an
	 * efficient alternative to String-based request attributes **/
//	static class RequestState {
//
//	}
	
	interface PageHandler {
		void accept(Request request) throws IOException, SQLException;
	}
	
	static Handler generatedHandler(PageHandler function) {
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest,
					HttpServletRequest request, HttpServletResponse response) throws IOException {
				try {
					function.accept(baseRequest);
				} catch(SQLException e) {
					/* TODO try https://stackoverflow.com/a/4375634/2442171 */
					throw new RuntimeException(e);
				}
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	static <T> T requestAttribute(Request request) {
		return (T)request.getAttribute("net.ritzow.web.request");
	}
	
	/* Set request-related data */
	static <T> void requestAttribute(Request request, T value) {
		request.setAttribute("net.ritzow.web.request", value);
	}
}
