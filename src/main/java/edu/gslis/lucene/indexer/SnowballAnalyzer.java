package edu.gslis.lucene.indexer;

import java.io.Reader;


import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.util.Version;

class SnowballAnalyzer extends StopwordAnalyzerBase {

	private int maxTokenLength = StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH;

	public SnowballAnalyzer(Version version) {
		super();
	}

	public SnowballAnalyzer(Version version, CharArraySet stopwords) {
		super(stopwords);
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		final StandardTokenizer src = new StandardTokenizer();
		src.setMaxTokenLength(maxTokenLength);
		TokenStream tok = new StandardFilter(src);
		tok = new LowerCaseFilter(tok);
		tok = new StopFilter(tok, stopwords);
		tok = new SnowballFilter(tok, "English");
		return new TokenStreamComponents(src, tok) {
			@Override
			protected void setReader(final Reader reader)  {
				src.setMaxTokenLength(maxTokenLength);
				super.setReader(reader);
			}
		};
	}
}