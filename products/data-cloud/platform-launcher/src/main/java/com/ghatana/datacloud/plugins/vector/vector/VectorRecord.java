/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.plugins.vector;

import com.ghatana.datacloud.DataRecord;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A data record enriched with vector embedding for semantic operations.
 *
 * <p>VectorRecord wraps any DataRecord with its computed embedding vector,
 * enabling similarity search and semantic retrieval operations.
 *
 * <h2>Embedding Properties</h2>
 * <ul>
 *   <li><b>Dimension</b>: Typically 384, 768, or 1536 depending on model</li>
 *   <li><b>Normalization</b>: Embeddings are L2-normalized for cosine similarity</li>
 *   <li><b>Provenance</b>: Tracks which model generated the embedding</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * VectorRecord vectorRecord = VectorRecord.builder()
 *     .record(eventRecord)
 *     .embedding(embeddingService.embed(eventRecord.content()))
 *     .embeddingModel("text-embedding-3-small")
 *     .build();
 * 
 * float similarity = vectorRecord.cosineSimilarity(otherVector);
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Data record with vector embedding
 * @doc.layer plugin
 * @doc.pattern Decorator, Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class VectorRecord {

    /**
     * The underlying data record.
     */
    DataRecord record;

    /**
     * The embedding vector representing the record's semantic content.
     *
     * <p>This is a dense vector where each dimension captures some aspect
     * of the record's meaning. Typically normalized to unit length.
     */
    float[] embedding;

    /**
     * The dimensionality of the embedding.
     */
    @Builder.Default
    int dimension = 384;

    /**
     * The model used to generate the embedding.
     */
    @Builder.Default
    String embeddingModel = "unknown";

    /**
     * When the embedding was generated.
     */
    @Builder.Default
    Instant embeddedAt = Instant.now();

    /**
     * The text content that was embedded (for debugging/auditing).
     */
    String embeddedContent;

    /**
     * Whether the embedding is normalized (unit length).
     */
    @Builder.Default
    boolean normalized = true;

    /**
     * Additional metadata about the embedding.
     */
    @Builder.Default
    Map<String, Object> vectorMetadata = Map.of();

    /**
     * Tags for filtering in vector search.
     */
    @Builder.Default
    List<String> tags = List.of();

    /**
     * The tenant this record belongs to.
     */
    String tenantId;

    /**
     * Computes cosine similarity with another embedding.
     *
     * @param other the other embedding vector
     * @return similarity score (-1.0 to 1.0, higher = more similar)
     * @throws IllegalArgumentException if dimensions don't match
     */
    public float cosineSimilarity(float[] other) {
        if (embedding.length != other.length) {
            throw new IllegalArgumentException(
                    "Embedding dimensions must match: " + embedding.length + " vs " + other.length);
        }

        if (normalized) {
            // For normalized vectors, cosine similarity is just dot product
            return dotProduct(other);
        }

        // Full cosine similarity calculation
        float dot = dotProduct(other);
        float normA = magnitude();
        float normB = magnitude(other);

        return dot / (normA * normB);
    }

    /**
     * Computes dot product with another embedding.
     *
     * @param other the other embedding vector
     * @return the dot product
     */
    public float dotProduct(float[] other) {
        float sum = 0f;
        for (int i = 0; i < embedding.length; i++) {
            sum += embedding[i] * other[i];
        }
        return sum;
    }

    /**
     * Computes the L2 magnitude (norm) of this embedding.
     *
     * @return the magnitude
     */
    public float magnitude() {
        return magnitude(embedding);
    }

    private static float magnitude(float[] vector) {
        float sum = 0f;
        for (float v : vector) {
            sum += v * v;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * Computes Euclidean distance to another embedding.
     *
     * @param other the other embedding vector
     * @return the Euclidean distance (lower = more similar)
     */
    public float euclideanDistance(float[] other) {
        if (embedding.length != other.length) {
            throw new IllegalArgumentException("Embedding dimensions must match");
        }

        float sum = 0f;
        for (int i = 0; i < embedding.length; i++) {
            float diff = embedding[i] - other[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }

    /**
     * Returns a normalized (unit length) version of this record.
     *
     * @return normalized vector record
     */
    public VectorRecord normalize() {
        if (normalized) return this;

        float mag = magnitude();
        if (mag == 0) return this;

        float[] normalizedEmbedding = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            normalizedEmbedding[i] = embedding[i] / mag;
        }

        return this.toBuilder()
                .embedding(normalizedEmbedding)
                .normalized(true)
                .build();
    }

    /**
     * Gets the record ID.
     *
     * @return the underlying record's ID
     */
    public String id() {
        return record.getId().toString();
    }

    /**
     * Gets the record type.
     *
     * @return the underlying record's type
     */
    public String recordType() {
        return record.getClass().getSimpleName();
    }

    /**
     * Creates a VectorRecord from a record and embedding.
     *
     * @param record the data record
     * @param embedding the embedding vector
     * @param model the embedding model used
     * @return a new VectorRecord
     */
    public static VectorRecord of(DataRecord record, float[] embedding, String model) {
        return VectorRecord.builder()
                .record(record)
                .embedding(embedding)
                .dimension(embedding.length)
                .embeddingModel(model)
                .normalized(false)
                .build()
                .normalize();
    }

    /**
     * Creates a builder pre-populated with the given record.
     *
     * @param record the data record
     * @return a builder
     */
    public static VectorRecordBuilder forRecord(DataRecord record) {
        return VectorRecord.builder().record(record);
    }
}
