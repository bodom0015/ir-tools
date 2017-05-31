package edu.gslis.docscoring;

import java.util.HashMap;

import java.util.Map;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.queries.GQuery;

public abstract class QueryDocScorer implements Scorer {
	protected GQuery gQuery;
	protected CollectionStats collectionStats;
	protected Map<String,Double> paramTable;
	
	public void setQuery(GQuery gQuery) {
		this.gQuery = gQuery;
	}
	public void setCollectionStats(CollectionStats collectionStats) {
		this.collectionStats = collectionStats;
	}
	
	public void setParameter(String paramName, double paramValue) {
		if(paramTable == null)
			paramTable = new HashMap<String,Double>();
		paramTable.put(paramName, paramValue);
	}
	
	/**
	 * Optional for string-valued parameters
	 * @param paramName parameter name
	 * @param paramValue parameter value
	 */
	public void setParameter(String paramName, String paramValue) {}
	
	/**
	 * To support any optional initialization steps required by the scorer.
	 */
	public void init() {}
}
