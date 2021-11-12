package net.ritzow.jetstart;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ContentUtils {
	public static String generateTimeEtag() {
		try {
			MessageDigest hash = MessageDigest.getInstance("MD5");
			hash.update(ByteBuffer.allocate(Long.BYTES).putLong(System.currentTimeMillis()).flip());
			return "W/" + "\"" + new BigInteger(1, hash.digest()).toString(Character.MAX_RADIX) + "\"";
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
