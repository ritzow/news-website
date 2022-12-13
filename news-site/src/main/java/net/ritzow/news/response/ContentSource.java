package net.ritzow.news.response;

import java.io.IOException;
import java.lang.StackWalker.Option;
import java.net.URL;
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

			@Override
			public String toString() {
				return "String Resource {" +
					"content size " + content.length +
					", mimeType '" + mimeType + '\'' +
					'}';
			}
		};
	}
	
	static ContentSource ofModuleResource(String path, String mimeType) {
		return new ContentSource() {
			private final URL holder;

			{
				this.holder = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
					.walk(frames -> frames.skip(1).findFirst().orElseThrow())
					.getDeclaringClass()
					//.getClassLoader()
					.getResource(path);
				if(!path.startsWith("/")) {
					throw new IllegalArgumentException("Module resource path must be absolute");
				}
			}

			@Override
			public byte[] load() throws IOException {
				return holder.openStream().readAllBytes();
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

			@Override
			public String toString() {
				return "Module Resource " + holder;
			}
		};
	}
}
