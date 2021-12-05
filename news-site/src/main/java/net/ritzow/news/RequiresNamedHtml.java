package net.ritzow.news;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface RequiresNamedHtml {
	String[] value();
}
