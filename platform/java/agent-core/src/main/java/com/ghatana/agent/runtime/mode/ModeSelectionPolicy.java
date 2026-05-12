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
     * Mode selection result with the chosen execution mode and reasoning.
     *
     * @doc.type record
     * @doc.purpose Mode selection result
     * @doc.layer agent-core
     * @doc.pattern Record
     */
    record ModeSelectionResult(
            @NotNull ExecutionMode mode,
            @NotNull String reasoning,
            boolean requiresApproval,
            boolean requiresVerification
    ) {
        /**
         * Creates a mode selection result.
         *
         * @param mode execution mode
         * @param reasoning selection reasoning
         * @return mode selection result
         */
        @NotNull
        public static ModeSelectionResult of(@NotNull ExecutionMode mode, @NotNull String reasoning) {
            return new ModeSelectionResult(mode, reasoning, false, false);
        }

        /**
         * Creates a mode selection result requiring approval.
         *
         * @param mode execution mode
         * @param reasoning selection reasoning
         * @return mode selection result
         */
        @NotNull
        public static ModeSelectionResult requiringApproval(@NotNull ExecutionMode mode, @NotNull String reasoning) {
            return new ModeSelectionResult(mode, reasoning, true, false);
        }

        /**
         * Creates a mode selection result requiring verification.
         *
         * @param mode execution mode
         * @param reasoning selection reasoning
         * @return mode selection result
         */
        @NotNull
        public static ModeSelectionResult requiringVerification(@NotNull ExecutionMode mode, @NotNull String reasoning) {
            return new ModeSelectionResult(mode, reasoning, false, true);
        }
    }
}
