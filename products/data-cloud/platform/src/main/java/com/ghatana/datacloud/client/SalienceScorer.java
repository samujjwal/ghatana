package com.ghatana.datacloud.attention;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Service Provider Interface for salience scoring.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for computing salience scores for data records.
 * Implementations can use rule-based, statistical, or ML-based approaches.
 *
 * <p><b>Scoring Factors</b><br>
 * Implementations should consider:
 * <ul>
 *   <li><b>Novelty</b> - How unusual/rare is this item</li>
 *   <li><b>Deviation</b> - How far from statistical baseline</li>
 *   <li><b>Relevance</b> - How aligned with organizational goals</li>
 *   <li><b>Urgency</b> - Time sensitivity and decay</li>
 *   <li><b>Context</b> - Situational factors</li>
 * </ul>
 *
 * <p><b>Implementation Requirements</b><br>
 * <ul>
 *   <li>Must be thread-safe</li>
 *   <li>Must return scores in [0.0, 1.0] range</li>
 *   <li>Must be non-blocking (use Promise)</li>
 *   <li>Should emit learning signals for feedback</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SalienceScorer scorer = new MLBasedSalienceScorer(modelRegistry, featureStore);
 * 
 * SalienceScore score = scorer.score(eventRecord, ScoringContext.builder()
 *     .tenantId("tenant-123")
 *     .includeAnomalyDetection(true)
 *     .build())
 *     .getResult();
 * }</pre>
 *
 * @see SalienceScore
 * @see AttentionManager
 * @doc.type interface
 * @doc.purpose Salience scoring SPI
 * @doc.layer core
 * @doc.pattern Strategy
 */
public interface SalienceScorer {

    /**
     * Compute salience score for a single record.
     *
     * @param record  The record to score
     * @param context Scoring context with tenant and options
     * @return Promise with computed SalienceScore
     */
    Promise<SalienceScore> score(DataRecord record, ScoringContext context);

    /**
     * Compute salience scores for multiple records (batch).
     *
     * @param records The records to score
     * @param context Scoring context with tenant and options
     * @return Promise with list of SalienceScores in same order as input
     */
    Promise<List<SalienceScore>> scoreBatch(List<DataRecord> records, ScoringContext context);

    /**
     * Get the scorer identifier.
     *
     * @return Unique scorer ID
     */
    String getScorerId();

    /**
     * Get the model version (for ML-based scorers).
     *
     * @return Model version string
     */
    String getModelVersion();

    /**
     * Check if scorer supports a specific scoring feature.
     *
     * @param feature The feature to check
     * @return true if supported
     */
    boolean supportsFeature(ScoringFeature feature);

    /**
     * Update scorer baseline for a tenant (continuous learning).
     *
     * @param tenantId Tenant ID
     * @return Promise completing when baseline is updated
     */
    Promise<Void> updateBaseline(String tenantId);

    /**
     * Scoring context containing tenant and options.
     */
    record ScoringContext(
            String tenantId,
            boolean includeAnomalyDetection,
            boolean includePatternMatching,
            boolean includePrediction,
            Map<String, Object> customFeatures,
            double urgencyBoost,
            List<String> goalIds
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private boolean includeAnomalyDetection = true;
            private boolean includePatternMatching = true;
            private boolean includePrediction = true;
            private Map<String, Object> customFeatures = Map.of();
            private double urgencyBoost = 0.0;
            private List<String> goalIds = List.of();

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder includeAnomalyDetection(boolean include) {
                this.includeAnomalyDetection = include;
                return this;
            }

            public Builder includePatternMatching(boolean include) {
                this.includePatternMatching = include;
                return this;
            }

            public Builder includePrediction(boolean include) {
                this.includePrediction = include;
                return this;
            }

            public Builder customFeatures(Map<String, Object> features) {
                this.customFeatures = features;
                return this;
            }

            public Builder urgencyBoost(double boost) {
                this.urgencyBoost = boost;
                return this;
            }

            public Builder goalIds(List<String> goalIds) {
                this.goalIds = goalIds;
                return this;
            }

            public ScoringContext build() {
                if (tenantId == null || tenantId.isBlank()) {
                    throw new IllegalArgumentException("tenantId is required");
                }
                return new ScoringContext(
                        tenantId,
                        includeAnomalyDetection,
                        includePatternMatching,
                        includePrediction,
                        customFeatures,
                        urgencyBoost,
                        goalIds
                );
            }
        }

        /**
         * Creates a minimal context for a tenant with default options.
         *
         * @param tenantId the tenant ID
         * @return a default scoring context for the tenant
         */
        public static ScoringContext forTenant(String tenantId) {
            return ScoringContext.builder()
                    .tenantId(tenantId)
                    .build();
        }
    }

    /**
     * Scoring features that can be enabled/disabled.
     */
    enum ScoringFeature {
        /**
         * Statistical anomaly detection.
         */
        ANOMALY_DETECTION,

        /**
         * Pattern matching against known patterns.
         */
        PATTERN_MATCHING,

        /**
         * ML-based prediction.
         */
        ML_PREDICTION,

        /**
         * Time-decay for urgency.
         */
        TIME_DECAY,

        /**
         * Context-aware scoring.
         */
        CONTEXT_AWARE,

        /**
         * Goal-relevance scoring.
         */
        GOAL_RELEVANCE,

        /**
         * Continuous baseline learning.
         */
        CONTINUOUS_LEARNING
    }
}
