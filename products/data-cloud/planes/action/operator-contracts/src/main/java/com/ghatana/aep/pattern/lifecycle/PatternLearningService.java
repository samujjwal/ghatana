package com.ghatana.aep.pattern.lifecycle;

import com.ghatana.aep.pattern.spec.LearningFeedback;
import com.ghatana.aep.pattern.spec.RecommendationCandidate;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Service for ingesting learning feedback and generating pattern recommendations.
 *
 * <p>WS2: Learning feedback service that:
 * <ul>
 *   <li>Ingests feedback from pattern execution</li>
 *   <li>Generates recommendations for pattern improvement</li>
 *   <li>Does NOT mutate production detectors directly</li>
 *   <li>Provides recommendations for human review</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Ingests learning feedback and generates pattern recommendations
 * @doc.layer product
 * @doc.pattern Service
 */
public interface PatternLearningService {

    /**
     * Ingest learning feedback from pattern execution.
     *
     * <p>WS2: Stores feedback for analysis and recommendation generation.
     * Does not directly mutate production detectors.
     *
     * @param tenantId tenant identifier
     * @param feedback learning feedback from pattern execution
     * @return promise that completes when feedback is ingested
     */
    Promise<Void> ingestFeedback(String tenantId, LearningFeedback feedback);

    /**
     * Generate recommendations based on accumulated feedback.
     *
     * <p>WS2: Analyzes feedback to produce candidate recommendations
     * for pattern improvement. Recommendations require human review
     * before application.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return list of recommendation candidates
     */
    Promise<List<RecommendationCandidate>> generateRecommendations(String tenantId, String patternId);

    /**
     * Get feedback history for a pattern.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @param limit maximum number of feedback records to return
     * @return list of learning feedback records
     */
    Promise<List<LearningFeedback>> getFeedbackHistory(String tenantId, String patternId, int limit);

    /**
     * Check if a pattern has sufficient feedback for recommendation generation.
     *
     * @param tenantId tenant identifier
     * @param patternId pattern identifier
     * @return true if sufficient feedback exists
     */
    Promise<Boolean> hasSufficientFeedback(String tenantId, String patternId);
}
