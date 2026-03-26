package com.ghatana.datacloud.spi.ai;

import io.activej.promise.Promise;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Optional AI capability for plugins that provide recommendations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Enables intelligent recommendations for:
 * <ul>
 * <li>Query optimization suggestions</li>
 * <li>Index creation recommendations</li>
 * <li>Schema improvements</li>
 * <li>Storage tier migrations</li>
 * <li>Configuration tuning</li>
 * </ul>
 *
 * <p>
 * <b>Safety Contract</b><br>
 * Recommendations are <b>advisory only</b>:
 * <ul>
 * <li>Never auto-apply without explicit approval</li>
 * <li>Provide rationale and impact assessment</li>
 * <li>Support feedback loops for learning</li>
 * <li>Log acceptance/rejection as signals</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof RecommendationCapability recommender) {
 *     List<Recommendation> recommendations = recommender.recommend(
 *         RecommendationContext.builder()
 *             .tenantId("tenant-123")
 *             .collectionName("events")
 *             .recommendationType(RecommendationType.INDEX)
 *             .analysisWindow(Duration.ofDays(7))
 *             .build()
 *     ).getResult();
 *
 *     for (Recommendation rec : recommendations) {
 *         if (rec.priority() == Priority.HIGH && rec.confidence() > 0.9) {
 *             // Present to user for approval
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @see PredictionCapability
 * @doc.type interface
 * @doc.purpose AI recommendation capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface RecommendationCapability {

    /**
     * Generates recommendations based on context.
     *
     * @param context Recommendation context
     * @return Promise with list of recommendations
     */
    Promise<List<Recommendation>> recommend(RecommendationContext context);

    /**
     * Records feedback on a recommendation (accepted/rejected/deferred).
     *
     * @param feedback Feedback record
     * @return Promise completing when feedback is recorded
     */
    Promise<Void> recordFeedback(RecommendationFeedback feedback);

    /**
     * Gets the supported recommendation types.
     *
     * @return Promise with list of supported types
     */
    Promise<List<RecommendationType>> getSupportedRecommendationTypes();

    /**
     * Context for generating recommendations.
     */
    @Value
    @Builder
    class RecommendationContext {
        /**
         * Tenant ID.
         */
        String tenantId;

        /**
         * Collection name (optional).
         */
        String collectionName;

        /**
         * Type of recommendations to generate.
         */
        RecommendationType recommendationType;

        /**
         * Historical window to analyze.
         */
        java.time.Duration analysisWindow;

        /**
         * Additional context data.
         */
        Map<String, Object> metadata;

        /**
         * Correlation ID for tracing.
         */
        String correlationId;

        /**
         * Timestamp of request.
         */
        @Builder.Default
        Instant timestamp = Instant.now();
    }

    /**
     * A single recommendation with rationale and impact.
     */
    @Value
    @Builder
    class Recommendation {
        /**
         * Unique recommendation ID.
         */
        String recommendationId;

        /**
         * Type of recommendation.
         */
        RecommendationType type;

        /**
         * Priority level.
         */
        Priority priority;

        /**
         * Confidence score (0.0 to 1.0).
         */
        double confidence;

        /**
         * Human-readable title.
         */
        String title;

        /**
         * Detailed description.
         */
        String description;

        /**
         * Rationale/reasoning behind recommendation.
         */
        String rationale;

        /**
         * Expected impact if applied.
         */
        Impact expectedImpact;

        /**
         * Actionable steps to apply.
         */
        List<String> actionSteps;

        /**
         * SQL/code to execute (if applicable).
         */
        String executableCode;

        /**
         * Supporting evidence (query stats, metrics, etc.).
         */
        Map<String, Object> evidence;

        /**
         * Timestamp of recommendation.
         */
        @Builder.Default
        Instant timestamp = Instant.now();

        /**
         * Expiration time (recommendations can become stale).
         */
        Instant expiresAt;
    }

    /**
     * Feedback on a recommendation.
     */
    @Value
    @Builder
    class RecommendationFeedback {
        /**
         * Recommendation ID being reviewed.
         */
        String recommendationId;

        /**
         * Feedback action taken.
         */
        FeedbackAction action;

        /**
         * Optional comment from user.
         */
        String comment;

        /**
         * Actual impact observed (if applied).
         */
        Impact actualImpact;

        /**
         * User who provided feedback.
         */
        String userId;

        /**
         * Timestamp of feedback.
         */
        @Builder.Default
        Instant timestamp = Instant.now();
    }

    /**
     * Types of recommendations.
     */
    enum RecommendationType {
        /**
         * Index creation recommendations.
         */
        INDEX,

        /**
         * Query optimization suggestions.
         */
        QUERY_OPTIMIZATION,

        /**
         * Schema improvements.
         */
        SCHEMA,

        /**
         * Storage tier migrations.
         */
        STORAGE_TIER,

        /**
         * Partition strategy changes.
         */
        PARTITIONING,

        /**
         * Configuration tuning.
         */
        CONFIGURATION,

        /**
         * Data retention policy adjustments.
         */
        RETENTION,

        /**
         * Custom plugin-specific recommendation.
         */
        CUSTOM
    }

    /**
     * Priority levels for recommendations.
     */
    enum Priority {
        /**
         * Critical - immediate action recommended.
         */
        CRITICAL,

        /**
         * High - should be addressed soon.
         */
        HIGH,

        /**
         * Medium - consider implementing.
         */
        MEDIUM,

        /**
         * Low - nice to have.
         */
        LOW,

        /**
         * Info - informational only.
         */
        INFO
    }

    /**
     * Expected or actual impact of recommendation.
     */
    @Value
    @Builder
    class Impact {
        /**
         * Performance improvement (e.g., "50% faster queries").
         */
        String performanceImprovement;

        /**
         * Cost reduction (e.g., "20% lower storage costs").
         */
        String costReduction;

        /**
         * Resource savings (e.g., "30% less CPU usage").
         */
        String resourceSavings;

        /**
         * Estimated effort to implement.
         */
        String effort;

        /**
         * Risk level.
         */
        RiskLevel risk;

        /**
         * Numeric metrics for programmatic comparison.
         */
        Map<String, Double> metrics;
    }

    /**
     * Risk level of applying recommendation.
     */
    enum RiskLevel {
        /**
         * Low risk - safe to apply.
         */
        LOW,

        /**
         * Medium risk - test first.
         */
        MEDIUM,

        /**
         * High risk - requires careful planning.
         */
        HIGH
    }

    /**
     * Feedback actions.
     */
    enum FeedbackAction {
        /**
         * Recommendation accepted and applied.
         */
        ACCEPTED,

        /**
         * Recommendation rejected.
         */
        REJECTED,

        /**
         * Recommendation deferred for later.
         */
        DEFERRED,

        /**
         * Recommendation partially applied.
         */
        PARTIALLY_APPLIED
    }
}

