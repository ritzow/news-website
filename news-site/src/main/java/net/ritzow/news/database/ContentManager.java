package net.ritzow.news.database;

import com.zaxxer.hikari.HikariDataSource;
import io.permazen.Permazen;
import io.permazen.PermazenFactory;
import io.permazen.core.Database;
import io.permazen.kv.mvstore.MVStoreAtomicKVStore;
import io.permazen.kv.mvstore.MVStoreKVDatabase;
import io.permazen.tuple.Tuple2;
import io.permazen.util.Bounds;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.BatchUpdateException;
import java.sql.Blob;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import org.apache.lucene.document.IntPoint;
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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.h2.mvstore.MVStore.Builder;

import static java.util.Map.entry;

public final class ContentManager {
	
	private final HikariDataSource db;
	
	private final Permazen pz;
	
//	private final PersistentEntityStore store;

	public static ContentManager ofMemoryDatabase() throws SQLException {
		return new ContentManager();
	}
	
	private ContentManager() throws SQLException {

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
			.setModelClasses(NewsArticle.class, NewsAccount.class, NewsContent.class, NewsComment.class)
			.newPermazen();
		
		HikariDataSource pool = new HikariDataSource();
		/* TODO log to SLF4J??? disable? */
		pool.setLogWriter(new PrintWriter(System.err));
		pool.setJdbcUrl("jdbc:hsqldb:mem:test");
		pool.setMinimumIdle(0);
		pool.setIdleTimeout(0);
		pool.setMaximumPoolSize(50);
		//pool.setJdbcUrl("jdbc:h2:mem:");
		pool.setDataSourceProperties(properties(
			entry("user", "SA")
		));
		this.db = pool;
		initNew();
	}
	
	public void shutdown() throws SQLException {
		pz.getDatabase().getKVDatabase().stop();
		try(var db = this.db.getConnection()) {
			db.prepareStatement("SHUTDOWN").execute();
		}
		this.db.close();
	}
	
	@SafeVarargs
	private static Properties properties(Entry<String, String>... properties) {
		var props = new Properties(properties.length);
		props.putAll(Map.ofEntries(properties));
		return props;
	}
	
	public void initNew() throws SQLException {
		runMultiple(
			"CREATE TYPE URLNAME AS VARCHAR(64)",
			"CREATE TYPE LOCALENAME AS VARCHAR(35)",
			"CREATE TYPE USERNAME AS VARCHAR(64)",
			"""
			CREATE TABLE locales (
				id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				code LOCALENAME NOT NULL UNIQUE
			)
			""",
			"""
			CREATE TABLE articles (
				id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				urlname URLNAME NOT NULL UNIQUE,
				locale_original SMALLINT NOT NULL,
				FOREIGN KEY (locale_original) REFERENCES locales(id)
			)
			""",
			"""
			CREATE TABLE articles_content (
				id INTEGER NOT NULL,
				locale SMALLINT NOT NULL,
				title BLOB NOT NULL,
				markdown BLOB NOT NULL,
				revision INTEGER NOT NULL,
				FOREIGN KEY (id) REFERENCES articles(id),
				FOREIGN KEY (locale) REFERENCES locales(id),
				UNIQUE (id, locale, revision)
			)
			""",
			"""
			CREATE TABLE accounts (
				id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				pwHash BINARY VARYING(512) NOT NULL,
				pwSalt BINARY VARYING(16) NOT NULL,
				username USERNAME NOT NULL UNIQUE,
			)""",
			"""
			CREATE TABLE comments (
				id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				article INTEGER NOT NULL,
				user INTEGER NOT NULL,
				timestamp BIGINT NOT NULL,
				content BLOB NOT NULL,
				FOREIGN KEY (user) REFERENCES accounts(id),
			)
			"""
		);
		
		uploadLocales();
		
		runMultiple(
			"""
			CREATE PROCEDURE new_article(IN in_urlname URLNAME, IN locale_original LOCALENAME,
				IN title BLOB, IN markdown BLOB, OUT out_article_id INTEGER)
			MODIFIES SQL DATA
			BEGIN ATOMIC
				DECLARE locale_id SMALLINT DEFAULT NULL;
				DECLARE article_id INTEGER;
				DECLARE revision_id INTEGER;
				SELECT id INTO locale_id FROM locales WHERE code = locale_original;
				SELECT id INTO article_id FROM articles WHERE urlname = in_urlname;
				IF article_id IS NULL THEN
					INSERT INTO articles (urlname, locale_original) VALUES(in_urlname, locale_id);
					SET article_id = IDENTITY();
					SET revision_id = 0;
				ELSE
					SET revision_id =
						(SELECT MAX(revision) + 1 FROM articles_content
						WHERE articles_content.id = article_id
						AND articles_content.locale = locale_id);
					IF revision_id IS NULL THEN
						SET revision_id = 0;
					END IF;
				END IF;
				INSERT INTO articles_content (id, locale, title, markdown, revision)
					VALUES (article_id, locale_id, title, markdown, revision_id);
				SET out_article_id = article_id;
			END
			""",
			"""
			CREATE FUNCTION get_latest_article(IN in_urlname URLNAME, IN in_locale LOCALENAME)
				RETURNS TABLE (title BLOB, markdown BLOB, id INTEGER)
				READS SQL DATA
			BEGIN ATOMIC
				DECLARE TABLE intermediate (title BLOB, markdown BLOB, revision INTEGER, id INTEGER);
				INSERT INTO intermediate (SELECT
						articles_content.title,
						articles_content.markdown,
						articles_content.revision,
						articles_content.id
					FROM (articles INNER JOIN articles_content
					ON articles.id = articles_content.id)
					WHERE articles.urlname = in_urlname
					AND articles_content.locale = (SELECT id FROM locales WHERE code = in_locale));
				RETURN TABLE (SELECT title, markdown, id FROM intermediate
					WHERE revision = (SELECT MAX(revision) FROM intermediate));
			END
			""",
			"""
            CREATE FUNCTION get_article_langs(IN in_urlname URLNAME)
                RETURNS TABLE (locale LOCALENAME)
                READS SQL DATA
            BEGIN ATOMIC
                RETURN TABLE (SELECT locales.code
                    FROM (articles INNER JOIN articles_content ON articles.id = articles_content.id)
                    INNER JOIN locales ON articles_content.locale = locales.id
                    WHERE articles.urlname = in_urlname);
            END
            """,
			"""
			CREATE FUNCTION get_all_articles_for_locale(IN in_locale LOCALENAME)
				RETURNS TABLE (id INTEGER, urlname URLNAME, title BLOB)
				READS SQL DATA
			BEGIN ATOMIC
				DECLARE locale_id SMALLINT;
				SELECT locales.id INTO locale_id FROM locales WHERE locales.code = in_locale;
				RETURN TABLE (
					SELECT id, urlname, title
					FROM (
						SELECT id, urlname, MAX(revision) AS max_revision
						FROM articles
						JOIN articles_content
						ON articles.id = articles_content.id
						WHERE articles_content.locale = locale_id
						GROUP BY id, urlname, locale
					) AS result
					JOIN articles_content
					ON articles_content.id = id
						AND articles_content.locale = locale_id
						AND articles_content.revision = result.max_revision
				);
			END
			""",
			"""
			CREATE PROCEDURE new_account(IN in_username VARCHAR(64), IN in_pwSalt BINARY VARYING(16), IN in_pwHash BINARY VARYING(512), OUT id INTEGER)
				MODIFIES SQL DATA
			BEGIN ATOMIC
				INSERT INTO accounts (username, pwSalt, pwHash) VALUES (in_username, in_pwSalt, in_pwHash);
				SET id = IDENTITY();
			END
			""",
			"""
			CREATE PROCEDURE new_comment(IN in_article INTEGER, IN in_user INTEGER, IN in_timestamp BIGINT, IN in_content BLOB, OUT comment_id INTEGER)
				MODIFIES SQL DATA
			BEGIN ATOMIC
				INSERT INTO comments (article, user, timestamp, content) VALUES (in_article, in_user, in_timestamp, in_content);
				SET comment_id = IDENTITY();
			END
			""",
			"""
			CREATE FUNCTION list_comments(IN article_id INTEGER)
				RETURNS TABLE (out_id INTEGER, out_username USERNAME, out_time BIGINT, out_content BLOB)
				READS SQL DATA
			BEGIN ATOMIC
				RETURN TABLE (
					SELECT comments.id, accounts.username, comments.timestamp, comments.content
					FROM comments
					JOIN accounts
					ON accounts.id = comments.user
					WHERE comments.article = article_id
					ORDER BY timestamp DESC
				);
			END
			"""
		);

		initSearch();
	}
	
	private IndexWriter indexer;
	private SearcherManager searcher;
	
	private void initSearch() {
		try {
			//Look into NRTManager
			//https://blog.mikemccandless.com/2011/11/near-real-time-readers-with-lucenes.html
			//Use NRTCachingDirectory when replacing ByteBuffersDirectory with disk directory
			indexer = new IndexWriter(new ByteBuffersDirectory(), new IndexWriterConfig(new StandardAnalyzer()));
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
				.add(IntPoint.newExactQuery("lang", getLocaleIds().get(lang).intValue()), Occur.MUST)
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
						try(var db = ContentManager.this.db.getConnection(); 
							var st = db.prepareStatement("SELECT urlname FROM articles WHERE id = ?")) {
							st.setInt(1, value);
							var query = st.executeQuery();
							query.next();
							getLatestArticle(query.getString(1), lang, content)
								.ifPresent(docs::add);
						} catch(SQLException e) {
							throw new RuntimeException(e);
						}
					}
				});
			}
			return docs;
		} finally {
			searcher.release(search);
		}
	}
	
	private void runMultiple(String... sql) {
		try {
			try(var db = this.db.getConnection()) {
				try(Statement st = db.createStatement()) {
					for(String query : sql) {
						st.addBatch(query);
					}
					st.executeBatch();
				}
			}
		} catch(BatchUpdateException e) {
			StringBuilder build = new StringBuilder(e.getClass().getCanonicalName()).append(": ").append(e.getMessage()).append('\n');
			Iterator<String> lines = sql[e.getUpdateCounts().length].lines().iterator();
			int line = 1;
			while(lines.hasNext()) {
				build.append(String.format("%5d %s%n", line, lines.next()));
				line++;
			}
			throw new RuntimeException(build.toString(), e);
		} catch(SQLException e) {
			var it = e.iterator();
			while(it.hasNext()) {
				it.next().printStackTrace();
			}
			throw new RuntimeException(e);
		}
	}
	
	private void uploadLocales() throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("INSERT INTO locales (code) VALUES (?)")) {
				for(Locale locale : List.of(
					Locale.of("en","US"),
					Locale.of("es"),
					Locale.of("ru"),
					Locale.of("zh")
				)) {
					st.setString(1, locale.toLanguageTag());
					st.addBatch();
				}
				st.executeBatch();
			}
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
				.toList();
		} finally {
			tx.rollback();
		}
	}
	
	public List<Locale> getSupportedLocales() {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT code FROM locales")) {
				ResultSet result = st.executeQuery();
				var list = new ArrayList<Locale>(4);
				while(result.next()) {
					list.add(Locale.forLanguageTag(result.getString("code")));
				}
				return list;
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public Map<Locale, Short> getLocaleIds() {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT code, id FROM locales")) {
				ResultSet result = st.executeQuery();
				var list = new HashMap<Locale, Short>(4);
				while(result.next()) {
					list.put(Locale.forLanguageTag(result.getString("code")), result.getShort("id"));
				}
				return list;
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public List<Locale> getArticleLocales(String urlname) {
		
		var tx = pz.createTransaction();
		try {
			return Optional.ofNullable(tx.queryIndex(NewsArticle.class, "urlName", String.class)
					.withValueBounds(Bounds.eq(urlname))
					.asMap()
					.firstEntry())
				.stream()
				.map(Entry::getValue)
				.flatMap(Collection::stream)
				.findFirst()
				.map(NewsArticle::getContent)
				.stream()
				.flatMap(List::stream)
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

			var article = tx.queryIndex(NewsArticle.class, "urlName", String.class)
				.withValueBounds(Bounds.eq(urlName))
				.asSet()
				.stream()
				.findFirst()
				.map(Tuple2::getValue2)
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
			doc.add(new IntPoint("lang", getLocaleIds().get(locale)));
			doc.add(new TextField("title", title, Store.NO));
			//doc.add(new TextField("content", new MarkdownTokenStream(Parser.builder().build().parse(markdown))));
			doc.add(new TextField("content", markdown, Store.NO));
			indexer.addDocument(doc);
			tx.commit();
		} catch(IOException e) {
			tx.rollback();
			throw new RuntimeException(e);
		}
	}
	
	/* Does not clear password parameter utf8 */
	public void newAccount(String username, byte[] utf8) {
		try(var db = this.db.getConnection(); var st = db.prepareCall("call new_account(?, ?, ?, ?)")) {
			st.setString(1, username);				
			/* For hashing: https://stackoverflow.com/a/2861125/2442171 */
			byte[] salt = new byte[16];
			SecureRandom.getInstanceStrong().nextBytes(salt);
			st.setBytes(2, salt);
			st.setBytes(3, Cryptography.passwordHash(utf8, salt));
			st.registerOutParameter(4, Types.INTEGER);
			st.execute();
		} catch(SQLException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
	
	public boolean authenticateLogin(String username, byte[] password) {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT pwSalt, pwHash FROM accounts WHERE username = ?")) {
				st.setString(1, username);
				try(var rs = st.executeQuery()) {
					if(rs.next()) {
						/* PASSWORD AUTHENTICATION!!! */
						return Arrays.equals(Cryptography.passwordHash(password, rs.getBytes("pwSalt")), rs.getBytes("pwHash"));
					}
					return false;
				}
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public record Article<T>(String title, T content, int id) {}
	
	public <T> Optional<Article<T>> getLatestArticle(String urlName, Locale locale, Function<Reader, T> transform) {
		var tx = pz.createTransaction();
		try {
			return Optional.ofNullable(tx.queryIndex(NewsArticle.class, "urlName", String.class)
					.withValueBounds(Bounds.eq(urlName))
					.asMap()
					.firstEntry())
				.stream()
				.map(Entry::getValue)
				.flatMap(Collection::stream)
				.findFirst()
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
	
	private static String readBlobUtf8(Blob blob) throws SQLException, IOException {
		if(blob.length() > Integer.MAX_VALUE) {
			throw new IOException("Blob too big");
		}
		return new String(blob.getBytes(1, (int)blob.length()), StandardCharsets.UTF_8);
	}
	
	public int newComment(int articleId, int userId, Instant timestamp, String content) {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call new_comment(?, ?, ?, ?, ?)")) {
				st.setInt(1, articleId);
				st.setInt(2, userId);
				st.setLong(3, timestamp.toEpochMilli());
				st.setBlob(4, new ByteArrayInputStream(
					content.getBytes(StandardCharsets.UTF_8)));
				st.registerOutParameter(5, JDBCType.INTEGER);
				st.execute();
				return st.getInt(5);
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public record Comment(String username, Instant timestamp, String content, int id) {}
	
	public List<Comment> listCommentsNewestFirst(int articleId) {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call list_comments(?)")) {
				st.setInt(1, articleId);
				try(var result = st.executeQuery()) {
					var comments = new ArrayList<Comment>(0);
					while(result.next()) {
						int commentId = result.getInt("comments.id");
						String username = result.getString("accounts.username");
						Instant timestamp = Instant.ofEpochMilli(result.getLong("comments.timestamp"));
						Blob content = result.getBlob("comments.content");
						comments.add(new Comment(username, timestamp, readBlobUtf8(content), commentId));
						//content.free();
					}
					return comments;
				}
			}
		} catch(SQLException | IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public int getArticleId(String urlname) {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT id FROM articles WHERE urlname = ? LIMIT 1")) {
				st.setString(1, urlname);
				try(var rs = st.executeQuery()) {
					rs.next();
					return rs.getInt(1);
				}
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public int getUserId(String username) {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT id FROM accounts WHERE username = ? LIMIT 1")) {
				st.setString(1, username);
				try(var rs = st.executeQuery()) {
					rs.next();
					return rs.getInt(1);
				}
			}
		} catch(SQLException e) {
			throw new RuntimeException(e);
		}
	}
}
