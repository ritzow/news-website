package net.ritzow.news;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Cryptography {
	public static byte[] passwordHash(byte[] passwordUtf8, byte[] salt) {
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
		} catch(
			CharacterCodingException |
			InvalidKeySpecException |
			NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
