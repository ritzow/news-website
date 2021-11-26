package net.ritzow.news;

import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import java.io.IOException;
import java.io.Reader;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

class MarkdownContent extends DomContent {
	private final Node markdown;
	
	public MarkdownContent(String markdown) {
		/* TODO use parseReader with Blob content */
		this.markdown = Parser.builder().build().parse(markdown);
	}
	
	public MarkdownContent(Reader markdown) throws IOException {
		this.markdown = Parser.builder().build().parseReader(markdown);
	}
	
	@Override
	public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) {
		HtmlRenderer.builder().escapeHtml(true).build().render(markdown, builder.output());
		return builder.output();
	}
}
