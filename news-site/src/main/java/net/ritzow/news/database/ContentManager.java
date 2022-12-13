package net.ritzow.news.database;

import io.permazen.JTransaction;
import io.permazen.Permazen;
import io.permazen.PermazenFactory;
import io.permazen.core.Database;
import io.permazen.kv.mvstore.MVStoreAtomicKVStore;
import io.permazen.kv.mvstore.MVStoreKVDatabase;
import io.permazen.tuple.Tuple2;
import io.permazen.util.Bounds;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.ritzow.news.Cryptography;
import net.ritzow.news.database.model.LocaleType;
import net.ritzow.news.database.model.NewsAccount;
import net.ritzow.news.database.model.NewsArticle;
import net.ritzow.news.database.model.NewsComment;
import net.ritzow.news.database.model.NewsContent;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.NRTCachingDirectory;
import org.h2.mvstore.MVStore.Builder;
import org.h2.mvstore.OffHeapStore;

public final class ContentManager {

	public static final List<Locale> SUPPORTED_LOCALES = List.of(
		Locale.of("en", "US"),
		Locale.of("es"),
		Locale.of("ru"),
		Locale.of("zh")
	);
	
	private final Permazen pz;
	private final SecureRandom random;

	public static ContentManager ofMemoryDatabase() throws NoSuchAlgorithmException {
		return new ContentManager();
	}
	
	private ContentManager() throws NoSuchAlgorithmException {
		this.random = SecureRandom.getInstanceStrong();

		MVStoreAtomicKVStore implimpl = new MVStoreAtomicKVStore();
		implimpl.setBuilder(new Builder()
				/* In-memory database */
				.fileStore(new OffHeapStore())
			/*.fileName("target/database.permazen")*/);
		var impl = new MVStoreKVDatabase();
		impl.setKVStore(implimpl);

		impl.start();
		
		var db = new Database(impl);
		
		db.getFieldTypeRegistry().add(new LocaleType());

		pz = new PermazenFactory()
			.setDatabase(db)
			.setModelClasses(NewsArticle.class, NewsAccount.class, NewsContent.class, NewsComment.class)
			.newPermazen();
		initSearch();
	}
	
	public void shutdown() {
		pz.getDatabase().getKVDatabase().stop();
	}
	
	@SafeVarargs
	private static Properties properties(Entry<String, String>... properties) {
		var props = new Properties(properties.length);
		props.putAll(Map.ofEntries(properties));
		return props;
	}

	private IndexWriter indexer;
	private SearcherManager searcher;
	
	private void initSearch() {
		try {
			//Look into NRTManager
			//https://blog.mikemccandless.com/2011/11/near-real-time-readers-with-lucenes.html
			//Use NRTCachingDirectory when replacing ByteBuffersDirectory with disk directory
			indexer = new IndexWriter(new NRTCachingDirectory(new ByteBuffersDirectory(), 16, 32), new IndexWriterConfig(new StandardAnalyzer()));
			//https://blog.mikemccandless.com/2011/11/near-real-time-readers-with-lucenes.html
			//TODO applyAllDeletes false can improve performance.
			searcher = new SearcherManager(indexer, new SearcherFactory());
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static final Pattern WORD_DELIM = Pattern.compile("\\s+");
	
	public <T> List<Article<T>> search(String query, Locale lang, Function<Reader, T> content) throws IOException {
		searcher.maybeRefresh();
		var search = searcher.acquire();
		try {
			var builder = new PhraseQuery.Builder().setSlop(5);
			WORD_DELIM.splitAsStream(query).forEachOrdered(token -> builder.add(new Term("content", token)));
			var query1 = new BooleanQuery.Builder()
				.add(builder.build(), Occur.MUST)
				//TODO match a list of langs in order instead of exact
				.add(new TermQuery(new Term("lang", lang.toLanguageTag())), Occur.MUST)
				//.add(IntPoint.newExactQuery("lang", getLocaleIds().get(lang).intValue()), Occur.MUST)
				.build(); //new StandardQueryParser(new StandardAnalyzer()).parse(query, "content");
			TopDocs results = search.search(query1, TopScoreDocCollector.createSharedManager(10, null, 10));
			List<Article<T>> docs = new ArrayList<>(results.scoreDocs.length);
			for(final var result : results.scoreDocs) {
				search.getIndexReader().document(result.doc, new StoredFieldVisitor() {
					@Override
					public Status needsField(FieldInfo fieldInfo) {
						return Status.YES;
					}

					@Override
					public void intField(FieldInfo fieldInfo, int value) {
						var tx = pz.createTransaction();
						//findArticle(tx, )
						tx.rollback();
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
					}
				});
			}
			return docs;
		} finally {
			searcher.release(search);
		}
	}
	
	public record Article3(String urlname, String title) {}
	
	public List<Article3> getArticlesForLocale(Locale locale) {
		var tx = pz.createTransaction();
		try {
			//TODO only get latest of each article!
			return Optional.ofNullable(tx.queryIndex(NewsContent.class, "locale", Locale.class)
				.withValueBounds(Bounds.eq(locale))
				.asMap()
				.firstEntry())
				.stream()
				.map(Entry::getValue)
				.flatMap(Collection::stream)
				.collect(Collectors.groupingBy(NewsContent::getArticle, Collectors.maxBy(Comparator.comparing(NewsContent::getPublishTime))))
				.values()
				.stream()
				.flatMap(Optional::stream)
				.map(newsContent -> new Article3(newsContent.getArticle().getUrlName(), newsContent.getTitle()))
				.sorted(Comparator.comparing(Article3::title, Collator.getInstance(locale)))
				.toList();
		} finally {
			tx.rollback();
		}
	}
	
	public List<Locale> getSupportedLocales() {
		return SUPPORTED_LOCALES;
	}
	
	public List<Locale> getArticleLocales(String urlname) {
		
		var tx = pz.createTransaction();
		try {
			return tx.queryIndex(NewsArticle.class, "urlName", String.class)
				.withValueBounds(Bounds.eq(urlname))
				.asMap()
				.values()
				.stream()
				.flatMap(Collection::stream)
				.map(NewsArticle::getContent)
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
			content.setMarkdown(markdown);
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

			Document doc = new Document();
			doc.add(new StoredField("id", article.getObjId().asLong()));
			doc.add(new StoredField("lang", locale.toLanguageTag()));
			doc.add(new TextField("title", title, Store.NO));
			doc.add(new TextField("content", markdown, Store.NO));
			indexer.addDocument(doc);
			tx.commit();
		} catch(IOException e) {
			tx.rollback();
			throw new RuntimeException(e);
		}
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
			var result = findAccount(tx, username)
				.map(newsAccount -> Arrays.equals(Cryptography.passwordHash(password, newsAccount.getPwSalt()), newsAccount.getPwHash()))
				.orElse(false);
			tx.commit();
			return result;
		} catch(RuntimeException e) {
			tx.rollback();
			throw e;
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
							return new Article<>(c.getTitle(), transform.apply(new StringReader(c.getMarkdown())), 0);
						}
					}
					return null;
				});
		} catch(RuntimeException e) {
			tx.rollback();
			throw e;
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
		}
		return Optional.empty();
	}
	
	public record Comment(String username, Instant timestamp, String content, long id) {}
	
	public List<Comment> listCommentsNewestFirst(String urlname) {
		var tx = pz.createTransaction();
		try {
			var article = findArticle(tx, urlname);
			if(article.isPresent()) {
				return tx.queryIndex(NewsComment.class, "article", NewsArticle.class)
					.withValueBounds(Bounds.eq(article.orElseThrow()))
					.asSet()
					.stream()
					.map(Tuple2::getValue2)
					.sorted(Comparator.comparing(NewsComment::getPostTime, Comparator.reverseOrder()))
					.map(newsComment -> new Comment(newsComment.getAuthor().getUsername(), newsComment.getPostTime(), 
						newsComment.getContent().get(newsComment.getContent().size() - 1), newsComment.getObjId().asLong()))
					.toList();
			}
		} finally {
			tx.rollback();
		}
		return List.of();
	}
}
