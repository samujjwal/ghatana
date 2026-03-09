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

package com.ghatana.datacloud.pattern;

import com.ghatana.datacloud.DataRecord;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable representation of a learned pattern.
 *
 * <p>A pattern represents a recurring structure, behavior, or relationship
 * discovered in the data. Patterns are learned through observation and
 * refined over time.
 *
 * <h2>Pattern Components</h2>
 * <ul>
 *   <li><b>Signature</b>: The pattern's identifying characteristics</li>
 *   <li><b>Confidence</b>: Statistical confidence in the pattern</li>
 *   <li><b>Conditions</b>: When the pattern applies</li>
 *   <li><b>Predictions</b>: What the pattern predicts or implies</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * PatternRecord pattern = PatternRecord.builder()
 *     .id("pat-latency-spike")
 *     .name("Pre-Outage Latency Spike")
 *     .type(PatternType.CAUSAL)
 *     .signature(Map.of(
 *         "trigger", "latency_p99 > 500ms",
 *         "outcome", "service_outage within 15m"
 *     ))
 *     .confidence(0.85f)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Learned pattern representation
 * @doc.layer core
 * @doc.pattern Value Object, Entity
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class PatternRecord {

    // ═══════════════════════════════════════════════════════════════════════════
    // Identity
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Unique identifier for this pattern.
     */
    String id;

    /**
     * Human-readable name.
     */
    String name;

    /**
     * Detailed description of what this pattern represents.
     */
    String description;

    /**
     * The type/category of this pattern.
     */
    PatternType type;

    /**
     * Current version of the pattern.
     */
    PatternVersion version;

    // ═══════════════════════════════════════════════════════════════════════════
    // Signature and Matching
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The pattern signature - defining characteristics.
     *
     * <p>Structure depends on pattern type:
     * <ul>
     *   <li>TEMPORAL: periodicity, phase, amplitude</li>
     *   <li>CAUSAL: trigger conditions, effect conditions</li>
     *   <li>BEHAVIORAL: action sequences, state transitions</li>
     * </ul>
     */
    @Builder.Default
    Map<String, Object> signature = Map.of();

    /**
     * Conditions when this pattern applies.
     */
    @Builder.Default
    List<PatternCondition> conditions = List.of();

    /**
     * Vector embedding of the pattern for similarity search.
     */
    float[] embedding;

    /**
     * Example records that exhibit this pattern.
     */
    @Builder.Default
    List<String> exampleRecordIds = List.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Confidence and Quality
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Confidence score (0.0 to 1.0).
     */
    @Builder.Default
    float confidence = 0.0f;

    /**
     * Support: proportion of data exhibiting this pattern.
     */
    @Builder.Default
    float support = 0.0f;

    /**
     * Lift: ratio of observed to expected occurrences.
     */
    @Builder.Default
    float lift = 1.0f;

    /**
     * Number of times this pattern has been observed.
     */
    @Builder.Default
    long observationCount = 0;

    /**
     * Number of times pattern matched correctly.
     */
    @Builder.Default
    long matchSuccessCount = 0;

    /**
     * Number of false positive matches.
     */
    @Builder.Default
    long falsePositiveCount = 0;

    /**
     * Number of false negative misses.
     */
    @Builder.Default
    long falseNegativeCount = 0;

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pattern lifecycle status.
     */
    @Builder.Default
    PatternStatus status = PatternStatus.DRAFT;

    /**
     * When the pattern was first discovered.
     */
    @Builder.Default
    Instant discoveredAt = Instant.now();

    /**
     * When the pattern was last updated.
     */
    @Builder.Default
    Instant updatedAt = Instant.now();

    /**
     * When the pattern was last matched.
     */
    Instant lastMatchedAt;

    /**
     * How long the pattern remains valid without new observations.
     */
    @Builder.Default
    Duration validityWindow = Duration.ofDays(30);

    /**
     * Whether the pattern has expired.
     */
    @Builder.Default
    boolean expired = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // Metadata
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Tags for categorization and search.
     */
    @Builder.Default
    List<String> tags = List.of();

    /**
     * The source of this pattern (learning algorithm, user-defined, etc.).
     */
    @Builder.Default
    String source = "learning";

    /**
     * Tenant ID if pattern is tenant-specific.
     */
    String tenantId;

    /**
     * Additional metadata.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    // ═══════════════════════════════════════════════════════════════════════════
    // Computed Properties
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Calculates precision (true positives / predicted positives).
     *
     * @return precision score
     */
    public float getPrecision() {
        long predicted = matchSuccessCount + falsePositiveCount;
        return predicted > 0 ? (float) matchSuccessCount / predicted : 0.0f;
    }

    /**
     * Calculates recall (true positives / actual positives).
     *
     * @return recall score
     */
    public float getRecall() {
        long actual = matchSuccessCount + falseNegativeCount;
        return actual > 0 ? (float) matchSuccessCount / actual : 0.0f;
    }

    /**
     * Calculates F1 score (harmonic mean of precision and recall).
     *
     * @return F1 score
     */
    public float getF1Score() {
        float precision = getPrecision();
        float recall = getRecall();
        return (precision + recall) > 0
                ? 2 * (precision * recall) / (precision + recall)
                : 0.0f;
    }

    /**
     * Checks if the pattern is ready for production use.
     *
     * @return true if pattern meets quality thresholds
     */
    public boolean isProductionReady() {
        return status == PatternStatus.ACTIVE
                && confidence >= 0.7f
                && observationCount >= 100
                && getPrecision() >= 0.8f;
    }

    /**
     * Checks if the pattern needs revalidation.
     *
     * @return true if revalidation needed
     */
    public boolean needsRevalidation() {
        if (lastMatchedAt == null) return true;
        return Instant.now().isAfter(lastMatchedAt.plus(validityWindow));
    }

    /**
     * Records a successful match.
     *
     * @return updated pattern with incremented success count
     */
    public PatternRecord recordMatch() {
        return this.toBuilder()
                .matchSuccessCount(matchSuccessCount + 1)
                .observationCount(observationCount + 1)
                .lastMatchedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Records a false positive.
     *
     * @return updated pattern
     */
    public PatternRecord recordFalsePositive() {
        return this.toBuilder()
                .falsePositiveCount(falsePositiveCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Records a false negative.
     *
     * @return updated pattern
     */
    public PatternRecord recordFalseNegative() {
        return this.toBuilder()
                .falseNegativeCount(falseNegativeCount + 1)
                .updatedAt(Instant.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * A condition for pattern applicability.
     */
    @Value
    @Builder
    public static class PatternCondition {
        /**
         * Field or expression to evaluate.
         */
        String field;

        /**
         * Comparison operator.
         */
        String operator;

        /**
         * Value to compare against.
         */
        Object value;

        /**
         * Whether this condition is required.
         */
        @Builder.Default
        boolean required = true;

        /**
         * Weight of this condition in matching.
         */
        @Builder.Default
        float weight = 1.0f;
    }

    /**
     * Pattern lifecycle status.
     */
    public enum PatternStatus {
        /**
         * Pattern is being learned, not yet validated.
         */
        DRAFT,

        /**
         * Pattern is under validation.
         */
        VALIDATING,

        /**
         * Pattern is active and being used.
         */
        ACTIVE,

        /**
         * Pattern is deprecated but still available.
         */
        DEPRECATED,

        /**
         * Pattern is archived and not active.
         */
        ARCHIVED,

        /**
         * Pattern failed validation.
         */
        REJECTED
    }
}
