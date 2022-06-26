package net.ritzow.news;

import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
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
		
		var urlRoot = "https://" + System.getProperties().getProperty("hostname") + "/";
		
		var image = doc.createElement("Image");
		image.setAttribute("type", "image/svg+xml");
		image.setTextContent(urlRoot + contentPath(NewsSite.RES_ICON));
		
		var template = doc.createElement("Url");
		template.setAttribute("type", "text/html");
		template.setAttribute("template", urlRoot + "search?q={searchTerms}");
		
		var self = doc.createElement("Url");
		self.setAttribute("type", "application/opensearchdescription+xml");
		self.setAttribute("rel", "self");
		self.setAttribute("template", urlRoot + "opensearch");
		
		root.appendChild(shortName);
		root.appendChild(description);
		root.appendChild(image);
		root.appendChild(template);
		root.appendChild(self);
		
		doc.appendChild(root);

		StringWriter writer = new StringWriter(64);

		try {
			TransformerFactory.newInstance()
				.newTransformer()
				.transform(new DOMSource(doc), new StreamResult(writer));
			return writer.toString();
		} catch(TransformerException e) {
			throw new RuntimeException(e);
		}
	}
}
