package net.ritzow.news;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;

public class ResourceUtil {
	
	private static final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
	
	public static String bytesToString(byte[] hash) {
		return encoder.encodeToString(hash);
	}
	
	public static String bytesToString2(byte[] data) {
		return HexFormat.of().formatHex(data);
	}
	
	public static byte[] hash(byte[]... data) {
		try {
			MessageDigest hasher = MessageDigest.getInstance("SHA-256");
			for(var d : data) {
				hasher.update(d);
			}
			return hasher.digest();
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static Properties properties(String path) {
		try(var in = new InputStreamReader(NewsSite.class.getResourceAsStream(path), StandardCharsets.UTF_8)) {
			var p = new Properties();
			p.load(in);
			return p;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
