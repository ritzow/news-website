package net.ritzow.news.test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.ritzow.news.Certs;
import net.ritzow.news.ContentUtil;
import net.ritzow.news.NewsSite;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.jupiter.api.Test;

public class WebsiteTest {
	@Test
	void run() throws Exception {
//		var root = CertificateAuthority.newRootCertificate(CertificateAuthority.rdn(
//			Map.entry(RFC4519Style.o, "test-root")
//		), random);
//		System.out.println(InetAddress.getLoopbackAddress());
//		var cert = CertificateAuthority.newSelfSignedCertificate(CertificateAuthority.rdn(
//			Map.entry(RFC4519Style.o, "test-web")
//		), random, CertificateAuthority.ipAddressName(InetAddress.getByName("127.0.0.1")), 
//			CertificateAuthority.ipAddressName(InetAddress.getByName("::1")));

		var random = SecureRandom.getInstanceStrong();
		
		Set<InetAddress> addresses = Set.of(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("::1"));
		String password = new BigInteger(512, random).toString(Character.MAX_RADIX);
		System.setProperty("title", "RedNet");
		String organization = "junit-test";
		var server = NewsSite.start(
			false,
			/*CertificateAuthority.newKeyStore(cert)*/
			Certs.selfSigned(new GeneralNames(addresses.stream().map(addr -> new GeneralName(GeneralName.iPAddress, new DEROctetString(addr.getAddress())))
				.toArray(GeneralName[]::new)), organization, password.toCharArray(), random),
			password,
			addresses.stream().map(InetAddress::getHostAddress).collect(Collectors.toSet()),
			addresses.toArray(InetAddress[]::new)
		);
		
		var queue = new ArrayBlockingQueue<Runnable>(4);
		try(var exec = new ThreadPoolExecutor(4, 4, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue)) {
			exec.prestartAllCoreThreads();
			while(!Thread.interrupted()) {
				queue.put(() -> {
					String username = ContentUtil.generateGibberish(random, false, 1, 10).trim();
					server.cm.newAccount(username, ContentUtil.generateGibberish(random, false, 1, 16).getBytes(StandardCharsets.UTF_8));
					System.out.println("generated user " + username);
				});
				queue.put(() -> {
					Locale locale = server.cm.getSupportedLocales().get(random.nextInt(server.cm.getSupportedLocales().size()));
					String urlna = ContentUtil.generateGibberish(random, false, 1, 10).trim();
					String title = ContentUtil.generateGibberish(random, false, 1, 10);
					server.cm.newArticle(urlna, locale,
						title, ContentUtil.generateGibberish(random, true, 1000, 8));
					System.out.println("generated " + urlna + " " + title + " " + locale);
					try {
						Thread.sleep(random.nextLong(1000, 2000));
					} catch(InterruptedException e) {
						throw new RuntimeException(e);
					}
				});
			}	
		}
		
		server.waitForExit();
	}
}
