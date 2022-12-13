module net.ritzow.news {
	exports net.ritzow.news.database.model;
	
	requires org.eclipse.jetty.server;
	requires org.eclipse.jetty.http2.server;
	/*requires org.eclipse.jetty.http3.server;*/
	requires org.eclipse.jetty.alpn.server;
	requires org.eclipse.jetty.http3.server;
	requires net.ritzow.certmanage;
	requires org.bouncycastle.pkix;
	requires org.bouncycastle.util;
	requires org.bouncycastle.provider;
	requires com.j2html;
	requires org.commonmark;
	requires permazen.main;
	requires permazen.coreapi;
	requires permazen.kv;
	requires permazen.kv.mvstore;
	requires java.sql;
//	requires xodus.entity.store;
//	requires xodus.environment;
//	requires xodus.utils;
//	requires xodus.compress;
//	requires xodus.openAPI;
	requires java.xml;
	requires com.zaxxer.hikari;
	requires org.apache.lucene.core;
	requires com.google.zxing;
	requires org.slf4j;
	requires pngj;
	requires com.h2database;
	requires permazen.util;
	requires com.google.common;
}