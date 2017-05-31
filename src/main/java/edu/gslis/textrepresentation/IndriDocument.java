package edu.gslis.textrepresentation;

import java.util.ArrayList;
import java.util.List;

import edu.gslis.utils.Stopper;
import lemurproject.indri.DocumentVector;
import lemurproject.indri.ParsedDocument;
import lemurproject.indri.QueryEnvironment;



/**
 * Basic class for handling interactions with an indri index pertaining to an individual document
 * 
 * @author Miles Efron
 *
 */
public class IndriDocument {

	private QueryEnvironment env;


	/**
	 * constructor for the case where we know the index 
	 */
	public IndriDocument(QueryEnvironment env) {
		this.env = env;
	}


	/**
	 * gets the parsed text of the document.  this is all nice and clean... exactly as indri stores it.
	 * @param docID the indri-internal numeric ID of the document.  i.e. not its TREC-assigned DOCNO element
	 * @return character String containing the full text of the document
	 */
	public String getDocString(int docID)  {
		StringBuilder b = new StringBuilder();
		String[] toks = getDocToks(docID);
		for(String tok : toks) {
			if(tok.equals("[OOV]")) {
				continue;
			}
			b = b.append(" " + tok + " ");
		}
		String docText = b.toString();
		return docText.replaceAll("  ", " ");
	}


	public String[] getDocToks(int docID) {
		int[] inds = new int[1];
		inds[0] = docID;
		String[] stems = null;
		int[] positions = null;
		inds[0] = docID;
		try{
			DocumentVector[] dv = env.documentVectors(inds);
			stems = dv[0].stems;
			positions = dv[0].positions;
		} catch (Exception e) {
			e.printStackTrace();
		}
		String[] toks = new String[positions.length];

		for(int i=0; i<positions.length; i++) {
			toks[i] = stems[positions[i]];
		}
		return toks;
	}

	public FeatureVector getFeatureVector(int docID, Stopper stopper) {
		String[] toks = getDocToks(docID);
		FeatureVector features = new FeatureVector(stopper);
		for(String tok : toks) {
			if(tok.equals("[OOV]"))
				continue;
			if(stopper==null) {
				features.addTerm(tok, 1.0);
			} else if(!stopper.isStopWord(tok))
				features.addTerm(tok, 1.0);
		}
		return features;
	}
	
	/**
	 * gets the unparsed text of the document.  i.e. all formatting/tags/structure are present.  the document
	 * is exactly as it appears in the input file.
	 * @param docID the indri-internal numeric ID of the document.  i.e. not its TREC-assigned DOCNO element
	 * @return unparsed text of the document
	 */
	public String getUnparsedText(int docID) {
		String trecText = null;

		int[] id = {docID};
		try {
			ParsedDocument[] docArray = env.documents(id);
			trecText =  docArray[0].text;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return trecText;
	}

	/**
	 * gets the TREC-assigned docno element for the document with this indri docID, or null if docno element isn't present.
	 * @param docID indri-internal numeric ID of the document.  i.e. not its TREC-assigned DOCNO element
	 * @return trec DOCNO element, or null
	 */
	public String getDocno(int docID) {
		String docno = null;
		int[] docIDs = {docID};
		try {
		String[] docnos = env.documentMetadata(docIDs, "docno");
		docno = docnos[0];
		} catch (Exception e) {
			e.printStackTrace();
		}
		return docno;
	}

	/**
	 * gets the indri-assigned docID for the document with this TREC-assigned docno element.
	 * @param docno TREC-assigned docno of the document. 
	 * @return indri-assigned docID, or -1
	 */
	public int getDocID(String docno) {
		String[] docnos = {docno};
		int[] docIDs = null;
		try {
			docIDs = env.documentIDsFromMetadata("docno", docnos);
		} catch (Exception e) {
			e.printStackTrace();		}
		if(docIDs==null || docIDs.length==0) {
			System.err.println("couldn't find the docId of doc " + docno);
			return 0;
		}
		return docIDs[0];
	}

	/**
	 * 
	 * @param e the QueryEnvironment that contains the document
	 */
	public void setIndex(QueryEnvironment e) {
		env = e;
	}
	
	/**
	 * Returns an ordered list of terms for the specified document.
	 * @param docID
	 * @return
	 */
    public List<String> getTerms(int docID) {
        List<String> terms = new ArrayList<String>();
        int[] inds = new int[1];
        inds[0] = docID;
        String[] stems = null;
        int[] positions = null;
        inds[0] = docID;
        try{
            DocumentVector[] dv = env.documentVectors(inds);
            stems = dv[0].stems;
            positions = dv[0].positions;
        } catch (Exception e) {
            e.printStackTrace();
        }

        for(int i=0; i<positions.length; i++) {
            if(stems[positions[i]].equals("[OOV]"))
                continue;

            terms.add(stems[positions[i]]);
        }
        return terms;
    }

}
