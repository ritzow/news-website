package net.ritzow.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import static java.util.Map.entry;

public final class ContentManager {
	private final Connection db;
	
	public static ContentManager ofMemoryDatabase() throws SQLException {
		return new ContentManager(DriverManager.getConnection("jdbc:hsqldb:mem:test", properties(
			entry("user", "SA")
		)));
	}
	
	private ContentManager(Connection db) throws SQLException {
		this.db = db;
		initNew();
	}
	
	public void shutdown() throws SQLException {
		db.prepareStatement("SHUTDOWN").execute();
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
			"""
		);
		
		uploadLocales();
		
		runMultiple(
			"""
			CREATE PROCEDURE new_article(IN urlname URLNAME, IN locale_original LOCALENAME,
				IN title BLOB, IN markdown BLOB)
			MODIFIES SQL DATA
			BEGIN ATOMIC
				DECLARE locale_id SMALLINT DEFAULT NULL;
				SELECT id INTO locale_id FROM locales WHERE code = locale_original;
				INSERT INTO articles (urlname, locale_original) VALUES(urlname, locale_id);
				INSERT INTO articles_content (id, locale, title, markdown, revision)
					VALUES (IDENTITY(), locale_id, title, markdown, 0);
			END
			""",
			"""
			CREATE FUNCTION get_latest_article(IN in_urlname URLNAME, IN in_locale LOCALENAME)
				RETURNS TABLE (title BLOB, markdown BLOB)
				READS SQL DATA
			BEGIN ATOMIC
				DECLARE TABLE intermediate (title BLOB, markdown BLOB, revision INTEGER);
				INSERT INTO intermediate (SELECT
						articles_content.title,
						articles_content.markdown,
						articles_content.revision
					FROM (articles INNER JOIN articles_content
					ON articles.id = articles_content.id)
					WHERE articles.urlname = in_urlname
					AND articles_content.locale = (SELECT id FROM locales WHERE code = in_locale));
				RETURN TABLE (SELECT title, markdown FROM intermediate
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
            """
		);
	}
	
	private void runMultiple(String... sql) throws SQLException {
		try(Statement st = db.createStatement()) {
			for(String query : sql) {
				st.addBatch(query);
			}
			st.executeBatch();
		}
	}
	
	private void uploadLocales() throws SQLException {
		try(var st = db.prepareStatement("INSERT INTO locales (code) VALUES (?)")) {
			for(Locale locale : List.of(
				Locale.forLanguageTag("en-US"),
				Locale.forLanguageTag("es"),
				Locale.forLanguageTag("ru"),
				Locale.forLanguageTag("zh")
			)) {
				st.setString(1, locale.toLanguageTag());
				st.addBatch();
			}
			st.executeBatch();
		}
	}
	
	public List<Entry<Short, String>> getSupportedLocales() throws SQLException {
		try(var st = db.prepareStatement("SELECT id, code FROM locales")) {
			ResultSet result = st.executeQuery();
			var list = new ArrayList<Entry<Short, String>>(4);
			while(result.next()) {
				list.add(entry(result.getShort("id"), result.getString("code")));
			}
			return list;
		}
	}
	
	public List<Locale> getArticleLocales(String urlname) throws SQLException {
		try(var st = db.prepareCall("call get_article_langs(?)")) {
			st.setString(1, urlname);
			ResultSet result = st.executeQuery();
			List<Locale> locales = new ArrayList<>(4);
			while(result.next()) {
				locales.add(Locale.forLanguageTag(result.getString(1)));
			}
			return locales;
		}
	}
	
	public void newArticle(String urlName, Locale locale, String title, String markdown) throws SQLException {
		try(var st = db.prepareCall("call new_article(?, ?, ?, ?)")) {
			st.setString(1, urlName);
			st.setString(2, locale.toLanguageTag());
			st.setBlob(3, new ByteArrayInputStream(title.getBytes(StandardCharsets.UTF_8)));
			st.setBinaryStream(4, new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
			st.execute();
		}
	}
	
	public record Article(String title, String markdown) {}
	
	public Optional<Article> getLatestArticle(String urlName, Locale locale)
			throws SQLException, IOException {
		try(var st = db.prepareCall("call get_latest_article(?, ?)")) {
			st.setString(1, urlName);
			st.setString(2, locale.toLanguageTag());
			ResultSet result = st.executeQuery();
			if(result.next()) {
				Blob blob = result.getBlob("title");
				String title = readBlobUtf8(blob);
				blob.free();
				blob = result.getBlob("markdown");
				String markdown = readBlobUtf8(blob);
				blob.free();
				return Optional.of(new Article(title, markdown));
			} else {
				return Optional.empty();
			}
		}
	}
	
	private static String readBlobUtf8(Blob blob) throws SQLException, IOException {
		if(blob.length() > Integer.MAX_VALUE) {
			throw new IOException("Blob too big");
		}
		return new String(blob.getBytes(1, (int)blob.length()), StandardCharsets.UTF_8);
	}
}
