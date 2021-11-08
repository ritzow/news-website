package net.ritzow.jetstart;

import jakarta.servlet.SessionTrackingMode;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Set;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.SecuredRedirectHandler;
import org.eclipse.jetty.server.session.DefaultSessionCacheFactory;
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.eclipse.jetty.server.session.NullSessionDataStoreFactory;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettySetup {
	private static final boolean DEBUG = true;
	
	public static Server newStandardServer(Path keyStore, String keyStorePassword, Handler handler) throws CertificateException,
			IOException, KeyStoreException, NoSuchAlgorithmException {
		QueuedThreadPool pool = new QueuedThreadPool(Runtime.getRuntime().availableProcessors());
		pool.setName("pool");
		Server server = new Server(pool);
		setupConnectors(server, keyStore, keyStorePassword);
		ErrorHandler onError = new ErrorHandler();
		if(DEBUG) {
			onError.setShowStacks(true);
		} else {
			onError.setShowStacks(false);
			onError.setShowMessageInTitle(false);
			onError.setShowServlet(false);
		}
		server.setErrorHandler(onError);
		/* TODO use directly instead of adding beans? */
		server.addBean(new DefaultSessionCacheFactory());
		server.addBean(new NullSessionDataStoreFactory());
		SecuredRedirectHandler secureHandler = new SecuredRedirectHandler();
		secureHandler.setHandler(setupSessionInfrastructure(server, handler));
		RequestLogHandler logHandler = new RequestLogHandler();
		logHandler.setHandler(secureHandler);
		Logger log = LoggerFactory.getLogger(JettySetup.class);
		logHandler.setRequestLog((request, response) -> log.info(request + " Response " + response.getStatus()));
		server.setHandler(logHandler);
		return server;
	}
	
	private static Handler setupSessionInfrastructure(Server server, Handler inner) {
		DefaultSessionIdManager idManager = new DefaultSessionIdManager(server);
		idManager.setWorkerName(null);
		server.setSessionIdManager(idManager);
		SessionHandler handler = new SessionHandler();
		handler.setServer(server);
		handler.setSessionIdManager(idManager);
		handler.setSessionCookie("session");
		handler.setHttpOnly(true);
		handler.setSecureRequestOnly(true);
		handler.setSameSite(SameSite.STRICT);
		handler.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
		handler.setHandler(inner);
		return handler;
	}
	
	private static void setupConnectors(Server server, Path keyStore, String keyStorePassword) throws CertificateException,
		IOException, KeyStoreException, NoSuchAlgorithmException {
		var httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		httpConfig.setSecureScheme("https");
		httpConfig.setSecurePort(443);
		httpConfig.setHttpCompliance(HttpCompliance.RFC7230);
		httpConfig.setUriCompliance(UriCompliance.RFC3986_UNAMBIGUOUS);
		httpConfig.setRequestCookieCompliance(CookieCompliance.RFC6265);
		httpConfig.setResponseCookieCompliance(CookieCompliance.RFC6265);
		var secureCustomizer = new SecureRequestCustomizer();
		secureCustomizer.setStsMaxAge(3600);
		secureCustomizer.setStsIncludeSubDomains(true);
		if(DEBUG) {
			secureCustomizer.setSniHostCheck(false);
		}
		httpConfig.addCustomizer(secureCustomizer);
		var http1 = new HttpConnectionFactory(httpConfig);
		var http11Insecure = new ServerConnector(server, http1);
		if(DEBUG) {
			http11Insecure.setHost("::1");
		}
		http11Insecure.setPort(80);
		var http2Config = new HttpConfiguration(httpConfig);
		var http2 = new HTTP2ServerConnectionFactory(http2Config);
		var alpn = new ALPNServerConnectionFactory(http2.getProtocol(), http1.getProtocol());
		alpn.setDefaultProtocol(http2.getProtocol());
		SslContextFactory.Server sslFactory = new SslContextFactory.Server();
		/* Create PKCS12 file: https://gist.github.com/novemberborn/4eb91b0d166c27c2fcd4 */
		KeyStore ks = KeyStore.getInstance(keyStore.toFile(), keyStorePassword.toCharArray());
		sslFactory.setKeyStore(ks);
		sslFactory.setKeyStorePassword(keyStorePassword);
		var ssl = new SslConnectionFactory(sslFactory, alpn.getProtocol());
		var httpSecure = new ServerConnector(server, ssl, alpn, http2, http1);
		if(DEBUG) {
			httpSecure.setHost("::1");
		}
		httpSecure.setPort(443);
		server.setConnectors(new Connector[] { http11Insecure, httpSecure });
	}
}
