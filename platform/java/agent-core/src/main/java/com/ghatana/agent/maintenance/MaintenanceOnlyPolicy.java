/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.maintenance;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

/**
 * Policy for enforcing maintenance-only behavior for mastery items.
 *
 * <p>Ensures that mastery items in MAINTENANCE_ONLY state are only used for
 * legacy work (maintenance, support, migration, retirement) and not for new work.
 *
 * @doc.type interface
 * @doc.purpose Policy for enforcing maintenance-only behavior
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public interface MaintenanceOnlyPolicy {

    /**
     * Checks if a mastery item can execute a task with the given intent.
     *
     * @param item mastery item to check
     * @param intent task intent
     * @return true if allowed, false otherwise
     */
    boolean canExecute(@NotNull MasteryItem item, @NotNull TaskIntent intent);

    /**
     * Validates that a task intent is compatible with the mastery item's state.
     *
     * @param item mastery item to validate against
     * @param intent task intent to validate
     * @return validation result
     */
    @NotNull
    ValidationResult validate(@NotNull MasteryItem item, @NotNull TaskIntent intent);

    /**
     * Result of validating a task intent against a mastery item.
     *
     * @doc.type record
     * @doc.purpose Result of validating task intent
     * @doc.layer agent-core
     * @doc.pattern Record
     */
    record ValidationResult(
            boolean allowed,
            @NotNull String reason,
            @NotNull TaskIntent intent,
            @NotNull MasteryState currentState
    ) {
        /**
         * Creates an allowed validation result.
         *
         * @param intent task intent
         * @param currentState current mastery state
         * @return allowed validation result
         */
        @NotNull
        public static ValidationResult allowed(@NotNull TaskIntent intent, @NotNull MasteryState currentState) {
            return new ValidationResult(true, "Task intent allowed for current state", intent, currentState);
        }

        /**
         * Creates a denied validation result.
         *
         * @param intent task intent
         * @param currentState current mastery state
         * @param reason denial reason
         * @return denied validation result
         */
        @NotNull
        public static ValidationResult denied(@NotNull TaskIntent intent, @NotNull MasteryState currentState, @NotNull String reason) {
            return new ValidationResult(false, reason, intent, currentState);
        }
    }
}
