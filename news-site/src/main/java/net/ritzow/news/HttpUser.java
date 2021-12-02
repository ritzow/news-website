package net.ritzow.news;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;

public class HttpUser {
	public static List<Locale> localesForUser(Request request) {
		List<String> user = request.getHttpFields()
			.getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);
		List<Locale> locales = new ArrayList<>(user.size() + 1);
		SessionData session = getExistingSession(request);
		if(session != null) {
			session.locale().ifPresent(locales::add);
		}
		user.stream().map(Locale::forLanguageTag).forEachOrdered(locales::add);
		return locales;
	}
	
	public static SessionData getExistingSession(Request request) {
		var session = request.getSession(false);
		if(session == null) {
			return null;
		}
		return (SessionData)session.getAttribute(sessionAttr);
	}
	
	private static final String sessionAttr = "net.ritzow.sessiondata";
	
	public static SessionData session(Request request) {
		HttpSession s = request.getSession();
		Object session = s.getAttribute(sessionAttr);
		if(session == null) {
			s.setAttribute(sessionAttr, session = new SessionData());
		}
		return (SessionData)session;
	}
	
	public static Locale bestLocale(Request request, Iterable<Locale> available) {
		for(Locale userL : localesForUser(request)) {
			for(Locale contentL : available) {
				if(contentL.getLanguage().equals(userL.getLanguage())) {
					for(Locale contentCountryL : available) {
						if(contentCountryL.equals(userL)) {
							return contentCountryL;
						}
					}
					return contentL;
				}
			}
		}
		return available.iterator().next();
	}
}
