package net.ritzow.jetstart;

import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import java.util.Map;
import org.eclipse.jetty.http.HttpStatus;

public record HtmlResult(HtmlTag html, Map<String, DomContent> named, int status) {
	public HtmlResult(HtmlTag html, Map<String, DomContent> named) {
		this(html, named, HttpStatus.OK_200);
	}
}
