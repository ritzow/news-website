package net.ritzow.news;

import java.util.List;
import java.util.Locale;

public class Languages {
	
	/** For setting up locales in the database **/
	public static List<Locale> recognizedLocales() {
		return List.of(
			Locale.forLanguageTag("en-US"),
			Locale.forLanguageTag("es"),
			Locale.forLanguageTag("ru"),
			Locale.forLanguageTag("zh")
		);
	}
	
	/*
	public static Locale selectBestLocale(String acceptLanguage, List<Locale> articleSupported) {
		return selectBestLocale(LanguageRange.parse(acceptLanguage), articleSupported);
	}
	
	public static Locale selectBestLocale(List<LanguageRange> userSupported, List<Locale> articleSupported) {
		return userSupported.stream()
			.map(range -> Locale.forLanguageTag(range.getRange()))
			.filter(lang -> articleSupported.stream().map(Locale::getLanguage).anyMatch(lang.getLanguage()::equals))
			.findFirst().orElse(articleSupported.get(0));
	}
	*/
}
