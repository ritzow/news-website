package net.ritzow.news;

import java.util.Locale;
import java.util.Optional;

public class SessionData {
	private Locale locale;
	private String authenticatedUsername;
	
	public void locale(Locale locale) {
		this.locale = locale;
	}
	
	public Optional<Locale> locale() {
		return Optional.ofNullable(locale);
	}
	
	public void user(String username) {
		this.authenticatedUsername = username;
	}
	
	public Optional<String> user() {
		return Optional.ofNullable(authenticatedUsername);
	}
}
