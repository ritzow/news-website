package net.ritzow.news;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Iterator;
import java.util.function.Supplier;
import net.ritzow.news.ResponseUtil.ContextRequestConsumer;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

class StaticContentHandler implements ContextRequestConsumer<IOException> {
	private final String contentType;
	private final Supplier<InputStream> resource;
	private SoftReference<byte[]> content;
	private String etag;
	
	public StaticContentHandler(Supplier<InputStream> resource, String contentType) {
		this.contentType = contentType;
		this.resource = resource;
		content = new SoftReference<>(null);
	}
	
	@Override
	public void accept(Request request, Iterator<String> path) throws IOException {
		
		Response baseResponse = request.getResponse();
		
		byte[] data = null;
		
		if(etag == null) {
			data = load();
		}
		
		HttpField field = request.getHttpFields().getField(HttpHeader.IF_NONE_MATCH);
		
		if(field != null && field.contains(etag)) {
			baseResponse.setStatus(HttpStatus.NOT_MODIFIED_304);
		}
		
		/* Let browser cache for 10 minutes but also always check that there were no changes. */
		baseResponse.getHttpFields().addCSV(HttpHeader.CACHE_CONTROL, "max-age=" + Duration.ofMinutes(10).toSeconds(), "no-cache");
		//TODO referrer should be added elsewhere
		baseResponse.setHeader(HttpHeader.REFERER, "no-referrer");
		baseResponse.setHeader(HttpHeader.ETAG, etag);
		
		if(baseResponse.getStatus() != HttpStatus.NOT_MODIFIED_304) {
			if(data == null && (data = content.get()) == null) {
				data = load();
			}
			baseResponse.setContentType(contentType);
			baseResponse.setContentLength(data.length);
			baseResponse.setStatus(HttpStatus.OK_200);
			baseResponse.getHttpOutput().write(data);
			baseResponse.getHttpOutput().flush();
		}
		request.setHandled(true);
	}
	
	private byte[] load() throws IOException {
		byte[] data;
		try(var in = resource.get()) {
			data = in.readAllBytes();
			if(etag == null) {
				MessageDigest hash = MessageDigest.getInstance("SHA-512");
				etag = "\"" + Base64.getEncoder().withoutPadding().encodeToString(hash.digest(data)) + "\"";
			}
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
		content = new SoftReference<>(data);
		
		return data;
	}
}
