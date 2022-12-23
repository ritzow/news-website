package net.ritzow.news.database;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.NRTCachingDirectory;

public class SearchIndex {

	private static final Pattern WORD_DELIM = Pattern.compile("\\s+");

	private final IndexWriter indexer;
	private final SearcherManager searcher;

	public SearchIndex() {
		try {
			//Look into NRTManager
			//https://blog.mikemccandless.com/2011/11/near-real-time-readers-with-lucenes.html
			//Use NRTCachingDirectory when replacing ByteBuffersDirectory with disk directory
			indexer = new IndexWriter(new NRTCachingDirectory(new ByteBuffersDirectory(), 16, 32), new IndexWriterConfig(new StandardAnalyzer()));
			//https://blog.mikemccandless.com/2011/11/near-real-time-readers-with-lucenes.html
			//TODO applyAllDeletes false can improve performance.
			searcher = new SearcherManager(indexer, null);
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Stream<Long> search(String query, Locale lang) throws IOException {
		searcher.maybeRefresh();
		var search = searcher.acquire();
		try {
			var builder = new PhraseQuery.Builder().setSlop(5);
			WORD_DELIM.splitAsStream(query).forEachOrdered(token -> builder.add(new Term("content", token)));
			var query1 = new BooleanQuery.Builder()
				.add(builder.build(), Occur.MUST)
				//TODO match a list of langs in order instead of exact
				.add(new TermQuery(new Term("lang", lang.toLanguageTag())), Occur.MUST)
				.build(); //new StandardQueryParser(new StandardAnalyzer()).parse(query, "content");
			var results = search.search(query1, TopScoreDocCollector.createSharedManager(10, null, 10));

			return Stream.empty(); /*Stream.of(results.scoreDocs)
				.map(result -> {
					search.getIndexReader().document(result.doc, new StoredFieldVisitor() {
						private boolean finished;
						@Override
						public Status needsField(FieldInfo fieldInfo) {
							if(fieldInfo.getName().equals("id")) {
								finished = true;
								return Status.YES;
							}
							return finished ? Status.STOP : Status.NO;
						}

						@Override
						public void longField(FieldInfo fieldInfo, long value) {
							if(fieldInfo.getName().equals("id")) {
								yield value;
							}
						}
					}
				});*/
		} finally {
			searcher.release(search);
		}
	}

	public void index(long id, Locale locale, String title, String markdown) throws IOException {
		indexer.addDocument(List.of(
			new StoredField("id", id),
			new StoredField("lang", locale.toLanguageTag()),
			new TextField("title", title, Store.NO),
			new TextField("content", markdown, Store.NO)
		));
	}
}
