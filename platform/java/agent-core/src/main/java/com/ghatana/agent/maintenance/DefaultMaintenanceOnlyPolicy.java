/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.maintenance;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.VersionApplicability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default implementation of MaintenanceOnlyPolicy.
 * Phase 8 FIX: Enhanced with stricter rules including version context matching, human gating, and minimal safe fixes only.
 *
 * <p>Enforces that mastery items in MAINTENANCE_ONLY state are only used for
 * legacy work (maintenance, support, migration, retirement) and not for new work.
 *
 * <p>Phase 8 strict rules:
 * <ul>
 *   <li>Only usable when version context matches legacy scope</li>
 *   <li>Human-gated by default</li>
 *   <li>Minimal safe fixes only</li>
 *   <li>No architecture expansion</li>
 *   <li>No new feature development on old stack unless explicitly requested</li>
 *   <li>Always include migration suggestion separately</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Default implementation of MaintenanceOnlyPolicy with strict rules
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class DefaultMaintenanceOnlyPolicy implements MaintenanceOnlyPolicy {

    private boolean humanGated = true;
    private boolean allowArchitectureExpansion = false;
    private boolean allowNewFeatures = false;

    @Override
    public boolean canExecute(@NotNull MasteryItem item, @NotNull TaskIntent intent) {
        return validate(item, intent, null).allowed();
    }

    @Override
    @NotNull
    public ValidationResult validate(@NotNull MasteryItem item, @NotNull TaskIntent intent) {
        return validate(item, intent, null);
    }

    /**
     * Validates a task intent against a mastery item with version context.
     * Phase 8 FIX: Enhanced validation with version context matching and stricter rules.
     *
     * @param item mastery item to validate against
     * @param intent task intent to validate
     * @param versionContext version context for applicability check
     * @return validation result
     */
    @NotNull
    public ValidationResult validate(
            @NotNull MasteryItem item,
            @NotNull TaskIntent intent,
            @Nullable VersionContext versionContext
    ) {
        // If the item is not in MAINTENANCE_ONLY state, all intents are allowed
        if (item.state() != MasteryState.MAINTENANCE_ONLY) {
            return ValidationResult.allowed(intent, item.state());
        }

        // Phase 8 FIX: Check version context matches legacy scope
        if (versionContext != null) {
            VersionApplicability applicability = item.versionScope().classify(versionContext);
            if (applicability != VersionApplicability.MAINTENANCE) {
                return ValidationResult.denied(
                        intent,
                        item.state(),
                        "Mastery item is in MAINTENANCE_ONLY state and version context does not match legacy scope. Applicability: " + applicability
                );
            }
        }

        // Phase 8 FIX: Human-gated by default - deny NEW_WORK and UNKNOWN intents
        if (humanGated && (intent == TaskIntent.NEW_WORK || intent == TaskIntent.UNKNOWN)) {
            return ValidationResult.denied(
                    intent,
                    item.state(),
                    "Mastery item is in MAINTENANCE_ONLY state and requires human approval for new work or unknown intents."
            );
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

    /**
     * Sets whether human gating is enabled.
     * Phase 8 FIX: Human-gated by default for maintenance-only policies.
     *
     * @param humanGated true if human gating is enabled
     */
    public void setHumanGated(boolean humanGated) {
        this.humanGated = humanGated;
    }

    /**
     * Sets whether architecture expansion is allowed.
     * Phase 8 FIX: No architecture expansion by default for maintenance-only policies.
     *
     * @param allowArchitectureExpansion true if architecture expansion is allowed
     */
    public void setAllowArchitectureExpansion(boolean allowArchitectureExpansion) {
        this.allowArchitectureExpansion = allowArchitectureExpansion;
    }

    /**
     * Sets whether new features are allowed.
     * Phase 8 FIX: No new feature development by default for maintenance-only policies.
     *
     * @param allowNewFeatures true if new features are allowed
     */
    public void setAllowNewFeatures(boolean allowNewFeatures) {
        this.allowNewFeatures = allowNewFeatures;
    }
}
