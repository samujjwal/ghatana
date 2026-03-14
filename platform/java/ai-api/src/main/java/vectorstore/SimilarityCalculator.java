package com.ghatana.ai.vectorstore;

/**
 * Utility class for calculating similarity scores between vectors.
 * 
 * @doc.type class
 * @doc.purpose Provides deterministic similarity scoring utilities for vector comparisons.
 * @doc.layer utility
 * @doc.pattern Utility
 */
public final class SimilarityCalculator {
    
    private SimilarityCalculator() {
        // Utility class, no instantiation
    }
    
    /**
     * Calculates the cosine similarity between two vectors.
     * 
     * Cosine similarity measures the angle between vectors, resulting in a value between -1 and 1.
     * For normalized vectors (unit length), this is equivalent to dot product.
     *
     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Cosine similarity score between -1 and 1
     * @throws IllegalArgumentException if vectors have different lengths or are empty
     */
    public static double cosineSimilarity(float[] vector1, float[] vector2) {
        validateVectors(vector1, vector2);
        
        double dotProduct = 0.0;
        double magnitude1 = 0.0;
        double magnitude2 = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            magnitude1 += vector1[i] * vector1[i];
            magnitude2 += vector2[i] * vector2[i];
        }
        
        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);
        
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (magnitude1 * magnitude2);
    }
    
    /**
     * Calculates the normalized cosine similarity (0 to 1 range).
     * 
     * Converts cosine similarity from [-1, 1] range to [0, 1] range by applying (similarity + 1) / 2.
     *
     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Normalized similarity score between 0 and 1
     * @throws IllegalArgumentException if vectors have different lengths or are empty
     */
    public static double normalizedCosineSimilarity(float[] vector1, float[] vector2) {
        double similarity = cosineSimilarity(vector1, vector2);
        return (similarity + 1.0) / 2.0;
    }
    
    /**
     * Calculates the Euclidean distance between two vectors.
     * 
     * Euclidean distance is the straight-line distance between two points in vector space.
     *

     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Euclidean distance (always >= 0)
     * @throws IllegalArgumentException if vectors have different lengths or are empty
     */
    public static double euclideanDistance(float[] vector1, float[] vector2) {
        validateVectors(vector1, vector2);
        
        double sumSquaredDifferences = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            double diff = vector1[i] - vector2[i];
            sumSquaredDifferences += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDifferences);
    }
    
    /**
     * Converts Euclidean distance to a similarity score (0 to 1 range).
     * 
     * Uses the formula: similarity = 1 / (1 + distance)
     *

     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Similarity score between 0 and 1
     * @throws IllegalArgumentException if vectors have different lengths or are empty
     */
    public static double euclideanSimilarity(float[] vector1, float[] vector2) {
        double distance = euclideanDistance(vector1, vector2);
        return 1.0 / (1.0 + distance);
    }
    
    /**
     * Calculates the dot product of two vectors.
     * 
     * For normalized vectors, dot product equals cosine similarity.
     *

     * @param vector1 First vector
     * @param vector2 Second vector
     * @return Dot product value
     * @throws IllegalArgumentException if vectors have different lengths or are empty
     */
    public static double dotProduct(float[] vector1, float[] vector2) {
        validateVectors(vector1, vector2);
        
        double result = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            result += vector1[i] * vector2[i];
        }
        return result;
    }
    
    /**
     * Calculates the magnitude (L2 norm) of a vector.
     *

     * @param vector The vector
     * @return The magnitude of the vector
     * @throws IllegalArgumentException if vector is empty
     */
    public static double magnitude(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("vector cannot be null or empty");
        }
        
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += v * v;
        }
        return Math.sqrt(sumSquares);
    }
    
    /**
     * Normalizes a vector to unit length (magnitude = 1).
     *

     * @param vector The vector to normalize
     * @return A new normalized vector
     * @throws IllegalArgumentException if vector is empty or has zero magnitude
     */
    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("vector cannot be null or empty");
        }
        
        double mag = magnitude(vector);
        if (mag == 0.0) {
            throw new IllegalArgumentException("vector has zero magnitude and cannot be normalized");
        }
        
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / mag);
        }
        return normalized;
    }
    
    /**
     * Validates that two vectors are compatible for similarity calculations.
     *

     * @param vector1 First vector
     * @param vector2 Second vector
     * @throws IllegalArgumentException if vectors are null, empty, or have different lengths
     */
    private static void validateVectors(float[] vector1, float[] vector2) {
        if (vector1 == null || vector1.length == 0) {
            throw new IllegalArgumentException("vector1 cannot be null or empty");
        }
        if (vector2 == null || vector2.length == 0) {
            throw new IllegalArgumentException("vector2 cannot be null or empty");
        }
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException(
                    String.format("vectors must have same length: %d vs %d", 
                            vector1.length, vector2.length)
            );
        }
    }
}
