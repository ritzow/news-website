package net.ritzow.news;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.util.EnumSet;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.CookieCompliance;
import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
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
			KeyStore keyStore, 
			String keyStorePassword, 
			RequestConsumer mainHandler, 
			RequestConsumer errorHandler, 
			InetAddress... bind) {
		
		QueuedThreadPool pool = new QueuedThreadPool();
		pool.setName("http");
		Server server = new Server(pool);
		
		var sslContextFactory = sslContext(keyStore, keyStorePassword);
		
		for(var addr : bind) {
			server.addConnector(httpPlaintextConnector(server, addr, httpConfig(requireSni)));
			server.addConnector(httpSslConnector(server, addr, httpConfig(requireSni), sslContextFactory));
			//server.addConnector(http3Connector(server, addr, httpConfig(requireSni), sslContextFactory));
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
		/*server.addBean(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));*/
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
	
	private static ServerConnector httpSslConnector(Server server, InetAddress bind, 
		HttpConfiguration config, SslContextFactory.Server sslContext) {
		var http1 = new HttpConnectionFactory(new HttpConfiguration(config));
		var http2 = new HTTP2ServerConnectionFactory(new HttpConfiguration(config));
		//var http3 = new HTTP3ServerConnectionFactory(config);
		var alpn = new ALPNServerConnectionFactory("h3", "h2", "http/1.1");
		//alpn.setDefaultProtocol("h3");
		//alpn.setDefaultProtocol("http/1.1");
		alpn.setDefaultProtocol("h2");
		var ssl = new SslConnectionFactory(sslContext, alpn.getProtocol());
		/* Handlers are found using string lookups in the ConnectionFactory list of the ServerConnector */
		@SuppressWarnings("all") var httpSecure = new ServerConnector(server, ssl, alpn, /*http3,*/ http2, http1);
		setCommonProperties(httpSecure, bind, 443);
		return httpSecure;
	}
	
/*	private static Connector http3Connector(Server server, InetAddress bind,
		HttpConfiguration config, SslContextFactory.Server sslContextFactory) {
		HTTP3ServerConnectionFactory http3 = new HTTP3ServerConnectionFactory(config);
		//http3.getHTTP3Configuration().setStreamIdleTimeout(15000);
		HTTP3ServerConnector connector = new HTTP3ServerConnector(server, sslContextFactory, http3);
		connector.setPort(443);
		// Configure the max number of requests per QUIC connection.
		connector.getQuicConfiguration().setMaxBidirectionalRemoteStreams(1024);
		connector.getQuicConfiguration().setProtocols(List.of("h3"));
		connector.setHost(bind.getHostAddress());
		return connector;
	}*/
	
	private static SslContextFactory.Server sslContext(KeyStore keyStorePkcs12, String keyStorePassword) {
		SslContextFactory.Server sslFactory = new SslContextFactory.Server();
		/* Create PKCS12 file: https://gist.github.com/novemberborn/4eb91b0d166c27c2fcd4 */
		sslFactory.setKeyStore(keyStorePkcs12);
		//sslFactory.setKeyStore(Certs.loadPkcs12(keyStorePkcs12, keyStorePassword.toCharArray()));
		sslFactory.setKeyStorePassword(keyStorePassword);
//		sslFactory.setProvider("NewsSite");
//		ServiceLoader.loadInstalled(SecurityProvider.class);
//		sslFactory.setKeyManagerFactoryAlgorithm(SecurityProvider.KEY_MANAGER_ALGORITHM);
		
		//sslFactory.setKeyStoreResource(new PathResource(keyStorePkcs12));
		
//		class SslCtx extends SslContextFactory {
//			
//		}
		
		return sslFactory;
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
		
		/*httpConfig.addCustomizer((connector, channelConfig, request) -> {
			if(request.getHttpVersion().getVersion() != HttpVersion.HTTP_3.getVersion()) {
				request.getResponse().getHttpFields().add(HttpHeader.ALT_SVC, "h3=\":443\"; ma=3600");
			}
		});*/
		
		return httpConfig;
	}

	private static class SuperHandler extends AbstractHandler {
		private final RequestConsumer mainHandler;

		public SuperHandler(RequestConsumer mainHandler) {this.mainHandler = mainHandler;}

		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException {
			mainHandler.accept(baseRequest);
		}
	}
}
