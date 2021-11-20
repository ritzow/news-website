package net.ritzow.jetstart;

import java.util.*;

public interface Translator<T> {
	
	T forLocale(String name, Locale locale);
	T forPrioritized(String name, List<Locale> acceptable);
	
	static Translator<String> ofProperties(Properties properties) {
		return new PropertiesTranslations(properties);
	}
	
	final class PropertiesTranslations implements Translator<String> {
		private final HashMap<String, HashMap<String, HashMap<String, String>>> translations;
		
		private PropertiesTranslations(Properties props) {
			this.translations = new HashMap<>();
			for(var entry : props.entrySet()) {
				String key = (String)entry.getKey();
				String translation = (String)entry.getValue();
				int dotIndex = key.indexOf('.');
				String name = key.substring(0, dotIndex);
				Locale locale = dotIndex == -1 ? Locale.ROOT : Locale.forLanguageTag(key.substring(dotIndex + 1));
				translations.compute(name, (name_, langs) -> {
					if(langs == null) {
						langs = new HashMap<>();
					}
					
					langs.compute(locale.getLanguage(), (lang_, countries) -> {
						if(countries == null) {
							countries = new HashMap<>();
						}
						
						/* locale.getCountry(): If no country specified, empty string */
						if(countries.putIfAbsent(locale.getCountry(), translation) != null) {
							throw new RuntimeException("'" + key + " assigned a value twice");
						}
						
						return countries;
					});
					return langs;
				});
			}
		}
		
		//TODO testing
		@Override
		public String forLocale(String name, Locale locale) {
			var langs = translations.get(name);
			var countries = Objects.requireNonNullElseGet(langs.get(locale.getLanguage()), () -> langs.get(""));
			String translation = countries.get(locale.getCountry());
			if(translation == null) {
				return countries.get("");
			}
			return translation;
		}
		
		@Override
		public String forPrioritized(String name, List<Locale> acceptable) {
			for(Locale locale : acceptable) {
				String result = forLocale(name, locale);
				if(result != null) {
					return result;
				}
			}
			return forLocale(name, Locale.ROOT);
		}
	}
}
