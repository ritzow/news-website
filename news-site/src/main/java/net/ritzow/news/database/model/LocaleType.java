package net.ritzow.news.database.model;

import com.google.common.base.Converter;
import com.google.common.reflect.TypeToken;
import io.permazen.annotation.JFieldType;
import io.permazen.core.type.StringConvertedType;
import io.permazen.core.type.StringEncodedType;
import java.util.Locale;

@JFieldType
public class LocaleType extends StringEncodedType<Locale> /*StringConvertedType<Locale>*/ {

	public LocaleType() {
		super(Locale.class, 0L, new Converter<Locale, String>() {
			@Override
			protected String doForward(Locale locale) {
				return locale.toLanguageTag();
			}

			@Override
			protected Locale doBackward(String s) {
				return Locale.forLanguageTag(s);
			}
		});
	}
}
