package net.ritzow.news.database.model;

import io.permazen.JObject;
import io.permazen.annotation.JField;
import io.permazen.annotation.PermazenType;
import java.util.List;
import java.util.Locale;

@PermazenType
public abstract class NewsArticle implements JObject {
	@JField(indexed = true)
	public abstract String getUrlName();
	public abstract void setUrlName(String urlName);
	public abstract Locale getOriginalLocale();
	public abstract void setOriginalLocale(Locale originalLocale);
	public abstract List<NewsContent> getContent();
}
