package com.ghatana.yappc.client;

/**
 * Query for knowledge graph search.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles knowledge query operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class KnowledgeQuery {
    
    private final String query;
    private final int limit;
    private final double minScore;
    
    public KnowledgeQuery(String query, int limit, double minScore) {
        this.query = query;
        this.limit = limit;
        this.minScore = minScore;
    }
    
    public String getQuery() {
        return query;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public double getMinScore() {
        return minScore;
    }
}
