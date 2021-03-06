package net.ritzow.news.response;

import java.time.Duration;
import java.util.Map.Entry;
import net.ritzow.news.ResourceUtil;
import net.ritzow.news.ResponseUtil.ContextRequestConsumer;

public interface NamedResourceConsumer<T> extends Entry<String, ContextRequestConsumer<T>> {
	static <U> NamedResourceConsumer<U> ofHashed(ContentSource src) {
		return new NamedResourceConsumer<U>() {
			private final ContextRequestConsumer<U> handler;
			private String name;

			{
				handler = new CachingImmutableRequestConsumer<>(src, Duration.ofDays(7));
			}
			
			@Override
			public String getKey() {
				return name == null ? name = ResourceUtil.bytesToString(src.hash()) : name;
			}

			@Override
			public ContextRequestConsumer<U> getValue() {
				return handler;
			}

			@Override
			public ContextRequestConsumer<U> setValue(ContextRequestConsumer<U> value) {
				throw new UnsupportedOperationException();
			}
		};
	}
}
