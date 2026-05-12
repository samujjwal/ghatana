/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.evaluation.EvaluationResult;
import com.ghatana.agent.learning.LearningDelta;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for determining when a learning delta can be promoted.
 *
 * @doc.type interface
 * @doc.purpose Policy for promotion decisions
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface PromotionPolicy {

    /**
     * Determines if a learning delta can be promoted based on evaluation results.
     *
     * @param delta learning delta to evaluate
     * @param result evaluation result
     * @return true if promotion is allowed
     */
    boolean canPromote(@NotNull LearningDelta delta, @NotNull EvaluationResult result);

    /**
     * Determines the target mastery state after promotion.
     *
     * @param delta learning delta
     * @param result evaluation result
     * @return target mastery state
     */
    @NotNull
    com.ghatana.agent.mastery.MasteryState targetState(
            @NotNull LearningDelta delta,
            @NotNull EvaluationResult result
    );
}
