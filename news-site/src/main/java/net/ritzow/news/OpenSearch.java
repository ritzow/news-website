package net.ritzow.news;

import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.eclipse.jetty.http.HttpURI;
import org.w3c.dom.Document;

import static net.ritzow.news.ResponseUtil.contentPath;

public class OpenSearch {

	public static String generateOpensearchXml() {
		Document doc;
		try {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch(ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		doc.setXmlVersion("1.0");
		
		var root = doc.createElement("OpenSearchDescription");
		root.setAttribute("xmlns", "http://a9.com/-/spec/opensearch/1.1/");
		
		var shortName = doc.createElement("ShortName");
		shortName.setTextContent(System.getProperties().getProperty("title", 
			System.getProperties().getProperty("hostname")));
		
		var description = doc.createElement("Description");
		description.setTextContent("Search " + shortName.getTextContent());
		
		var uri = HttpURI.build()
			.scheme("https")
			.host(System.getProperties().getProperty("hostname"));
		
		var image = doc.createElement("Image");
		image.setAttribute("type", "image/svg+xml");
		image.setTextContent(HttpURI.build(uri).path(contentPath(NewsSite.RES_ICON)).toString());
		
		var template = doc.createElement("Url");
		template.setAttribute("type", "text/html");
		template.setAttribute("template", HttpURI.build(uri).path("/search").query("q={searchTerms}").toString());
		
		var self = doc.createElement("Url");
		self.setAttribute("type", "application/opensearchdescription+xml");
		self.setAttribute("rel", "self");
		self.setAttribute("template", HttpURI.build(uri).path("/opensearch").toString());
		
		root.appendChild(shortName);
		root.appendChild(description);
		root.appendChild(image);
		root.appendChild(template);
		root.appendChild(self);
		
		doc.appendChild(root);
		
		try {
			StringWriter writer = new StringWriter(64);
			var transform = TransformerFactory.newInstance().newTransformer();
			//transform.setOutputProperty(OutputKeys.INDENT, "true");
			transform.transform(new DOMSource(doc), new StreamResult(writer));
			return writer.toString();
		} catch(TransformerException e) {
			throw new RuntimeException(e);
		}
	}
}
