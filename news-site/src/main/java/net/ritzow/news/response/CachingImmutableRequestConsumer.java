package net.ritzow.news.response;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.time.Duration;
import java.util.Iterator;
import net.ritzow.news.ResponseUtil.ContextRequestConsumer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

/* TODO make a similar one that streams from inputstream */
public class CachingImmutableRequestConsumer<T> implements ContextRequestConsumer<T> {
	private final ContentSource src;
	private SoftReference<byte[]> data;
	private final Duration cacheFor;

	public CachingImmutableRequestConsumer(ContentSource src, Duration cacheDuration) {
		this.src = src;
		this.data = new SoftReference<>(null);
		this.cacheFor = cacheDuration;
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

		doResponse(response, bytes, cacheFor, src.mimeType());
	}

	public static void doResponse(Response response, byte[] bytes, Duration cacheFor, String contentType) throws IOException {
		response.getHttpFields().addCSV(HttpHeader.CACHE_CONTROL,
			"max-age=" + cacheFor.toSeconds(),
			"public",
			"immutable"//,
			//TODO this isn't used here (since we're immutable), but it could maybe be used somewhere else
			//"stale-while-revalidate=" + Duration.ofSeconds(30).toSeconds()
		);
		response.setContentType(contentType);
		response.setContentLength(bytes.length);
		response.setStatus(HttpStatus.OK_200);
		response.getHttpOutput().write(bytes);
		response.getHttpOutput().flush();
	}
}
