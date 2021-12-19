package net.ritzow.news;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;

public class HttpUser {
	private static final String SESSION_ATTRIBUTE = "net.ritzow.sessiondata";
	
	public static List<Locale> localesForUser(Request request) {
		List<String> user = request.getHttpFields()
			.getQualityCSV(HttpHeader.ACCEPT_LANGUAGE);
		List<Locale> locales = new ArrayList<>(user.size() + 1);
		Optional<SessionData> session = getExistingSession(request);
		session.flatMap(SessionData::locale).ifPresent(locales::add);
		user.stream().map(Locale::forLanguageTag).forEachOrdered(locales::add);
		return locales;
	}
	
	public static Optional<SessionData> getExistingSession(Request request) {
		var session = request.getSession(false);
		return session == null ? Optional.empty() : Optional.of((SessionData)session.getAttribute(SESSION_ATTRIBUTE));
	}
	
	public static SessionData session(Request request) {
		HttpSession s = request.getSession();
		Object session = s.getAttribute(SESSION_ATTRIBUTE);
		if(session == null) {
			s.setAttribute(SESSION_ATTRIBUTE, session = new SessionData());
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
