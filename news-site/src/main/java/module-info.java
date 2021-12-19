module net.ritzow.news {
	requires org.eclipse.jetty.server;
	requires org.eclipse.jetty.http2.server;
	requires org.eclipse.jetty.alpn.server;
	requires org.eclipse.jetty.jmx;
	requires com.j2html;
	requires org.commonmark;
	requires java.sql;
	requires com.zaxxer.hikari;
	requires java.management;
}