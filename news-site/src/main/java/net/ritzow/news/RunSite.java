package net.ritzow.news;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Set;

public class RunSite {
	public static void main(String[] args) throws Exception {
		NewsSite.start(
			false, 
			Path.of(System.getProperty("net.ritzow.certs")), 
			System.getProperty("net.ritzow.pass"),
			Set.of("127.0.0.1", "[::1]"),
			InetAddress.getByName("::1"),
			InetAddress.getByName("127.0.0.1")
		);
	}
}
