package net.ritzow.news;

import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import java.io.IOException;
import java.util.function.Supplier;

final class DynamicTextContent extends DomContent {
	private final Supplier<String> sup;
	
	DynamicTextContent(Supplier<String> sup) {
		this.sup = sup;
	}
	
	@Override
	public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
		builder.appendEscapedText(sup.get());
		return builder.output();
	}
}
