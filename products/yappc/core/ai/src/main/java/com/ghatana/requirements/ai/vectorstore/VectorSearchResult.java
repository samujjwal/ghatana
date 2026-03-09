package com.ghatana.requirements.ai.vectorstore;

/**
 * Result from vector similarity search.
 
 * @doc.type class
 * @doc.purpose Handles vector search result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class VectorSearchResult {
    private final String id;
    private final float score;
    private final String content;

    public VectorSearchResult(String id, float score, String content) {
        this.id = id;
        this.score = score;
        this.content = content;
    }

    public String getId() { return id; }
    public float getScore() { return score; }
    public String getContent() { return content; }
}
