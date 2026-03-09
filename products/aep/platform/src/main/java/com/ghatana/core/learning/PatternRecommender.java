package com.ghatana.aep.learning.mining;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Collaborative filtering recommendation engine for pattern suggestions.
 *
 * <p><b>Purpose</b><br>
 * Recommends patterns based on event characteristics using collaborative
 * filtering. Learns from user feedback to improve recommendations over time.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PatternRecommender recommender = new PatternRecommender();
 *
 * // Get recommendations
 * List<Recommendation> recs = recommender
 *     .getRecommendations(eventTypes, 5)
 *     .getResult();
 *
 * // Record feedback
 * recommender.recordFeedback(patternId, true, 0.95);
 * }</pre>
 *
 * <p><b>Algorithm</b><br>
 * User-based collaborative filtering:
 * <ul>
 *   <li>Similar events → Similar users</li>
 *   <li>Find similar patterns from similar users</li>
 *   <li>Rank by relevance score</li>
 *   <li>Boost with feedback history</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Collaborative filtering for pattern recommendations
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class PatternRecommender {

    private static final Logger logger = LoggerFactory.getLogger(PatternRecommender.class);

    private final Map<String, PatternProfile> patterns;
    private final Map<String, UserFeedback> userFeedback;
    private final Map<String, Integer> patternPopularity;

    /**
     * Create pattern recommender.
     */
    public PatternRecommender() {
        this.patterns = Collections.synchronizedMap(new HashMap<>());
        this.userFeedback = Collections.synchronizedMap(new HashMap<>());
        this.patternPopularity = Collections.synchronizedMap(new HashMap<>());

        logger.debug("Created pattern recommender");
    }

    /**
     * Register pattern for recommendations.
     *
     * @param patternId Pattern identifier
     * @param eventTypes Event types pattern detects
     * @param quality Pattern quality score (0-1)
     */
    public void registerPattern(String patternId, Set<String> eventTypes, double quality) {
        patterns.compute(patternId, (id, existing) -> {
            Set<String> combinedEventTypes = new HashSet<>(eventTypes);
            double combinedQuality = quality;

            if (existing != null) {
                combinedEventTypes.addAll(existing.eventTypes);
                combinedQuality = Math.max(existing.quality, quality);
            }

            logger.debug("Registered pattern: {} with {} event types (quality={})",
                patternId, combinedEventTypes.size(), combinedQuality);
            return new PatternProfile(patternId, combinedEventTypes, combinedQuality);
        });
    }

    /**
     * Get pattern recommendations for event types.
     *
     * @param eventTypes Current event types
     * @param maxRecommendations Maximum recommendations to return
     * @return Promise of recommended patterns
     */
    public Promise<List<Recommendation>> getRecommendations(Set<String> eventTypes, int maxRecommendations) {
        logger.info("Computing recommendations for {} event types", eventTypes.size());

        List<Recommendation> recommendations = new ArrayList<>();

        for (PatternProfile pattern : patterns.values()) {
            // Calculate relevance score
            double relevance = calculateRelevance(eventTypes, pattern);

            if (relevance > 0) {
                // Boost with popularity
                double popularity = getPopularityScore(pattern.patternId);
                double finalScore = (relevance * 0.7) + (popularity * 0.3);

                recommendations.add(new Recommendation(
                    pattern.patternId,
                    pattern.eventTypes,
                    finalScore,
                    relevance
                ));
            }
        }

        // Sort by score and limit
        recommendations.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        List<Recommendation> result = recommendations.stream()
            .limit(maxRecommendations)
            .collect(Collectors.toList());

        logger.info("Generated {} recommendations", result.size());
        return Promise.of(result);
    }

    /**
     * Calculate relevance of pattern to current events.
     *
     * @param eventTypes Current events
     * @param pattern Pattern to score
     * @return Relevance score (0-1)
     */
    private double calculateRelevance(Set<String> eventTypes, PatternProfile pattern) {
        if (eventTypes.isEmpty() || pattern.eventTypes.isEmpty()) {
            return 0;
        }

        // Jaccard similarity
        Set<String> intersection = new HashSet<>(eventTypes);
        intersection.retainAll(pattern.eventTypes);

        Set<String> union = new HashSet<>(eventTypes);
        union.addAll(pattern.eventTypes);

        double jaccard = (double) intersection.size() / union.size();

        // Weight by pattern quality
        return jaccard * pattern.quality;
    }

    /**
     * Get popularity score for pattern.
     *
     * @param patternId Pattern identifier
     * @return Popularity score (0-1)
     */
    private double getPopularityScore(String patternId) {
        int count = patternPopularity.getOrDefault(patternId, 0);
        return Math.min(count / 100.0, 1.0);  // Normalize to 0-1
    }

    /**
     * Record user feedback on recommendation.
     *
     * @param patternId Pattern identifier
     * @param accepted Whether user accepted recommendation
     * @param score Quality score if accepted (0-1)
     */
    public void recordFeedback(String patternId, boolean accepted, double score) {
        UserFeedback feedback = userFeedback.computeIfAbsent(
            patternId,
            k -> new UserFeedback(patternId)
        );

        if (accepted) {
            feedback.recordAcceptance(score);
            patternPopularity.put(patternId, patternPopularity.getOrDefault(patternId, 0) + 1);
            logger.debug("Pattern accepted: {} (score: {})", patternId, score);
        } else {
            feedback.recordRejection();
            logger.debug("Pattern rejected: {}", patternId);
        }
    }

    /**
     * Pattern profile for recommendations.
     */
    private static class PatternProfile {
        private final String patternId;
        private final Set<String> eventTypes;
        private final double quality;

        PatternProfile(String patternId, Set<String> eventTypes, double quality) {
            this.patternId = patternId;
            this.eventTypes = new HashSet<>(eventTypes);
            this.quality = quality;
        }
    }

    /**
     * User feedback tracking.
     */
    private static class UserFeedback {
        private final String patternId;
        private int acceptanceCount = 0;
        private int rejectionCount = 0;
        private double avgScore = 0;

        UserFeedback(String patternId) {
            this.patternId = patternId;
        }

        void recordAcceptance(double score) {
            avgScore = (avgScore * acceptanceCount + score) / (acceptanceCount + 1);
            acceptanceCount++;
        }

        void recordRejection() {
            rejectionCount++;
        }

        double getAcceptanceRate() {
            int total = acceptanceCount + rejectionCount;
            return total == 0 ? 0 : (double) acceptanceCount / total;
        }
    }

    /**
     * Recommendation result.
     */
    public static class Recommendation {
        private final String patternId;
        private final Set<String> eventTypes;
        private final double score;
        private final double relevance;

        Recommendation(String patternId, Set<String> eventTypes, double score, double relevance) {
            this.patternId = patternId;
            this.eventTypes = new HashSet<>(eventTypes);
            this.score = score;
            this.relevance = relevance;
        }

        public String getPatternId() {
            return patternId;
        }

        public Set<String> getEventTypes() {
            return Collections.unmodifiableSet(eventTypes);
        }

        public double getScore() {
            return score;
        }

        public double getRelevance() {
            return relevance;
        }

        @Override
        public String toString() {
            return String.format("%s (score=%.2f, relevance=%.2f, events=%s)",
                    patternId, score, relevance, eventTypes);
        }
    }
}

