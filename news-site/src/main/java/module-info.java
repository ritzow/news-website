module net.ritzow.news {
	exports net.ritzow.news;
	exports net.ritzow.news.database;
	exports net.ritzow.news.database.model;
	requires java.annotation;
	requires org.eclipse.jetty.server;
	requires org.eclipse.jetty.http2.server;
	requires org.eclipse.jetty.alpn.server;
	requires org.eclipse.jetty.http3.server;
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.util;
	requires org.bouncycastle.provider;
	requires com.j2html;
	requires org.commonmark;
	requires permazen.main;
	requires io.permazen.kv;
	requires io.permazen.coreapi;
	requires io.permazen.kv.array;
	requires io.permazen.util;
	requires java.xml;
	requires org.apache.lucene.core;
	requires com.google.zxing;
	requires org.slf4j;
	requires pngj;
	requires com.google.common;
}