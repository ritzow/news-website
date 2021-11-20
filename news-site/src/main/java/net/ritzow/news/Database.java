package net.ritzow.news;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import static java.util.Map.entry;

public class Database {
	public static Connection openTestDb() throws SQLException {
		var db = DriverManager.getConnection("jdbc:hsqldb:mem:test", properties(
			entry("user", "SA")
		));
		return db;
	}
	
	@SafeVarargs
	private static Properties properties(Entry<String, String>... properties) {
		var props = new Properties(properties.length);
		props.putAll(Map.ofEntries(properties));
		return props;
	}
}
