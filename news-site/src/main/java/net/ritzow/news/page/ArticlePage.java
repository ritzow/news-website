package net.ritzow.news.page;

import j2html.tags.DomContent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.ritzow.news.*;
import net.ritzow.news.Forms.FormField;
import net.ritzow.news.Forms.FormWidget;
import net.ritzow.news.component.LangSelectComponent;
import net.ritzow.news.database.ContentManager.Article;
import net.ritzow.news.database.ContentManager.Comment;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.doFormResponse;
import static net.ritzow.news.PageTemplate.*;

public class ArticlePage {
	private static final Logger LOG = LoggerFactory.getLogger(ArticlePage.class);

	public static void articlePageProcessor(Request request, NewsSite site, Iterator<String> path) {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				Optional<String> name = Optional.ofNullable(path.hasNext() ? path.next() : null);
				Locale mainLocale = NewsSite.pageLocale(request, site);
				
				//TODO make sure there isn't an extra component in the path
				
				if(name.isEmpty()) {
					doWrongArticlePath(request, site, mainLocale);
					return;
				}
				
				String urlname = name.orElseThrow();
				List<Locale> supported = site.cm.getArticleLocales(urlname);
				
				if(path.hasNext()) {
					NewsSite.doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "Not Found",
						p(
							rawHtml("There is no such page " + NewsSite.prettyUrl(request))
						)
					);
					return;
				}
				
				if(supported.isEmpty()) {
					doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				Locale articleLocale = HttpUser.bestLocale(request, supported);
				Optional<Article<MarkdownContent>> article = site.cm.getLatestArticle(urlname, articleLocale, MarkdownContent::new);
				
				if(article.isEmpty()) {
					doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				//TODO create a "share" button that pops-up a selectable area with user-select: all;
				NewsSite.doDecoratedPage(HttpStatus.OK_200, request, site, mainLocale, article.orElseThrow().title(),
					each(
						generateArticlePage(articleLocale, mainLocale, article.orElseThrow()),
						HttpUser.getExistingSession(request).flatMap(SessionData::user).map(ArticlePage::newCommentBox).orElse(null),
						eachStreamed(site.cm.listCommentsNewestFirst(article.orElseThrow().id()).stream().map(comment -> commentBox(mainLocale, comment)))
					)
				);
			}
			
			case POST -> doFormResponse(request, 
				entry(MainPage.LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
				entry(LangSelectComponent.LANG_SELECT_FORM, values -> LangSelectComponent.doLangSelectForm(request, site, values)),
				entry(Login.LOGGED_IN_FORM, values -> Login.doLoggedInForm(request, values)),
				entry(NEW_COMMENT_FORM, values -> doNewCommentForm(request, site, path, values))
			);
		}
	}

	private static final String NEW_COMMENT_CONTENT_NAME = "comment-content";
	
	private static String doNewCommentForm(Request request, NewsSite site, Iterator<String> path, Function<String, Optional<Object>> values) {
		if(values.apply("comment-submit").filter("new-comment"::equals).isPresent()) {
			//Locale mainLocale = NewsSite.pageLocale(request, site);
			
			if(!path.hasNext()) {
				//NewsSite.doWrongArticlePath(request, site, mainLocale);
				//return;
				//TODO allow not returning a redirect URI, so I can doWrongArticlePath
				LOG.atInfo().log("Rejected comment on non-existant article");
				return request.getHttpURI().getPathQuery();
			}

			String username = HttpUser.getExistingSession(request).flatMap(SessionData::user).orElse(null);
			if(username == null) {
				//TODO error
				LOG.atInfo().log("Rejected comment from anonymous viewer");
				return request.getHttpURI().getPathQuery();
			}
			
			String urlname = path.next();
			int id = site.cm.newComment(site.cm.getArticleId(urlname), site.cm.getUserId(username), 
				Instant.now(), (String)(values.apply(NEW_COMMENT_CONTENT_NAME).orElseThrow()));
			return request.getHttpURI().getPathQuery() + "#" + commentIdStr(id);
		}
		return request.getHttpURI().getPathQuery();
	}
	
	private static String commentIdStr(int commentId) {
		return "comment-" + commentId;
	}
	
	private static final Forms.FormWidget NEW_COMMENT_FORM = FormWidget.of(
		/* TODO optionals broken? */
		FormField.required(NEW_COMMENT_CONTENT_NAME, Forms::stringReader),
		FormField.required("comment-submit", Forms::stringReader)
	);
	
	private static DomContent newCommentBox(String username) {
		return postForm().withClasses("comment-box", "foreground").with(
			span(h3(username)),
			textarea().withClass("comment-text-box").withName(NEW_COMMENT_CONTENT_NAME).withPlaceholder("Write a comment..."),
			postButton().withText("Post").withName("comment-submit").withValue("new-comment")
		);
	}
	
	private static DomContent commentBox(Locale pageLocale, Comment comment) {
		return div().withId(commentIdStr(comment.id())).withClasses("comment-box", "foreground").with(
			span(
				h3(comment.username()),
				time(comment.timestamp().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(pageLocale)))
					/* Non-visible part for machine readability (accessibility) */
					.withDatetime(comment.timestamp().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
			),
			div(new MarkdownContent(comment.content()))
		);
	}
	
	public static DomContent generateArticlePage(Locale articleLocale, Locale mainLocale, Article<MarkdownContent> article) {
		return main().withClasses("main-box", "foreground").withLang(articleLocale.toLanguageTag()).with(
			article(
				h1(article.title()).withClass("title-article"),
				articleLocale.equals(mainLocale) ? null :
					h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
				div().withClass("markdown").with(article.content())
			)
		);	
	}


	/* TODO translate error pages */

	public static void doNoSuchArticle(Request request, NewsSite site, Locale mainLocale, String urlname) {
		NewsSite.doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "No such article",
			p("No such article \"" + urlname + "\"")
		);
	}

	public static void doWrongArticlePath(Request request, NewsSite site, Locale mainLocale) {
		NewsSite.doDecoratedPage(HttpStatus.NOT_FOUND_404, request, site, mainLocale, "Not an article",
			each(
				p("Please specify an article URL component: ").with(
					span(request.getHttpURI().toURI().normalize() + "<article-path>")
				),
				a("Go home").withHref("/")
			)
		);
	}
}
