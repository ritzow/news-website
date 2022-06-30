package net.ritzow.news.database;

import java.io.IOException;
import org.apache.lucene.analysis.TokenStream;
import org.commonmark.node.Node;

public class MarkdownTokenStream extends TokenStream {
	
	private final Node markdown;
	private final Node current;
	
	public MarkdownTokenStream(Node markdown) {
		this.markdown = markdown;
		this.current = markdown;
	}
	
	@Override
	public boolean incrementToken() throws IOException {
		return false;
	}

	@Override
	public void end() throws IOException {
		super.end();
	}

	@Override
	public void reset() throws IOException {
		super.reset();
	}

	@Override
	public void close() throws IOException {
		super.close();
	}
}
