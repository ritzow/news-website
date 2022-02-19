package net.ritzow.news.page;

import j2html.tags.DomContent;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import net.ritzow.news.Forms;
import net.ritzow.news.Forms.FormField;
import net.ritzow.news.Forms.FormWidget;
import net.ritzow.news.HttpUser;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.server.Request;

import static j2html.TagCreator.*;
import static net.ritzow.news.PageTemplate.freeze;
import static net.ritzow.news.PageTemplate.postForm;
import static net.ritzow.news.ResponseUtil.doRefreshPage;

public class Login {
	
	public static final FormWidget LOGGED_IN_FORM = FormWidget.of(
		FormField.required("logout", Forms::stringReader)
	);
	
	private static final DomContent LOGIN_FORM_CONTENT = freeze(
		input().withClasses("text-field")
			.withName("username")
			.withType("text")
			.attr("autocomplete", "username")
			.withPlaceholder("Username"),
		input().withClasses("text-field")
			.withName("password")
			.withType("password")
			.attr("autocomplete", "current-password")
			.withPlaceholder("Password"),
		label(
			input()
				.withName("login-remember")
				.withType("checkbox"), 
			rawHtml("Remember me")
		),
		button("Login")
			.withName("login-action")
			.withValue("login"),
		button("Sign up")
			.withName("login-action")
			.withValue("signup")
	);
	
	/* Cool checkboxes https://stackoverflow.com/questions/4148499/how-to-style-a-checkbox-using-css */
	/* Custom checkbox https://stackoverflow.com/questions/44299150/set-text-inside-a-check-box/44299305 */
	/* TODO the username should be prefilled in "value" on the next page if the user clicks "Sign up" */
	public static DomContent loginForm() {
		return postForm().withClass("login-form").with(LOGIN_FORM_CONTENT);
	}
	
	public static DomContent loggedInForm(String username) {
		return postForm().withClass("logged-in-form").with(
			text(username),
			button("Log out").withName("logout").withValue("logout")
		);
	}
	
	public static URI doLoginForm(Request request, NewsSite site, Function<String, Optional<Object>> values) {
		var login = values.apply("login-action");
		
		if(login.isPresent()) {
			String username = (String)values.apply("username").orElseThrow();
			byte[] password = (byte[])values.apply("password").orElseThrow();
			
			try {
				switch(login.map(o -> (String)o).orElseThrow()) {
					case "login" -> accountLogin(request, site, username, password);
					case "signup" -> accountSignup(request, site, username, password);
				}
				
				/* TODO check for errors or if username already exists */
				//if(values.apply("login-remember").map(o -> (String)o).orElse("off").equals("on")) {
					/* TODO remember me */
				//}
				
			} finally {
				Arrays.fill(password, (byte)0);
			}
		}
		return request.getHttpURI().toURI();
	}
	
	public static URI doLoggedInForm(Request request, Function<String, Optional<Object>> values) {
		if(values.apply("logout").filter(val -> val.equals("logout")).isPresent()) {
			HttpUser.getExistingSession(request).ifPresent(session -> session.user(null));
		}
		return request.getHttpURI().toURI();
	}
	
	private static void accountLogin(Request request, NewsSite site, String username, byte[] password) {
		/* TODO implement rate limiting for retries based on IP address, etc. */
		if(site.cm.authenticateLogin(username, password)) {
			HttpUser.session(request).user(username);
		}
		
		doRefreshPage(request);
	}
	
	private static void accountSignup(Request request, NewsSite site, String username, byte[] password) {
		site.cm.newAccount(
			username,
			password
		);
		
		var session = HttpUser.session(request);
		session.user(username);
		doRefreshPage(request);
	}
}
