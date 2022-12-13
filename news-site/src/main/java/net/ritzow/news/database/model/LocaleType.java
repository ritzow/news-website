package net.ritzow.news.database.model;

import com.google.common.base.Converter;
import io.permazen.annotation.JFieldType;
import io.permazen.core.type.StringEncodedType;
import java.util.Locale;

@JFieldType
public class LocaleType extends StringEncodedType<Locale> {

	public LocaleType() {
		super(Locale.class, 0L, Converter.from(Locale::toLanguageTag, Locale::forLanguageTag));
	}
}
