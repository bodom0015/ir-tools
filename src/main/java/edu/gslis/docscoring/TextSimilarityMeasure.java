package edu.gslis.docscoring;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import edu.gslis.textrepresentation.FeatureVector;

public class TextSimilarityMeasure {

	/**
	 * DANGER: assumes that both argument vectors have been L2 normalized!
	 * @param x feature vector
	 * @param y feature vector
	 * @param needNormalizing normalize
	 * @return cosine
	 */
	public static double cosine(FeatureVector x, FeatureVector y, boolean needNormalizing) {

		Set<String> vocab = new HashSet<String>();
		vocab.addAll(x.getFeatures());
		vocab.addAll(y.getFeatures());
		
		if(needNormalizing) {
			FeatureVector a = x.deepCopy();
			a.l2Normalize();
			x = a;
			FeatureVector b = y.deepCopy();
			b.l2Normalize();
			y = b;
		}
		
		double z = 0.0;
		Iterator<String> terms = vocab.iterator();
		while(terms.hasNext()) {
			String term = terms.next();
			double xTerm = x.getFeatureWeight(term);
			double yTerm = y.getFeatureWeight(term);
			
			if(Double.isInfinite(xTerm) || Double.isNaN(xTerm) || Double.isInfinite(yTerm) || Double.isNaN(yTerm))
			continue;
			
			z += xTerm * yTerm;
		}
		return z;
	}
}
