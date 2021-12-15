package net.ritzow.news;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Supplier;

public class ResourceUtil {
	static String resourceAsString(String resource) throws IOException {
		try(var in = NewsSite.class.getResourceAsStream(resource)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
	
	static Supplier<InputStream> open(String resource) {
		return () -> NewsSite.class.getResourceAsStream(resource);
	}
	
	static Properties properties(String path) {
		try(var in = new InputStreamReader(NewsSite.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			var p = new Properties();
			p.load(in);
			return p;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
