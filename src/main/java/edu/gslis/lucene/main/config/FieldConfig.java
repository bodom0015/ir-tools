package edu.gslis.lucene.main.config;

/**
 * YAML configuration binding for a Lucene field. 
 * http://lucene.apache.org/core/4_0_0/core/org/apache/lucene/document/Field.html
 */
public class FieldConfig {

    public static final String TYPE_ID = "id";
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_INT = "int";
    public static final String TYPE_LONG = "long";
    public static final String TYPE_DOUBLE = "double";
    
    public static final String SOURCE_ELEMENT = "element";
    public static final String SOURCE_FILE = "file";
    public static final String SOURCE_FILENAME = "filename";
        
    String element = "";
    String name = "";
    String type = "";
    String source = "";
    boolean indexed = true;
    boolean stored = true;
    boolean storedTermVectors = true;
    boolean storedTermVectorPositions = true;
    boolean storedTermVectorOffsets = true;
    boolean storedTermVectorPayloads = true;
    boolean analyzed = true;
    String analyzer = "";
    
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public boolean isIndexed() {
        return indexed;
    }
    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }
    public boolean isStored() {
        return stored;
    }
    public void setStored(boolean stored) {
        this.stored = stored;
    }
    public boolean isStoredTermVectors() {
        return storedTermVectors;
    }
    public void setStoredTermVectors(boolean storedTermVectors) {
        this.storedTermVectors = storedTermVectors;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public boolean isStoredTermVectorPositions() {
        return storedTermVectorPositions;
    }
    public void setStoredTermVectorPositions(boolean storedTermVectorPositions) {
        this.storedTermVectorPositions = storedTermVectorPositions;
    }
    public boolean isStoredTermVectorOffsets() {
        return storedTermVectorOffsets;
    }
    public void setStoredTermVectorOffsets(boolean storedTermVectorOffsets) {
        this.storedTermVectorOffsets = storedTermVectorOffsets;
    }
    public boolean isStoredTermVectorPayloads() {
        return storedTermVectorPayloads;
    }
    public void setStoredTermVectorPayloads(boolean storedTermVectorPayloads) {
        this.storedTermVectorPayloads = storedTermVectorPayloads;
    }
    public String getAnalyzer() {
        return analyzer;
    }
    public void setAnalyzer(String analyzer) {
        this.analyzer = analyzer;
    }
    public String getSource() {
        return source;
    }
    public void setSource(String source) {
        this.source = source;
    }
    public boolean isAnalyzed() {
        return analyzed;
    }
    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }
    public String getElement() {
        return element;
    }
    public void setElement(String element) {
        this.element = element;
    }        
}
