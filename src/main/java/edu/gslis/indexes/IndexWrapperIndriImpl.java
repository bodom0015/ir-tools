package edu.gslis.indexes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.gslis.docaccumulators.Postings;
import edu.gslis.docaccumulators.PostingsAggregator;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.textrepresentation.IndriDocument;
import edu.gslis.utils.Stopper;
import lemurproject.indri.QueryEnvironment;
import lemurproject.indri.ScoredExtentResult;



public class IndexWrapperIndriImpl implements IndexWrapper{

    private static final String SERVER_PREFIX = "server:";
    
	private QueryEnvironment index;
	private double vocabularySize = -1.0;
	private double docLengthAvg   = -1.0;
	private String timeFieldName  = null;
	
	public IndexWrapperIndriImpl(String pathToIndex) {
		index = new QueryEnvironment();
		addIndex(pathToIndex);
		getVocabularySize(pathToIndex);
	}
	

	private void addIndex(String pathToIndex) {
		try {
		    // If the index path starts with 'server:', treat it as a server
		    if (pathToIndex.startsWith(SERVER_PREFIX)) {
		        String server = pathToIndex.substring(SERVER_PREFIX.length());
		        index.addServer(server);
		    }
		    else
		        index.addIndex(pathToIndex);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	   /**
     * Initialize the vocabulary size using the lemur API
     * @param pathToIndex
     */
    private void getVocabularySize(String pathToIndex) {
        if (pathToIndex.startsWith(SERVER_PREFIX))
            System.err.println("Unable to get vocabulary size from remote server. Must use local index.");
        else
        {
            try {
                vocabularySize = index.termCountUnique();
            } catch (Exception e) {
                System.err.println("Error getting vocabulary size: perhaps liblemur library is missing?");
            }         
        }
    }

	public SearchHits runQuery(GQuery query, int count) {
				
		SearchHits hits = new SearchHits();
		try {
			// assumes that this gQuery's text member is all formatted and ready to go.
			StringBuilder queryString = new StringBuilder("#weight(");
			Iterator<String> qt = query.getFeatureVector().iterator();
			while(qt.hasNext()) {
				String term = qt.next();
				queryString.append(String.format("%.12f", query.getFeatureVector().getFeatureWeight(term)) + " " + term + " ");
			}
			queryString.append(")");
			
			ScoredExtentResult[] res = index.runQuery(queryString.toString(), count);
			String[] docnos = index.documentMetadata(res, "docno");
			String[] timeStrings  = null;
			double[] times = null;
			if(timeFieldName != null) {
				timeStrings = index.documentMetadata(res, timeFieldName);
				times = new double[timeStrings.length];
				for(int i=0; i<timeStrings.length; i++) {
					times[i] = Double.parseDouble(timeStrings[i]);
				}
			}


			int k=0;
			for(ScoredExtentResult r : res) {
				SearchHit hit = new SearchHit();
				hit.setDocID(r.document);
				hit.setScore(r.score);
				if(times != null)  {
					hit.setMetadataValue(timeFieldName, times[k]);
				}
                double length = (double)index.documentLength(r.document);
                
                // TODO: Do we need it?
                //IndriDocument doc = new IndriDocument (index);
                //FeatureVector fv = doc.getFeatureVector(r.document, null);
                //hit.setFeatureVector(fv);
                hit.setLength(length);
				hit.setDocno(docnos[k++]);
				hits.add(hit);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hits;
	}

	public SearchHits runQuery(String query, int count) {
		SearchHits hits = new SearchHits();
		try {
			// assumes that this gQuery's text member is all formatted and ready to go.
			ScoredExtentResult[] res = index.runQuery(query, count);
			String[] docnos = index.documentMetadata(res, "docno");
			String[] timeStrings  = null;
			double[] times = null;
			if(timeFieldName != null) {
				timeStrings = index.documentMetadata(res, timeFieldName);
				times = new double[timeStrings.length];
				for(int i=0; i<timeStrings.length; i++) {
					times[i] = Double.parseDouble(timeStrings[i]);
				}
			}


			int k=0;
			for(ScoredExtentResult r : res) {
				SearchHit hit = new SearchHit();
				hit.setDocID(r.document);
				hit.setScore(r.score);

				if(times != null)  {
					hit.setMetadataValue(timeFieldName, times[k]);
				}
                double length = (double)index.documentLength(r.document);
                hit.setLength(length);

				hit.setDocno(docnos[k++]);
				hits.add(hit);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hits;
	}
	
	public void setTimeFieldName(String timeFieldName) {
		System.err.println("setting time to " + timeFieldName);
		this.timeFieldName = timeFieldName;
	}

	public double docCount() {
		try {
			return (double)index.documentCount();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1.0;
	}

	public double termCount() {
		try {
			return (double)index.termCount();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1.0;
	}

	public double termTypeCount() {
		return vocabularySize;
	}

	public double docFreq(String term) {
		try {
			return (double)index.documentCount(term);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1.0;
	}

	public double termFreq(String term) {
		try {
			return (double)index.termCount(term);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1.0;
	}

	public double docLengthAvg() {
		return docLengthAvg;
	}

	public FeatureVector getDocVector(int docID, Stopper stopper) {
		IndriDocument doc = new IndriDocument(index);
		return doc.getFeatureVector(docID, stopper);
	}

	public FeatureVector getDocVector(String docno, Stopper stopper) {
		IndriDocument doc = new IndriDocument(index);
		int docID = 1;
		try {
			docID = doc.getDocID(docno);
			if(docID < 1) {
				System.err.println("no doc ID found for docno " + docno);
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this.getDocVector(docID, stopper);
	}
	

	public Object getActualIndex() {
		return index;
	}

	public String getMetadataValue(String docno, String metadataName) {
		String value = null;
		String[] d = {docno};
		try {
			int[] i    = index.documentIDsFromMetadata("docno", d);
			String[] v = index.documentMetadata(i, metadataName);
			if(v.length != d.length) {
				System.err.println("got mismatch of metadata and docnos in IndexWrapperIndriImpl.getMetadataValue()");
				return null;
			}
			value = v[0];
		} catch (Exception e) {
			e.printStackTrace();
		}
		return value;
	}

	public double getDocLength(int docID) {
		double length = 0; 
		try {
			length = (double)index.documentLength(docID);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return length;
	}

	public int getDocId(String docno) {
		try {
			String[] d = {docno};
			int[] docIds = index.documentIDsFromMetadata("docno", d);
			if(docIds == null || docIds.length == 0)
				return -1;
			return docIds[0];
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return -1;
	}
	
	/**
	 * Return a single SearchHit for the specified docno
	 * @param docno Document nubmer
	 * @param stopper Stopper object
	 * @return SearchHit
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
   
   public String getDocText(int docid) {
       IndriDocument doc = new IndriDocument(index);
       return doc.getDocString(docid);       
   }
   
   /**
    * Returns an ordered list of terms
    */
   public List<String> getDocTerms(int docid) {
       IndriDocument doc = new IndriDocument(index);
       return doc.getTerms(docid);
   }
   
   public Map<Integer, Integer> getDocsByTerm(String term, Set<Integer> docids) {
       
       Map<Integer, Integer> df = new HashMap<Integer, Integer>();
       String query = "#band(" + term + ")";           
          
       try
       {
           ScoredExtentResult[] featureResults = index.expressionList(query);
        
           if(featureResults.length==0)
               return df;
           
           // convert expression list to term-doc counts
           int[] docIds = this.extractDocIds(featureResults);
           PostingsAggregator postingsAggregator = new PostingsAggregator();
           Postings postingsForFeature = postingsAggregator.aggregate(docIds);
           for (int docid: docids) {
               int count = postingsForFeature.lookup(docid);
               df.put(docid, count);
           }
           /*
           Iterator<Integer> matchingDocIdIterator = postingsForFeature.docIdIterator();
           while(matchingDocIdIterator.hasNext()) {
               int docId = matchingDocIdIterator.next();
               int count = postingsForFeature.lookup(docId);
               df.put(docId, count);
           } */       
       } catch (Exception e) {
           e.printStackTrace();
       }
       return df;
   }


    private int[] extractDocIds(ScoredExtentResult[] r) {
       int[] d = new int[r.length];
       for(int i=0; i<r.length; i++) {
           d[i] = r[i].document;
       }
       return d;
    }
    
    public String toAndQuery(String query, Stopper stopper) {
        StringBuilder queryString = new StringBuilder("#band(");
        String[] terms = query.split("\\s+");
        for (int i=0; i<terms.length; i++) {
            if (stopper != null && stopper.isStopWord(terms[i]))
                continue;
            if (i > 0) 
                queryString.append(" ");
            queryString.append(terms[i]);
        }
        queryString.append(")");
        return queryString.toString();
    }
    
    public String toWindowQuery(String query, int window, Stopper stopper) {
        StringBuilder queryString = new StringBuilder("#" + window + "(");
        String[] terms = query.split("\\s+");
        for (int i=0; i<terms.length; i++) {

            if (stopper != null && stopper.isStopWord(terms[i]))
                continue;

            if (i > 0) 
                queryString.append(" ");
            queryString.append(terms[i]);
        }
        queryString.append(")");
        return queryString.toString();
    }
    
    public String stem(String input) {
        String stemmed = "";

        String[] terms = input.split("\\s");
        try
        {
            int i = 0;
            for (String term: terms) {        
                Process proc = Runtime.getRuntime().exec("bin/porter " + term);
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String output = br.readLine();
                if (i > 0)
                    stemmed += " ";
                stemmed += output;
                i++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stemmed;
    }
    
    public String toDMQuery(String query, String type, double w0, double w1, double w2)
    {
       query = query.trim();
        
        String queryT = "#combine( ";
        String queryO = "#combine(";
        String queryU = "#combine(";
        
        String[] terms = query.split("\\s+");
        for (String term: terms) {
            queryT += term + " ";
        }
        
        int numTerms = terms.length;
        
        // Skip the rest of the processing if we're just 
        // interested in term features or if we only have 1 term
        if (( w1 == 0 && w2 == 0) || numTerms == 1) {
            queryT += ")";
            return queryT;
        }
        
        int start =1;
        if (type.equals("sd")) start =3 ;
        
        for (int i=start; i<Math.pow(2, numTerms); i++) {
            
            String bin = String.format("%032d", Integer.valueOf(Integer.toBinaryString(i)));  
            
            int numExtracted = 0;
            String extractedTerms = "";
            
            // Get query terms corresponding to 'on' bits
            for (int j=0; j<numTerms; j++) {
                int len = bin.length();
                int pos = len + j - numTerms;
            
                String bit = bin.substring(pos, pos+1);
                if (bit.equals("1")) {
                    extractedTerms += terms[j] + " ";
                    numExtracted++;
                }
            }
            
            if (numExtracted == 1) {
                // skip these, since we already took care of the term features
                continue;
            }
            
            if (bin.matches("^0+11+[^1]*$")) {
                queryO += " #1(" + extractedTerms + ") ";
            }
            
            //every subset of terms, unordered features (f_U)
            queryU += " #uw" + (4*numExtracted) + "(" + extractedTerms + ") "; 
            
            if (type.equals("sd")) { i *=2; i--; }
        }
        
        String newQuery = "#weight(";
        if (w0 != 0 && !queryT.equals("#combine( ")) { 
            newQuery += " " + w0 + " " + queryT + ")";
        }
        if (w1 != 0 && !queryO.equals("#combine(")) { 
            newQuery += " " + w1 + " " + queryO + ")";
        }
        if (w2 != 0 && !queryU.equals("#combine(")) { 
            newQuery += " " + w2 + " " + queryU + ")";
        }
            
        if (newQuery.equals("#weight(")) { return ""; }
        
        newQuery += " )";
        return newQuery;
    }
    
    public void close() {
    	try {
	    	index.close();
	    	index.delete();
    	} catch (Exception e) {
    		System.err.println("Error closing index.");
    		System.err.println(e.getStackTrace());
    	}
    }

}
