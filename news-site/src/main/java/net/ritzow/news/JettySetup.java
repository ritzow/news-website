package net.ritzow.news;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.session.DefaultSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettySetup {
	
	@FunctionalInterface
	public interface RequestConsumer<T extends Exception> {
		void accept(Request request) throws T;
	}
	
	public static Server newStandardServer(InetAddress bind, boolean requireSni,
			Path keyStore, String keyStorePassword, RequestConsumer<? extends Exception> mainHandler, RequestConsumer<? extends Exception> errorHandler)
			throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
		QueuedThreadPool pool = new QueuedThreadPool(Runtime.getRuntime().availableProcessors());
		pool.setName("pool");
		Server server = new Server(pool);
		setupConnectors(bind, requireSni, server, keyStore, keyStorePassword);
		var onError = new ErrorHandler() {
			@Override
			public void handle(String target, Request baseRequest,
				HttpServletRequest request, HttpServletResponse response) throws IOException {
				Throwable cause = (Throwable)request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
				if(cause != null) {
					cause.printStackTrace();
				}
				try {
					errorHandler.accept(baseRequest);
				} catch(Exception e) {
					throw new IOException(e);
				}
			}
		};
		server.setErrorHandler(onError);
		server.setHandler(setupHandlers(server, new AbstractHandler() {
			@Override
			public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
					throws IOException {
				try {
					mainHandler.accept(baseRequest);
				} catch(Exception e) {
					throw new IOException(e);
				}
			}
		}));
		server.setStopAtShutdown(true);
		server.addBean(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
		return server;
	}
	
	private static Handler setupHandlers(Server server, Handler userHandler) {
		GzipHandler gzipHandler = new GzipHandler();
		gzipHandler.setHandler(setupSessionInfrastructure(server, userHandler));
		SecuredRedirectHandler secureHandler = new SecuredRedirectHandler();
		secureHandler.setHandler(gzipHandler);
		RequestLogHandler logHandler = new RequestLogHandler();
		logHandler.setHandler(secureHandler);
		Logger log = LoggerFactory.getLogger(JettySetup.class);
		logHandler.setRequestLog((request, response) -> log.atInfo().log(
			request.getRemoteInetSocketAddress().getAddress().getHostAddress()
				+ " " + request.getMethod()
				+ " " + request.getHttpURI().asString()
				+ " \"" + HttpStatus.getCode(response.getStatus()).getMessage()
				+ "\" (" + response.getStatus() + ")"));
		StatisticsHandler statsHandler = new StatisticsHandler();
		statsHandler.setHandler(logHandler);
		return statsHandler;
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
		handler.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
		handler.setHandler(inner);
		return handler;
	}
	
	private static void setupConnectors(InetAddress bind, boolean requireSni,
			Server server, Path keyStore, String keyStorePassword) throws CertificateException,
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
		secureCustomizer.setSniHostCheck(requireSni);
		httpConfig.addCustomizer(secureCustomizer);
		var http1 = new HttpConnectionFactory(httpConfig);
		var http11Insecure = new ServerConnector(server, http1);
		http11Insecure.setHost(bind.getHostAddress());
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
		httpSecure.setHost(bind.getHostAddress());
		httpSecure.setPort(443);
		server.setConnectors(new Connector[] { http11Insecure, httpSecure });
	}
}
