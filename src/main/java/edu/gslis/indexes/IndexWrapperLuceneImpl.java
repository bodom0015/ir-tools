package edu.gslis.indexes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsAndPositionsEnum;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

import edu.gslis.docscoring.ScorerDirichlet;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStatsLucene;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

/**
 * IndexWrapper implementation backed by Lucene. See the edu.gslis.lucene package
 * for index-builder and query applications.
 * 
 * A few things to note about this implementation:
 *   
 *   1. DefaultSimilarity: Lucene requires the scorer (Similarity) to be 
 *      set during both indexing and retrieval. Fortunately, we rescore
 *      everything. For now, the DefaultSimilarity is used for both.
 *      
 *   2. Fields: 
 * 
 *   3. Document length: Lucene doesn't store the document length in a useful
 *      way for use. LuceneBuildIndex calculates the document length and
 *      stores it in a separate field called "doclen" (Indexer.FIELD_DOC_LEN).
 *
 */
public class IndexWrapperLuceneImpl implements IndexWrapper 
{
    Logger logger = Logger.getLogger(IndexWrapperLuceneImpl.class.getName());
    
    ClassLoader loader = ClassLoader.getSystemClassLoader();

    IndexReader index;
    IndexSearcher searcher;
    Similarity similarity;
    Analyzer analyzer;
    
	double vocabularySize = -1.0;
	double docLengthAvg   = -1.0;
	String timeFieldName  =  Indexer.FIELD_EPOCH;
	
	/**
	 * Construct an instance of this index wrapper using the specified path
	 * @param pathToIndex
	 */
	public IndexWrapperLuceneImpl(String pathToIndex) {
	    try
	    {
	        index = DirectoryReader.open(FSDirectory.open(new File(pathToIndex))); 
	        searcher = new IndexSearcher(index);
	        
	        // Read the analyzer/similarity class from the index metadata,
	        // otherwise use defaults.
	        Map<String,String> indexMetadata = readIndexMetadata(pathToIndex);
	        
	        if ( indexMetadata.get("analyzer") != null) {
	            String analyzerClass = indexMetadata.get("analyzer");
	            
	            @SuppressWarnings("rawtypes")
	            Class analyzerCls = loader.loadClass(analyzerClass);

                @SuppressWarnings({ "rawtypes", "unchecked" })
                java.lang.reflect.Constructor analyzerConst = analyzerCls.getConstructor(Version.class);
                analyzer = (StopwordAnalyzerBase)analyzerConst.newInstance(Indexer.VERSION);

	        } else {
	            analyzer = new StandardAnalyzer(Indexer.VERSION);
	        }
	        
	        if (indexMetadata.get("similarity") != null) {
	            String similarityClass = indexMetadata.get("similarity");
	                similarity = (Similarity)loader.loadClass(similarityClass).newInstance();
	        } else {
	            similarity = new LMDirichletSimilarity();
	        }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

	/**
	 * Execute a query given a GQuery object
	 * @param gquery GQuery object
	 * @param count  Number of hits
	 * @return SearchHits
	 */
    public SearchHits runQuery(GQuery gquery, int count) {
        String queryString = getLuceneQueryString(gquery);
        return runQuery(queryString, count);
    }
    
    /**
	 * Execute a query given a GQuery object and set of fields
	 * @param gquery GQuery object
	 * @param fields Array of fields to search
	 * @param count  Number of hits
	 * @return SearchHits
	 */
    public SearchHits runQuery(GQuery gquery, String[] fields, int count) {
    	String queryString = getLuceneQueryString(gquery);
    	return runQuery(queryString, fields, count);
    }
    
    /**
     * Convert GQuery to Lucene query
     * @param gquery        Query
	 * @return Query string
     */
    public String getLuceneQueryString(GQuery gquery) {
    	StringBuilder queryString = new StringBuilder();
        Iterator<String> qt = gquery.getFeatureVector().iterator();
        while(qt.hasNext()) {
            String term = qt.next();
            queryString.append(" ");
            queryString.append(term+"^"+gquery.getFeatureVector().getFeatureWeight(term));
        }
        return queryString.toString();
    }
    
    public String toAndQuery(String query, Stopper stopper) {
        StringBuilder queryString = new StringBuilder();
        String[] terms = query.split("\\s+");
        for (int i=0; i<terms.length; i++) {
            if (stopper != null && stopper.isStopWord(terms[i]))
                continue;

            if (i > 0) 
                queryString.append(" ");

            queryString.append("+" + terms[i]);
        }        
        return queryString.toString();
    }
    
    public String toWindowQuery(String query, int window, Stopper stopper) {
        StringBuilder queryString = new StringBuilder("\"");
        String[] terms = query.split("\\s+");
        for (int i=0; i<terms.length; i++) {
            if (stopper != null && stopper.isStopWord(terms[i]))
                continue;

            if (i > 0) 
                queryString.append(" ");
            queryString.append(terms[i]);
        }
        queryString.append("\"~" + window);
        return queryString.toString();
    }
    
    
    /**
     * Execute a query given a query string against all fields
     * @param q Query string
     * @param count Number of hits
     */
    public SearchHits runQuery(String q, int count) {

        SearchHits hits = new SearchHits();
        try {
            List<String> fieldNames = new ArrayList<String>();
            Fields fields = MultiFields.getFields(index);
            Iterator<String> it = fields.iterator();
            while (it.hasNext()) {
                String fieldName = it.next();
                fieldNames.add(fieldName);
            }
            hits = runQuery(q, fieldNames.toArray(new String[0]), count);
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }    
        return hits;
    }
    
    /**
     * Execute a query given a query string against the specified field
     * @param q   Query string
     * @param field  Field to be queried
     * @param count Number of hits
	 * @return SearchHits
     */    
    public SearchHits runQuery(String q, String field, int count) {
        return runQuery(q, new String[] { field } , count);
    }

    /**
     * Execute a query given a query string against the specified fields
     * @param q   Query string
     * @param field  Field to be queried
     * @param count Number of hits
	 * @return SearchHits
     */
	public SearchHits runQuery(String q, String[] field, int count) {
				
	    SearchHits hits = new SearchHits();
	    Set<String> fields = new HashSet<String>();
	    fields.add(Indexer.FIELD_DOCNO);
	    fields.add(Indexer.FIELD_DOC_LEN);
	    fields.add(timeFieldName);
	    try
	    {            
            QueryParser parser = new MultiFieldQueryParser(Indexer.VERSION, field, analyzer);
            Query query = parser.parse(q);
            searcher.setSimilarity(similarity);
            TopDocs topDocs = searcher.search(query,  null, count);
            ScoreDoc[] docs = topDocs.scoreDocs;

            for (int i=0; i<docs.length; i++) {
                SearchHit hit = new SearchHit();
                int docid = docs[i].doc;
                                
                Document d = index.document(docid, fields);
                
                String docno = d.get(Indexer.FIELD_DOCNO);
                hit.setDocID(docid);
                hit.setDocno(docno);
                hit.setScore(docs[i].score);
                IndexableField dl = d.getField(Indexer.FIELD_DOC_LEN);
                if (dl != null) 
                    hit.setLength(dl.numericValue().longValue());
                if(timeFieldName != null) {
                    String timeString = d.get(timeFieldName);
                    if (timeString != null) {
                        double time = Double.parseDouble(timeString);
                        hit.setMetadataValue(timeFieldName, time);
                    }
                }

                hits.add(hit);                
            }
	    } catch (Exception e) {
	        logger.log(Level.SEVERE, e.getMessage(), e);
	    }
	    return hits;
	}

	/**
	 * Set the field name used to store the document time.  Defaults
	 * to "epoch".
	 * @param timeFieldName Name of the time field (e.g., epoch)
	 */
	public void setTimeFieldName(String timeFieldName) {
	    logger.info("setting time to " + timeFieldName);
		this.timeFieldName = timeFieldName;
	}

	/**
	 * Returns the total number of documents in the index.
	 */
	public double docCount() {
		try {
			return (double)index.numDocs();
		} catch (Exception e) {
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return -1.0;
	}

	/**
	 * Returns the total number of terms across all fields
	 */
	public double termCount() {
	    double count = 0;
		try {
            Fields fields = MultiFields.getFields(index);  
            Iterator<String> it = fields.iterator();
            while (it.hasNext()) {
                String field = it.next();
                count += index.getSumTotalTermFreq(field);
            }
		} catch (Exception e) {
		    logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return count;
	}
	
	/**
	 * Returns the total vocabulary size in all fields
	 */
	public double termTypeCount() {
	    if (vocabularySize == -1) {
	        try {
                Fields fields = MultiFields.getFields(index);  
                Iterator<String> it = fields.iterator();
                while (it.hasNext()) {
                    String field = it.next();
                    Terms terms = fields.terms(field);
                    long fieldSize = terms.size();
                    if (fieldSize > 0) 
                        vocabularySize += fieldSize;

                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
	    }
	    return vocabularySize;
	}
	
	/** 
	 * Returns the total vocabulary size for the specified field
	 * @param field Field name
	 * @return total size
	 */
    public double termTypeCount(String field) {
        if (vocabularySize == -1) {
            try {
                Fields fields = MultiFields.getFields(index);  
                Terms terms = fields.terms(field);
                vocabularySize = terms.size();
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        }
        return vocabularySize;
    }

	/**
	 * Returns the number of documents containing the specified term, across
	 * all indexed fields.
	 * @param term  Term
	 */
	public double docFreq(String term) {
	    double df = 0;
	    
		try {
            Fields fields = MultiFields.getFields(index);  
            Iterator<String> it = fields.iterator();
            while (it.hasNext()) {
                String field = it.next();
                df += index.docFreq(new Term(field, term));
            }
		} catch (Exception e) {
		    logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return df;
	}

	/**
	 * Returns the number of documents containing the specified term
	 * in the specified field.
	 * @param term  Term
	 * @param field Field
	 * @return Document frequency
	 */
    public double docFreq(String term, String field) {
        try {
            return (double)index.docFreq(new Term(field, term));
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return -1.0;
    }

	/**
	 * Returns total term frequency in the specified field
	 * @param term Term
	 * @param field Field name
	 * @return Term frequency
	 */
	public double termFreq(String term, String field) {
		try {
			return (double)index.totalTermFreq(new Term(field, term));
		} catch (Exception e) {
		    logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return -1.0;
	}

	/**
	 * Returns total term frequency across all fields
	 * @param term Term
	 */
    public double termFreq(String term) {
        double tf = 0;
        
        try {
            Fields fields = MultiFields.getFields(index);  
            Iterator<String> it = fields.iterator();
            while (it.hasNext()) {
                String field = it.next();
                tf += index.totalTermFreq(new Term(field, term));
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return tf;
    }
	
	/**
	 * Returns average document length
	 */
	public double docLengthAvg() {
	    // TODO: Not implemented
		return docLengthAvg;
	}

	/**
	 * Returns a feature vector for the specified intermal document ID
	 * @param docID    Lucene internal identifier
	 * @param stopper  Stopper
	 */
    public FeatureVector getDocVector(int docID,  Stopper stopper) {
        return getDocVector(docID, null, stopper);
    }
    
    /**
     * Returns the complete feature vector for the specified document
     * based on the specified field
     * @param docID     Lucene internal identifier
     * @param field     Field   
     * @param stopper   Stopper
     * @return Feature vector
     */
	public FeatureVector getDocVector(int docID,  String field, Stopper stopper) {
	   
	    FeatureVector fv = new FeatureVector(stopper);
	    try
	    {
            Set<Terms> termsSet = new HashSet<Terms>();

	        if (field == null) {	            
	            Fields fields = index.getTermVectors(docID);
	            Iterator<String> it = fields.iterator();
	            while (it.hasNext()) {
	                String fieldName = it.next();
	                Terms terms = fields.terms(fieldName);
	                if (terms != null) {	                    
	                    termsSet.add(terms);
	                }
	            }
	        }
	        else {
	            Terms terms = index.getTermVector(docID, field);
	            termsSet.add(terms);
	        }
	        
	        for (Terms terms: termsSet) {
        	    if (terms != null) { 
        	        TermsEnum termsEnum = terms.iterator(null); 
        	        while (termsEnum.next() != null) { 
        	            String term = termsEnum.term().utf8ToString();
        	            
        	            
        	            if (stopper != null && stopper.isStopWord(term))
        	                continue;
        	            
        	            long f = termsEnum.totalTermFreq();
        	            fv.addTerm(term, f);
        	        }
        	    }
	        }
	    } catch (Exception e) {
	        logger.log(Level.SEVERE, e.getMessage(), e);
	    }
	    return fv;
	}
	
	
	
	/**
	 * Returns an ordered list of terms for the specified document
	 * @param docID Document ID
	 * @return List of terms
	 */
    public List<String> getDocTerms(int docID) 
    {       
        Map<Integer, String> termPos = new TreeMap<Integer, String>();
        try
        {
            Fields fields = index.getTermVectors(docID);
            Iterator<String> it = fields.iterator();
            int fieldPos = 0;
            while (it.hasNext()) {
                String field = it.next();
   
                Terms terms = index.getTermVector(docID, field);
                if (terms != null) { 
                    TermsEnum termsEnum = terms.iterator(null); 
                    DocsAndPositionsEnum dp = null; 
                    while (termsEnum.next() != null) { 
                        String term = termsEnum.term().utf8ToString();
                       
                        dp = termsEnum.docsAndPositions(null, dp);
                        dp.nextDoc();
                        int freq = dp.freq();
                        for (int i=0; i<freq; i++) {
                            int pos = fieldPos + dp.nextPosition();
                            termPos.put(pos, term);
                        }
                    }
                    fieldPos = termPos.size();
                }
                
            }
         } catch (Exception e) {
             logger.log(Level.SEVERE, e.getMessage(), e);
         }
        List<String> termList = new LinkedList<String>();
        termList.addAll(termPos.values());
        return termList;
     }	
 
    /**
     * Recomposes the document text
     * @param docID    Lucene internal document identifier
	 * @return Document text
     */
    public String getDocText(int docID) {
        StringBuffer text = new StringBuffer();
        try
        {
            Fields fields = index.getTermVectors(docID);
            Iterator<String> it = fields.iterator();
            while (it.hasNext()) {
                String fieldName = it.next();
                text.append(getDocText(docID, fieldName));
                text.append(" ");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);            
        }
        return text.toString();
    }

    /**
     * Recomposes the document text from term position information.
     * @param docID Document ID
     * @param field Field name
     * @return Document text
     */
	   public String getDocText(int docID,  String field) {
	       
	       StringBuffer text = new StringBuffer();
           FeatureVector fv = new FeatureVector(null);
	       try
	       {
	            Terms terms = index.getTermVector(docID, field);
	            Map<Integer, String> dv = new TreeMap<Integer, String>();
	            if (terms != null) { 
	                TermsEnum termsEnum = terms.iterator(null); 
	                DocsAndPositionsEnum dp = null; 
	                while (termsEnum.next() != null) { 
	                    String term = termsEnum.term().utf8ToString();
	                   
	                    dp = termsEnum.docsAndPositions(null, dp);
	                    dp.nextDoc();
	                    int freq = dp.freq();
	                    for (int i=0; i<freq; i++) {
	                        int pos = dp.nextPosition();
	                        dv.put(pos, term);
	                    }
	                    long f = termsEnum.totalTermFreq();
	                    fv.addTerm(term, f);
	                }
	                for (int pos: dv.keySet()) 
	                    text.append(dv.get(pos) + " ");
	            }
	        } catch (Exception e) {
	            logger.log(Level.SEVERE, e.getMessage(), e);
	        }
	        return text.toString();
	    }
	
	/**
	 * Returns the internal document identifier given a docno
	 * @param docno Docno
	 * @return Document ID
	 */
	public int getDocId(String docno) {
	    return getDocId(Indexer.FIELD_DOCNO, docno);
	}
	
	/**
	 * Get the internal document ID the field with the specified value
	 * @param field    Field name
	 * @param value    value
	 * @return
	 */
	public int getDocId(String field, String value) {
        int docid = -1;
        
        try
        {
            IndexSearcher searcher = new IndexSearcher(index);
            Analyzer analyzer = new KeywordAnalyzer();
            QueryParser parser = new QueryParser(Indexer.VERSION, field, analyzer);
            Query q = parser.parse("\"" + value + "\"");
    
    //        TermQuery q = new TermQuery(new Term(field, docno));
                            

            TopDocs docs = searcher.search(q,  100);
            if (docs.totalHits > 0)
                docid = docs.scoreDocs[0].doc;
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
            
        return docid;
	}
	
	/**
	 * Returns a document vector given the docno (assumes all fields)
	 */
	public FeatureVector getDocVector(String docno, Stopper stopper) {
	    int docid = getDocId(docno);
	    	    
    	return getDocVector(docid, stopper);
	}
	
	/**
	 * Returns a term vector given the specified field
	 * @param docno    Document number
	 * @param field    Field
	 * @param stopper  Stopper
	 * @return
	 */
    public FeatureVector getDocVector(String docno, String field, Stopper stopper) {
        int docid = getDocId(docno);
        	                
        return getDocVector(docid, field, stopper);
    }
    
	/**
	 * Returns the underlying Lucene IndexReader
	 */
	public Object getActualIndex() {
		return index;
	}

	
	/**
	 * Returns the value of a specific field as a string
	 * @param docno  Document number
	 * @param metadataName  Field name
	 */
	public String getMetadataValue(String docno, String metadataName) {
	    int docid = getDocId(docno);
	    String value = null;
	    try {
    	    Document doc = index.document(docid);	    
    	    value = doc.get(metadataName);
	    } catch (Exception e) {
	        logger.log(Level.SEVERE, e.getMessage(), e);
	    }
	    return value;
	}
	


	/**
	 * Returns the document length. Note: this is stored in a custom field during indexing.
	 * @param docID Lucene internal identifier
	 * @see edu.gslis.lucene.main.LuceneBuildIndex
	 */
	public double getDocLength(int docID) {
		double length = -1; 
		try {
		    Document doc = index.document(docID);
		    if (doc != null)
		        length = doc.getField(Indexer.FIELD_DOC_LEN).numericValue().longValue();
		} catch (Exception e) {
		    logger.log(Level.SEVERE, e.getMessage(), e);
		}
		return length;
	}

	
	/**
	 * Return a single SearchHit for the specified docno
	 * @param docno
	 * @param stopper
	 * @return
	 */
   public SearchHit getSearchHit(String docno, Stopper stopper) {
       SearchHit hit = new SearchHit();
       FeatureVector dv = getDocVector(docno, stopper);
       int docid = getDocId(docno);
       hit.setFeatureVector(dv);
       hit.setDocID(docid);
        
       String timeString = getMetadataValue(docno, timeFieldName);
       if (timeString != null) {
           double time = Double.parseDouble(timeString);
           hit.setMetadataValue(timeFieldName, time);
       }
       return hit;
   }
      
   public static void main(String[] args) throws IOException 
   {
       File indexDir = new File("/Users/cwillis/dev/uiucGSLIS/indexes/lucene/FT.train");
       IndexWrapper index = new IndexWrapperLuceneImpl(indexDir.getAbsolutePath());
       
       
//       index.runQuery("headline:\"jubilee\" and text:\"financial\" and pub:\"financial\"", 10);
       index.runQuery("headline:jubilee and financial", 10);
       double dc = index.docCount();
       assert(dc == 53356);
       
       double df = index.docFreq("cranwell");
       assert (df == 2);
       //double dlavg = index.docLengthAvg();
       int docid = index.getDocId("FT911-1");
       FeatureVector fv1 = index.getDocVector(docid, null);       
       assert(fv1.getFeatureWeight("the") == 12);
       
       double tc = index.termCount();
       assert(tc == 22058714);
       double tf = index.termFreq("the");
       assert(tf == 1425270);
       
       FeatureVector qv = new FeatureVector(null);
       qv.addTerm("raf", 0.8);  
       qv.addTerm("cranwell", 0.5);       

       GQuery query = new GQuery();
       query.setText("raf cranwell");
       query.setFeatureVector(qv);
       
       double ttc = index.termTypeCount();
       assert (ttc == 163279);
//       FeatureVector fv2 = index.getDocVector("test-docno2", null);
//       String epoch = index.getMetadataValue("test-docno1", "epoch");
       // -6.68693  FT911-382   0   1510
       // -6.80837    FT911-1 0   217
       SearchHits hits = index.runQuery(query, 88);
       assert(hits.size() == 88);
       ScorerDirichlet scorer = new ScorerDirichlet();
       scorer.setParameter("mu", 2000);
       scorer.setQuery(query);

       CollectionStats stat = new IndexBackedCollectionStatsLucene();
       stat.setStatSource(indexDir.getAbsolutePath());
       scorer.setCollectionStats(stat);
       
       hits.rank();
       for (int i=0; i<hits.size(); i++) {
           SearchHit hit = hits.getHit(i);
           double score = scorer.score(hit);
           hit.setScore(score);
//           System.out.println(hit.getDocno() + "\t" + hit.getScore() + "\t" + score);
       }
       hits.rank();
       for (int i=0; i<hits.size(); i++) {
           SearchHit hit = hits.getHit(i);
           double score = scorer.score(hit);
           hit.setScore(score);
           System.out.println(hit.getDocno() + "\t" + hit.getScore());
       }

   }
   
   public Map<Integer, Integer> getDocsByTerm(String term, Set<Integer> docids) {
       Map<Integer, Integer> df = new HashMap<Integer, Integer>();
       try
       {
           Fields fields = MultiFields.getFields(index);
           Iterator<String> it = fields.iterator();
           while (it.hasNext()) {
               String field = it.next();
               DocsEnum de =  MultiFields.getTermDocsEnum(index, MultiFields.getLiveDocs(index), 
                       field, new BytesRef(term));
               if (de != null) {
                   int doc;
                   while((doc = de.nextDoc()) != DocsEnum.NO_MORE_DOCS) {
                       if (docids.contains(doc))
                           df.put(doc,  de.freq());
                   }
               }
           }
       } catch (Exception e) {
           e.printStackTrace();
       }
       return df;
   }
   
   public String stem(String input) {
       String stemmed = "";
       try
       {
           TokenStream tokenStream = analyzer.tokenStream(null, input);        
           tokenStream.reset();
           CharTermAttribute token = tokenStream.getAttribute(CharTermAttribute.class);
           int i = 0;
           while (tokenStream.incrementToken()) {
               if (i > 0)
                   stemmed += " ";
               stemmed += token.toString();
               i++;
           }           
           tokenStream.close();
       } catch (IOException e) {
           e.printStackTrace();
       }
       return stemmed;
   }
   
   private Map<String, String> readIndexMetadata(String indexPath) 
   {
       Map<String, String> map = new HashMap<String, String>();
       try
       {
           File metadata = new File(indexPath + File.separator + "index.metadata");
           List<String> lines = FileUtils.readLines(metadata);
           for (String line: lines) {
               String[] fields = line.split("=");
               map.put(fields[0], fields[1]);            
           }
       } catch (IOException e) {
           // Can't find the index.metadata file, use overrides
       }
       return map;
   }
   
   public String toDMQuery(String query, String type, double w1, double w2, double w3)
   {
       return null;
   }

}
