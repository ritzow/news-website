module net.ritzow.jetstart {
	requires transitive org.eclipse.jetty.server;
	requires org.eclipse.jetty.http2.server;
	requires org.eclipse.jetty.alpn.server;
	requires com.j2html;
	exports net.ritzow.jetstart;
}