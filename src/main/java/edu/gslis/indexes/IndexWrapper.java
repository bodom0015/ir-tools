package edu.gslis.indexes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


public interface IndexWrapper {

	public SearchHits runQuery(GQuery query, int count);
	
	public SearchHits runQuery(String query, int count);
	
	public double docCount();

	public double termCount();

	public double docFreq(String term);

	public double termFreq(String term);
	
	public double docLengthAvg();
	
	public FeatureVector getDocVector(int docID, Stopper stopper);
	
	public FeatureVector getDocVector(String docno, Stopper stopper);
	
	public String getMetadataValue(String docno, String metadataName);
	
	public int getDocId(String docno);
	
	public double getDocLength(int docID);
	
	public double termTypeCount();
	
	public SearchHit getSearchHit(String docno, Stopper stopper);
	
	// really shouldn't use this
	public Object getActualIndex();
	
	public void setTimeFieldName(String field);
	
	/**
	 * Returns the text for the specified document id
	 * @param docid Document ID
	 * @return text
	 */
	public String getDocText(int docid);
	
	/**
	 * Returns an ordered list of terms in the document. 
	 * Used by proximity-based models
	 * 
	 * @param docid Document ID
	 * @return list of terms
	 */
	public List<String> getDocTerms (int docid);
	
	/**
	 * Returns a map of docids to df for the given term
	 * @param term Term
	 * @param docs Set of document IDs
	 * @return Map of docids to frequency
	 */
	public Map<Integer, Integer> getDocsByTerm (String term, Set<Integer> docs);
	
	/**
	 * Returns a boolean query for the specified GQuery
	 * @param query Query number
	 * @param stopper stopper
	 * @return boolean query
	 */
	public String toAndQuery(String query, Stopper stopper);
	
	/**
	 * Returns an unordered window query for the specified GQuery
	 * @param query Query number
	 * @param window window size
	 * @param stopper stopper
	 * @return boolean query
	 */
    public String toWindowQuery(String query, int window, Stopper stopper);
    
    /**
     * Returns dependency model query
     * @param query Query number
     * @param w1 weight
     * @param w2 weight
     * @param w3 weight
     * @return SDM query
     */
    public String toDMQuery(String query, String type, double w1, double w2, double w3);
    
}
