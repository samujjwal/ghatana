/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Service for managing learning deltas.
 *
 * @doc.type interface
 * @doc.purpose Service for learning delta management
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public interface LearningDeltaService {

    /**
     * Proposes a new learning delta.
     *
     * @param delta learning delta to propose
     * @param contract learning contract for validation
     * @return promise of proposed delta
     */
    @NotNull
    Promise<LearningDelta> propose(@NotNull LearningDelta delta, @NotNull LearningContract contract);

    /**
     * Evaluates a learning delta.
     *
     * @param deltaId delta identifier
     * @return promise of evaluation result
     */
    @NotNull
    Promise<Boolean> evaluate(@NotNull String deltaId);
}
