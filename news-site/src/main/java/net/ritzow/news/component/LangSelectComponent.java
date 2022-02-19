package net.ritzow.news.component;

import j2html.tags.specialized.ButtonTag;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.ritzow.news.Forms;
import net.ritzow.news.Forms.FormField;
import net.ritzow.news.Forms.FormWidget;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.button;
import static j2html.TagCreator.span;
import static net.ritzow.news.ResponseUtil.doRefreshPage;

public class LangSelectComponent {
	public static final FormWidget LANG_SELECT_FORM = FormWidget.of(
		FormField.required("lang-select", Forms::stringReader)
	);
	
	public static ButtonTag langButton(Locale locale, Locale pageLocale) {
		var button = button()
			.withType("submit")
			.withName("lang-select")
			.withValue(locale.toLanguageTag())
			.withClass("lang-button").with(
				span(locale.getDisplayLanguage(locale))
			);
		
		if(locale.equals(pageLocale)) {
			button.withCondDisabled(true);
		}
		return button;
	}
	
	/** Respond to {@code request} with a 303 redirect to the page, and set the language. **/
	private static void processLangForm(Request request, NewsSite site, String langTag) {
		NewsSite.storeLocale(request, site, langTag);
		doRefreshPage(request);
	}
	
	public static URI doLangSelectForm(Request request, NewsSite site, Function<String, Optional<Object>> values) {
		values.apply("lang-select").ifPresent(lang -> processLangForm(request, site, (String)lang));
		return request.getHttpURI().toURI();
	}
}
