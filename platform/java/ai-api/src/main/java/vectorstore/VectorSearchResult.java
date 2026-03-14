package com.ghatana.ai.vectorstore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Immutable value object representing the result of a vector similarity search.
 * 
 * @doc.type class
 * @doc.purpose Represents a single result from a vector similarity search with the matched content and similarity score.
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class VectorSearchResult {
    private final String id;
    private final String content;
    private final float[] vector;
    private final double similarity;
    private final int rank;
    private final java.util.Map<String, String> metadata;

    @JsonCreator
    public VectorSearchResult(
            @JsonProperty("id") String id,
            @JsonProperty("content") String content,
            @JsonProperty("vector") float[] vector,
            @JsonProperty("similarity") double similarity,
            @JsonProperty("rank") int rank,
            @JsonProperty("metadata") java.util.Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.vector = Objects.requireNonNull(vector, "vector cannot be null").clone();
        this.similarity = similarity;
        this.rank = rank;
        this.metadata = metadata != null ? java.util.Collections.unmodifiableMap(new java.util.HashMap<>(metadata)) : java.util.Collections.emptyMap();
        
        if (similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException("similarity must be between 0 and 1");
        }
        if (rank < 0) {
            throw new IllegalArgumentException("rank cannot be negative");
        }
    }

    // Constructor for backward compatibility
    public VectorSearchResult(String id, String content, float[] vector, double similarity, int rank) {
        this(id, content, vector, similarity, rank, java.util.Collections.emptyMap());
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
    
    public java.util.Map<String, String> getMetadata() {
        return metadata;
    }

    public float[] getVector() {
        return vector.clone();
    }

    public double getSimilarity() {
        return similarity;
    }

    public int getRank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VectorSearchResult that = (VectorSearchResult) o;
        return Double.compare(that.similarity, similarity) == 0 &&
               rank == that.rank &&
               id.equals(that.id) &&
               content.equals(that.content) &&
               java.util.Arrays.equals(vector, that.vector);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, content, similarity, rank);
        result = 31 * result + java.util.Arrays.hashCode(vector);
        return result;
    }

    @Override
    public String toString() {
        return "VectorSearchResult{" +
               "id='" + id + '\'' +
               ", content='" + content + '\'' +
               ", similarity=" + String.format("%.4f", similarity) +
               ", rank=" + rank +
               '}';
    }
}
