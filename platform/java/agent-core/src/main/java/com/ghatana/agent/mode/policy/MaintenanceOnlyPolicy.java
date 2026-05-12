/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode.policy;

import com.ghatana.agent.mastery.MasteryItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Policy for maintenance-only execution mode.
 *
 * <p>Restricts agent behavior to minimal fixes for legacy code:
 * <ul>
 *   <li>No architectural changes</li>
 *   <li>No refactoring</li>
 *   <li>Only bug fixes and security patches</li>
 *   <li>Version scope must match legacy environment</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Policy for maintenance-only execution mode
 * @doc.layer agent-core
 * @doc.pattern Policy
 */
public final class MaintenanceOnlyPolicy {

    /**
     * Validates if an action is allowed in maintenance-only mode.
     *
     * @param action proposed action
     * @param mastery mastery item
     * @return true if action is allowed
     */
    public boolean isActionAllowed(@NotNull String action, @NotNull MasteryItem mastery) {
        // Only allow actions that are bug fixes or security patches
        String lowerAction = action.toLowerCase();
        return lowerAction.contains("fix") ||
               lowerAction.contains("patch") ||
               lowerAction.contains("security") ||
               lowerAction.contains("update") ||
               lowerAction.contains("upgrade");
    }

    /**
     * Filters a list of proposed actions to only those allowed in maintenance-only mode.
     *
     * @param actions proposed actions
     * @param mastery mastery item
     * @return filtered list of allowed actions
     */
    @NotNull
    public List<String> filterActions(@NotNull List<String> actions, @NotNull MasteryItem mastery) {
        return actions.stream()
                .filter(action -> isActionAllowed(action, mastery))
                .toList();
    }

    /**
     * Returns the constraints for maintenance-only mode.
     *
     * @return map of constraints
     */
    @NotNull
    public Map<String, String> getConstraints() {
        return Map.of(
                "allow_refactoring", "false",
                "allow_architectural_changes", "false",
                "allow_new_features", "false",
                "allow_bug_fixes", "true",
                "allow_security_patches", "true",
                "allow_version_upgrades", "true"
        );
    }
}
