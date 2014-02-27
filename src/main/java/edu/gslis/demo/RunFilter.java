package edu.gslis.demo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;

import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.eval.Qrels;
import edu.gslis.filtering.session.FilterSession;
import edu.gslis.filtering.session.SimpleFilterSessionImpl;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.ParameterBroker;
import edu.gslis.utils.Stopper;


/**
 * runs a basic test/train batch filter session
 * 
 * @author mefron
 *
 */

public class RunFilter {
	
	/**
	 * runs a basic test/train batch filter session
	 * 
	 * @param args[0] /path/to/json/param/file
	 * @throws ClassNotFoundException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws NoSuchFieldException 
	 * @throws SecurityException 
	 */
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, SecurityException, NoSuchFieldException {
		File paramFile = new File(args[0]);
		if(!paramFile.exists()) {
			System.err.println("you must specify a parameter file to run against.");
			System.exit(-1);
		}
		
		ParameterBroker params = new ParameterBroker(args[0]);

		
		GQueries        queries    = new GQueriesJsonImpl();
		queries.setMetadataField(FilterSession.NAME_OF_EMIT_STATUS_FIELD);
		queries.setMetadataField(FilterSession.NAME_OF_CONSTRAINT_FIELD);
		queries.read(params.getParamValue(ParameterBroker.QUERY_PATH_PARAM));
		
		IndexWrapper    trainIndex = new IndexWrapperIndriImpl(params.getParamValue("train-index"));
		IndexWrapper    testIndex = new IndexWrapperIndriImpl(params.getParamValue("test-index"));
		
		Qrels trainQrels = null;
		String pathToQrels = params.getParamValue("train-qrels");
		if(pathToQrels != null) {
			trainQrels =  new Qrels(pathToQrels, false, 2);
		}
		
		
		String runId = "gslis";
		if(params.getParamValue("run-name") != null)
			runId = params.getParamValue("run-name");

		Stopper stopper = null;
		if(params.getParamValue(ParameterBroker.STOPPER_PARAM) != null)
			stopper = new Stopper(params.getParamValue(ParameterBroker.STOPPER_PARAM));
		
	
		
		
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		
		// figure out how we'll be accessing corpus-level stats
		// default
		String corpusStatsClass = "edu.gslis.docscoring.support.IndexBackedCollectionStats";
		// if we've been told otherwise
		if(params.getParamValue("bg-stat-type") != null)
			corpusStatsClass = params.getParamValue("bg-stat-type");
		CollectionStats corpusStats = (CollectionStats)loader.loadClass(corpusStatsClass).newInstance();
		
		// figure out the source of our corpus-level stats.
		//  this could be the name of a variable pointing to an index, or...
		//  the name of a variable pointing to a dumpindex-type file
		// default
		String corpusStatsPath = params.getParamValue("bg-source-path");
		corpusStats.setStatSource(corpusStatsPath);
		
		// set up our document scorer
		String scorerType = "edu.gslis.docscoring.ScorerDirichlet";
		if(params.getParamValue("scorer-name") != null)
			scorerType = params.getParamValue("scorer-name");
		QueryDocScorer docScorer = (QueryDocScorer)loader.loadClass(scorerType).newInstance();
		docScorer.setCollectionStats(corpusStats);
		Iterator<String> parameterIt = params.getAllParams().keySet().iterator();
		while(parameterIt.hasNext()) {
			String paramName = parameterIt.next();
			if(! paramName.startsWith("scorer-param-"))
				continue;
			double paramValue = Double.parseDouble(params.getParamValue(paramName));
			paramName = paramName.replaceFirst("scorer-param-", "");
			docScorer.setParameter(paramName, paramValue);
		}
		
		Writer outputWriter = new BufferedWriter(new OutputStreamWriter(System.out));
		FormattedOutputTrecEval output = FormattedOutputTrecEval.getInstance(runId, outputWriter);
		
		Iterator<GQuery> queryIterator = queries.iterator();
		while(queryIterator.hasNext()) {
			GQuery query = queryIterator.next();
			
			System.err.println(query.getTitle());
			
			FeatureVector surfaceForm = new FeatureVector(stopper);
			Iterator<String> queryTerms = query.getFeatureVector().iterator();
			while(queryTerms.hasNext()) {
				String term = queryTerms.next();
				surfaceForm.addTerm(term, query.getFeatureVector().getFeatureWeight(term));
			}
			query.setFeatureVector(surfaceForm);
			
			docScorer.setQuery(query);

			FilterSession filterSession = new SimpleFilterSessionImpl(query, 
					trainIndex,
					testIndex,
					trainQrels,
					docScorer);
			
			filterSession.train();
			SearchHits results = filterSession.filter();
			
			output.write(results, query.getTitle());
		}
		output.close();
	}

}
