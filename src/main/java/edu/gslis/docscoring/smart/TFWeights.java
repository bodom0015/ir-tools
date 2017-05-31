package edu.gslis.docscoring.smart;

import java.util.Iterator;

import edu.gslis.textrepresentation.FeatureVector;

/** 
 * Below are the various weighting procedures implemented in the SMART 11.0
 * system from libconvert library:
 * 
 * weights_tf.c
 * convert.wt_tf.x  Reweighting procedure no-op for term-freq weighting.
 * convert.wt_tf.n  Reweighting procedure no-op for term-freq weighting.
 * convert.wt_tf.b  Reweight vector by using binary term weights
 * convert.wt_tf.m  Reweight vector by dividing by max weight in vector
 * convert.wt_tf.a  Reweight vector by augmented tf
 * convert.wt_tf.s  Reweight vector by squaring each weight
 * convert.wt_tf.l  Reweight vector by logrithmic term freq
 */
public class TFWeights 
{
    
    public static final char TF_NO_OP = 'n';
    public static final char TF_LOG_WEIGHT = 'l';
    public static final char TF_AUGMENTED_WEIGHT = 'a';
    public static final char TF_BINARY_WEIGHT = 'b';
    public static final char TF_AVERAGE_WEIGHT = 'L';
    public static final char TF_MAX_WEIGHT = 'm';
    public static final char TF_SQUARE_WEIGHT = 's';
        
    public static TFWeight getTFWeight(char type) throws Exception
    { 
        if (type == TF_LOG_WEIGHT)
            return new TFWeights().new TFLogWeight();
        else if (type == TF_AUGMENTED_WEIGHT)
            return new TFWeights().new TFAugmentedWeight();
        else if (type == TF_BINARY_WEIGHT)
            return new TFWeights().new TFBinaryWeight();
        else if (type == TF_AVERAGE_WEIGHT)
            return new TFWeights().new TFAverageWeight();
        else if (type == TF_MAX_WEIGHT)
            return new TFWeights().new TFMaxWeight();
        else if (type == TF_SQUARE_WEIGHT)
            return new TFWeights().new TFSquareWeight();
        else if (type == TF_NO_OP)
            return new TFWeights().new TFWeight();
        else 
            throw new Exception("Unsupported TF weight type '" + type + "'");
    }
    
    // Simple interface for TFWeights
    public class TFWeight {
        public void weight(FeatureVector fv) throws Exception {}
    }
    // Implements the tfwt_binary (b) weight
    public class TFBinaryWeight extends TFWeight {
        /**
         * convert.wt_tf.b
         * tfwt_binary
         * @param fv feature vector
         */
        public void weight(FeatureVector fv) {
            Iterator<String> it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                fv.setTerm(term, 1.0);
            }
        }
    }
    
    public class TFMaxWeight extends TFWeight {
        /**
         * For each term, find the maximum frequency
         * Divide each term frequency by the maximum frequency
         * @param fv feature vector
         */
        public void weight(FeatureVector fv) {
            Iterator<String> it = fv.iterator();
            double max = 0.0;
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                if (weight > max) 
                    max = weight;
            }
            max += 0.00001;
            
            it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                weight = weight/max;
                fv.setTerm(term, weight);
            }   
        }
    }    

    public class TFAugmentedWeight extends TFWeight 
    {
        public void weight(FeatureVector fv)  {
            Iterator<String> it = fv.iterator();
            double max = 0.0;
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                if (weight > max) 
                    max = weight;
            }
            max += 0.00001;
            
            it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                weight = 0.5 + 0.5 + (weight/max);
                fv.setTerm(term, weight);
            }      
        }
    }
    
    public class TFSquareWeight extends TFWeight 
    {
        public void weight(FeatureVector fv) {
            Iterator<String> it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                fv.setTerm(term, (weight*weight));
            }
        }
    }
    
    public class TFLogWeight extends TFWeight {
        public void weight(FeatureVector fv) {
            Iterator<String> it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                fv.setTerm(term, (Math.log(weight) + 1.0));
            } 
        }    
    }
    
    public class TFAverageWeight extends TFWeight {
       public void weight(FeatureVector fv) {
            Iterator<String> it = fv.iterator();
            double avg = 0;
            while (it.hasNext()) {
                String term = it.next();
                avg += fv.getFeatureWeight(term);            
            } 
            avg = avg/(double)fv.getFeatureCount();
            
            it = fv.iterator();
            while (it.hasNext()) {
                String term = it.next();
                double weight = fv.getFeatureWeight(term);
                weight = (1+Math.log(weight)) / (1 + Math.log(avg));
                fv.setTerm(term, weight);
            } 
        }    
    }    
}
