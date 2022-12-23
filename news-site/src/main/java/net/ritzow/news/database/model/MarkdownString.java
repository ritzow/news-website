package net.ritzow.news.database.model;

import io.permazen.annotation.PermazenType;

@PermazenType
public abstract class MarkdownString {
	public abstract String getContent();
	public abstract void setContent(String content);
}
