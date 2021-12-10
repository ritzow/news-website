package net.ritzow.news;

import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

class MarkdownContent extends DomContent {
	private final Node markdown;
	
	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).build();
	
	public MarkdownContent(String markdown) {
		this.markdown = Parser.builder().build().parse(markdown);
	}
	
	public MarkdownContent(Reader markdown) {
		try {
			this.markdown = Parser.builder().build().parseReader(markdown);
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	@Override
	public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) {
		RENDERER.render(markdown, builder.output());
		return builder.output();
	}
}
