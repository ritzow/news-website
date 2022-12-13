package net.ritzow.news.database.model;

import io.permazen.annotation.PermazenType;
import java.util.Locale;

@PermazenType
public abstract class NewsContent {
	public abstract Locale getLocale();

	public abstract void setLocale(Locale locale);

	public abstract String getTitle();

	public abstract void setTitle(String title);

	public abstract String getMarkdown();
	public abstract void setMarkdown(String markdown);
}
