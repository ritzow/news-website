package net.ritzow.news;

import java.util.Locale;
import java.util.Optional;

public class SessionData {
	private Locale locale;
	
	public void locale(Locale locale) {
		this.locale = locale;
	}
	
	public Optional<Locale> locale() {
		return Optional.ofNullable(locale);
	}
}
