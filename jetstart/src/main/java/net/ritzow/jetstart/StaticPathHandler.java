package net.ritzow.jetstart;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/** Handle requests to specific paths, handle requests for exact path match of non-existant subpath **/
public class StaticPathHandler extends AbstractHandler {
	private final Map<String, Handler> subpaths;
	private final Handler leafHandler;
	
	private static final String PATH_ATTRIBUTE_NAME =
		StaticPathHandler.class.getModule().getName() + ".path";
	
	public static String subPath(Request request) {
		return request.getHttpURI().getDecodedPath().substring(currentIndex(request));
	}
	
	public static Optional<String> peekComponent(Request request) {
		String path = request.getHttpURI().getDecodedPath();
		int index = currentIndex(request);
		int endIndex = end(path, index);
		return endIndex - index > 1 ? Optional.of(path.substring(index + 1, endIndex)) : Optional.empty();
	}
	
	public static Optional<String> consumeComponent(Request request) {
		//return Optional.empty();
		throw new UnsupportedOperationException("Not implemented");
	}
	
	StaticPathHandler(Handler handler, Entry<String, Handler>[] paths) {
		this.subpaths = Map.ofEntries(paths);
		this.leafHandler = handler;
	}
	
	private static int currentIndex(Request request) {
		return Objects.requireNonNullElse(
			(Integer)request.getAttributes().getAttribute(PATH_ATTRIBUTE_NAME), 0);
	}
	
	private static int end(String path, int index) {
		int endIndex = path.indexOf('/', index + 1);
		if(endIndex == -1) {
			return path.length();
		}
		return endIndex;
	}
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		
		/* start index of component, inclusive */
		int index = currentIndex(baseRequest);
		
		String path = baseRequest.getHttpURI().getDecodedPath();
	
		/* end index of component, exclusive */
		int endIndex = end(path, index);
		
		Handler handler;
		if(endIndex - index > 1) {
			handler = subpaths.get(path.substring(index, endIndex));
			if(handler != null) {
				baseRequest.getAttributes().setAttribute(PATH_ATTRIBUTE_NAME, endIndex);
			} else {
				handler = leafHandler;
			}
		} else {
			handler = leafHandler;
		}
		
		if(handler != null) {
			handler.handle(target, baseRequest, request, response);
		}
		
		/* Otherwise unhandled error */
	}
}
