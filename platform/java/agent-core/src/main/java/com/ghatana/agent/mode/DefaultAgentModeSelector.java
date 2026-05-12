/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.release.AgentRelease;
import com.ghatana.agent.runtime.mode.ExecutionMode;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of AgentModeSelector with rule-based decision logic.
 *
 * <p><b>Deprecated:</b> This implementation is superseded by {@link com.ghatana.agent.runtime.mode.MasteryAwareModeSelector}
 * which provides better integration with the mastery registry and version context.
 * This class is retained for backward compatibility but should not be used in new code.
 *
 * <p>Decision rules:
 * <ul>
 *   <li>Mastered + fresh + version match + low risk → DETERMINISTIC</li>
 *   <li>Competent + version match + medium uncertainty → BOUNDED_PROBABILISTIC</li>
 *   <li>Unknown version/tool/library → FAST_LEARNING</li>
 *   <li>Legacy version + no migration requested → MAINTENANCE_ONLY</li>
 *   <li>Irreversible side effect / high-risk action → HUMAN_GATED</li>
 *   <li>Contradiction or stale knowledge → VERIFICATION_FIRST</li>
 *   <li>Obsolete/unsafe skill → BLOCKED</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of AgentModeSelector (deprecated)
 * @doc.layer agent-core
 * @doc.pattern Service
 * @deprecated Use {@link com.ghatana.agent.runtime.mode.MasteryAwareModeSelector} instead
 */
@Deprecated
public final class DefaultAgentModeSelector implements AgentModeSelector {

    private final TaskClassifier taskClassifier;

    public DefaultAgentModeSelector(@NotNull TaskClassifier taskClassifier) {
        this.taskClassifier = taskClassifier;
    }

    @Override
    @NotNull
    public Promise<ModeDecision> decide(
            @NotNull AgentDefinition definition,
            @NotNull AgentRelease release,
            @NotNull AgentContext context,
            @NotNull EnvironmentFingerprint env,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull Object input
    ) {
        String taskDescription = input.toString();
        TaskClass taskClass = taskClassifier.classify(taskDescription, mastery, env);
        ExecutionMode mode = selectMode(taskClass, mastery, env, taskDescription);
        String reasoning = generateReasoning(taskClass, mode, mastery, env);

        return Promise.of(ModeDecision.of(taskClass, mode, reasoning));
    }

    @NotNull
    private ExecutionMode selectMode(
            @NotNull TaskClass taskClass,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull EnvironmentFingerprint env,
            @NotNull String taskDescription
    ) {
        // Check for obsolete/unsafe skills first
        if (mastery.isPresent()) {
            MasteryItem item = mastery.get();
            if (item.state() == MasteryState.OBSOLETE || item.state() == MasteryState.QUARANTINED) {
                return ExecutionMode.BLOCKED;
            }
            if (!item.isFresh(env.observedAt())) {
                return ExecutionMode.VERIFICATION_FIRST;
            }
        }

        // High-risk tasks require human gating
        if (taskClass == TaskClass.HIGH_RISK_TASK) {
            return ExecutionMode.HUMAN_GATED;
        }

        // Maintenance tasks use maintenance-only mode
        if (taskClass == TaskClass.MAINTENANCE_TASK) {
            return ExecutionMode.MAINTENANCE_ONLY;
        }

        // Unknown tasks use fast-learning mode
        if (taskClass == TaskClass.UNKNOWN_TASK) {
            return ExecutionMode.EXPLORATORY_FAST_LEARNING;
        }

        // Exploration tasks use fast-learning mode
        if (taskClass == TaskClass.EXPLORATION_TASK) {
            return ExecutionMode.EXPLORATORY_FAST_LEARNING;
        }

        // Migration tasks use verification-first mode
        if (taskClass == TaskClass.MIGRATION_TASK) {
            return ExecutionMode.VERIFICATION_FIRST;
        }

        // Known mastered tasks use deterministic mode
        if (taskClass == TaskClass.KNOWN_TASK && mastery.isPresent()) {
            MasteryItem item = mastery.get();
            if (item.state() == MasteryState.MASTERED && item.isFresh(env.observedAt())) {
                return ExecutionMode.DETERMINISTIC_EXECUTION;
            }
        }

        // Known variations use bounded probabilistic mode
        if (taskClass == TaskClass.KNOWN_VARIATION && mastery.isPresent()) {
            MasteryItem item = mastery.get();
            if (item.state() == MasteryState.COMPETENT || item.state() == MasteryState.MASTERED) {
                return ExecutionMode.BOUNDED_PROBABILISTIC_REASONING;
            }
        }

        // Default to verification-first for uncertain cases
        return ExecutionMode.VERIFICATION_FIRST;
    }

    @NotNull
    private String generateReasoning(
            @NotNull TaskClass taskClass,
            @NotNull ExecutionMode mode,
            @NotNull Optional<MasteryItem> mastery,
            @NotNull EnvironmentFingerprint env
    ) {
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("Task classified as ").append(taskClass).append(". ");

        if (mastery.isPresent()) {
            MasteryItem item = mastery.get();
            reasoning.append("Mastery state: ").append(item.state())
                    .append(", execution score: ").append(String.format("%.2f", item.score().executionScore())).append(". ");
        } else {
            reasoning.append("No mastery found. ");
        }

        reasoning.append("Selected mode: ").append(mode).append(".");
        return reasoning.toString();
    }
}
