/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.runtime;

import com.ghatana.agent.AgentResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Routes an agent result through the autonomy gate, raising a human-approval request
 * when the result's confidence is below the configured threshold.
 *
 * <p>This class is wired into {@link AgentTurnPipeline} after the REASON phase.
 * When approval is required, it returns an {@link AgentResult} with status
 * {@code PENDING_APPROVAL} so the caller can surface the request without blocking.
 *
 * <h2>Behaviour by AutonomyLevel</h2>
 * <pre>
 * AUTONOMOUS  confidence ≥ threshold → pass through immediately
 * AUTONOMOUS  confidence < threshold → raise HumanApprovalRequest
 * SUPERVISED  confidence ≥ threshold → pass through immediately
 * SUPERVISED  confidence < threshold → raise HumanApprovalRequest
 * MANUAL      always                 → raise HumanApprovalRequest
 * </pre>
 *
 * @param <O> the output type of the agent
 *
 * @doc.type class
 * @doc.purpose Routes post-REASON results through autonomy gate
 * @doc.layer framework
 * @doc.pattern Strategy, Filter
 * @doc.gaa.lifecycle reason
 */
public class AutonomyRouter<O> {

    private static final Logger log = LoggerFactory.getLogger(AutonomyRouter.class);

    private final AutonomyLevel autonomyLevel;
    private final double confidenceThreshold;
    private final HumanApprovalHandler<O> approvalHandler;

    /**
     * Functional interface invoked when a human approval request must be raised.
     *
     * <p>Implementations typically persist an approval record and return an
     * {@link AgentResult} with status {@code PENDING_APPROVAL}.
     */
    @FunctionalInterface
    public interface HumanApprovalHandler<O> {
        @NotNull Promise<AgentResult<O>> requestApproval(
                @NotNull AgentResult<O> pendingResult,
                @NotNull String reason);
    }

    /**
     * Creates an {@code AutonomyRouter}.
     *
     * @param autonomyLevel       the agent's autonomy configuration
     * @param confidenceThreshold minimum confidence for autonomous execution [0.0, 1.0]
     * @param approvalHandler     handler invoked when human review is required
     */
    public AutonomyRouter(
            @NotNull AutonomyLevel autonomyLevel,
            double confidenceThreshold,
            @NotNull HumanApprovalHandler<O> approvalHandler) {
        this.autonomyLevel       = Objects.requireNonNull(autonomyLevel);
        this.confidenceThreshold = confidenceThreshold;
        this.approvalHandler     = Objects.requireNonNull(approvalHandler);
    }

    /**
     * Evaluates whether the given result may proceed autonomously.
     *
     * <p>If the confidence is sufficient (or the level is {@code AUTONOMOUS} without
     * constraint), returns the result unchanged. Otherwise delegates to the
     * {@link HumanApprovalHandler}.
     *
     * @param result the agent result from the REASON phase
     * @return {@link Promise} of either the original result or a pending-approval result
     */
    @NotNull
    public Promise<AgentResult<O>> route(@NotNull AgentResult<O> result) {
        Objects.requireNonNull(result);

        if (result.isFailed()) {
            // Failed results always pass through — approval gates only apply to successes.
            return Promise.of(result);
        }

        if (autonomyLevel.canActAutonomously(result.getConfidence(), confidenceThreshold)) {
            log.debug("AutonomyRouter: confidence={:.2f} ≥ threshold={:.2f}, passing through",
                    result.getConfidence(), confidenceThreshold);
            return Promise.of(result);
        }

        String reason = String.format(
                "confidence=%.2f < threshold=%.2f (autonomyLevel=%s)",
                result.getConfidence(), confidenceThreshold, autonomyLevel);
        log.info("AutonomyRouter: raising HumanApprovalRequest — {}", reason);

        return approvalHandler.requestApproval(result, reason);
    }

    /**
     * Returns a no-op router that always passes results through (for testing or
     * {@code AUTONOMOUS} agents with threshold = 0.0).
     *
     * @param <O> output type
     * @return a pass-through router
     */
    @NotNull
    public static <O> AutonomyRouter<O> passThrough() {
        return new AutonomyRouter<>(
                AutonomyLevel.AUTONOMOUS,
                0.0,
                (result, reason) -> Promise.of(result));
    }
}
