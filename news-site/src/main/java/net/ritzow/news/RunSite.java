package net.ritzow.news;

import java.net.InetAddress;
import java.nio.file.Path;

public class RunSite {
	public static void main(String[] args) throws Exception {
		var server = NewsSite.start(
			InetAddress.getByName("::1"), false,
			Path.of(System.getProperty("net.ritzow.certs")),
			System.getProperty("net.ritzow.pass")
		);
		server.start();
		server.await();
		server.stop();
	}
}
