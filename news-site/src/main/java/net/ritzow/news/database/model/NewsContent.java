package net.ritzow.news.database.model;

import io.permazen.annotation.JField;
import io.permazen.annotation.PermazenType;
import java.time.Instant;
import java.util.Locale;

@PermazenType
public abstract class NewsContent {
	public abstract NewsArticle getArticle();
	public abstract void setArticle(NewsArticle article);
	@JField(indexed = true)
	public abstract Locale getLocale();
	public abstract void setLocale(Locale locale);
	public abstract String getTitle();
	public abstract void setTitle(String title);
	public abstract String getMarkdown();
	public abstract void setMarkdown(String markdown);
	public abstract boolean isLatest();
	public abstract void setLatest(boolean latest);
	public abstract Instant getPublishTime();
	public abstract void setPublishTime(Instant publishTime);
}
