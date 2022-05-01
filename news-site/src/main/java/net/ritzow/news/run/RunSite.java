package net.ritzow.news.run;

import java.net.InetAddress;
import java.util.Set;
import net.ritzow.news.Certs;
import net.ritzow.news.NewsSite;
import org.bouncycastle.cert.X509CertificateHolder;

public class RunSite {
	public static void main(String[] args) throws Exception {

		//Certs.loadPkcs12(Path.of(System.getProperty("keystore")), System.getProperty("keystorePassword").toCharArray())
		
		String host = System.getProperty("hostname");
		String password = System.getProperty("keystorePassword");
		
		var cert = Certs.selfSigned(host, password.toCharArray());
		
		for(var alias : (Iterable<String>)cert.aliases()::asIterator) {
			//cert.getEntry(alias, new PasswordProtection(password.toCharArray()));
			System.out.println(new X509CertificateHolder(cert.getCertificateChain(alias)[0].getEncoded()));
		}
		
		NewsSite.start(
			false, 
			cert, 
			password,
			Set.of("127.0.0.1", "[::1]", host),
			InetAddress.getByName("0.0.0.0")
		);
	}
}
