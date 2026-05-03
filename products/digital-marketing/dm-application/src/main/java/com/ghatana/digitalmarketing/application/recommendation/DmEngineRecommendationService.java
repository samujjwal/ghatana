package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for engine recommendation management.
 *
 * @doc.type interface
 * @doc.purpose Handles creation, review, and lifecycle of engine recommendations (DMOS-F3-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmEngineRecommendationService {

    Promise<DmEngineRecommendation> publish(DmOperationContext ctx, PublishRecommendationCommand command);

    Promise<DmEngineRecommendation> accept(DmOperationContext ctx, String recommendationId);

    Promise<DmEngineRecommendation> reject(DmOperationContext ctx, String recommendationId);

    Promise<Optional<DmEngineRecommendation>> findById(DmOperationContext ctx, String recommendationId);

    Promise<List<DmEngineRecommendation>> listByTenant(DmOperationContext ctx);

    Promise<List<DmEngineRecommendation>> listByStatus(DmOperationContext ctx, DmEngineRecommendationStatus status);

    /**
     * Command to publish an engine recommendation.
     */
    record PublishRecommendationCommand(
        String recommendationType,
        String rationale,
        double confidenceScore,
        List<String> supportingMetricKeys,
        List<String> suggestedActions,
        Instant expiresAt
    ) {
        public PublishRecommendationCommand {
            Objects.requireNonNull(recommendationType, "recommendationType must not be null");
            Objects.requireNonNull(rationale, "rationale must not be null");
            if (recommendationType.isBlank()) throw new IllegalArgumentException("recommendationType must not be blank");
            if (confidenceScore < 0.0 || confidenceScore > 1.0) {
                throw new IllegalArgumentException("confidenceScore must be between 0.0 and 1.0");
            }
        }
    }
}
