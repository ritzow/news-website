package net.ritzow.jetstart;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public abstract class DynamicContentHandler extends AbstractHandler {
	@Override
	public final void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		//TODO check if already on client to make use of etag
		ContentInfo result = generateContent(baseRequest, response.getWriter());
		response.setContentType(result.contentType());
		response.setStatus(HttpStatus.OK_200);
		response.setHeader(HttpHeader.ETAG.asString(), result.etag());
		/* TODO generate etag value from content written to body outputstream */
		baseRequest.setHandled(true);
	}
	
	public static record ContentInfo(String contentType, String etag) {}
	protected abstract ContentInfo generateContent(Request request, Writer body) throws IOException;
}
