package com.ghatana.datacloud.application.ai;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.ghatana.platform.observability.util.BlockingExecutors.blockingExecutor;

/**
 * Query recommendations engine.
 *
 * <p><b>Purpose</b><br>
 * Provides intelligent query recommendations based on:
 * - User query patterns
 * - Historical analysis
 * - Performance insights
 * - Common workflows
 *
 * <p><b>Features</b><br>
 * - Pattern-based recommendations
 * - Performance-optimized suggestions
 * - Learning from user feedback
 * - Template library
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QueryRecommender recommender = new QueryRecommender(metricsCollector);
 *
 * // Get recommendations
 * Promise<List<QueryRecommendation>> recs = recommender.getRecommendations(
 *     userContext,
 *     "Show active products"
 * );
 *
 * // Get templates
 * Promise<List<QueryTemplate>> templates = recommender.getQueryTemplates();
 * }</pre>
 *
 * @doc.type service
 * @doc.purpose Query recommendations and templates
 * @doc.layer application
 * @doc.pattern Recommender
 */
public class QueryRecommender {

    private static final Logger logger = LoggerFactory.getLogger(QueryRecommender.class);

    private final MetricsCollector metricsCollector;
    private final Map<String, UserQueryPattern> userPatterns = new HashMap<>();
    private final QueryTemplateLibrary templates = new QueryTemplateLibrary();
    private final FeedbackCollector feedback = new FeedbackCollector();

    /**
     * Creates a new query recommender.
     *
     * @param metricsCollector the metrics collector (required)
     */
    public QueryRecommender(MetricsCollector metricsCollector) {
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector cannot be null");
        initializeTemplates();
    }

    /**
     * Get query recommendations.
     *
     * @param userId the user ID
     * @param queryText the query text
     * @return promise of recommendations
     */
    public Promise<List<QueryRecommendation>> getRecommendations(
            String userId,
            String queryText) {

        return Promise.ofBlocking(blockingExecutor(), () -> {
            logger.info("Generating recommendations for user: {} query: {}", userId, queryText);

            List<QueryRecommendation> recommendations = new ArrayList<>();

            // Get pattern-based recommendations
            recommendations.addAll(getPatternBasedRecommendations(queryText));

            // Get template-based recommendations
            recommendations.addAll(getTemplateBasedRecommendations(queryText));

            // Get user history recommendations
            recommendations.addAll(getUserHistoryRecommendations(userId));

            // Sort by confidence and relevance
            recommendations.sort(Comparator
                    .comparingDouble(QueryRecommendation::confidence).reversed()
                    .thenComparingInt(QueryRecommendation::frequency).reversed()
            );

            // Limit to top 5
            List<QueryRecommendation> topRecommendations = recommendations.stream()
                    .limit(5)
                    .collect(Collectors.toList());

            metricsCollector.incrementCounter("ai.recommendations_generated", "count", String.valueOf(topRecommendations.size()));
            logger.info("Generated {} recommendations", topRecommendations.size());

            return topRecommendations;
        });
    }

    /**
     * Get query templates.
     *
     * @return promise of available templates
     */
    public Promise<List<QueryTemplate>> getQueryTemplates() {
        return Promise.of(templates.getAll());
    }

    /**
     * Record user query for learning.
     *
     * @param userId the user ID
     * @param query the query
     * @param resultCount the number of results
     * @param executionTime the execution time in ms
     */
    public void recordQuery(
            String userId,
            String query,
            int resultCount,
            long executionTime) {

        UserQueryPattern pattern = userPatterns.computeIfAbsent(
                userId,
                k -> new UserQueryPattern(userId)
        );

        pattern.recordQuery(query, resultCount, executionTime);
        metricsCollector.incrementCounter("ai.query_recorded");
        logger.debug("Recorded query for user: {}", userId);
    }

    /**
     * Record user feedback on recommendation.
     *
     * @param recommendationId the recommendation ID
     * @param helpful true if helpful
     */
    public void recordFeedback(String recommendationId, boolean helpful) {
        feedback.recordFeedback(recommendationId, helpful);
        metricsCollector.incrementCounter(
                "ai.feedback_recorded",
                "helpful", String.valueOf(helpful)
        );
        logger.debug("Recorded feedback for recommendation: {} (helpful: {})", recommendationId, helpful);
    }

    /**
     * Get pattern-based recommendations.
     *
     * @param queryText the query text
     * @return list of recommendations
     */
    private List<QueryRecommendation> getPatternBasedRecommendations(String queryText) {
        List<QueryRecommendation> recommendations = new ArrayList<>();

        // Detect query patterns
        if (queryText.toLowerCase().contains("active")) {
            recommendations.add(new QueryRecommendation(
                    UUID.randomUUID().toString(),
                    "Filter by status = 'active'",
                    0.85,
                    "status-filter",
                    100
            ));
        }

        if (queryText.toLowerCase().contains("price")) {
            recommendations.add(new QueryRecommendation(
                    UUID.randomUUID().toString(),
                    "Add price range filter (BETWEEN)",
                    0.80,
                    "price-range",
                    85
            ));
        }

        if (queryText.toLowerCase().contains("recent")) {
            recommendations.add(new QueryRecommendation(
                    UUID.randomUUID().toString(),
                    "Sort by created_at DESC",
                    0.90,
                    "sort-recent",
                    120
            ));
        }

        return recommendations;
    }

    /**
     * Get template-based recommendations.
     *
     * @param queryText the query text
     * @return list of recommendations
     */
    private List<QueryRecommendation> getTemplateBasedRecommendations(String queryText) {
        return templates.findMatching(queryText).stream()
                .map(t -> new QueryRecommendation(
                        UUID.randomUUID().toString(),
                        "Use template: " + t.name,
                        0.75,
                        t.id,
                        t.usageCount
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get user history recommendations.
     *
     * @param userId the user ID
     * @return list of recommendations
     */
    private List<QueryRecommendation> getUserHistoryRecommendations(String userId) {
        UserQueryPattern pattern = userPatterns.get(userId);
        if (pattern == null) {
            return Collections.emptyList();
        }

        return pattern.getCommonPatterns().stream()
                .map(p -> new QueryRecommendation(
                        UUID.randomUUID().toString(),
                        "Similar to your previous: " + p.query,
                        0.70,
                        "history",
                        p.frequency
                ))
                .collect(Collectors.toList());
    }

    /**
     * Initialize query templates.
     */
    private void initializeTemplates() {
        templates.add(new QueryTemplate(
                "active-products",
                "Show Active Products",
                "Filter for active status and sort by name",
                "status = 'active' ORDER BY name ASC",
                0
        ));

        templates.add(new QueryTemplate(
                "recent-sales",
                "Recent Sales",
                "Show recent transactions sorted by date",
                "created_at >= NOW() - INTERVAL 7 DAY ORDER BY created_at DESC",
                0
        ));

        templates.add(new QueryTemplate(
                "high-value",
                "High Value Items",
                "Find items with price above threshold",
                "price BETWEEN 100 AND 1000 ORDER BY price DESC",
                0
        ));

        logger.info("Initialized {} query templates", templates.size());
    }

    /**
     * User query pattern tracking.
     */
    private static class UserQueryPattern {
        private final String userId;
        private final List<QueryRecord> queryHistory = new ArrayList<>();
        private static final int MAX_HISTORY = 100;

        UserQueryPattern(String userId) {
            this.userId = userId;
        }

        void recordQuery(String query, int resultCount, long executionTime) {
            queryHistory.add(new QueryRecord(query, resultCount, executionTime));
            if (queryHistory.size() > MAX_HISTORY) {
                queryHistory.remove(0);
            }
        }

        List<QueryFrequency> getCommonPatterns() {
            return queryHistory.stream()
                    .collect(Collectors.groupingByConcurrent(
                            r -> r.query,
                            Collectors.counting()
                    ))
                    .entrySet().stream()
                    .map(e -> new QueryFrequency(e.getKey(), (int) e.getValue().intValue()))
                    .sorted(Comparator.comparingInt(QueryFrequency::frequency).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
        }

        record QueryRecord(String query, int resultCount, long executionTime) {}
        record QueryFrequency(String query, int frequency) {}
    }

    /**
     * Query recommendation.
     */
    public record QueryRecommendation(
            String id,
            String suggestion,
            double confidence,
            String type,
            int frequency
    ) {}

    /**
     * Query template.
     */
    public record QueryTemplate(
            String id,
            String name,
            String description,
            String queryPattern,
            int usageCount
    ) {}

    /**
     * Query template library.
     */
    private static class QueryTemplateLibrary {
        private final List<QueryTemplate> templates = new ArrayList<>();

        void add(QueryTemplate template) {
            templates.add(template);
        }

        List<QueryTemplate> getAll() {
            return new ArrayList<>(templates);
        }

        List<QueryTemplate> findMatching(String queryText) {
            String lower = queryText.toLowerCase();
            return templates.stream()
                    .filter(t -> t.description.toLowerCase().contains(lower) ||
                            t.name.toLowerCase().contains(lower))
                    .collect(Collectors.toList());
        }

        int size() {
            return templates.size();
        }
    }

    /**
     * Feedback collector.
     */
    private static class FeedbackCollector {
        private final Map<String, List<Boolean>> feedbackMap = new HashMap<>();

        void recordFeedback(String recommendationId, boolean helpful) {
            feedbackMap.computeIfAbsent(recommendationId, k -> new ArrayList<>())
                    .add(helpful);
        }

        double getHelpfulnessRatio(String recommendationId) {
            List<Boolean> feedback = feedbackMap.get(recommendationId);
            if (feedback == null || feedback.isEmpty()) {
                return 0.5; // Default to neutral
            }
            long helpful = feedback.stream().filter(b -> b).count();
            return (double) helpful / feedback.size();
        }
    }
}

