package net.ritzow.jetstart;

import j2html.tags.DomContent;
import java.util.Map;
import org.eclipse.jetty.server.Request;

public class HtmlSessionState {
	private final Request request;
	private final Map<String, DomContent> tags;
	private final Translator<String> translator;
	
	public HtmlSessionState(Request request, Translator<String> translations, Map<String, DomContent> namedContent) {
		this.request = request;
		this.tags = namedContent;
		this.translator = translations;
	}
	
	public DomContent named(String tag) {
		DomContent content = tags.get(tag);
		if(content == null) {
			throw new IllegalArgumentException("No DOM contente available for name '" + tag + "'");
		}
		return content;
	}
	
	public Request request() {
		return request;
	}
	
	public Translator<String> translator() {
		return translator;
	}
}
