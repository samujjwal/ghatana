package com.ghatana.datacloud.ai;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DC-P1-011: Heuristic fallback service for AI operations.
 *
 * <p>Provides rule-based fallback logic when AI quality metrics are below threshold.
 * This ensures system resilience and graceful degradation when AI services are
 * unavailable or producing low-quality results.
 *
 * <p>Quality metrics thresholds:
 * <ul>
 *   <li>Accuracy: Minimum 0.7 for acceptable results</li>
 *   <li>Confidence: Minimum 0.6 for acceptable results</li>
 *   <li>Latency: Maximum 5000ms for acceptable results</li>
 *   <li>Success rate: Minimum 0.8 for acceptable results</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Heuristic fallback service for AI operations
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface HeuristicFallbackService {

    /**
     * Quality thresholds for heuristic fallback.
     */
    record QualityThresholds(
        double minAccuracy,
        double minConfidence,
        double maxLatencyMs,
        double minSuccessRate
    ) {
        public static QualityThresholds defaults() {
            return new QualityThresholds(0.7, 0.6, 5000.0, 0.8);
        }
    }

    /**
     * Fallback decision result.
     */
    record FallbackDecision(
        boolean shouldFallback,
        String reason,
        double qualityScore,
        AIEvaluationMetrics metrics
    ) {}

    /**
     * Evaluate whether to use heuristic fallback based on quality metrics.
     *
     * @param metrics current AI evaluation metrics
     * @param thresholds quality thresholds for fallback decision
     * @return fallback decision
     */
    Promise<FallbackDecision> evaluateFallback(
        AIEvaluationMetrics metrics,
        QualityThresholds thresholds
    );

    /**
     * Generate heuristic fallback SQL for natural language query.
     *
     * <p>Uses rule-based pattern matching when AI generation fails or produces
     * low-quality results.
     *
     * @param description natural language description
     * @param schema database schema
     * @return promise of generated SQL with heuristic fallback
     */
    Promise<AIAssistService.GeneratedSQL> generateSQLHeuristic(
        String description,
        AIAssistService.DatabaseSchema schema
    );

    /**
     * Generate heuristic fallback explanation for query results.
     *
     * <p>Uses template-based explanation when AI explanation fails or produces
     * low-quality results.
     *
     * @param query original query
     * @param results query results
     * @param context explanation context
     * @return promise of explanation with heuristic fallback
     */
    Promise<AIAssistService.Explanation> explainResultsHeuristic(
        String query,
        List<Map<String, Object>> results,
        AIAssistService.QueryContext context
    );

    /**
     * Generate heuristic fallback query suggestions.
     *
     * <p>Uses pattern-based suggestions when AI suggestion fails or produces
     * low-quality results.
     *
     * @param context query context
     * @param limit maximum suggestions
     * @return promise of suggestions with heuristic fallback
     */
    Promise<List<AIAssistService.QuerySuggestion>> suggestQueriesHeuristic(
        AIAssistService.QueryContext context,
        int limit
    );

    /**
     * Record fallback usage for telemetry and monitoring.
     *
     * @param fallbackType type of fallback used
     * @param originalQuality quality score that triggered fallback
     * @param context operation context
     * @return promise completing when recorded
     */
    Promise<Void> recordFallbackUsage(
        String fallbackType,
        double originalQuality,
        Map<String, Object> context
    );

    /**
     * Get fallback usage statistics.
     *
     * @param timeRange time range for statistics
     * @return promise of fallback statistics
     */
    Promise<FallbackStatistics> getFallbackStatistics(TimeRange timeRange);

    /**
     * Fallback usage statistics.
     */
    record FallbackStatistics(
        int totalFallbacks,
        int sqlFallbacks,
        int explanationFallbacks,
        int suggestionFallbacks,
        double averageQualityScore,
        Map<String, Integer> fallbackTypeCounts,
        java.time.Instant timestamp
    ) {}

    /**
     * Time range for statistics queries.
     */
    enum TimeRange {
        LAST_HOUR,
        LAST_DAY,
        LAST_WEEK,
        LAST_MONTH,
        ALL_TIME
    }
}
