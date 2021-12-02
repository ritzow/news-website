package net.ritzow.news;

import j2html.rendering.HtmlBuilder;
import j2html.tags.DomContent;
import java.io.IOException;

public class RequestHandlerContent extends DomContent {
	private final RequestHandler sup;
	
	public interface RequestHandler {
		DomContent handle(HtmlSessionState request) throws IOException;
	}
	
	public RequestHandlerContent(RequestHandler sup) {
		this.sup = sup;
	}
	
	@Override
	public <T extends Appendable> T render(HtmlBuilder<T> builder, Object model) throws IOException {
		sup.handle((HtmlSessionState)model).render(builder, model);
		return builder.output();
	}
}
