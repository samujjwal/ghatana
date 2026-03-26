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

/**
 * Enumeration of pattern categories recognized by the brain.
 *
 * <p>Patterns are classified by their nature to enable specialized
 * handling, matching algorithms, and validation strategies.
 *
 * @doc.type enum
 * @doc.purpose Classification of learned patterns
 * @doc.layer core
 * @doc.pattern Enumeration
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public enum PatternType {

    /**
     * Structural patterns describe data shape and schema.
     *
     * <p>Examples: JSON schemas, entity relationships, field co-occurrence.
     */
    STRUCTURAL("Structural", "Data shape and schema patterns"),

    /**
     * Temporal patterns describe time-based regularities.
     *
     * <p>Examples: Daily cycles, weekly seasonality, burst patterns.
     */
    TEMPORAL("Temporal", "Time-based patterns and seasonality"),

    /**
     * Behavioral patterns describe user or system behaviors.
     *
     * <p>Examples: User journeys, API call sequences, interaction flows.
     */
    BEHAVIORAL("Behavioral", "User and system behavior patterns"),

    /**
     * Sequential patterns describe ordered event sequences.
     *
     * <p>Examples: Event chains, workflow stages, state transitions.
     */
    SEQUENTIAL("Sequential", "Ordered event sequence patterns"),

    /**
     * Causal patterns describe cause-effect relationships.
     *
     * <p>Examples: Error cascades, performance impacts, trigger-response.
     */
    CAUSAL("Causal", "Cause-effect relationship patterns"),

    /**
     * Anomaly patterns describe deviations from normal.
     *
     * <p>Examples: Spike patterns, drift signatures, outlier clusters.
     */
    ANOMALY("Anomaly", "Deviation and anomaly patterns"),

    /**
     * Correlation patterns describe co-occurring phenomena.
     *
     * <p>Examples: Metric correlations, event co-occurrence, feature associations.
     */
    CORRELATION("Correlation", "Co-occurrence and correlation patterns"),

    /**
     * Contextual patterns depend on situational factors.
     *
     * <p>Examples: Region-specific, customer-tier patterns, environment-dependent.
     */
    CONTEXTUAL("Contextual", "Context-dependent patterns"),

    /**
     * Aggregate patterns emerge from multiple data points.
     *
     * <p>Examples: Distribution patterns, percentile behaviors, cluster profiles.
     */
    AGGREGATE("Aggregate", "Aggregate and distribution patterns"),

    /**
     * Custom patterns defined by users or specialized learning.
     */
    CUSTOM("Custom", "User-defined or specialized patterns");

    private final String displayName;
    private final String description;

    PatternType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Gets the human-readable display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the pattern type description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Checks if this pattern type requires temporal data.
     *
     * @return true if temporal data is required
     */
    public boolean requiresTemporalData() {
        return this == TEMPORAL || this == SEQUENTIAL || this == CAUSAL;
    }

    /**
     * Checks if this pattern type is derived from multiple records.
     *
     * @return true if aggregate pattern
     */
    public boolean isAggregate() {
        return this == AGGREGATE || this == CORRELATION || this == CONTEXTUAL;
    }
}
