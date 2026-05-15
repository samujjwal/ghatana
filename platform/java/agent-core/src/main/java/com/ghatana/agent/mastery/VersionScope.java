/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import com.ghatana.agent.context.version.VersionContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Version scope defining which versions a mastery item applies to.
 *
 * <p>The version scope partitions constraints into three categories:
 * <ul>
 *   <li><b>active</b>: Versions where the skill is actively recommended</li>
 *   <li><b>maintenance</b>: Legacy versions where the skill is still usable but not recommended for new work</li>
 *   <li><b>obsolete</b>: Versions where the skill should not be used</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Version scope for mastery applicability across versions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record VersionScope(
        @NotNull List<VersionConstraint> active,
        @NotNull List<VersionConstraint> maintenance,
        @NotNull List<VersionConstraint> obsolete
) {
    public VersionScope {
        Objects.requireNonNull(active, "active must not be null");
        Objects.requireNonNull(maintenance, "maintenance must not be null");
        Objects.requireNonNull(obsolete, "obsolete must not be null");
        
        // Phase 1 FIX: Validate before defensive copying using local variables
        List<VersionConstraint> activeParam = active;
        List<VersionConstraint> maintenanceParam = maintenance;
        List<VersionConstraint> obsoleteParam = obsolete;
        
        validateNoOverlappingConstraints(activeParam, maintenanceParam, obsoleteParam);
        validateRangeSyntax(activeParam, maintenanceParam, obsoleteParam);
        
        // Defensive copying after validation
        active = List.copyOf(activeParam);
        maintenance = List.copyOf(maintenanceParam);
        obsolete = List.copyOf(obsoleteParam);
    }
    
    /**
     * Phase 1 FIX: Validates that version constraints do not overlap across categories.
     * Overlapping constraints would create ambiguous version classification.
     * Detects conflicts across:
     * - active vs maintenance
     * - active vs obsolete
     * - maintenance vs obsolete
     *
     * @throws IllegalArgumentException if constraints overlap
     */
    private void validateNoOverlappingConstraints(
            List<VersionConstraint> active,
            List<VersionConstraint> maintenance,
            List<VersionConstraint> obsolete) {
        // Skip validation if all lists are empty
        if (active.isEmpty() && maintenance.isEmpty() && obsolete.isEmpty()) {
            return;
        }

        // Validate active vs maintenance overlap
        for (VersionConstraint activeConstraint : active) {
            for (VersionConstraint maintenanceConstraint : maintenance) {
                if (constraintsOverlap(activeConstraint, maintenanceConstraint)) {
                    throw new IllegalArgumentException(
                            "Active and maintenance constraints overlap for " + activeConstraint.name() +
                            ": active=" + activeConstraint.range() +
                            ", maintenance=" + maintenanceConstraint.range() +
                            ". Maintenance constraints must not overlap active constraints without explicit precedence.");
                }
            }
        }

        // Validate active vs obsolete overlap
        for (VersionConstraint activeConstraint : active) {
            for (VersionConstraint obsoleteConstraint : obsolete) {
                if (constraintsOverlap(activeConstraint, obsoleteConstraint)) {
                    throw new IllegalArgumentException(
                            "Active and obsolete constraints overlap for " + activeConstraint.name() +
                            ": active=" + activeConstraint.range() +
                            ", obsolete=" + obsoleteConstraint.range() +
                            ". Same package cannot appear in both active and obsolete with overlapping ranges.");
                }
            }
        }

        // Validate maintenance vs obsolete overlap
        for (VersionConstraint maintenanceConstraint : maintenance) {
            for (VersionConstraint obsoleteConstraint : obsolete) {
                if (constraintsOverlap(maintenanceConstraint, obsoleteConstraint)) {
                    throw new IllegalArgumentException(
                            "Maintenance and obsolete constraints overlap for " + maintenanceConstraint.name() +
                            ": maintenance=" + maintenanceConstraint.range() +
                            ", obsolete=" + obsoleteConstraint.range() +
                            ". Same package cannot appear in both maintenance and obsolete with overlapping ranges.");
                }
            }
        }
    }
    
    /**
     * Phase 1 FIX: Checks if two version constraints overlap by comparing their names and ranges.
     * Two constraints overlap if they apply to the same component and have overlapping version ranges.
     * Uses real semver/range overlap detection from VersionRangeEvaluator.
     *
     * @param c1 first constraint
     * @param c2 second constraint
     * @return true if constraints overlap
     */
    private boolean constraintsOverlap(@NotNull VersionConstraint c1, @NotNull VersionConstraint c2) {
        // Constraints only overlap if they apply to the same component
        if (!c1.name().equals(c2.name())) {
            return false;
        }
        
        // Check if ranges overlap using VersionRangeEvaluator's real overlap detection
        return VersionRangeEvaluator.rangesOverlap(c1.range(), c2.range());
    }

    /**
     * Phase 1 FIX: Validates that all range syntaxes are valid for their ecosystems.
     * Rejects unknown range syntax to prevent ambiguous version classification.
     *
     * @throws IllegalArgumentException if range syntax is invalid
     */
    private void validateRangeSyntax(
            List<VersionConstraint> active,
            List<VersionConstraint> maintenance,
            List<VersionConstraint> obsolete) {
        for (VersionConstraint constraint : active) {
            if (!VersionRangeEvaluator.isValidRangeSyntax(constraint.range(), constraint.ecosystem())) {
                throw new IllegalArgumentException(
                        "Invalid range syntax for " + constraint.name() +
                        " in active constraints: range=" + constraint.range() +
                        ", ecosystem=" + constraint.ecosystem());
            }
        }

        for (VersionConstraint constraint : maintenance) {
            if (!VersionRangeEvaluator.isValidRangeSyntax(constraint.range(), constraint.ecosystem())) {
                throw new IllegalArgumentException(
                        "Invalid range syntax for " + constraint.name() +
                        " in maintenance constraints: range=" + constraint.range() +
                        ", ecosystem=" + constraint.ecosystem());
            }
        }

        for (VersionConstraint constraint : obsolete) {
            if (!VersionRangeEvaluator.isValidRangeSyntax(constraint.range(), constraint.ecosystem())) {
                throw new IllegalArgumentException(
                        "Invalid range syntax for " + constraint.name() +
                        " in obsolete constraints: range=" + constraint.range() +
                        ", ecosystem=" + constraint.ecosystem());
            }
        }
    }

    /**
     * Creates an empty version scope with no constraints.
     *
     * @return empty version scope
     */
    @NotNull
    public static VersionScope empty() {
        return new VersionScope(List.of(), List.of(), List.of());
    }

    /**
     * Creates a version scope with only active constraints.
     *
     * @param active active version constraints
     * @return version scope with active constraints
     */
    @NotNull
    public static VersionScope activeOnly(@NotNull List<VersionConstraint> active) {
        return new VersionScope(active, List.of(), List.of());
    }

    /**
     * Classifies the applicability of a version context.
     *
     * @param context version context to classify
     * @return version applicability decision
     */
    @NotNull
    public VersionApplicability classify(@NotNull VersionContext context) {
        // Check obsolete constraints first
        for (VersionConstraint constraint : obsolete) {
            if (matchesConstraint(context, constraint)) {
                return VersionApplicability.OBSOLETE;
            }
        }

        // Check maintenance constraints
        for (VersionConstraint constraint : maintenance) {
            if (matchesConstraint(context, constraint)) {
                return VersionApplicability.MAINTENANCE;
            }
        }

        // Check active constraints
        for (VersionConstraint constraint : active) {
            if (matchesConstraint(context, constraint)) {
                return VersionApplicability.ACTIVE;
            }
        }

        return VersionApplicability.UNKNOWN;
    }

    /**
     * Returns true if the version context supports active mode.
     *
     * @param context version context to check
     * @return true if active mode is supported
     */
    public boolean supportsActive(@NotNull VersionContext context) {
        return classify(context) == VersionApplicability.ACTIVE;
    }

    /**
     * Returns true if the version context supports maintenance mode.
     *
     * @param context version context to check
     * @return true if maintenance mode is supported
     */
    public boolean supportsMaintenance(@NotNull VersionContext context) {
        VersionApplicability applicability = classify(context);
        return applicability == VersionApplicability.ACTIVE || applicability == VersionApplicability.MAINTENANCE;
    }

    /**
     * Returns true if the version context is obsolete.
     *
     * @param context version context to check
     * @return true if obsolete
     */
    public boolean isObsolete(@NotNull VersionContext context) {
        return classify(context) == VersionApplicability.OBSOLETE;
    }

    /**
     * Checks if a version context matches a constraint.
     * Delegates to VersionRangeEvaluator for version range evaluation.
     *
     * @param context version context
     * @param constraint version constraint
     * @return true if matches
     */
    private boolean matchesConstraint(@NotNull VersionContext context, @NotNull VersionConstraint constraint) {
        String range = constraint.range();
        String name = constraint.name();

        // Get current version from context
        String currentVersion = context.dependencies().get(name);
        if (currentVersion == null) {
            return false;
        }

        // Delegate to VersionRangeEvaluator
        return VersionRangeEvaluator.evaluate(currentVersion, range);
    }
}
