package net.ritzow.news;

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
		return (SessionData)session.getAttribute("net.ritzow.sessiondata");
	}
	
	public static SessionData session(Request request) {
		return (SessionData)request.getSession().getAttribute("net.ritzow.sessiondata");
	}
	
	public static Locale bestLocale(Request request, List<Locale> available) {
		return localesForUser(request).stream()
			//.map(range -> Locale.forLanguageTag(range.getRange()))
			.filter(lang -> available.stream().map(Locale::getLanguage).anyMatch(lang.getLanguage()::equals))
			.findFirst().orElse(available.get(0));
	}
}
