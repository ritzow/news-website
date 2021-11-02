package net.ritzow.jettywebsite;

import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jetty.http.MimeTypes.Type;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.util.resource.Resource;

public class RunServer {
	
	public static void main(String[] args) throws Exception {
		
		var base = Resource.newClassPathResource("base");
		var osxml = base.getResource("opensearch.xml");
		var index = base.getResource("index.html");
		var favicon = base.getResource("icon.svg");
		var style = base.getResource("style.css");
		
		Handler handler = StaticPathHandler.newPath(
			JettyHandlers.newResource(index, Type.TEXT_HTML.asString()),
			Map.entry("opensearch", JettyHandlers.newResource(osxml, "application/opensearchdescription+xml")),
			Map.entry("style.css", JettyHandlers.newResource(style, "text/css"))
		);
		
		JettySetup.newStandardServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass"),
			handler
		).start();
	}
}