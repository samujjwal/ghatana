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
     * Supports:
     * <ul>
     *   <li>Basic operators: >=, <=, >, <, =</li>
     *   <li>Compound ranges: ">=7.0.0 <8.0.0"</li>
     *   <li>npm caret (^): ^1.2.3 means >=1.2.3 <2.0.0</li>
     *   <li>npm tilde (~): ~1.2.3 means >=1.2.3 <1.3.0</li>
     *   <li>Maven ranges: [1.0,2.0), (1.0,2.0], [1.0,)</li>
     *   <li>Pre-release versions: 1.2.3-alpha, 1.2.3-beta.1 (semver spec)</li>
     * </ul>
     *
     * @param version current version
     * @param range range specification
     * @return true if version matches range
     */
    private boolean evaluateVersionRange(@NotNull String version, @NotNull String range) {
        try {
            range = range.trim();
            
            // Handle Maven range notation
            if (range.startsWith("[") || range.startsWith("(")) {
                return evaluateMavenRange(version, range);
            }
            
            // Handle compound ranges (space-separated constraints)
            if (range.contains(" ")) {
                String[] constraints = range.split("\\s+");
                for (String constraint : constraints) {
                    if (!evaluateVersionRange(version, constraint)) {
                        return false;
                    }
                }
                return true;
            }

            // Handle npm caret (^) - compatible with same major version
            if (range.startsWith("^")) {
                String baseVersion = range.substring(1).trim();
                return evaluateCaretRange(version, baseVersion);
            }

            // Handle npm tilde (~) - compatible with same minor version
            if (range.startsWith("~")) {
                String baseVersion = range.substring(1).trim();
                return evaluateTildeRange(version, baseVersion);
            }

            // Handle exact match (no operator)
            if (!range.startsWith(">=") && !range.startsWith("<=") && !range.startsWith(">") && !range.startsWith("<")) {
                return compareVersions(version, range) == 0;
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
     * Evaluates Maven-style range notation.
     * Supports:
     * <ul>
     *   <li>[1.0,2.0] - inclusive 1.0 to 2.0</li>
     *   <li>(1.0,2.0) - exclusive 1.0 to 2.0</li>
     *   <li>[1.0,2.0) - inclusive 1.0, exclusive 2.0</li>
     *   <li>(1.0,2.0] - exclusive 1.0, inclusive 2.0</li>
     *   <li>[1.0,) - 1.0 or higher</li>
     *   <li>(,2.0] - up to and including 2.0</li>
     * </ul>
     *
     * @param version current version
     * @param range Maven range specification
     * @return true if version matches Maven range
     */
    private boolean evaluateMavenRange(@NotNull String version, @NotNull String range) {
        // Remove trailing comma if present (e.g., [1.0,) -> [1.0)
        range = range.replaceAll(",\\)", ")");
        range = range.replaceAll(",\\]", "]");
        
        if (!range.matches("[\\[\\(].*[\\]\\)]")) {
            return false;
        }
        
        char startBracket = range.charAt(0);
        char endBracket = range.charAt(range.length() - 1);
        String inner = range.substring(1, range.length() - 1);
        
        String[] parts = inner.split(",");
        String lower = parts[0].trim();
        String upper = parts.length > 1 ? parts[1].trim() : null;
        
        // Check lower bound
        if (!lower.isEmpty()) {
            int lowerComparison = compareVersions(version, lower);
            if (startBracket == '[' && lowerComparison < 0) {
                return false; // version < lower bound (inclusive)
            }
            if (startBracket == '(' && lowerComparison <= 0) {
                return false; // version <= lower bound (exclusive)
            }
        }
        
        // Check upper bound
        if (upper != null && !upper.isEmpty()) {
            int upperComparison = compareVersions(version, upper);
            if (endBracket == ']' && upperComparison > 0) {
                return false; // version > upper bound (inclusive)
            }
            if (endBracket == ')' && upperComparison >= 0) {
                return false; // version >= upper bound (exclusive)
            }
        }
        
        return true;
    }

    /**
     * Evaluates npm caret range (^1.2.3 means >=1.2.3 <2.0.0).
     *
     * @param version current version
     * @param baseVersion base version from caret range
     * @return true if version matches caret range
     */
    private boolean evaluateCaretRange(@NotNull String version, @NotNull String baseVersion) {
        // Strip pre-release from base for comparison
        String baseStable = baseVersion.split("-")[0];
        String[] baseParts = baseStable.split("\\.");
        
        int baseMajor = Integer.parseInt(baseParts[0]);
        
        // ^0.x.x means >=0.x.x <0.(x+1).0
        // ^x.y.z where x > 0 means >=x.y.z <(x+1).0.0
        
        String minVersion = baseStable;
        String maxVersion;
        
        if (baseMajor == 0) {
            if (baseParts.length >= 2) {
                int baseMinor = Integer.parseInt(baseParts[1]);
                maxVersion = "0." + (baseMinor + 1) + ".0";
            } else {
                maxVersion = "1.0.0";
            }
        } else {
            maxVersion = (baseMajor + 1) + ".0.0";
        }
        
        return compareVersions(version, minVersion) >= 0 && compareVersions(version, maxVersion) < 0;
    }

    /**
     * Evaluates npm tilde range (~1.2.3 means >=1.2.3 <1.3.0).
     *
     * @param version current version
     * @param baseVersion base version from tilde range
     * @return true if version matches tilde range
     */
    private boolean evaluateTildeRange(@NotNull String version, @NotNull String baseVersion) {
        String baseStable = baseVersion.split("-")[0];
        String[] baseParts = baseStable.split("\\.");
        
        String minVersion = baseStable;
        String maxVersion;
        
        if (baseParts.length >= 2) {
            int baseMajor = Integer.parseInt(baseParts[0]);
            int baseMinor = Integer.parseInt(baseParts[1]);
            maxVersion = baseMajor + "." + (baseMinor + 1) + ".0";
        } else {
            int baseMajor = Integer.parseInt(baseParts[0]);
            maxVersion = (baseMajor + 1) + ".0.0";
        }
        
        return compareVersions(version, minVersion) >= 0 && compareVersions(version, maxVersion) < 0;
    }

    /**
     * Compares two semantic version strings according to semver spec.
     * Handles pre-release versions: 1.2.3-alpha < 1.2.3-beta < 1.2.3.
     *
     * @param v1 version 1
     * @param v2 version 2
     * @return comparison result (negative if v1 < v2, 0 if equal, positive if v1 > v2)
     */
    private int compareVersions(@NotNull String v1, @NotNull String v2) {
        // Split version and pre-release
        String[] v1Parts = v1.split("-", 2);
        String[] v2Parts = v2.split("-", 2);
        
        String v1Main = v1Parts[0];
        String v2Main = v2Parts[0];
        String v1Pre = v1Parts.length > 1 ? v1Parts[1] : null;
        String v2Pre = v2Parts.length > 1 ? v2Parts[1] : null;
        
        // Compare main version parts
        String[] v1MainParts = v1Main.split("\\.");
        String[] v2MainParts = v2Main.split("\\.");
        
        for (int i = 0; i < Math.max(v1MainParts.length, v2MainParts.length); i++) {
            int n1 = i < v1MainParts.length ? parseVersionPart(v1MainParts[i]) : 0;
            int n2 = i < v2MainParts.length ? parseVersionPart(v2MainParts[i]) : 0;
            if (n1 != n2) {
                return n1 - n2;
            }
        }
        
        // Main versions are equal, compare pre-release
        // Pre-release versions have lower precedence than normal versions
        if (v1Pre == null && v2Pre == null) {
            return 0;
        }
        if (v1Pre == null) {
            return 1; // v1 is normal, v2 is pre-release
        }
        if (v2Pre == null) {
            return -1; // v1 is pre-release, v2 is normal
        }
        
        // Both have pre-release, compare them
        return comparePreRelease(v1Pre, v2Pre);
    }

    /**
     * Parses a version part that may contain non-numeric characters.
     * Returns 0 for non-numeric parts.
     *
     * @param part version part
     * @return numeric value
     */
    private int parseVersionPart(@NotNull String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Compares two pre-release identifiers according to semver spec.
     * Pre-release identifiers are compared dot-separated.
     * Numeric identifiers have lower precedence than non-numeric.
     *
     * @param pre1 pre-release identifier 1
     * @param pre2 pre-release identifier 2
     * @return comparison result
     */
    private int comparePreRelease(@NotNull String pre1, @NotNull String pre2) {
        String[] parts1 = pre1.split("\\.");
        String[] parts2 = pre2.split("\\.");
        
        for (int i = 0; i < Math.max(parts1.length, parts2.length); i++) {
            if (i >= parts1.length) {
                return -1; // pre1 has fewer parts
            }
            if (i >= parts2.length) {
                return 1; // pre2 has fewer parts
            }
            
            String p1 = parts1[i];
            String p2 = parts2[i];
            
            boolean p1Numeric = p1.matches("\\d+");
            boolean p2Numeric = p2.matches("\\d+");
            
            if (p1Numeric && p2Numeric) {
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) {
                    return n1 - n2;
                }
            } else if (p1Numeric) {
                return -1; // numeric < non-numeric
            } else if (p2Numeric) {
                return 1; // non-numeric > numeric
            } else {
                int cmp = p1.compareTo(p2);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        
        return 0;
    }
}
