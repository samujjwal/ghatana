/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Engine for promoting learning deltas to mastery after successful evaluation.
 *
 * @doc.type interface
 * @doc.purpose Engine for promoting learning deltas to mastery
 * @doc.layer agent-core
 * @doc.pattern Engine
 */
public interface PromotionEngine {

    /**
     * Evaluates a learning delta to determine if it is ready for promotion.
     *
     * @param delta learning delta to evaluate
     * @return promise of evaluation result
     */
    @NotNull
    Promise<EvaluationResult> evaluate(@NotNull LearningDelta delta);

    /**
     * Promotes a learning delta to mastery after successful evaluation.
     *
     * @param delta learning delta to promote
     * @param result evaluation result
     * @param tenantId tenant identifier for tenant-scoped promotion
     * @return promise of promotion result
     */
    @NotNull
    Promise<PromotionResult> promote(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result,
            @NotNull String tenantId
    );

    /**
     * Sets the promotion policy.
     *
     * @param policy promotion policy
     */
    void setPolicy(@NotNull PromotionPolicy policy);
}
