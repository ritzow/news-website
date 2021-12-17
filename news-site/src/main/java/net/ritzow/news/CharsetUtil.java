package net.ritzow.news;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

public class CharsetUtil {
	public static CharsetDecoder decoder(Charset charset) {
		return charset.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
	}
}
