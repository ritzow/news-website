package net.ritzow.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;

import static java.util.Map.entry;

public final class ContentManager {
	
	/** Manages tables articles, articles_content, locales **/
	public static ContentManager of(Connection db) {
		return new ContentManager(db);
	}
	
	private final Connection db;
	
	private ContentManager(Connection db) {
		this.db = db;
	}
	
	public void initNew() throws SQLException {
		db.prepareStatement("CREATE TYPE URLNAME AS VARCHAR(64)").execute();
		db.prepareStatement("CREATE TYPE LOCALENAME AS VARCHAR(35)").execute();
		db.prepareStatement("""
			CREATE TABLE locales (
				id SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				code LOCALENAME NOT NULL UNIQUE
			)""").execute();
		db.prepareStatement("""
			CREATE TABLE articles (
				id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
				urlname URLNAME NOT NULL UNIQUE,
				locale_original SMALLINT NOT NULL,
				FOREIGN KEY (locale_original) REFERENCES locales(id)
			)""").execute();
		db.prepareStatement("""
			CREATE TABLE articles_content (
				id INTEGER NOT NULL,
				locale SMALLINT NOT NULL,
				title BLOB NOT NULL,
				markdown BLOB NOT NULL,
				revision INTEGER NOT NULL,
				FOREIGN KEY (id) REFERENCES articles(id),
				FOREIGN KEY (locale) REFERENCES locales(id),
				UNIQUE (id, locale, revision)
			)""").execute();
		PreparedStatement st = db.prepareStatement("INSERT INTO locales (code) VALUES (?)");
		for(Locale locale : Languages.recognizedLocales()) {
			st.setString(1, locale.toLanguageTag());
			st.addBatch();
		}
		st.executeBatch();
		db.prepareStatement("""
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
			""").execute();
		db.prepareStatement("""
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
			""").execute();
		db.prepareStatement("""
            CREATE FUNCTION get_article_langs(IN in_urlname URLNAME)
                RETURNS TABLE (locale LOCALENAME)
                READS SQL DATA
            BEGIN ATOMIC
                RETURN TABLE (SELECT locales.code
                    FROM (articles INNER JOIN articles_content ON articles.id = articles_content.id)
                    INNER JOIN locales ON articles_content.locale = locales.id
                    WHERE articles.urlname = in_urlname);
            END
            """).execute();
	}
	
	public List<Entry<Short, String>> getSupportedLocales() throws SQLException {
		var list = new ArrayList<Entry<Short, String>>();
		ResultSet result = db.prepareStatement("SELECT id, code FROM locales").executeQuery();
		while(result.next()) {
			list.add(entry(result.getShort("id"), result.getString("code")));
		}
		return list;
	}
	
	public List<Locale> getArticleLocales(String urlname) throws SQLException {
		var st = db.prepareStatement("call get_article_langs(?)");
		st.setString(1, urlname);
		ResultSet result = st.executeQuery();
		List<Locale> locales = new ArrayList<>(4);
		while(result.next()) {
			locales.add(Locale.forLanguageTag(result.getString(1)));
		}
		return locales;
	}
	
	public void newArticle(String urlName, Locale locale, String title, String markdown) throws SQLException {
		var st = db.prepareStatement("call new_article(?, ?, ?, ?)");
		st.setString(1, urlName);
		st.setString(2, locale.toLanguageTag());
		st.setBlob(3, new ByteArrayInputStream(title.getBytes(StandardCharsets.UTF_8)));
		st.setBinaryStream(4, new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
		st.execute();
	}
	
	public record Article(String title, String markdown) {}
	
	public Optional<Article> getLatestArticle(String urlName, Locale locale)
			throws SQLException, IOException {
		try(CallableStatement st = db.prepareCall("call get_latest_article(?, ?)")) {
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
