package net.ritzow.news;

import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public class ResponseUtil {
	public static void doGetHtmlStreamed(Request request, int status, List<Locale> langs, DomContent html) {
		try {
			/* Must consume all request body content! (Jetty was giving a DEBUG exception) */
			skipInput(request);
			
			//TODO send prefetch 103 Early Hints
			//request.getResponse().getHttpChannel().sendResponse(new MetaData.Response())
			
			setBasicStreamingHeaders(request.getResponse(), status, "text/html; charset=utf-8");
			request.getResponse().getHttpFields().addCSV(HttpHeader.CONTENT_LANGUAGE,
				langs.stream().distinct().map(Locale::toLanguageTag).toArray(String[]::new));
			Writer body = new OutputStreamWriter(request.getResponse().getHttpOutput(), StandardCharsets.UTF_8);
			html.render(FlatHtml.into(body).appendUnescapedText("<!DOCTYPE html>"), null);
			body.flush();
			request.setHandled(true);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	public static void doEmptyResponse(Request request, int status) throws IOException {
		skipInput(request);
		request.getResponse().setStatus(status);
		request.getResponse().setContentLength(0);
		request.getResponse().setHeader(HttpHeader.CACHE_CONTROL, "no-store");
		request.setHandled(true);
	}
	
	public static void skipInput(Request request) throws IOException {
		/* might be inefficient? could throw exception if request body larger than 2GB */
		//request.getHttpInput().readAllBytes();
		request.getHttpInput().consumeAll();
	}
	
	private static void setBasicStreamingHeaders(Response response, int status, String contentType) {
		response.setStatus(status);
		response.setHeader(HttpHeader.TRANSFER_ENCODING, "chunked");
		response.setContentType(contentType);
		response.setHeader(HttpHeader.CACHE_CONTROL, "no-store");
		response.setHeader("Referrer-Policy", "no-referrer");
	}
	
	private static final Pattern PATH_COMPONENT = Pattern.compile("/");
	
	public static Iterator<String> path(Request request) {
		return PATH_COMPONENT.splitAsStream(request.getHttpURI().getDecodedPath()).filter(Predicate.not(String::isEmpty)).iterator();
	}
	
	public static void doRefreshPage(Request request) {
		doSeeOther(request, URI.create(request.getHttpURI().getDecodedPath()));
	}
	
	private static void doSeeOther(Request request, URI location) {
		request.getResponse().setHeader(HttpHeader.LOCATION, location.toString());
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	@FunctionalInterface
	public interface RequestConsumer<S, T extends Exception> {
		void accept(Request request, S data) throws T;
	}
	
	@FunctionalInterface
	public interface ContextRequestConsumer<S, T extends Exception> {
		void accept(Request request, S data, Iterator<String> path) throws T;
	}
	
	public static <S, T extends Exception> RequestConsumer<S, T> matchStaticPaths(ContextRequestConsumer<S, T> paths) {
		return (request, data) -> {
			var path = ResponseUtil.path(request);
			paths.accept(request, data, path);
		};
	}
	
	@SafeVarargs
	public static <S, T extends Exception> ContextRequestConsumer<S, T> rootNoMatchOrNext(RequestConsumer<S, ? extends T> root,
			ContextRequestConsumer<S, ? extends T> noMatch, Entry<String, ContextRequestConsumer<S, ? extends T>>... paths) {
		var map = Map.ofEntries(paths);
		return (request, data, it) -> {
			if(it.hasNext()) {
				map.getOrDefault(it.next(), noMatch).accept(request, data, it);
			} else {
				root.accept(request, data);
			}
		};
	}
}
