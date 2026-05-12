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
        active = List.copyOf(active);
        maintenance = List.copyOf(maintenance);
        obsolete = List.copyOf(obsolete);
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
     * Implements basic semantic version range evaluation.
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

        // Parse and evaluate version range
        return evaluateVersionRange(currentVersion, range);
    }

    /**
     * Evaluates if a version matches a range specification.
     * Supports basic operators: >=, <=, >, <, =
     *
     * @param version current version
     * @param range range specification
     * @return true if version matches range
     */
    private boolean evaluateVersionRange(@NotNull String version, @NotNull String range) {
        try {
            // Handle exact match
            if (!range.startsWith(">=") && !range.startsWith("<=") && !range.startsWith(">") && !range.startsWith("<")) {
                return version.equals(range);
            }

            // Handle >=
            if (range.startsWith(">=")) {
                String required = range.substring(2).trim();
                return compareVersions(version, required) >= 0;
            }

            // Handle <=
            if (range.startsWith("<=")) {
                String required = range.substring(2).trim();
                return compareVersions(version, required) <= 0;
            }

            // Handle >
            if (range.startsWith(">")) {
                String required = range.substring(1).trim();
                return compareVersions(version, required) > 0;
            }

            // Handle <
            if (range.startsWith("<")) {
                String required = range.substring(1).trim();
                return compareVersions(version, required) < 0;
            }

            return false;
        } catch (Exception e) {
            // If parsing fails, conservatively return false
            return false;
        }
    }

    /**
     * Compares two semantic version strings.
     *
     * @param v1 version 1
     * @param v2 version 2
     * @return comparison result (negative if v1 < v2, 0 if equal, positive if v1 > v2)
     */
    private int compareVersions(@NotNull String v1, @NotNull String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        return 0;
    }
}
