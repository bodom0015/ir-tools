package edu.gslis.queries.expansion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.utils.KeyValuePair;
import edu.gslis.utils.KeyValuePairs;



/**
 * Positional relevance model (Lv and Zhai 2010)
 * 
 * Ported from: *  http://sifaka.cs.uiuc.edu/~ylv2/pub/prm/PositionalRelevanceModel.cpp
 */
public class FeedbackPRM extends Feedback {
	private double sigma = 10;
	private double lambda = 0.1;
	int fbMethod = 2;
	
    Map<String, Integer> queryTerms = new HashMap<String, Integer>();

    public FeedbackPRM(String query) {
        String[] terms = query.split(" ");
        for (String term: terms) {
            Integer cnt = queryTerms.get(term);
            if (cnt == null)
                queryTerms.put(term, 1);
            else 
                queryTerms.put(term, cnt++);
        }
    }
    
    public void setMethod(int fbMethod) {
        this.fbMethod = fbMethod;
    }
    public void setSigma(double sigma) { 
        this.sigma = sigma;
    }
    
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }
	@Override
	public void build() {
	    
        features = new KeyValuePairs();
        Map<String, Double> featureMap = new TreeMap<String, Double>();

		try 
		{

			if(relDocs == null) {
				relDocs = index.runQuery(originalQuery, fbDocCount);
			}

			relDocs.logToPosterior();
			
			Iterator<SearchHit> hitIterator = relDocs.iterator();
	

			// estimate a simple collection language model for the current query
			Map<String, Double> colLM = new HashMap<String, Double>();
			double colFreq = index.termCount();
            for (String qterm: queryTerms.keySet()) {
                double colpr = index.termFreq(qterm) / colFreq;
                colLM.put(qterm, colpr);
            }
			
			// For each query result
			while (hitIterator.hasNext()) {
	            SearchHit hit = hitIterator.next();
	            List<String> docTerms = index.getDocTerms(hit.getDocID());
			    
	            // First pass: record the position of each query term
                Map<Integer, String> posQTermMap = new HashMap<Integer, String>();
                
                // double docLength = termPosMap.size();
                double stoppedDocLength = 0;
                
	            for (int pos = 0; pos < docTerms.size(); pos++) {
	                String term = docTerms.get(pos);
	                
                    if (!isValidWord(term))
	                    continue;
	                
	                stoppedDocLength++;
	                
    			    for (String qterm: queryTerms.keySet()) {
    			        if (term.equals(qterm)) {
      			            posQTermMap.put(pos, term);
    			        }
    			    }
	            }
			    
	            double[] posScores = new double[docTerms.size()];
	            double posScoreSum = 0;
	            
			    // Second pass: estimate PLMs
			    double lenNorm = Math.sqrt(2 * Math.PI) * sigma;
			    for (int i=0; i< docTerms.size(); i++) {
			        String term = docTerms.get(i);
			        
                    if (!isValidWord(term))
			            continue;
			        
			        // Language model for position
			        Map<String, Double> plm_i = new HashMap<String, Double>();
			        
			        // aggregate evidence from every query term
			        for (int j: posQTermMap.keySet()) {
			            String qterm = posQTermMap.get(j);
			            
			            double dis = (j - i) / sigma;
			            double pr = Math.exp( - dis * dis/2.0) / lenNorm;
			            
			            if (pr > 0) {
    			            Double pr_i = plm_i.get(qterm);
    			            if (pr_i == null) {
    			                plm_i.put(qterm, pr);			                
    			            }
    			            else {
    			                plm_i.put(qterm, pr_i + pr);
    			            }	
			            }
			        }
		             // Smooth PLM 
	                smooth(plm_i, colLM, lambda);
	                
	                // compute query likelihood
	                double ql = computeQL(queryTerms, plm_i, colLM);
	                posScores[i] = ql;
	                posScoreSum += ql;
			    }
			    
	             // Third pass: aggregate feedback counts
			    for (int i=0; i<docTerms.size(); i++) {
                    double posScore = posScores[i];
                    String term = docTerms.get(i);
                    
                    if (!isValidWord(term))
                        continue;
                    
                    if (fbMethod == 1) 
                        posScore = posScore/stoppedDocLength;
                    else
                        posScore = posScore * hit.getScore() / posScoreSum;
                    
                    if (posScore > 0) {
                        if (featureMap.get(term) != null) {
                            double weight = featureMap.get(term);
                            weight += posScore;
                            featureMap.put(term, weight);
                        }
                        else
                            featureMap.put(term, posScore);
                    }
                }
			}
			double sum = 0;
            for (String term: featureMap.keySet()) {
                if (stopper.isStopWord(term))
                    continue;
                sum += featureMap.get(term);
            }			
            
			for (String term: featureMap.keySet()) {
			    if (stopper.isStopWord(term))
			        continue;
                KeyValuePair tuple = new KeyValuePair(term, featureMap.get(term)/sum);                
                features.add(tuple);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	public double computeQL(Map<String, Integer> queryTerms, Map<String, Double> docLM, 
	        Map<String, Double> colLM) {
	    double ql = 1.0;
	    for (String qterm: queryTerms.keySet()) {
	        double qc = queryTerms.get(qterm);
	        double dpr = docLM.get(qterm);
	        double cpr = colLM.get(qterm);
	        ql *= Math.pow(dpr / cpr, qc);	       
	    }
	    return ql;
	}
	
	public void smooth(Map<String, Double> docLM, Map<String, Double> colLM, double lambda)
	{
	    for (String term: colLM.keySet()) {
	        double colpr = colLM.get(term);

	        if (docLM.get(term) == null)
	            docLM.put(term, lambda * colpr);
	        else 
	            docLM.put(term, (1 - lambda) * docLM.get(term) + lambda * colpr);
	    }
	}
	
	public boolean isValidWord(String term) {
	    if (term.equals("[OOV]") || !StringUtils.isAlphanumeric(term) || term.length() <= 2)
	        return false;
	    else 
	        return true;
	}
}
