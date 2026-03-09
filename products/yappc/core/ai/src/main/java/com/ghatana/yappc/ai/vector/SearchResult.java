package com.ghatana.yappc.ai.vector;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Result of a vector similarity search.
 * 
 * <p>Contains matching items with their similarity scores.</p>
 
 * @doc.type class
 * @doc.purpose Handles search result operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class SearchResult {
    
    private final String id;
    private final String content;
    private final double score;
    private final Vector vector;
    
    public SearchResult(
        @NotNull String id,
        @NotNull String content,
        double score,
        @NotNull Vector vector
    ) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.content = Objects.requireNonNull(content, "Content cannot be null");
        this.score = score;
        this.vector = Objects.requireNonNull(vector, "Vector cannot be null");
    }
    
    @NotNull
    public String getId() {
        return id;
    }
    
    @NotNull
    public String getContent() {
        return content;
    }
    
    public double getScore() {
        return score;
    }
    
    @NotNull
    public Vector getVector() {
        return vector;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult that = (SearchResult) o;
        return Double.compare(that.score, score) == 0 &&
               Objects.equals(id, that.id) &&
               Objects.equals(content, that.content);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, content, score);
    }
    
    @Override
    public String toString() {
        return "SearchResult{id='" + id + "', score=" + score + "}";
    }
    
    /**
     * Create a list of search results sorted by score (descending).
     */
    @NotNull
    public static List<SearchResult> sortedByScore(@NotNull List<SearchResult> results) {
        return results.stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .toList();
    }
}
