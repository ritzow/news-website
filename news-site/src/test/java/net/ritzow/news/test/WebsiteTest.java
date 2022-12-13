package net.ritzow.news.test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;
import net.ritzow.cert.CertificateAuthority;
import net.ritzow.news.Certs;
import net.ritzow.news.NewsSite;
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
			Certs.selfSigned(new GeneralNames(addresses.stream().map(CertificateAuthority::ipAddressName)
				.toArray(GeneralName[]::new)), organization, password.toCharArray(), random),
			password,
			addresses.stream().map(InetAddress::getHostAddress).collect(Collectors.toSet()),
			addresses.toArray(InetAddress[]::new)
		);
		server.waitForExit();
	}
}
