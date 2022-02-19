package net.ritzow.news.page;

import j2html.tags.DomContent;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.ritzow.news.ContentManager.Article;
import net.ritzow.news.HttpUser;
import net.ritzow.news.MarkdownContent;
import net.ritzow.news.NewsSite;
import net.ritzow.news.component.LangSelectComponent;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;
import static java.util.Map.entry;
import static net.ritzow.news.Forms.doFormResponse;
import static net.ritzow.news.PageTemplate.*;

public class ArticlePage {
	public static void articlePageProcessor(Request request, NewsSite site, Iterator<String> path) {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				Optional<String> name = Optional.ofNullable(path.hasNext() ? path.next() : null);
				Locale mainLocale = NewsSite.pageLocale(request, site);
				
				//TODO make sure there isn't an extra component in the path
				
				if(name.isEmpty()) {
					NewsSite.doWrongArticlePath(request, site, mainLocale);
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
				}
				
				if(supported.isEmpty()) {
					NewsSite.doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				Locale articleLocale = HttpUser.bestLocale(request, supported);
				Optional<Article<MarkdownContent>> article = site.cm.getLatestArticle(urlname, articleLocale, MarkdownContent::new);
				
				if(article.isEmpty()) {
					NewsSite.doNoSuchArticle(request, site, mainLocale, urlname);
					return;
				}
				
				NewsSite.doDecoratedPage(HttpStatus.OK_200, request, site, mainLocale, article.orElseThrow().title(),
					generateArticlePage(articleLocale, mainLocale, article.orElseThrow())
				);
			}
			
			case POST -> doFormResponse(request, 
				entry(MainPage.LOGIN_FORM, values -> Login.doLoginForm(request, site, values)),
				entry(LangSelectComponent.LANG_SELECT_FORM, values -> LangSelectComponent.doLangSelectForm(request, site, values)),
				entry(Login.LOGGED_IN_FORM, values -> Login.doLoggedInForm(request, values))
			);
		}
	}
	
	public static DomContent generateArticlePage(Locale articleLocale, Locale mainLocale, Article<MarkdownContent> article) {
		return main().withLang(articleLocale.toLanguageTag()).with(
			article(
				h1(article.title()).withClass("title-article"),
				articleLocale.equals(mainLocale) ? null :
					h3(articleLocale.getDisplayName(mainLocale)).withClass("article-lang"),
				div().withClass("markdown").with(article.content())
			)
		);	
	}
	
	public static DomContent generateArticlesList(Locale bestLocale, NewsSite site) {
		return each(
			h1(translated("greeting")).withClass("title"),
			dynamic(state -> eachStreamed(
				site.cm.getArticlesForLocale(bestLocale).stream().map(
					article3 -> articleBox(article3.title(), "/article/" + article3.urlname())
				)
			))
		);
	}
}
