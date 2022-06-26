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
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.ByteBufferOutputStream2;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.MultiPartParser;
import org.eclipse.jetty.server.MultiPartParser.Handler;
import org.eclipse.jetty.server.Request;

public class Forms {
	
	public enum FieldType {
		REQUIRED,
		OPTIONAL
	}
	
	public record FormField(String name, Supplier<FieldReader> transform, FieldType type) {
		public static FormField required(String name, Supplier<FieldReader> transform) {
			return new FormField(name, transform, FieldType.REQUIRED);
		}
		
		public static FormField optional(String name, Supplier<FieldReader> transform) {
			return new FormField(name, transform, FieldType.OPTIONAL);
		}
	}
	
	/**
	 * <p>An HTML form handler.</p>
	 * <p>This class represents a group of form fields.</p>
	 */
	public record FormWidget(FormField... fields) {
		public static FormWidget of(FormField... fields) {
			return new FormWidget(fields);
		}
	}
	
	private static class Shared {
		final Function<Function<String, Optional<Object>>, String> handler;
		int count;
		
		private Shared(Function<Function<String, Optional<Object>>, String> handler, int count) {
			this.handler = handler;
			this.count = count;
		}
	}
	
	private static class FormHandler {
		final FormField field;
		final Shared shared;
		FieldReader value;
		
		FormHandler(Shared shared, FormField field) {
			this.field = field;
			this.shared = shared;
		}
	}
	
	private static final class Holder<T> {
		T value;
	}
	
	/** Only allow a single HTML form to get called. handler returns the redirect URI **/
	@SafeVarargs
	public static void doFormResponse(Request request, Entry<FormWidget, Function<Function<String, Optional<Object>>, String>>... handlers) {
		var storage = new HashMap<String, FormHandler>();
		for(var entry : handlers) {
			Shared shared = new Shared(entry.getValue(), entry.getKey().fields.length);
			for(var field : entry.getKey().fields) {
				storage.put(field.name, new FormHandler(shared, field));
			}
		}
		
		var handler = new Holder<Function<Function<String, Optional<Object>>, String>>();
		
		doProcessForms(request, name -> storage.get(name).field.transform.get(), (name, reader) -> {
			var widget = storage.get(name);
			widget.value = reader;
			handler.value = widget.shared.handler;
			/*widget.shared.count--;
			if(widget.shared.count == 0) {

			}*/
		});
		
		/* Parsed last field TODO stop parsing after this, ignore remaining multipart */
		String redirect = handler.value.apply(fieldName -> {
			var field = storage.get(fieldName).value;
			if(field == null) {
				return Optional.empty();
			} else {
				return Optional.of(field.result());
			}
		});
		//TODO probably not robust enough URI stringify
		request.getResponse().setHeader(HttpHeader.LOCATION, redirect);
		request.getResponse().setStatus(HttpStatus.SEE_OTHER_303);
		request.setHandled(true);
	}
	
	public static Function<String, Optional<Object>> doProcessForms(Request request, Function<String, FieldReader> actions) {
		var storage = new TreeMap<String, FieldReader>();
		doProcessForms(request, actions, storage::put);
		return name -> {
			var field = storage.get(name);
			if(field == null) {
				return Optional.empty();
			} else {
				return Optional.ofNullable(field.result());
			}
		};
	}
	
	private static void doProcessForms(Request request,
		Function<String, FieldReader> actions, BiConsumer<String, FieldReader> aggregator) {
	HttpField contentType = request.getHttpFields().getField(HttpHeader.CONTENT_TYPE);
	Objects.requireNonNull(contentType, "No content type specified by client");
	String contentTypeStr = contentType.getValue();
	var map = new HashMap<String, String>(1);
	HttpField.getValueParameters(contentTypeStr, map);
	String boundary = map.get("boundary");
	
	try {
		//Map<String, FieldReader<? extends T>> storage = new TreeMap<>();
		parse(request.getHttpInput(), boundary, new Handler() {
			private FieldReader reader;
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
					/* New value or overrride existing values */
				}
				if(item.hasRemaining()) reader.read(item, last, filename);
				if(last) {
					aggregator.accept(name, reader);
				}
				return false;
			}
			
			@Override
			public void earlyEOF() {
				throw new RuntimeException("early EOF");
			}
		});
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
	
	public interface FieldReader {
		void read(ByteBuffer item, boolean last, String filename);
		Object result();
	}
	
	public static FieldReader stringReader() {
		return new FieldReader() {
			//TODO this could be optimized to use CharBuffer and decode during read.
			// Hard to deal with edge case of char data remaining.
			final ByteBufferOutputStream2 data = new ByteBufferOutputStream2();
			String result;
			
			@Override
			public void read(ByteBuffer item, boolean last, String filename) {
				data.write(item);
			}
			
			@Override
			public String result() {
				try {
					if(result == null) {
						result = StandardCharsets.UTF_8.newDecoder()
							.onUnmappableCharacter(CodingErrorAction.REPORT)
							.onMalformedInput(CodingErrorAction.REPORT)
							.decode(data.toByteBuffer())
							.toString();
					}
					return result;
				} catch(CharacterCodingException e) {
					throw new UncheckedIOException(e);
				}
			}
		};
	}
	
	public static FieldReader secretBytesReader() {
		return new FieldReader() {
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
	public static FieldReader fileReader() {
		return new FieldReader() {
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
