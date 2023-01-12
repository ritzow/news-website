package net.ritzow.news.database;

import io.permazen.JTransaction;
import io.permazen.Permazen;
import io.permazen.PermazenFactory;
import io.permazen.core.Database;
import io.permazen.kv.mvstore.MVStoreAtomicKVStore;
import io.permazen.kv.mvstore.MVStoreKVDatabase;
import io.permazen.tuple.Tuple2;
import io.permazen.tuple.Tuple3;
import io.permazen.util.Bounds;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Collator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ritzow.news.Cryptography;
import net.ritzow.news.database.model.LocaleType;
import net.ritzow.news.database.model.MarkdownString;
import net.ritzow.news.database.model.NewsAccount;
import net.ritzow.news.database.model.NewsArticle;
import net.ritzow.news.database.model.NewsComment;
import net.ritzow.news.database.model.NewsContent;
import org.h2.mvstore.MVStore.Builder;

public final class ContentManager {

	/** Sorted list of supported locales in order of priority **/
	public static final List<Locale> SUPPORTED_LOCALES = List.of(
		Locale.of("en", "US"),
		Locale.of("es"),
		Locale.of("ru"),
		Locale.of("zh")
	);
	
	private final Permazen pz;
	private final SecureRandom random;
	private final SearchIndex search;

	public static ContentManager ofMemoryDatabase() throws NoSuchAlgorithmException {
		return new ContentManager();
	}
	
	private ContentManager() throws NoSuchAlgorithmException {
		this.random = SecureRandom.getInstanceStrong();
		
		this.search = new SearchIndex();

		MVStoreAtomicKVStore implimpl = new MVStoreAtomicKVStore();
		implimpl.setBuilder(new Builder()
				/* In-memory database */
				/*.fileStore(new OffHeapStore())*/
			.fileName("target/database.permazen"));
		var impl = new MVStoreKVDatabase();
		impl.setKVStore(implimpl);

		impl.start();
		
		var db = new Database(impl);
		
		db.getFieldTypeRegistry().add(new LocaleType());

		pz = new PermazenFactory()
			.setDatabase(db)
			.setModelClasses(NewsArticle.class, 
				NewsAccount.class, 
				NewsContent.class, 
				NewsComment.class,
				MarkdownString.class)
			.newPermazen();
	}
	
	public void shutdown() {
		pz.getDatabase().getKVDatabase().stop();
	}
	
	public SearchIndex searcher() {
		return search;
	}
	
	public Function<Long, Article<Reader>> searchLookup() {
		//var tx = pz.createTransaction();
		//findArticle(tx, )
		//tx.rollback();
		//TODO reimplement with permzen
//						try(var db = ContentManager.this.db.getConnection(); 
//							var st = db.prepareStatement("SELECT urlname FROM articles WHERE id = ?")) {
//							st.setInt(1, value);
//							var query = st.executeQuery();
//							query.next();
//							getLatestArticle(query.getString(1), lang, content)
//								.ifPresent(docs::add);
//						} catch(SQLException e) {
//							throw new RuntimeException(e);
//						}
		return value -> null;
	}
	
	public record Article3(String urlname, String title, Instant published) {}
	
	public Stream<Article3> getArticlesForLocale(Locale locale) {
		var tx = pz.createTransaction();
		return tx.queryIndex(NewsContent.class, "locale", Locale.class)
			.withValueBounds(Bounds.eq(locale))
			.asSet()
			.stream()
			.map(Tuple2::getValue2)
			.collect(Collectors.groupingBy(NewsContent::getArticle, Collectors.maxBy(Comparator.comparing(NewsContent::getPublishTime))))
			.values()
			.stream()
			.flatMap(Optional::stream)
			.map(newsContent -> new Article3(newsContent.getArticle().getUrlName(), newsContent.getTitle(), newsContent.getPublishTime()))
			.sorted(Comparator.comparing(ContentManager.Article3::title, Collator.getInstance(locale)))
			.onClose(tx::rollback);
	}

	public Stream<Article3> getRecentArticlesForLocale(Locale locale) {
		var tx = pz.createTransaction();
		return tx.queryCompositeIndex(NewsContent.class, "recent", Locale.class, Instant.class)
			.withValue1Bounds(Bounds.eq(locale))
			.asSet()
			.stream()
			.map(Tuple3::getValue3)
			//TODO need to sort by timestamp but group by article somehow.
			//.collect(Collectors.groupingBy(NewsContent::getArticle, () -> new TreeMap<NewsArticle>(Comparator.), Collectors.maxBy(Comparator.comparing(NewsContent::getPublishTime))))
			//.values()
			//.stream()
			//.flatMap(Optional::stream)
			.map(newsContent -> new Article3(newsContent.getArticle().getUrlName(), newsContent.getTitle(), newsContent.getPublishTime()))
			.onClose(tx::rollback);
	}
	
	public List<Locale> getSupportedLocales() {
		return SUPPORTED_LOCALES;
	}
	
	public List<Locale> getArticleLocales(String urlname) {
		var tx = pz.createTransaction();
		try {
			return findArticle(tx, urlname)
				.map(NewsArticle::getContent)
				.stream()
				.flatMap(Collection::stream)
				.map(NewsContent::getLocale)
				.distinct()
				.toList();
		} finally {
			tx.rollback();
		}
	}
	
	public void newArticle(String urlName, Locale locale, String title, String markdown) {
		var tx = pz.createTransaction();
		try {
			var content = tx.create(NewsContent.class);

			content.setTitle(title);
			var md = tx.create(MarkdownString.class);
			md.setContent(markdown);
			content.setMarkdown(md);
			content.setLocale(locale);
			content.setPublishTime(Instant.now());

			var article = findArticle(tx, urlName)
				.orElse(null);

			if(article == null) {
				article = tx.create(NewsArticle.class);
				article.setUrlName(urlName);
				article.setOriginalLocale(locale);
			}

			content.setArticle(article);
			article.getContent().add(content);

			//TODO need to undo this if there's a problem.
			//TODO doesn't seem to have a way to multithread rollbacks of only some stuff so I have it disabled for now
			//search.index(article.getObjId().asLong(), locale, title, markdown);
			tx.commit();
		} catch(RuntimeException e) {
			tx.rollback();
			throw e;
		}/* catch(IOException e) {
			tx.rollback();
			throw new RuntimeException(e);
		}*/
	}

	private static Optional<NewsArticle> findArticle(JTransaction tx, String urlName) {
		return tx.queryIndex(NewsArticle.class, "urlName", String.class)
			.withValueBounds(Bounds.eq(urlName))
			.asSet()
			.stream()
			.findFirst()
			.map(Tuple2::getValue2);
	}

	/* Does not clear password parameter utf8 */
	public void newAccount(String username, byte[] utf8) {
		var tx = pz.createTransaction();
		try {
			var account = tx.create(NewsAccount.class);
			account.setUsername(username);
			byte[] salt = new byte[16];
			random.nextBytes(salt);
			account.setPwSalt(salt);
			account.setPwHash(Cryptography.passwordHash(utf8, salt));
			tx.commit();
		} catch(RuntimeException e) {
			tx.rollback();
			throw e;
		}
	}
	
	public boolean authenticateLogin(String username, byte[] password) {
		var tx = pz.createTransaction();
		try {
			return findAccount(tx, username)
				.map(newsAccount -> Arrays.equals(Cryptography.passwordHash(password, newsAccount.getPwSalt()), newsAccount.getPwHash()))
				.orElse(false);
		} finally {
			tx.rollback();
		}
	}

	private static Optional<NewsAccount> findAccount(JTransaction tx, String username) {
		return tx.queryIndex(NewsAccount.class, "username", String.class)
			.withValueBounds(Bounds.eq(username))
			.asSet()
			.stream()
			.findFirst()
			.map(Tuple2::getValue2);
	}

	public record Article<T>(String title, T content, int id) {}
	
	public <T> Optional<Article<T>> getLatestArticle(String urlName, Locale locale, Function<Reader, T> transform) {
		var tx = pz.createTransaction();
		try {
			return findArticle(tx, urlName)
				.map(a -> {
					/* Find an article in the same language */
					List<NewsContent> content = a.getContent();
					var it = content.listIterator(content.size());
					while(it.hasPrevious()) {
						var c = it.previous();
						//TODO take a list of locales and search in order.
						if(c.getLocale().equals(locale)) {
							return new Article<>(c.getTitle(), transform.apply(new StringReader(c.getMarkdown().getContent())), 0);
						}
					}
					return null;
				});
		} finally {
			tx.rollback();
		}
	}

	public Optional<Long> newComment(String urlname, String user, Instant timestamp, String content) {
		var tx = pz.createTransaction();
		try {
			var article = findArticle(tx, urlname);
			var account = findAccount(tx, user);
			if(article.isPresent() && account.isPresent()) {
				var comment = tx.create(NewsComment.class);
				article.ifPresent(comment::setArticle);	
				account.ifPresent(comment::setAuthor);
				comment.setPostTime(timestamp);
				comment.getContent().add(content);
				tx.commit();
				return Optional.of(comment.getObjId().asLong());
			} else {
				tx.rollback();
			}
		} catch(RuntimeException e) {
			tx.rollback();
			throw e;
		}
		return Optional.empty();
	}
	
	public record Comment(String username, Instant timestamp, String content, long id) {}
	
	public List<Comment> listCommentsNewestFirst(String urlname) {
		var tx = pz.createTransaction();
		try {
			return findArticle(tx, urlname)
				.stream()
				.flatMap(newsArticle -> tx.queryIndex(NewsComment.class, "article", NewsArticle.class)
					.withValueBounds(Bounds.eq(newsArticle))
					.asSet()
					.stream()
					.map(Tuple2::getValue2)
					.sorted(Comparator.comparing(NewsComment::getPostTime, Comparator.reverseOrder()))
					.map(newsComment -> new Comment(newsComment.getAuthor().getUsername(), newsComment.getPostTime(),
						newsComment.getContent().get(newsComment.getContent().size() - 1), newsComment.getObjId().asLong())))
				.toList();
		} finally {
			tx.rollback();
		}
	}
}
