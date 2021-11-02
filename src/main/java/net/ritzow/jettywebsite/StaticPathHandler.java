package net.ritzow.jettywebsite;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

/** Handle requests to specific paths, handle requests for exact path match of non-existant subpath **/
public class StaticPathHandler extends AbstractHandler {
	private final Map<String, Handler> subpaths;
	private final Handler leafHandler;
	
	private static final String PATH_ATTRIBUTE_NAME =
		StaticPathHandler.class.getModule().getName() + ".path";
	
	@SafeVarargs
	public static Handler newPath(Handler handler, Entry<String, Handler>... paths) {
		return new StaticPathHandler(handler, paths);
	}
	
	private StaticPathHandler(Handler handler, Entry<String, Handler>[] paths) {
		this.subpaths = Map.ofEntries(paths);
		this.leafHandler = handler;
	}
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		String path = (String)baseRequest.getAttribute(PATH_ATTRIBUTE_NAME);
		if(path == null) {
			baseRequest.setAttribute(PATH_ATTRIBUTE_NAME, path = target);
		}
		
		/* start index of component, inclusive */
		int startIndex;
		if(path.charAt(0) == '/') {
			startIndex = 1;
		} else {
			startIndex = 0;
		}
		
		/* end index of component, exclusive */
		int endIndex = path.indexOf('/', startIndex);
		if(endIndex == -1) {
			endIndex = path.length();
		}
		
		/* remove the leading component */
		baseRequest.setAttribute(PATH_ATTRIBUTE_NAME, path.substring(endIndex));
		
		Handler handler;
		if(startIndex < endIndex) {
			handler = subpaths.get(path.substring(startIndex, endIndex));
		} else {
			handler = leafHandler;
		}
		if(handler != null) {
			handler.handle(target, baseRequest, request, response);
		}
		
		/* Otherwise unhandled error */
	}
}
