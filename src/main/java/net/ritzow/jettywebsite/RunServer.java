package net.ritzow.jettywebsite;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jetty.http.ResourceHttpContent;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ResourceService;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

public class RunServer {
	
	public static void main(String[] args) throws Exception {
		ResourceHandler res = new ResourceHandler();
		res.setDirectoriesListed(false);
		res.setEtags(true);
		res.setBaseResource(Resource.newClassPathResource("base"));
		res.setWelcomeFiles(new String[] {"index.html"});
		
		ContextHandler root = new ContextHandler("/");
		root.setHandler(res);
		root.setWelcomeFiles(new String[]{"index.html"});
		
		ResourceHandler opensearchRes = new ResourceHandler();
		opensearchRes.setEtags(true);
		opensearchRes.setDirectoriesListed(false);
		opensearchRes.setBaseResource(Resource
			.newClassPathResource("base")
			.getResource("opensearch"));
		
		/* ContextHandler handles matching the URL path */
		ContextHandler opensearchContext = new ContextHandler("/opensearch");
		
		/* ResourceService handles setting Etags and other header stuff */
		ResourceService resources = new ResourceService();

		resources.setContentFactory((path, maxBuffer) -> switch(path) {
			//TODO remove unnecessary object creation invariants
			case "/opensearch" -> new ResourceHttpContent(
				/* Resource handles reading data from a file */
				Resource.newClassPathResource("base").getResource("opensearch").getResource("opensearch.xml"),
				"application/opensearchdescription+xml",
				maxBuffer
			);
			default -> null;
		});
		
		opensearchContext.setHandler(new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
				System.out.println("get opensearch.xml");
				resources.doGet(request, response);
				baseRequest.setHandled(true);
			}
		});
		
		ContextHandlerCollection contexts = new ContextHandlerCollection(root, opensearchContext);
		
		JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"), contexts
		).start();
	}
}