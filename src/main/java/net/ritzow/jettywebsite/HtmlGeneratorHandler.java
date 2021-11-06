package net.ritzow.jettywebsite;

import j2html.TagCreator;
import j2html.tags.specialized.HtmlTag;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class HtmlGeneratorHandler extends AbstractHandler {
	private final HtmlTag html;
	private static final String etag = generateEtag();

	public HtmlGeneratorHandler(HtmlTag html) {
		this.html = html;
	}
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {
		response.setContentType("text/html");
		response.setStatus(HttpStatus.OK_200);
		response.setHeader(HttpHeader.ETAG.asString(), etag);
		//TODO check if already on client to make use of etag
		response.getWriter().append(TagCreator.document(html));
		baseRequest.setHandled(true);
	}
	
	private static String generateEtag() {
		try {
			MessageDigest hash = MessageDigest.getInstance("MD5");
			hash.update(ByteBuffer.allocate(Long.BYTES).putLong(System.currentTimeMillis()).flip());
			return "W/" + "\"" + new BigInteger(1, hash.digest()).toString(Character.MAX_RADIX) + "\"";
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
