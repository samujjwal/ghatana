package com.ghatana.digitalmarketing.application.recommendation;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.recommendation.DmAgentRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmRecommendationStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Gateway service that converts agent recommendations into executable commands.
 *
 * @doc.type class
 * @doc.purpose Defines the recommendation-to-command lifecycle management contract (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmRecommendationGateway {

    /**
     * Submit a new recommendation from an agent for processing.
     *
     * @param ctx           operation context with tenant and actor
     * @param request       recommendation submission request
     * @return saved recommendation in PENDING status
     */
    Promise<DmAgentRecommendation> submit(DmOperationContext ctx, SubmitRecommendationRequest request);

    /**
     * Accept a recommendation, converting it into a command.
     *
     * @param ctx operation context
     * @param id  recommendation id
     * @return updated recommendation in ACCEPTED status with commandId set
     */
    Promise<DmAgentRecommendation> accept(DmOperationContext ctx, String id);

    /**
     * Reject a recommendation with a given reason.
     *
     * @param ctx    operation context
     * @param id     recommendation id
     * @param reason human-readable rejection reason
     * @return updated recommendation in REJECTED status
     */
    Promise<DmAgentRecommendation> reject(DmOperationContext ctx, String id, String reason);

    /**
     * Expire a pending recommendation that was not processed in time.
     *
     * @param ctx operation context
     * @param id  recommendation id
     * @return updated recommendation in EXPIRED status
     */
    Promise<DmAgentRecommendation> expire(DmOperationContext ctx, String id);

    /**
     * Find recommendation by id (returns empty if not found or cross-tenant).
     */
    Promise<Optional<DmAgentRecommendation>> findById(DmOperationContext ctx, String id);

    /**
     * List pending recommendations for the tenant in the context.
     */
    Promise<List<DmAgentRecommendation>> listPending(DmOperationContext ctx, int limit);

    /**
     * Count recommendations by status for the tenant.
     */
    Promise<Long> countByStatus(DmOperationContext ctx, DmRecommendationStatus status);

    /**
     * Request object for submitting a new agent recommendation.
     *
     * @param agentId           id of the agent making the recommendation
     * @param targetCommandType command type the recommendation maps to
     * @param payload           data payload for the command
     * @param rationale         human-readable explanation from the agent
     */
    record SubmitRecommendationRequest(
        String agentId,
        DmCommandType targetCommandType,
        Map<String, Object> payload,
        String rationale
    ) {
        public SubmitRecommendationRequest {
            Objects.requireNonNull(targetCommandType, "targetCommandType must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId must not be blank");
        }
    }
}
