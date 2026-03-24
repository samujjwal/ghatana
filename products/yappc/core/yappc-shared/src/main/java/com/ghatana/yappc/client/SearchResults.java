package com.ghatana.yappc.client;

import java.util.List;

/**
 * Search results from knowledge graph.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles search results operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class SearchResults {
    
    private final List<SearchResult> results;
    private final int totalCount;
    
    public SearchResults(List<SearchResult> results, int totalCount) {
        this.results = List.copyOf(results);
        this.totalCount = totalCount;
    }
    
    public List<SearchResult> getResults() {
        return results;
    }
    
    public int getTotalCount() {
        return totalCount;
    }
    
    public static final class SearchResult {
        private final String id;
        private final String title;
        private final String content;
        private final double score;
        
        public SearchResult(String id, String title, String content, double score) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.score = score;
        }
        
        public String getId() {
            return id;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getContent() {
            return content;
        }
        
        public double getScore() {
            return score;
        }
    }
}
