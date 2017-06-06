package edu.gslis.lucene.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.lucene.main.config.QueryConfig;
import edu.gslis.lucene.main.config.QueryFile;
import edu.gslis.lucene.main.config.RunQueryConfig;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesFedwebImpl;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.textrepresentation.FeatureVector;

/**
 * Lucene query runner modeled after IndriRunQuery
 */
public class LuceneRunQuery {
    ClassLoader loader = ClassLoader.getSystemClassLoader();
    RunQueryConfig config;
    
    public LuceneRunQuery(RunQueryConfig config) {
        this.config = config;
    }
    

    public static void main(String[] args) throws Exception {
        RunQueryConfig config = new RunQueryConfig();
        if (args.length == 0) 
        {
            System.err.println("you must specify a configuration file.");
            System.exit(-1);
        }
        else if (args.length == 1) {
            File yamlFile = new File(args[0]);
            if(!yamlFile.exists()) {
                System.err.println("Configuration file not found.");
                System.exit(-1);
            }
            Yaml yaml = new Yaml(new Constructor(RunQueryConfig.class));
            config = (RunQueryConfig)yaml.load(new FileInputStream(yamlFile));
        }        
        else {
        	// No yaml file specified, use command line options
            Options options = createOptions();
            CommandLineParser parser = new GnuParser();
            CommandLine cmd = parser.parse( options, args);
            
            String analyzer = cmd.getOptionValue("analyzer", Indexer.DEFAULT_ANALYZER);
            String docno = cmd.getOptionValue("docno", Indexer.FIELD_DOCNO);
            String field = cmd.getOptionValue("field", Indexer.FIELD_TEXT);
            String querynum = cmd.getOptionValue("querynum", "1");
            String runname = cmd.getOptionValue("name", "default");
            
            String similarity = cmd.getOptionValue("similarity", Indexer.DEFAULT_SIMILARITY);

            String query = cmd.getOptionValue("query");
            String queryfile = cmd.getOptionValue("queryfile");
            String format = cmd.getOptionValue("format");
            String index = cmd.getOptionValue("index");
            String stopwords = cmd.getOptionValue("stopwords");
            
            Set<QueryConfig> queries = new HashSet<QueryConfig>();                    
            if (!StringUtils.isEmpty(query)) {
                QueryConfig querycfg = new QueryConfig();
                querycfg.setNumber(querynum);
                querycfg.setText(query.replaceAll("'", "\""));
                queries.add(querycfg);
            } else if (!StringUtils.isEmpty(queryfile)) {
                queries = readQueries(queryfile, format);
            }
            
            config.setAnalyzer(analyzer);
            config.setField(field);
            config.setIndex(index);
            config.setQueries(queries);
            config.setDocno(docno);
            config.setSimilarity(similarity);
            config.setStopwords(stopwords);
            config.setRunName(runname);
        }            
        LuceneRunQuery runner = new LuceneRunQuery(config);
        runner.run();
    }
    
    /**
     * Read index metadata, if present
     * @param indexPath Path to index directory
     * @return Map of metadata properties and values
     */
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
    
    /**
     * Read the query files given the specified format
     * @param file Path to topics
     * @param format One of fedweb, json, indri
     * @return Set of QueryConfig objects
     * @throws IOException 
     */
    private static Set<QueryConfig> readQueries(String file, String format) throws IOException 
    {
        Set<QueryConfig> queries = new TreeSet<QueryConfig>();

        GQueries gqueries = null;
        if (format.equals("fedweb")) {
           gqueries = new GQueriesFedwebImpl();
        }
        else if (format.equals("json")) {
            gqueries = new GQueriesJsonImpl();
        }
        else if (format.equals("indri")) {
            gqueries = new GQueriesIndriImpl();
        }
        
        gqueries.read(file);


        Iterator<GQuery> it = gqueries.iterator();
        while(it.hasNext()) {
            GQuery query = it.next();
            
            StringBuffer qstr = new StringBuffer();
            FeatureVector fv = query.getFeatureVector();
            Iterator<String> fit = fv.iterator();
            while (fit.hasNext()) {
                String feature = fit.next();
                double weight = fv.getFeatureWeight(feature);
                qstr.append(feature + "^" + weight);
                if (fit.hasNext()) 
                    qstr.append(" ");
                
            }
            
            QueryConfig qc = new QueryConfig();
            qc.setText(qstr.toString());
            qc.setNumber(query.getTitle());
            queries.add(qc);
        }
        
        return queries;
    }
    
    /**
     * Run the specified query configuration
     * @throws Exception
     */
    private void run() throws Exception 
    {
        String indexPath = config.getIndex();
        String docnoField = config.getDocno();
        if (StringUtils.isEmpty(docnoField))
            docnoField = "docno";
        String similarityModel = config.getSimilarity();
        String stopwordsPath = config.getStopwords();
        String analyzerClass = config.getAnalyzer();
        StopwordAnalyzerBase defaultAnalyzer;
        
        Map<String, String> indexMetadata = readIndexMetadata(indexPath);
        if (StringUtils.isEmpty(analyzerClass) && indexMetadata.get("analyzer") != null) 
            analyzerClass = indexMetadata.get("analyzer");
        if (StringUtils.isEmpty(similarityModel) && indexMetadata.get("similarity") != null) 
        	similarityModel = indexMetadata.get("similarity");
                                
        // Use the specified analyzer
        if (!StringUtils.isEmpty(analyzerClass))
        {
            @SuppressWarnings("rawtypes")
            Class analyzerCls = loader.loadClass(analyzerClass);

            if (!StringUtils.isEmpty(stopwordsPath))
            {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                java.lang.reflect.Constructor analyzerConst = analyzerCls.getConstructor(Version.class, Reader.class);
                defaultAnalyzer = (StopwordAnalyzerBase)analyzerConst.newInstance(Indexer.VERSION, new FileReader(stopwordsPath));
            } else {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                java.lang.reflect.Constructor analyzerConst = analyzerCls.getConstructor(Version.class);
                defaultAnalyzer = (StopwordAnalyzerBase)analyzerConst.newInstance(Indexer.VERSION);
            }
        } else {
            defaultAnalyzer = new StandardAnalyzer(Indexer.VERSION, new CharArraySet(Indexer.VERSION, 0, true));
        }

        // Construct the configured similarity
        Similarity similarity = getSimilarity(similarityModel);
        
        // Setup the index searcher
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        String field = config.getField();
        if (StringUtils.isEmpty(field))
            field = "text";
        String[] fields = field.split(",");
 

        System.err.println("Similarity model: " + similarityModel);
        System.err.println("Analyzer: " + analyzerClass);


        // Read the queries
        Set<QueryConfig> queries = config.getQueries();        
        if (queries == null)
            queries = new HashSet<QueryConfig>(); 
        QueryFile queryFile = config.getQueryFile();
        if (queryFile != null) {
            String path = queryFile.getPath();
            String format = queryFile.getFormat();
            
            queries = readQueries(path, format);
        }
        
        // Run each query
        for (QueryConfig query: queries) {
            QueryParser parser = new MultiFieldQueryParser(Indexer.VERSION, fields, defaultAnalyzer);
            Query q = parser.parse(query.getText());
            TopDocs topDocs = searcher.search(q, 1000);
            ScoreDoc[] docs = topDocs.scoreDocs;
            Set<String> seen = new HashSet<String>();
            for (int i=0; i<docs.length; i++) {
                int docid = docs[i].doc;
                double score = docs[i].score;
                Document doc = searcher.doc(docid);
                
                String docno = doc.getField(docnoField).stringValue();
                //long doclen = doc.getField(Indexer.FIELD_DOC_LEN).numericValue().longValue();

                if (!seen.contains(docno)) {
                    System.out.println(query.getNumber() + " Q0 " + docno + " " + i + " "  + score + " " + config.getRunName());
                    seen.add(docno);
                }
            }

        }
    }
    
    
    /**
     * Construct a Similarity object based on the model specification
     * @param model Model specification (e.g., method:dir,mu:2500)
     * @return Similarity instance
     */
    private Similarity getSimilarity(String model) {
    	
    	Similarity similarity = null;
    	Map<String, String> params = new HashMap<String, String>();
    	String[] fields = model.split(",");
    	
    	// Parse the model spec
    	for (String field: fields) {
    		String[] nvpair = field.split(":");
    		params.put(nvpair[0], nvpair[1]);
    	}
    	
    	
    	String method = params.get("method");
    	if (method.equals("dir") || method.equals("dirichlet")) {
    		float mu = 2500;
    		if (params.get("mu") != null) 
    			mu = Float.parseFloat(params.get("mu"));
    		similarity = new LMDirichletSimilarity(mu);    		
    	}
    	else if (method.equals("jm") || method.equals("linear")) {
    		float lambda = 0.5f;
    		if (params.get("lambda") != null) 
    			lambda = Float.parseFloat(params.get("lambda"));
    		similarity = new LMJelinekMercerSimilarity(lambda);    		
    	}
    	else if (method.equals("bm25")) {
    		float k1 = 1.2f;
    		float b = 0.75f;
    		if (params.get("k1") != null) 
    			k1 = Float.parseFloat(params.get("k1"));
    		if (params.get("b") != null) 
    			k1 = Float.parseFloat(params.get("b"));
    		similarity = new BM25Similarity(k1, b);    		
    	}
       	else if (method.equals("tfidf")) {
    		similarity = new DefaultSimilarity();  
       	} else {
       		System.err.println("Warning: unknown similarity specified, defaulting to LMDirichlet(mu=2500)");
       		similarity = new LMDirichletSimilarity(2500);
       	}
    	
    	return similarity;
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("analyzer", true, "Analyzer class");
        options.addOption("index", true, "Path to index");
        options.addOption("field", true, "Field to search");
        options.addOption("docno", true, "Docno field");
        options.addOption("querynum", true, "Query identifier");
        options.addOption("query", true, "Query string");
        options.addOption("queryfile", true, "Query file");
        options.addOption("format", true, "Query file format");
        options.addOption("similarity", true, "Similarity class");
        options.addOption("stopwords", true, "Stopwords list");
        options.addOption("name", true, "Run name");
        return options;
    }

}
