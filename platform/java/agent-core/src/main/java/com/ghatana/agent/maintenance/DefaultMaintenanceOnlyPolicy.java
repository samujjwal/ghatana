/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.maintenance;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

/**
 * Default implementation of MaintenanceOnlyPolicy.
 *
 * <p>Enforces that mastery items in MAINTENANCE_ONLY state are only used for
 * legacy work (maintenance, support, migration, retirement) and not for new work.
 *
 * @doc.type class
 * @doc.purpose Default implementation of MaintenanceOnlyPolicy
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultMaintenanceOnlyPolicy implements MaintenanceOnlyPolicy {

    @Override
    public boolean canExecute(@NotNull MasteryItem item, @NotNull TaskIntent intent) {
        return validate(item, intent).allowed();
    }

    @Override
    @NotNull
    public ValidationResult validate(@NotNull MasteryItem item, @NotNull TaskIntent intent) {
        // If the item is not in MAINTENANCE_ONLY state, all intents are allowed
        if (item.state() != MasteryState.MAINTENANCE_ONLY) {
            return ValidationResult.allowed(intent, item.state());
        }

        // MAINTENANCE_ONLY state only allows legacy intents
        if (intent.isLegacy()) {
            return ValidationResult.allowed(intent, item.state());
        }

        // NEW_WORK intent is not allowed for MAINTENANCE_ONLY items
        if (intent == TaskIntent.NEW_WORK) {
            return ValidationResult.denied(
                    intent,
                    item.state(),
                    "Mastery item is in MAINTENANCE_ONLY state and cannot execute new work. Only legacy maintenance, support, migration, or retirement tasks are allowed."
            );
        }

        // UNKNOWN intent is not allowed for MAINTENANCE_ONLY items
        if (intent == TaskIntent.UNKNOWN) {
            return ValidationResult.denied(
                    intent,
                    item.state(),
                    "Task intent is UNKNOWN and cannot be executed on MAINTENANCE_ONLY mastery item. Please classify the task intent as legacy-related."
            );
        }

        // Default: allow
        return ValidationResult.allowed(intent, item.state());
    }
}
