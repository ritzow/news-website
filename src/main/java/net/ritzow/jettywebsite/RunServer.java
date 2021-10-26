package net.ritzow.jettywebsite;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.MimeTypes.Type;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;

public class RunServer {
	
	public static void main(String[] args) throws Exception {
		ResourceHandler res = new ResourceHandler();
		res.setDirectoriesListed(false);
		res.setEtags(true);
		
		ContextHandler root = new ContextHandler("/");
		Resource resource = Resource.newClassPathResource("base");
		root.setBaseResource(resource);
		root.setWelcomeFiles(new String[] {"index.html"});
		root.setHandler(res);
		
		JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"), root /*new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request,
				HttpServletResponse response) {
				request.getSession();
				try(var in = RunServer.class.getClassLoader().getResourceAsStream("index.html")) {
					response.getOutputStream().write(in.readAllBytes());
					response.addHeader("content-type", "text/html");
				} catch(IOException e) {
					e.printStackTrace();
				}
				baseRequest.setHandled(true);
			}
		}*/).start();
	}
}