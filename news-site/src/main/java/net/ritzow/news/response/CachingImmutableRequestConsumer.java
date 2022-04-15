package net.ritzow.news.response;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.Iterator;
import net.ritzow.news.ResponseUtil.ContextRequestConsumer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

public class CachingImmutableRequestConsumer<T> implements ContextRequestConsumer<T> {
	private final ContentSource src;
	private SoftReference<byte[]> data;

	public CachingImmutableRequestConsumer(ContentSource src) {
		this.src = src;
		this.data = new SoftReference<>(null);
	}

	public byte[] load() throws IOException {
		byte[] data = this.data.get();
		if(data == null) {
			this.data = new SoftReference<>(data = src.load());
		}
		return data;
	}

	@Override
	public void accept(Request request, T data, Iterator<String> path) throws IOException {
		if(path.hasNext()) {
			throw new RuntimeException("Too many path components " + request.getHttpURI().getPath());
		}

		var response = request.getResponse();

		byte[] bytes = load();

		response.getHttpFields().addCSV(HttpHeader.CACHE_CONTROL,
			"max-age=" + Duration.ofMinutes(10).toSeconds(),
			"public",
			"immutable",
			"stale-while-revalidate=" + Duration.ofSeconds(30).toSeconds()
		);
		response.setContentType(src.mimeType());
		response.setContentLength(bytes.length);
		response.setStatus(HttpStatus.OK_200);

		response.getHttpOutput().write(bytes);
		response.getHttpOutput().flush();
	}
}
