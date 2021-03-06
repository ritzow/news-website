module net.ritzow.news {
	requires org.eclipse.jetty.server;
	requires org.eclipse.jetty.http2.server;
	requires org.eclipse.jetty.http3.server;
	requires org.eclipse.jetty.alpn.server;
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.util;
	requires org.bouncycastle.provider;
	requires com.j2html;
	requires org.commonmark;
	requires java.sql;
	requires java.xml;
	requires com.zaxxer.hikari;
	requires org.apache.lucene.core;
	requires org.apache.lucene.queryparser;
	requires com.google.zxing;
	requires org.slf4j;
	requires pngj;
}