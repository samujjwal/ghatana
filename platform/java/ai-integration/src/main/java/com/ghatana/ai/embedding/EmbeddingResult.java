package com.ghatana.ai.embedding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Immutable value object representing the result of an embedding operation.
 * 
 * @doc.type class
 * @doc.purpose Represents the result of an embedding operation with the original text and its vector representation.
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class EmbeddingResult {
    private final String text;
    private final float[] vector;
    private final String model;

    @JsonCreator
    public EmbeddingResult(
            @JsonProperty("text") String text,
            @JsonProperty("vector") float[] vector,
            @JsonProperty("model") String model) {
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.vector = Objects.requireNonNull(vector, "vector cannot be null").clone();
        this.model = Objects.requireNonNull(model, "model cannot be null");
    }

    public String getText() {
        return text;
    }

    public float[] getVector() {
        return vector.clone();
    }
    
    /**
     * Convenience method to get the embedding vector (alias for getVector).
     */
    public float[] embedding() {
        return getVector();
    }

    public String getModel() {
        return model;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmbeddingResult that = (EmbeddingResult) o;
        return text.equals(that.text) && 
               model.equals(that.model) && 
               java.util.Arrays.equals(vector, that.vector);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(text, model);
        result = 31 * result + java.util.Arrays.hashCode(vector);
        return result;
    }

    @Override
    public String toString() {
        return "EmbeddingResult{" +
               "text='" + text + '\'' +
               ", vector=[...]" +
               ", model='" + model + '\'' +
               '}';
    }
    
    /**
     * Creates an EmbeddingResult from an OpenAI Embedding object using official SDK.
     *
     * <p>Uses reflection to extract the embedding vector from the SDK object, making it
     * compatible with multiple SDK versions. The SDK object must expose an {@code embedding()}
     * method returning {@code List<Double>} (OpenAI Java SDK convention).
     *
     * @param embedding The OpenAI Embedding object from official SDK
     * @param text The original text that was embedded
     * @param model The model used for embedding
     * @return A new EmbeddingResult instance
     * @throws IllegalArgumentException if the embedding vector cannot be extracted
     */
    @SuppressWarnings("unchecked")
    public static EmbeddingResult fromOpenAI(Object embedding, String text, String model) {
        Objects.requireNonNull(embedding, "embedding cannot be null");
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(model, "model cannot be null");
        try {
            // OpenAI Java SDK: Embedding.embedding() returns List<Double>
            java.lang.reflect.Method embeddingMethod =
                    embedding.getClass().getMethod("embedding");
            java.util.List<Double> vector =
                    (java.util.List<Double>) embeddingMethod.invoke(embedding);
            float[] floatVector = new float[vector.size()];
            for (int i = 0; i < vector.size(); i++) {
                floatVector[i] = vector.get(i).floatValue();
            }
            return new EmbeddingResult(text, floatVector, model);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                "Cannot extract embedding vector from " + embedding.getClass().getName() +
                ". Expected a method embedding() returning List<Double>. " +
                "Alternatively, use constructor directly: new EmbeddingResult(text, vector, model)",
                e);
        }
    }

    /**
     * Creates an EmbeddingResult from a vector only (for caching scenarios where text is the key).
     *
     * @param vector The embedding vector
     * @return A new EmbeddingResult instance
     */
    public static EmbeddingResult of(float[] vector) {
        return new EmbeddingResult("", vector, "unknown");
    }

    /**
     * Returns the model name used for embedding (alias for getModel for consistency).
     *
     * @return The model name
     */
    public String getModelName() {
        return model;
    }
}
