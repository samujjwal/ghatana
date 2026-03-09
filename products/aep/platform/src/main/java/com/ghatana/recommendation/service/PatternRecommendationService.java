package com.ghatana.recommendation.service;

import com.ghatana.pattern.api.model.PatternStatus;
import com.ghatana.recommendation.config.RecommendationConfig;
import com.ghatana.recommendation.domain.PatternRecommendation;
import com.ghatana.recommendation.domain.PatternPromotionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Day 32: Pattern Recommendation Service
 * 
 * Monitors pattern scores and makes intelligent promotion/demotion decisions
 * based on configurable thresholds with hysteresis to prevent oscillation.
 
 *
 * @doc.type class
 * @doc.purpose Pattern recommendation service
 * @doc.layer core
 * @doc.pattern Service
*/
public class PatternRecommendationService {
    private static final Logger logger = LoggerFactory.getLogger(PatternRecommendationService.class);
    
    private final RecommendationConfig config;
    private final Map<String, PatternStatus> patternStatuses;
    private final Map<String, Instant> lastRecommendationTimes;
    
    public PatternRecommendationService(RecommendationConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.patternStatuses = new ConcurrentHashMap<>();
        this.lastRecommendationTimes = new ConcurrentHashMap<>();
        
        logger.info("Pattern Recommendation Service initialized with config: {}", config);
    }

    /**
     * Evaluates a pattern score and generates recommendations.
     */
    public Optional<PatternRecommendation> evaluatePattern(String patternId, String patternType, 
                                                          double currentScore, int observationCount) {
        logger.debug("Evaluating pattern {} (type: {}) with score: {}, observations: {}", 
                    patternId, patternType, currentScore, observationCount);
        
        // Check if we have enough observations
        if (observationCount < config.getMinimumObservations()) {
            logger.trace("Pattern {} has insufficient observations: {} < {}", 
                        patternId, observationCount, config.getMinimumObservations());
            return Optional.empty();
        }
        
        // Check cooldown period
        Instant lastRecommendation = lastRecommendationTimes.get(patternId);
        if (lastRecommendation != null && 
            Instant.now().isBefore(lastRecommendation.plus(config.getCooldownPeriod()))) {
            logger.trace("Pattern {} is in cooldown period", patternId);
            return Optional.empty();
        }
        
        // Get current pattern status and ensure pattern is tracked
        PatternStatus currentStatus = patternStatuses.getOrDefault(patternId, PatternStatus.CANDIDATE);
        // Track the pattern even if no recommendation is made
        if (!patternStatuses.containsKey(patternId)) {
            patternStatuses.put(patternId, PatternStatus.CANDIDATE);
        }
        
        // Evaluate recommendation based on current status and score
        PatternRecommendation recommendation = evaluateRecommendation(
            patternId, patternType, currentScore, currentStatus
        );
        
        if (recommendation != null) {
            // Update pattern status and recommendation time
            updatePatternStatus(patternId, recommendation);
            lastRecommendationTimes.put(patternId, recommendation.getTimestamp());
            
            logger.info("Generated recommendation for pattern {}: {}", patternId, recommendation);
        }
        
        return Optional.ofNullable(recommendation);
    }

    /**
     * Evaluates recommendation based on current status and score.
     */
    private PatternRecommendation evaluateRecommendation(String patternId, String patternType, 
                                                        double currentScore, PatternStatus currentStatus) {
        Instant timestamp = Instant.now();
        Map<String, Object> context = Map.of(
            "currentStatus", currentStatus,
            "evaluationTime", timestamp,
            "service", "PatternRecommendationService"
        );
        
        switch (currentStatus) {
            case CANDIDATE:
                if (config.shouldPromote(currentScore, patternType)) {
                    return new PatternRecommendation(
                        patternId, patternType, PatternRecommendation.RecommendationDecision.PROMOTE,
                        currentScore, config.getPromotionThreshold(patternType),
                        "Score exceeded promotion threshold with hysteresis margin",
                        timestamp, context
                    );
                }
                break;
                
            case ACTIVE:
                if (config.shouldDemote(currentScore, patternType)) {
                    return new PatternRecommendation(
                        patternId, patternType, PatternRecommendation.RecommendationDecision.DEMOTE,
                        currentScore, config.getDemotionThreshold(patternType),
                        "Score fell below demotion threshold with hysteresis margin",
                        timestamp, context
                    );
                }
                break;
                
            case SUSPENDED:
                if (config.shouldPromote(currentScore, patternType)) {
                    return new PatternRecommendation(
                        patternId, patternType, PatternRecommendation.RecommendationDecision.PROMOTE,
                        currentScore, config.getPromotionThreshold(patternType),
                        "Suspended pattern score recovered above promotion threshold",
                        timestamp, context
                    );
                }
                break;
        }
        
        // No recommendation needed - pattern is performing within acceptable range
        return null;
    }

    /**
     * Updates the pattern status based on recommendation.
     */
    private void updatePatternStatus(String patternId, PatternRecommendation recommendation) {
        PatternStatus newStatus = switch (recommendation.getRecommendationDecision()) {
            case PROMOTE -> PatternStatus.ACTIVE;
            case DEMOTE -> PatternStatus.SUSPENDED;
            case MAINTAIN -> patternStatuses.getOrDefault(patternId, PatternStatus.CANDIDATE);
            case EVALUATE -> PatternStatus.CANDIDATE;
        };
        
        PatternStatus oldStatus = patternStatuses.put(patternId, newStatus);
        logger.debug("Updated pattern {} status: {} -> {}", patternId, oldStatus, newStatus);
    }

    /**
     * Creates a promotion event from a recommendation.
     */
    public PatternPromotionEvent createPromotionEvent(PatternRecommendation recommendation) {
        String eventId = "promotion-" + recommendation.getPatternId() + "-" + 
                        recommendation.getTimestamp().toEpochMilli();
        
        PatternPromotionEvent.PromotionAction action = switch (recommendation.getRecommendationDecision()) {
            case PROMOTE -> PatternPromotionEvent.PromotionAction.PROMOTED;
            case DEMOTE -> PatternPromotionEvent.PromotionAction.DEMOTED;
            default -> null;
        };
        
        if (action == null) {
            return null;
        }
        
        Map<String, Object> metadata = new HashMap<>(recommendation.getContext());
        metadata.put("recommendationService", "PatternRecommendationService");
        metadata.put("scoreMargin", recommendation.getScoreMargin());
        
        return new PatternPromotionEvent(
            eventId,
            recommendation.getPatternId(),
            recommendation.getPatternType(),
            action,
            recommendation.getCurrentScore(),
            recommendation.getThreshold(),
            recommendation.getReason(),
            recommendation.getTimestamp(),
            metadata
        );
    }

    /**
     * Gets the current status of all patterns.
     */
    public Map<String, PatternStatus> getPatternStatuses() {
        return new HashMap<>(patternStatuses);
    }

    /**
     * Gets the current status of a specific pattern.
     */
    public PatternStatus getPatternStatus(String patternId) {
        return patternStatuses.getOrDefault(patternId, PatternStatus.CANDIDATE);
    }

    /**
     * Gets service statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Long> statusCounts = new HashMap<>();
        for (PatternStatus status : patternStatuses.values()) {
            statusCounts.merge(status.name(), 1L, Long::sum);
        }
        
        return Map.of(
            "totalPatterns", patternStatuses.size(),
            "statusCounts", statusCounts,
            "totalRecommendations", lastRecommendationTimes.size(),
            "config", config.toString()
        );
    }
}