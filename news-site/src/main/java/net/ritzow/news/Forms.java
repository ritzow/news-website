package net.ritzow.news;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import org.eclipse.jetty.io.ByteBufferOutputStream2;

public class Forms {

	//TODO need to be able to: combine name sets, have reusable name sets, have reusable input stream aggregation.
	
	public interface FieldReader<T> {
		void read(ByteBuffer item, boolean last, String filename);
		T result();
	}
	
//	public static class FormStorage {
//		/* Use a tree map because the data is very small, hashing may take up too much time */
//		private final TreeMap<String, FieldReader> data;
//
//		private FormStorage() {
//			data = new TreeMap<>();
//		}
//
//		public
//
//		public <T> T get(String name) {
//			return (T)data.get(name).result();
//		}
//	}

//	public static FormStorage newStorage() {
//		return new FormStorage();
//	}
	
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
