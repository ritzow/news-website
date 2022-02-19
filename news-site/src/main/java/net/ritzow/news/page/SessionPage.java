package net.ritzow.news.page;

import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import net.ritzow.news.NewsSite;
import net.ritzow.news.ResponseUtil;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;

public class SessionPage {//TODO this only works if privacy mode is off, because it blocks cross-site cookies.
	
	//TODO maybe use redirects instead?
	public static void sessionPage(Request request, NewsSite site, Iterator<String> path) throws IOException {
		switch(HttpMethod.fromString(request.getMethod())) {
			case GET, HEAD -> {
				String origin = request.getHttpFields().get(HttpHeader.ORIGIN);
				if(origin != null) {
					URI originUrl = URI.create(origin);
					if(HttpScheme.HTTPS.is(originUrl.getScheme()) && site.peers.contains(originUrl.getHost())) {
						request.getResponse().getHttpFields()
							.add("Access-Control-Allow-Methods", "GET")
							.add("Access-Control-Allow-Origin", origin)
							.add("Access-Control-Allow-Credentials", "true")
							.add("Access-Control-Allow-Headers", HttpHeader.COOKIE.asString())
							.add(HttpHeader.VARY, HttpHeader.ORIGIN.asString());
						NewsSite.doSessionInitResponse(request);
					} else {
						ResponseUtil.doEmptyResponse(request, HttpStatus.UNAUTHORIZED_401);
					}
				} else {
					ResponseUtil.doEmptyResponse(request, HttpStatus.UNAUTHORIZED_401);
				}
			}
		}
	}
}