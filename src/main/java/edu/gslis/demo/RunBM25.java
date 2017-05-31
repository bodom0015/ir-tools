package edu.gslis.demo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;

import edu.gslis.docaccumulators.ResultAccumulator;
import edu.gslis.docscoring.QueryDocScorer;
import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperIndriImpl;
import edu.gslis.output.FormattedOutputTrecEval;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.searchhits.UnscoredSearchHit;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.ParameterBroker;
import edu.gslis.utils.Stopper;


/**
 * runs a basic test/train batch filter session
 * 
 * @author mefron
 *
 */

public class RunBM25 {
	
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, SecurityException, NoSuchFieldException, FileNotFoundException {
		File paramFile = new File(args[0]);
		if(!paramFile.exists()) {
			System.err.println("you must specify a parameter file to run against.");
			System.exit(-1);
		}
		
		ParameterBroker params = new ParameterBroker(args[0]);

		
		GQueries        queries    = new GQueriesJsonImpl();
						
						String queryPath = params.getParamValue(ParameterBroker.QUERY_PATH_PARAM);
						
						// If queries are not in JSON, convert them
						String jsonLocation = queryPath;
						if (queryPath.indexOf("json") == -1) {
							jsonLocation = "/home/gsherma2/devel/jsonTopics/" + queryPath.split("/")[queryPath.split("/").length-1] + ".json";
							
							// Create JSON file
							PrintWriter out = new PrintWriter(jsonLocation);
							out.write(getGQueries(queryPath));
							out.close();
						}
						
						queries.read(jsonLocation);
		
		IndexWrapper    index = new IndexWrapperIndriImpl(params.getParamValue("index"));
		
		
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
		if(corpusStatsPath != null)
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

			System.err.println(query.getText());
			
			ResultAccumulator accumulator = new ResultAccumulator((IndexWrapperIndriImpl)index, 
					query.getFeatureVector(), "the");
			accumulator.accumulate();
			List<UnscoredSearchHit> accumulated = accumulator.getChronologicallyOrderedDocs();
			
			System.err.println(accumulated.size());
			
			SearchHits results = new SearchHits();
			Iterator<UnscoredSearchHit> docIterator = accumulated.iterator();
			while(docIterator.hasNext()) {
				UnscoredSearchHit unscoredHit = docIterator.next();
				SearchHit hit = unscoredHit.toSearchHit();
				double score = docScorer.score(hit);
				hit.setScore(score);
				
				results.add(hit);
			}
			output.write(results, query.getTitle());
		}
		output.close();
	}

	public static String getGQueries(String pathToIndriQueries) {
		GQueries gQueries = new GQueriesJsonImpl();
		
		List<String> lines = null;
		try {
			lines = IOUtils.readLines(new FileReader(pathToIndriQueries));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Iterator<String> lineIterator = lines.iterator();
		while(lineIterator.hasNext()) {
			String line = lineIterator.next();
			if(line.contains("<number>")) {
				String title = line;
				title = title.replaceAll("<.?number>", "").trim();
				lineIterator.next(); // <text>
				String text = lineIterator.next();
				lineIterator.next(); // </text>
				String epoch = lineIterator.next();
				epoch = epoch.replaceAll("<.*?rel>", "").trim();
				GQuery gQuery = new GQuery();
				gQuery.setTitle(title);
				gQuery.setText(text);
				gQuery.setMetadata("epoch", epoch);
				gQuery.setFeatureVector(new FeatureVector(text, null));
				gQueries.addQuery(gQuery);
			}
		}
		
		return gQueries.toString();
	}
	
}

