package net.ritzow.news.response;

import java.io.IOException;
import java.lang.StackWalker.Option;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import net.ritzow.news.ResourceUtil;

public interface ContentSource {
	byte[] load() throws IOException;
	String mimeType();
	byte[] hash();
	
	static ContentSource ofString(String str, String contentType) {
		return new ContentSource() {
			private final byte[] content;
			private final String mimeType;

			{
				this.content = str.getBytes(StandardCharsets.UTF_8);
				this.mimeType = contentType;
			}

			@Override
			public byte[] load() {
				return content;
			}

			@Override
			public String mimeType() {
				return mimeType;
			}

			@Override
			public byte[] hash() {
				return ResourceUtil.hash(content, mimeType.getBytes(StandardCharsets.UTF_8));
			}
		};
	}
	
	static ContentSource ofModuleResource(String path, String mimeType) {
		return new ContentSource() {
			private final URLConnection holder;

			{
				try {
					this.holder = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
						.walk(frames -> frames.skip(1).findFirst().orElseThrow())
						.getDeclaringClass()
						//.getClassLoader()
						.getResource(path)
						.openConnection();
					if(!path.startsWith("/")) {
						throw new IllegalArgumentException("Module resource path must be absolute");
					}
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public byte[] load() throws IOException {
				try(var in = holder.getInputStream()) {
					return in.readAllBytes();
				}
			}

			@Override
			public String mimeType() {
				return mimeType;
			}

			@Override
			public byte[] hash() {
				try {
					return ResourceUtil.hash(load(), mimeType.getBytes(StandardCharsets.UTF_8));
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			}
		};
	}
}
