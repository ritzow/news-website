package net.ritzow.jetstart;

import j2html.tags.DomContent;
import j2html.tags.specialized.HtmlTag;
import java.util.Map;

public record HtmlResult(HtmlTag html, Map<String, DomContent> named) {}
