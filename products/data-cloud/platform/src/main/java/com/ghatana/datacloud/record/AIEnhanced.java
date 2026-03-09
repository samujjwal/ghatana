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

package com.ghatana.datacloud.record;

import java.util.Map;
import java.util.Optional;

/**
 * Trait for records with AI-generated metadata.
 *
 * <p>Records implementing this trait store AI/ML-generated metadata
 * alongside the core data. This enables AI-first data operations
 * where every record carries AI context.
 *
 * <h2>AI Metadata Examples</h2>
 * <ul>
 *   <li>Vector embeddings for semantic search</li>
 *   <li>Classification labels and scores</li>
 *   <li>Anomaly detection flags</li>
 *   <li>Entity extraction results</li>
 *   <li>Summarization outputs</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * interface AIRecord extends Record, AIEnhanced {}
 *
 * // Check AI confidence
 * if (record.aiConfidence().orElse(0.0) > 0.9) {
 *     // High-confidence AI results
 *     List<String> labels = record.getAIMeta("labels", List.class);
 * }
 *
 * // Get explanation for AI decisions
 * record.aiExplanation().ifPresent(System.out::println);
 * }</pre>
 *
 * @see Record
 * @see com.ghatana.datacloud.ai.AIAspect
 * @doc.type interface
 * @doc.purpose AI metadata trait
 * @doc.layer core
 * @doc.pattern Trait, Mixin
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface AIEnhanced {

    /**
     * Returns AI-generated metadata for this record.
     *
     * <p>Common keys include:
     * <ul>
     *   <li>{@code embeddings} - Vector embeddings</li>
     *   <li>{@code labels} - Classification labels</li>
     *   <li>{@code anomalyScore} - Anomaly detection score</li>
     *   <li>{@code entities} - Extracted entities</li>
     *   <li>{@code summary} - AI-generated summary</li>
     * </ul>
     *
     * @return immutable map of AI metadata (never null)
     */
    Map<String, Object> aiMetadata();

    /**
     * Returns the overall AI confidence score.
     *
     * <p>Score ranges from 0.0 (no confidence) to 1.0 (full confidence).
     * This typically represents the lowest confidence across all AI operations.
     *
     * @return confidence score, or empty if no AI processing
     */
    Optional<Double> aiConfidence();

    /**
     * Returns a human-readable explanation of AI decisions.
     *
     * <p>This explains why specific labels, scores, or flags were assigned.
     * Useful for debugging and explainability requirements.
     *
     * @return explanation text, or empty if not available
     */
    Optional<String> aiExplanation();

    /**
     * Returns true if this record has AI enhancements.
     *
     * @return true if aiMetadata is non-empty
     */
    default boolean hasAIEnhancements() {
        Map<String, Object> meta = aiMetadata();
        return meta != null && !meta.isEmpty();
    }

    /**
     * Returns a specific AI metadata value.
     *
     * @param key the metadata key
     * @return the value, or null if not present
     */
    default Object getAIMeta(String key) {
        return aiMetadata().get(key);
    }

    /**
     * Returns a specific AI metadata value cast to the expected type.
     *
     * @param <T> the expected type
     * @param key the metadata key
     * @param type the expected class
     * @return the value cast to type, or null if not present
     */
    @SuppressWarnings("unchecked")
    default <T> T getAIMeta(String key, Class<T> type) {
        Object value = aiMetadata().get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * Returns the vector embedding if present.
     *
     * @return float array of embeddings, or null if not present
     */
    default float[] getEmbedding() {
        return getAIMeta("embedding", float[].class);
    }

    /**
     * Returns true if the record has a vector embedding.
     *
     * @return true if embedding is present
     */
    default boolean hasEmbedding() {
        return aiMetadata().containsKey("embedding");
    }
}
