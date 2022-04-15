package net.ritzow.news;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.file.Files;
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
	
	private static final Logger LOG = LoggerFactory.getLogger(JettySetup.class);
	
	@FunctionalInterface
	public interface RequestConsumer {
		void accept(Request request) throws IOException;
	}
	
	public static Server newStandardServer(
			boolean requireSni, 
			Path keyStore, 
			String keyStorePassword, 
			RequestConsumer mainHandler, 
			RequestConsumer errorHandler, 
			InetAddress... bind)
		
			throws CertificateException, 
			IOException, 
			KeyStoreException, 
			NoSuchAlgorithmException {
		
		QueuedThreadPool pool = new QueuedThreadPool();
		pool.setName("pool");
		Server server = new Server(pool);
		
		for(var addr : bind) {
			server.addConnector(httpPlaintextConnector(server, addr, httpConfig(requireSni)));
			server.addConnector(httpSslConnector(server, addr, httpConfig(requireSni), keyStore, keyStorePassword));
		}

		var onError = new ErrorHandler() {
			@Override
			public void handle(String target, Request baseRequest,
				HttpServletRequest request, HttpServletResponse response) throws IOException {
				Throwable cause = (Throwable)request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
				if(cause != null) {
					cause.printStackTrace();
				}
				errorHandler.accept(baseRequest);
			}
		};
		
		server.setErrorHandler(onError);
		server.setHandler(setupHandlers(server, new SuperHandler(mainHandler)));
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
		logHandler.setRequestLog(JettySetup::log);
		StatisticsHandler statsHandler = new StatisticsHandler();
		statsHandler.setHandler(logHandler);
		return statsHandler;
	}
	
	private static void log(Request request, Response response) {
		LOG.atInfo().log(
			request.getRemoteInetSocketAddress().getAddress().getHostAddress()
				+ " " + request.getMethod()
				+ " " + request.getHttpURI().asString()
				+ " \"" + HttpStatus.getCode(response.getStatus()).getMessage()
				+ "\" (" + response.getStatus() + ")"
				+ " (" + (System.currentTimeMillis() - request.getTimeStamp()) + " ms)"
		);
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
		//handler.setSameSite(SameSite.NONE); /* TODO test, is this correct or a vulnerability? will allow other sites to access sensitive info */
		handler.setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
		handler.setHandler(inner);
		return handler;
	}
	
	private static ServerConnector httpPlaintextConnector(Server server, InetAddress bind, HttpConfiguration config) {
		var http1 = new HttpConnectionFactory(new HttpConfiguration(config));
		@SuppressWarnings("all") var http11Insecure = new ServerConnector(server, http1);
		setCommonProperties(http11Insecure, bind, 80);
		return http11Insecure;
	}
	
	private static ServerConnector httpSslConnector(Server server, InetAddress bind, HttpConfiguration config, Path keyStorePkcs12, String keyStorePassword) 
			throws CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
		var http1 = new HttpConnectionFactory(new HttpConfiguration(config));
		var http2 = new HTTP2ServerConnectionFactory(new HttpConfiguration(config));
		var alpn = new ALPNServerConnectionFactory("h2", "http/1.1");
		alpn.setDefaultProtocol(http2.getProtocol());
		SslContextFactory.Server sslFactory = new SslContextFactory.Server();
		/* Create PKCS12 file: https://gist.github.com/novemberborn/4eb91b0d166c27c2fcd4 */
		sslFactory.setKeyStore(Certs.loadPkcs12(keyStorePkcs12, keyStorePassword.toCharArray()));
		sslFactory.setKeyStorePassword(keyStorePassword);
		var ssl = new SslConnectionFactory(sslFactory, alpn.getProtocol());
		/* Handlers are found using string lookups in the ConnectionFactory list of the ServerConnector */
		@SuppressWarnings("all") var httpSecure = new ServerConnector(server, ssl, alpn, http2, http1);
		setCommonProperties(httpSecure, bind, 443);
		return httpSecure;
	}
	
	private static void setCommonProperties(ServerConnector con, InetAddress bind, int port) {
		con.setInheritChannel(false);
		con.setAcceptedTcpNoDelay(false);
		con.setReuseAddress(false);
		con.setReusePort(false);
		con.setHost(bind.getHostAddress());
		con.setPort(port);
	}
	
	private static HttpConfiguration httpConfig(boolean requireSni) {
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
		
		return httpConfig;
	}

	private static class SuperHandler extends AbstractHandler {
		private final RequestConsumer mainHandler;

		public SuperHandler(RequestConsumer mainHandler) {this.mainHandler = mainHandler;}

		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException {
			try {
				mainHandler.accept(baseRequest);
			} catch(Exception e) {
				if(e instanceof RuntimeException r) {
					throw r;
				}
				throw new IOException(e);
			}
		}
	}
}
