/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.mastery.MasteryDecision;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for selecting execution mode based on mastery, task classification, and context.
 *
 * @doc.type interface
 * @doc.purpose Policy for selecting execution mode
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface ModeSelectionPolicy {

    /**
     * Selects the execution mode based on mastery decision, task classification, and version context.
     *
     * @param masteryDecision mastery decision from registry
     * @param taskClassification task classification
     * @param versionContext version context
     * @return promise of mode selection result
     */
    @NotNull
    Promise<ModeSelectionResult> selectMode(
            @NotNull MasteryDecision masteryDecision,
            @NotNull TaskClassification taskClassification,
            @NotNull com.ghatana.agent.context.version.VersionContext versionContext
    );

    /**
     * Mode selection result with the chosen execution strategy, supervision mode, and reasoning.
     * See {@link ModeSelectionResult}.
     */
}
