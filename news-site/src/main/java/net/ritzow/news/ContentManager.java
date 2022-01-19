package net.ritzow.news;

import com.zaxxer.hikari.HikariDataSource;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Map.entry;

public final class ContentManager {
	private static final Logger LOG = LoggerFactory.getLogger(ContentManager.class);
	
	private final HikariDataSource db;
	
	public static ContentManager ofMemoryDatabase() throws SQLException {
		return new ContentManager();
	}
	
	private ContentManager() throws SQLException {
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
				username VARCHAR(64) NOT NULL UNIQUE,
			)"""
		);
		
		uploadLocales();
		
		runMultiple(
			"""
			CREATE PROCEDURE new_article(IN in_urlname URLNAME, IN locale_original LOCALENAME,
				IN title BLOB, IN markdown BLOB)
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
			"""
		);
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
			/*if(e.getCause() != null) {
				System.err.print("Caused by: ");
				e.getCause().printStackTrace();
			}*/
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
	}
	
	public Connection getConnection() throws SQLException {
		return db.getConnection();
	}
	
	public record Article3(String urlname, String title) {}
	
	public List<Article3> getArticlesForLocale(Locale locale) throws SQLException, IOException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call get_all_articles_for_locale(?)")) {
				st.setString(1, locale.toLanguageTag());
				ResultSet result = st.executeQuery();
				List<Article3> articles = new ArrayList<>();
				while(result.next()) {
					Blob title = result.getBlob("title");
					articles.add(new Article3(result.getString("urlname"), readBlobUtf8(title)));
					title.free();
				}
				return articles;
			}
		}
	}
	
	public List<Locale> getSupportedLocales() throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT code FROM locales")) {
				ResultSet result = st.executeQuery();
				var list = new ArrayList<Locale>(4);
				while(result.next()) {
					list.add(Locale.forLanguageTag(result.getString("code")));
				}
				return list;
			}
		}
	}
	
	public List<Locale> getArticleLocales(String urlname) throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call get_article_langs(?)")) {
				st.setString(1, urlname);
				try(ResultSet result = st.executeQuery()) {
					List<Locale> locales = new ArrayList<>(4);
					while(result.next()) {
						locales.add(Locale.forLanguageTag(result.getString(1)));
					}
					return locales;
				}
			}
		}
	}
	
	public void newArticle(String urlName, Locale locale, String title, String markdown) throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call new_article(?, ?, ?, ?)")) {
				st.setString(1, urlName);
				st.setString(2, locale.toLanguageTag());
				st.setBlob(3, new ByteArrayInputStream(title.getBytes(StandardCharsets.UTF_8)));
				st.setBinaryStream(4, new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));
				st.execute();
			}
		}
	}
	
	/* Does not clear password parameter utf8 */
	public void newAccount(String username, byte[] utf8) throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call new_account(?, ?, ?, ?)")) {
				st.setString(1, username);
				
				/* For hashing: https://stackoverflow.com/a/2861125/2442171 */
				byte[] salt = new byte[16];
				SecureRandom.getInstanceStrong().nextBytes(salt);
				st.setBytes(2, salt);
				st.setBytes(3, passwordHash(utf8, salt));
				st.registerOutParameter(4, Types.INTEGER);
				st.execute();
			} catch(NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
			
			/*try(var st = db.prepareStatement("SELECT pwHash FROM accounts LIMIT 1")) {
				try(var res = st.executeQuery()) {
					res.next();
					System.out.println(Base64.getEncoder().withoutPadding().encodeToString(res.getBytes("pwHash")));	
				}
			}*/
		}
	}
	
	public boolean authenticateLogin(String username, byte[] password) throws SQLException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareStatement("SELECT pwSalt, pwHash FROM accounts WHERE username = ?")) {
				st.setString(1, username);
				try(var rs = st.executeQuery()) {
					if(rs.next()) {
						/* PASSWORD AUTHENTICATION!!! */
						return Arrays.equals(passwordHash(password, rs.getBytes("pwSalt")), rs.getBytes("pwHash"));
					}
					return false;
				}
			}
		}
	}
	
	private static byte[] passwordHash(byte[] passwordUtf8, byte[] salt) {
		try {
			CharBuffer utf16 = CharsetUtil.decoder(StandardCharsets.UTF_8).decode(ByteBuffer.wrap(passwordUtf8));
			char[] password = new char[utf16.length()];
			utf16.get(password);
			/* TODO use more iterations? */
			PBEKeySpec spec = new PBEKeySpec(password, salt, 100_000, 512);
			Arrays.fill(password, '\0');
			utf16.flip();
			while(utf16.hasRemaining()) {
				utf16.put('\0');
			}
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).getEncoded();
			spec.clearPassword();
			return hash;
		} catch(CharacterCodingException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
	
	public record Article<T>(String title, T content) {}
	
	/* Returned reader must be closed */
	public <T> Optional<Article<T>> getLatestArticle(String urlName, Locale locale, Function<Reader, T> transform)
			throws SQLException, IOException {
		try(var db = this.db.getConnection()) {
			try(var st = db.prepareCall("call get_latest_article(?, ?)")) {
				st.setString(1, urlName);
				st.setString(2, locale.toLanguageTag());
				try(ResultSet result = st.executeQuery()) {
					if(result.next()) {
						Blob blob = result.getBlob("title");
						String title = readBlobUtf8(blob);
						blob.free();
						blob = result.getBlob("markdown");
						var content = transform.apply(new InputStreamReader(blob.getBinaryStream(),
							CharsetUtil.decoder(StandardCharsets.UTF_8)));
						blob.free();
						return Optional.of(new Article<>(title, content));
					} else {
						return Optional.empty();
					}
				}
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
