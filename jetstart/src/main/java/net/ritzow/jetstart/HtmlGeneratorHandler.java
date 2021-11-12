package net.ritzow.jetstart;

import j2html.tags.specialized.HtmlTag;
import java.io.IOException;
import java.io.Writer;
import org.eclipse.jetty.server.Handler;

import static j2html.TagCreator.document;

public class HtmlGeneratorHandler extends DynamicContentHandler {
	private final HtmlTag page;
	
	private HtmlGeneratorHandler(HtmlTag page) {
		this.page = page;
	}
	
	public static Handler newPage(/*Function<Supplier<Session>, HtmlTag>*/ HtmlTag page) {
		return new HtmlGeneratorHandler(page);
	}
	
	@Override
	protected ContentInfo generateContent(Writer body) throws IOException {
		body.append(document().render()).append('\n').append(page.renderFormatted());
		return new ContentInfo("text/html", ContentUtils.generateTimeEtag());
	}
}
