package net.ritzow.news.database.model;

import io.permazen.JObject;
import io.permazen.annotation.JField;
import io.permazen.annotation.PermazenType;
import java.time.Instant;
import java.util.List;

@PermazenType
public abstract class NewsComment implements JObject {
	@JField(indexed = true) //TODO create composite index with postTime
	public abstract NewsArticle getArticle();
	public abstract void setArticle(NewsArticle article);
	public abstract NewsAccount getAuthor();
	public abstract void setAuthor(NewsAccount author);
	public abstract Instant getPostTime();
	public abstract void setPostTime(Instant time);
	public abstract List<String> getContent();
}
