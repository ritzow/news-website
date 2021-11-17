package net.ritzow.jetstart;

import j2html.Config;
import j2html.rendering.FlatHtml;
import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.document;

public abstract class HtmlGeneratorHandler extends DynamicContentHandler {
	@Override
	protected ContentInfo generateContent(Request request, Writer body) throws IOException {
		body.append(document().render()).append('\n');
		HtmlResult result = onRequest(request);
		result.html.render(FlatHtml.into(body, Config.global()), new HtmlSessionState(request, result.named));
		return new ContentInfo("text/html", ContentUtils.generateTimeEtag());
	}
	
	public static record HtmlResult(HtmlTag html, Map<String, DomContent> named) {}
	protected abstract HtmlResult onRequest(Request request) throws IOException;
}
