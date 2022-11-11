package net.ritzow.news.test;

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import net.ritzow.cert.CertificateAuthority;
import net.ritzow.news.Certs;
import net.ritzow.news.NewsSite;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.junit.jupiter.api.Test;

public class WebsiteTest {
	@Test
	void run() throws Exception {
		SecureRandom random = SecureRandom.getInstanceStrong();
		var host = InetAddress.getLoopbackAddress();
//		var root = CertificateAuthority.newRootCertificate(CertificateAuthority.rdn(
//			Map.entry(RFC4519Style.o, "test-root")
//		), random);
		System.out.println(InetAddress.getLoopbackAddress());
		var cert = CertificateAuthority.newSelfSignedCertificate(CertificateAuthority.rdn(
			Map.entry(RFC4519Style.o, "test-web")
		), random, CertificateAuthority.ipAddressName(host));
		String password = "fiwhr243s"; //new BigInteger(512, random).toString(Character.MAX_RADIX);
		String organization = "junit-test";
		System.setProperty("title", "RedNet");
		var server = NewsSite.start(
			false,
			/*CertificateAuthority.newKeyStore(cert)*/
			Certs.selfSigned(host.getHostAddress(), organization, password.toCharArray()),
			password,
			Set.of(host.getHostAddress()),
			host
		);
		server.waitForExit();
	}
}
