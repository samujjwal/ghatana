package com.ghatana.platform.domain.learning;

import java.util.*;

/**
 * Pattern-based recommender for learning from historical patterns.
 *
 * <p><b>Purpose</b><br>
 * Learns from historical data patterns to provide recommendations.
 * Used by ApprovalRecommender and other learning-based components.
 *
 * <p><b>Features</b><br>
 * - Pattern registration and matching
 * - Collaborative filtering
 * - Recommendation ranking
 * - Feedback learning
 *
 * @doc.type class
 * @doc.purpose Recommends learned patterns based on historical data
 * @doc.layer platform
 * @doc.pattern Service
 */
public class PatternRecommender {

    private final Map<String, Pattern> patterns;
    private final Map<String, Double> patternScores;

    public PatternRecommender() {
        this.patterns = new HashMap<>();
        this.patternScores = new HashMap<>();
    }

    /**
     * Register a pattern with the recommender.
     *
     * @param patternId Unique pattern identifier
     * @param pattern Pattern definition
     */
    public void registerPattern(String patternId, Pattern pattern) {
        patterns.put(patternId, pattern);
        patternScores.put(patternId, 1.0);
    }

    /**
     * Register a pattern with default implementation.
     *
     * @param patternId Unique pattern identifier
     * @param description Pattern description
     */
    public void registerPattern(String patternId, String description) {
        registerPattern(patternId, new Pattern(patternId, description, Collections.emptyMap()));
    }

    /**
     * Find patterns matching the given context.
     *
     * @param context Context data for pattern matching
     * @return List of matching pattern IDs
     */
    public List<String> findPatterns(Map<String, Object> context) {
        return patterns.entrySet().stream()
            .filter(e -> e.getValue().matches(context))
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparing(patternScores::get).reversed())
            .toList();
    }

    /**
     * Get recommendations based on patterns.
     *
     * @param context Context data
     * @param limit Maximum number of recommendations
     * @return List of recommendations
     */
    public List<Recommendation> recommend(Map<String, Object> context, int limit) {
        return findPatterns(context).stream()
            .limit(limit)
            .map(id -> new Recommendation(id, patterns.get(id), patternScores.getOrDefault(id, 0.0)))
            .toList();
    }

    /**
     * Record feedback for a pattern to improve learning.
     *
     * @param patternId Pattern identifier
     * @param success Whether the recommendation was successful
     */
    public void recordFeedback(String patternId, boolean success) {
        double currentScore = patternScores.getOrDefault(patternId, 1.0);
        double newScore = success 
            ? currentScore * 1.1 + 0.1 
            : currentScore * 0.9;
        patternScores.put(patternId, Math.min(10.0, Math.max(0.1, newScore)));
    }

    /**
     * Register a pattern with tags and initial weight.
     *
     * @param patternId Unique pattern identifier
     * @param tags Set of tags for pattern matching
     * @param initialWeight Initial weight/score for the pattern
     */
    public void registerPattern(String patternId, Set<String> tags, double initialWeight) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tags", tags);
        attributes.put("weight", initialWeight);
        registerPattern(patternId, new Pattern(patternId, "Pattern for " + patternId, attributes));
        patternScores.put(patternId, initialWeight);
    }

    /**
     * Record feedback for a pattern with detailed scoring.
     *
     * @param patternId Pattern identifier
     * @param success Whether the recommendation was successful
     * @param score Additional score/weight adjustment
     */
    public void recordFeedback(String patternId, boolean success, double score) {
        recordFeedback(patternId, success);
        double currentScore = patternScores.getOrDefault(patternId, 1.0);
        patternScores.put(patternId, currentScore + score);
    }

    /**
     * Get recommendations based on event types.
     *
     * @param eventTypes Set of event types to match
     * @param limit Maximum number of recommendations
     * @return RecommendationResult containing the recommendations
     */
    public RecommendationResult getRecommendations(Set<String> eventTypes, int limit) {
        Map<String, Object> context = new HashMap<>();
        context.put("eventTypes", eventTypes);
        List<Recommendation> recs = recommend(context, limit);
        return new RecommendationResult(recs);
    }

    /**
     * Result wrapper for getRecommendations.
     */
    public static class RecommendationResult {
        private final List<Recommendation> recommendations;

        public RecommendationResult(List<Recommendation> recommendations) {
            this.recommendations = recommendations;
        }

        public List<Recommendation> getResult() {
            return recommendations;
        }
    }

    /**
     * Pattern definition.
     */
    public static class Pattern {
        private final String id;
        private final String description;
        private final Map<String, Object> attributes;

        public Pattern(String id, String description, Map<String, Object> attributes) {
            this.id = id;
            this.description = description;
            this.attributes = new HashMap<>(attributes);
        }

        public boolean matches(Map<String, Object> context) {
            // Simple matching: check if all pattern attributes exist in context
            return attributes.entrySet().stream()
                .allMatch(e -> Objects.equals(e.getValue(), context.get(e.getKey())));
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
    }

    /**
     * Recommendation result.
     */
    public static class Recommendation {
        private final String patternId;
        private final Pattern pattern;
        private final double score;

        public Recommendation(String patternId, Pattern pattern, double score) {
            this.patternId = patternId;
            this.pattern = pattern;
            this.score = score;
        }

        public String getPatternId() { return patternId; }
        public Pattern getPattern() { return pattern; }
        public double getScore() { return score; }
    }
}
