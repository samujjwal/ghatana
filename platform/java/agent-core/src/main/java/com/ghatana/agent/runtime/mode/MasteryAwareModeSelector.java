/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryDecision;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Mastery-aware mode selector that uses mastery registry to determine execution mode.
 *
 * @doc.type class
 * @doc.purpose Mastery-aware mode selector
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class MasteryAwareModeSelector {

    private final MasteryRegistry masteryRegistry;
    private final TaskClassifier taskClassifier;
    private final ModeSelectionPolicy selectionPolicy;

    /**
     * Creates a mastery-aware mode selector.
     *
     * @param masteryRegistry mastery registry
     * @param taskClassifier task classifier
     * @param selectionPolicy mode selection policy
     */
    public MasteryAwareModeSelector(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull TaskClassifier taskClassifier,
            @NotNull ModeSelectionPolicy selectionPolicy
    ) {
        this.masteryRegistry = masteryRegistry;
        this.taskClassifier = taskClassifier;
        this.selectionPolicy = selectionPolicy;
    }

    /**
     * Selects the execution mode for a task based on mastery and task classification.
     *
     * @param skillId skill identifier
     * @param agentId agent identifier
     * @param taskDescription task description
     * @param context additional context
     * @param versionContext version context
     * @return promise of mode selection result
     */
    @NotNull
    public Promise<ModeSelectionResult> selectMode(
            @NotNull String skillId,
            @NotNull String agentId,
            @NotNull String taskDescription,
            @NotNull String context,
            @NotNull VersionContext versionContext
    ) {
        // Query mastery registry
        MasteryQuery query = MasteryQuery.bySkill(skillId)
                .withAgentId(agentId)
                .withTenantId(versionContext.dependencies().getOrDefault("tenant", "default"));

        return masteryRegistry.decide(query)
                .then(masteryDecision -> {
                    // Classify task
                    return taskClassifier.classify(taskDescription, context)
                            .then(taskClassification -> {
                                // Apply selection policy
                                return selectionPolicy.selectMode(masteryDecision, taskClassification, versionContext)
                                        .map(policyResult -> new ModeSelectionResult(policyResult.mode(), policyResult.reasoning(), false, false));
                            });
                });
    }

    /**
     * Selects the execution mode with custom query parameters.
     *
     * @param query mastery query
     * @param taskClassification task classification
     * @param versionContext version context
     * @return promise of mode selection result
     */
    @NotNull
    public Promise<ModeSelectionResult> selectMode(
            @NotNull MasteryQuery query,
            @NotNull TaskClassification taskClassification,
            @NotNull VersionContext versionContext
    ) {
        return masteryRegistry.decide(query)
                .then(masteryDecision -> selectionPolicy.selectMode(masteryDecision, taskClassification, versionContext)
                        .map(policyResult -> new ModeSelectionResult(policyResult.mode(), policyResult.reasoning(), false, false)));
    }
}
