package net.ritzow.jettywebsite;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.resource.Resource;

public class JettyHandlers {
	public static Handler newResource(Resource resource, String contentType) {
		ResourceService resources = new ResourceService();
		resources.setEtags(true);
		resources.setDirAllowed(false);
		resources.setContentFactory((path, maxBuffer) -> {
			//if(path.equals("/")) {
				return new ResourceHttpContent(resource, contentType, maxBuffer);
			//} else {
			//	throw new InvalidPathException(path, "Invalid PathInContext");
			//}
		});
		return new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest
					request, HttpServletResponse response) throws IOException, ServletException {
				if(HttpMethod.GET.is(request.getMethod()) || HttpMethod.HEAD.is(request.getMethod())) {
					/* MessageDigest class can be used to get hash to use for better ETAG */
					resources.doGet(request, response);
					baseRequest.setHandled(true);
				}
			}
		};
	}
}
