package com.ghatana.requirements.ai.dto;

/**

 * @doc.type class

 * @doc.purpose Handles vector search result operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

public class VectorSearchResult {
    private String id;
    private String content;
    private double similarity;
    private String source;

    private VectorSearchResult() {}

    public String getId() { return id; }
    public String getContent() { return content; }
    public double getSimilarity() { return similarity; }
    public String getSource() { return source; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final VectorSearchResult r = new VectorSearchResult();
        public Builder id(String id) { r.id = id; return this; }
        public Builder content(String c) { r.content = c; return this; }
        public Builder similarity(double s) { r.similarity = s; return this; }
        public Builder source(String s) { r.source = s; return this; }
        public VectorSearchResult build() { return r; }
    }
}
