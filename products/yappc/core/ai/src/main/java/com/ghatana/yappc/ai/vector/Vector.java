package com.ghatana.yappc.ai.vector;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a vector embedding for semantic search.
 * 
 * <p>Vectors are numerical representations of text or other data,
 * used for similarity comparisons in AI/ML operations.</p>
 
 * @doc.type class
 * @doc.purpose Handles vector operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class Vector {
    
    private final float[] values;
    private final int dimensions;
    
    public Vector(@NotNull float[] values) {
        this.values = Objects.requireNonNull(values, "Vector values cannot be null");
        this.dimensions = values.length;
    }
    
    public float[] getValues() {
        return Arrays.copyOf(values, dimensions);
    }
    
    public int getDimensions() {
        return dimensions;
    }
    
    /**
     * Calculate cosine similarity with another vector.
     */
    public double cosineSimilarity(@NotNull Vector other) {
        if (this.dimensions != other.dimensions) {
            throw new IllegalArgumentException("Vectors must have same dimensions");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < dimensions; i++) {
            dotProduct += values[i] * other.values[i];
            normA += values[i] * values[i];
            normB += other.values[i] * other.values[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vector vector = (Vector) o;
        return Arrays.equals(values, vector.values);
    }
    
    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
    
    @Override
    public String toString() {
        return "Vector{dimensions=" + dimensions + "}";
    }
}
