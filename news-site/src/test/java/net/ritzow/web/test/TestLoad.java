package net.ritzow.web.test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import net.ritzow.news.NewsSite;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Request.Listener;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Use Jetty Load Generator to test server speed")
class TestLoad {
	
	//@Test
	@DisplayName("Test using HttpClient")
	@Timeout(5)
	void withJavaHttpClient() throws IOException, InterruptedException, NoSuchAlgorithmException {
		
		System.out.println(SSLContext.getDefault().getProvider());
		
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(1))
			.build();
		
		var request = HttpRequest.newBuilder(URI.create("http://[::1]")).build();
		
		HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
		
		
		
		System.out.println(response);
	}
	
	@Test
	@DisplayName("Run Load Generator")
	@Timeout(5)
	void runTest() throws Exception {
		
		var server = NewsSite.start(
			false, 
			Path.of(System.getProperty("net.ritzow.certs")), 
			System.getProperty("net.ritzow.pass"), 
			Set.of(), 
			InetAddress.getByName("::1")
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
		
		SslContextFactory.Client cl = new Client();
		//cl.setSNIProvider(SniProvider.NON_DOMAIN_SNI_PROVIDER);
		cl.setTrustAll(true);
		cl.setEndpointIdentificationAlgorithm(null);
		cl.setHostnameVerifier((hostname, session) -> true);
		
		LoadGenerator.builder()
			.scheme("https")
			.host("[::1]")
			.port(443)
			.resource(resource)
			.sslContextFactory(cl)
			.httpClientTransportBuilder(new HTTP1ClientTransportBuilder())
			//.httpClientTransportBuilder(new HTTP2ClientTransportBuilder())
			//.threads(4)
			.resourceRate(2)
			.threads(1)
			.runFor(1, TimeUnit.SECONDS)
			//.usersPerThread(1)
			//.channelsPerUser(6)
			//.iterationsPerThread(1)
			//.resourceRate(0)
			//.connectBlocking(true)
			.requestListener(new Listener() {

				@Override
				public void onQueued(Request request) {
					System.out.println(request + " queued");
				}

				@Override
				public void onSuccess(Request request) {
					System.out.println("Success " + request);
				}

				@Override
				public void onFailure(Request request, Throwable failure) {
					//System.out.println(request + " failed: " + failure.getClass() + " " + failure.getMessage());
					failure.printStackTrace();
					//fail(failure);
				}
			})
			.build().begin().join();
	}
}
