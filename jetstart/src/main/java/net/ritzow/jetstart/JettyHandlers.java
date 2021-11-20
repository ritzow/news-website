package net.ritzow.jetstart;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.Map.Entry;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.resource.Resource;

public class JettyHandlers {
	
	public static Handler newResource(URL resource, String contentType) {
		ResourceService resources = new ResourceService();
		resources.setEtags(true);
		resources.setDirAllowed(false);
		resources.setContentFactory((path, maxBuffer) ->
			new ResourceHttpContent(Resource.newResource(resource), contentType, maxBuffer));
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest
					request, HttpServletResponse response) throws IOException, ServletException {
				if(HttpMethod.GET.is(request.getMethod()) || HttpMethod.HEAD.is(request.getMethod())) {
					resources.doGet(request, response);
				}
			}
		};
	}
	
	@SafeVarargs
	public static Handler newPath(Handler handler, Entry<String, Handler>... paths) {
		return new StaticPathHandler(handler, paths);
	}
}
