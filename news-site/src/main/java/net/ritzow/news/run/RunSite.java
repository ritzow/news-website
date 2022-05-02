package net.ritzow.news.run;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Set;
import net.ritzow.news.Certs;
import net.ritzow.news.NewsSite;

public class RunSite {
	public static void main(String[] args) throws Exception {
		
		String host = System.getProperty("hostname");
		String organization = System.getProperty("organization");
		String password = System.getProperty("keystorePassword");
		
		var cert = System.getProperty("keystore") == null ? Certs.selfSigned(host, organization, password.toCharArray())
			: Certs.loadPkcs12(Path.of(System.getProperty("keystore")), password.toCharArray());
		
		NewsSite.start(
			false, 
			cert, 
			password,
			Set.of("127.0.0.1", "[::1]", host),
			InetAddress.getByName("0.0.0.0")
		);
	}
}
