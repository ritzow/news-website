package net.ritzow.web.test;

import java.nio.file.Path;
import java.security.KeyStore;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Request.Listener;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Use Jetty Load Generator to test server speed")
class TestLoad {
	
	@Test
	@DisplayName("Run Load Generator")
	@Timeout(2)
	void runTest() throws Exception {
		var server = net.ritzow.news.RunSite.startServer(
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass")
		);
		
		Resource resource = new Resource("/",
			new Resource("/style.css"),
			new Resource("/icon.svg"),
			new Resource("/article")
		);
		
		KeyStore trust = KeyStore.getInstance(
			Path.of(System.getProperty("net.ritzow.certs")).toFile(),
			System.getProperty("net.ritzow.pass").toCharArray()
		);
		
		Client cl = new Client();
		cl.setKeyStore(trust);
		cl.setKeyStorePassword(System.getProperty("net.ritzow.pass"));
		
		LoadGenerator generator = LoadGenerator.builder()
			.scheme("https")
			.host("[::1]")
			.port(443)
			.resource(resource)
			.sslContextFactory(cl)
			.httpClientTransportBuilder(new HTTP2ClientTransportBuilder())
			.threads(1)
			.usersPerThread(1)
			.channelsPerUser(6)
			//.warmupIterationsPerThread(10)
			.iterationsPerThread(100)
			.resourceRate(0)
			.requestListener(new Listener() {
				@Override
				public void onSuccess(Request request) {
					System.out.println("Success " + request);
				}
				
				@Override
				public void onFailure(Request request, Throwable failure) {
					fail(failure);
				}
			})
			.build();
		
		generator.begin().join();
		server.stop();
	}
}
