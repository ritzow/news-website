package net.ritzow.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.ByteBufferOutputStream2;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.MultiPartParser;
import org.eclipse.jetty.server.MultiPartParser.Handler;
import org.eclipse.jetty.server.Request;

public class Forms {
	static <T> Function<String, Optional<? extends T>> doProcessForms(Request request,
		Function<String, FieldReader<? extends T>> actions) {
	HttpField contentType = request.getHttpFields().getField(HttpHeader.CONTENT_TYPE);
	Objects.requireNonNull(contentType, "No content type specified by client");
	String contentTypeStr = contentType.getValue();
	var map = new HashMap<String, String>(1);
	HttpField.getValueParameters(contentTypeStr, map);
	String boundary = map.get("boundary");
	try {
		Map<String, FieldReader<? extends T>> storage = new TreeMap<>();
		parse(request.getHttpInput(), boundary, new Handler() {
			private FieldReader<? extends T> reader;
			private String name, filename;
			
			@Override
			public void parsedField(String name, String value) {
				if(name.equalsIgnoreCase("Content-Disposition")) {
					var map = new TreeMap<String, String>();
					String disposition = HttpField.getValueParameters(value, map);
					if(!disposition.equals("form-data")) {
						/* Only form-data is valid https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Content-Disposition */
						throw new RuntimeException("received non-form-data");
					}
					
					this.name = map.get("name");
					this.filename = map.get("filename");
					reader = null;
				}
			}
			
			@Override
			public boolean content(ByteBuffer item, boolean last) {
				if(reader == null) {
					reader = Objects.requireNonNull(actions.apply(name));
					/* Overrride existing values */
					storage.put(name, reader);
				}
				if(item.hasRemaining()) reader.read(item, last, filename);
				return false;
			}
			
			@Override
			public void earlyEOF() {
				throw new RuntimeException("early EOF");
			}
		});
		return name -> {
			var field = storage.get(name);
			if(field == null) {
				return Optional.empty();
			} else {
				return Optional.ofNullable(field.result());
			}
		};
	} catch(IOException e) {
		throw new UncheckedIOException(e);
	}
}
	
	/** Parse {@code in} incrementally using the provided {@code handler}. **/
	private static void parse(HttpInput in, String boundary, Handler handler) throws IOException {
		MultiPartParser parser = new MultiPartParser(handler, boundary);
		ByteBuffer buffer = null;
		while(true) {
			int available = in.available();
			if(buffer == null) {
				buffer = ByteBuffer.wrap(new byte[Math.max(available, 2048)]);
			} else if(available > buffer.capacity()) {
				buffer = ByteBuffer.wrap(new byte[available]);
			}
			int count = in.read(buffer.array());
			if(count != -1) {
				parser.parse(buffer.clear().limit(count), false);
			} else {
				parser.parse(buffer.clear().limit(0), true);
				break;
			}
		}
	}
	
	//TODO need to be able to: combine name sets, have reusable name sets, have reusable input stream aggregation.
	
	public interface FieldReader<T> {
		void read(ByteBuffer item, boolean last, String filename);
		T result();
	}
	
	public static FieldReader<String> stringReader() {
		return new FieldReader<>() {
			//TODO this could be optimized to use CharBuffer and decode during read.
			// Hard to deal with edge case of char data remaining.
			final ByteBufferOutputStream2 data = new ByteBufferOutputStream2();
			
			@Override
			public void read(ByteBuffer item, boolean last, String filename) {
				data.write(item);
			}
			
			@Override
			public String result() {
				try {
					return StandardCharsets.UTF_8.newDecoder()
						.onUnmappableCharacter(CodingErrorAction.REPORT)
						.onMalformedInput(CodingErrorAction.REPORT)
						.decode(data.toByteBuffer())
						.toString();
				} catch(CharacterCodingException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}
	
	public static FieldReader<byte[]> secretBytesReader() {
		return new FieldReader<byte[]>() {
			private final ByteArrayOutputStream out = new ByteArrayOutputStream(12);
			@Override
			public void read(ByteBuffer item, boolean last, String filename) {
				byte[] ba = new byte[item.remaining()];
				item.get(ba).flip();
				while(item.hasRemaining()) {
					/* Clear buffer data */
					item.put((byte)0);
				}
				try {
					out.write(ba);
				} catch(IOException e) {
					throw new UncheckedIOException(e);
				} finally {
					Arrays.fill(ba, (byte)0);
				}
			}
			
			@Override
			public byte[] result() {
				byte[] result = out.toByteArray();
				out.reset();
				for(int i = 0; i < result.length; i++) {
					out.write(0);
				}
				return result;
			}
		};
	}
	
	//TODO should have secretFileReader as well
	public static FieldReader<Entry<Optional<String>, byte[]>> fileReader() {
		return new FieldReader<Entry<Optional<String>, byte[]>>() {
			private final ByteBufferOutputStream2 out = new ByteBufferOutputStream2();
			private String filename;
			@Override
			public void read(ByteBuffer item, boolean last, String filename) {
				out.write(item);
				this.filename = filename;
			}
			
			@Override
			public Entry<Optional<String>, byte[]> result() {
				return Map.entry(Optional.ofNullable(filename), out.toByteArray());
			}
		};
	}
}
