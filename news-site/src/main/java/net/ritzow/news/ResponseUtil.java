package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class ResponseUtil {
	
	static void doGetHtmlStreamed(Request request, int status, DomContent html) {
		try {
			setBasicStreamingHeaders(request.getResponse(), status, "text/html; charset=utf-8");
			Writer body = new OutputStreamWriter(request.getResponse().getHttpOutput(), StandardCharsets.UTF_8);
			html.render(FlatHtml.into(body).appendUnescapedText("<!DOCTYPE html>"), null);
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
