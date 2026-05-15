/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

/**
 * Evaluates version range specifications against version strings.
 *
 * <p>Supports multiple range syntaxes:
 * <ul>
 *   <li>Basic operators: >=, <=, >, <, =</li>
 *   <li>Compound ranges: ">=7.0.0 <8.0.0"</li>
 *   <li>npm caret (^): ^1.2.3 means >=1.2.3 <2.0.0</li>
 *   <li>npm tilde (~): ~1.2.3 means >=1.2.3 <1.3.0</li>
 *   <li>Maven ranges: [1.0,2.0), (1.0,2.0], [1.0,)</li>
 *   <li>Pre-release versions: 1.2.3-alpha, 1.2.3-beta.1 (semver spec)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Version range evaluation for version scope constraints
 * @doc.layer agent-core
 * @doc.pattern Utility
 */
public final class VersionRangeEvaluator {

    private VersionRangeEvaluator() {
        // Utility class
    }

    /**
     * Evaluates if a version matches a range specification.
     *
     * @param version current version
     * @param range range specification
     * @return true if version matches range
     */
    public static boolean evaluate(@NotNull String version, @NotNull String range) {
        try {
            range = range.trim();

            // Handle Maven range notation
            if (range.startsWith("[") || range.startsWith("(")) {
                return evaluateMavenRange(version, range);
            }

            // Handle Ruby-style range (e.g., "17..21")
            if (range.matches("^\\d+(\\.\\d+)*\\.\\.\\d+(\\.\\d+)*$")) {
                return evaluateRubyRange(version, range);
            }

            // Handle compound ranges (space-separated constraints)
            if (range.contains(" ")) {
                String[] constraints = range.split("\\s+");
                for (String constraint : constraints) {
                    if (!evaluate(version, constraint)) {
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
     * Evaluates Ruby-style range notation (e.g., "17..21" means >17 <=21).
     * Note: In this context, the range is exclusive of the lower bound.
     *
     * @param version current version
     * @param range Ruby range specification
     * @return true if version matches Ruby range
     */
    private static boolean evaluateRubyRange(@NotNull String version, @NotNull String range) {
        String[] parts = range.split("\\.\\.");
        String min = parts[0];
        String max = parts[1];
        
        // Range is exclusive of lower bound, inclusive of upper bound: > min and <= max
        return compareVersions(version, min) > 0 && compareVersions(version, max) <= 0;
    }

    /**
     * Evaluates Maven-style range notation.
     *
     * @param version current version
     * @param range Maven range specification
     * @return true if version matches Maven range
     */
    private static boolean evaluateMavenRange(@NotNull String version, @NotNull String range) {
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
    private static boolean evaluateCaretRange(@NotNull String version, @NotNull String baseVersion) {
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
    private static boolean evaluateTildeRange(@NotNull String version, @NotNull String baseVersion) {
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
    private static int compareVersions(@NotNull String v1, @NotNull String v2) {
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
    private static int parseVersionPart(@NotNull String part) {
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
    private static int comparePreRelease(@NotNull String pre1, @NotNull String pre2) {
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

    /**
     * Checks if two version ranges overlap.
     * Two ranges overlap if there exists at least one version that satisfies both ranges.
     *
     * @param range1 first version range
     * @param range2 second version range
     * @return true if ranges overlap
     */
    public static boolean rangesOverlap(@NotNull String range1, @NotNull String range2) {
        try {
            RangeBounds bounds1 = parseRangeToBounds(range1);
            RangeBounds bounds2 = parseRangeToBounds(range2);
            return boundsOverlap(bounds1, bounds2);
        } catch (Exception e) {
            // If parsing fails, conservatively assume overlap to avoid false negatives
            return true;
        }
    }

    /**
     * Parses a version range into its minimum and maximum bounds.
     * Returns null for unbounded ranges.
     *
     * @param range version range to parse
     * @return RangeBounds with min and max versions
     */
    private static RangeBounds parseRangeToBounds(@NotNull String range) {
        range = range.trim();

        // Handle Maven range notation
        if (range.startsWith("[") || range.startsWith("(")) {
            return parseMavenRangeToBounds(range);
        }

        // Handle npm caret (^)
        if (range.startsWith("^")) {
            String baseVersion = range.substring(1).trim();
            return parseCaretRangeToBounds(baseVersion);
        }

        // Handle npm tilde (~)
        if (range.startsWith("~")) {
            String baseVersion = range.substring(1).trim();
            return parseTildeRangeToBounds(baseVersion);
        }

        // Handle compound ranges (space-separated constraints)
        if (range.contains(" ")) {
            return parseCompoundRangeToBounds(range);
        }

        // Handle exact match
        if (!range.startsWith(">=") && !range.startsWith("<=") && !range.startsWith(">") && !range.startsWith("<")) {
            return new RangeBounds(range, range, true, true);
        }

        // Handle >=
        if (range.startsWith(">=")) {
            String min = range.substring(2).trim();
            return new RangeBounds(min, null, true, false);
        }

        // Handle <=
        if (range.startsWith("<=")) {
            String max = range.substring(2).trim();
            return new RangeBounds(null, max, false, true);
        }

        // Handle >
        if (range.startsWith(">")) {
            String min = range.substring(1).trim();
            return new RangeBounds(min, null, false, false);
        }

        // Handle <
        if (range.startsWith("<")) {
            String max = range.substring(1).trim();
            return new RangeBounds(null, max, false, false);
        }

        // Unknown format - assume unbounded
        return new RangeBounds(null, null, false, false);
    }

    /**
     * Parses Maven range notation into bounds.
     */
    private static RangeBounds parseMavenRangeToBounds(@NotNull String range) {
        range = range.replaceAll(",\\)", ")");
        range = range.replaceAll(",\\]", "]");

        if (!range.matches("[\\[\\(].*[\\]\\)]")) {
            return new RangeBounds(null, null, false, false);
        }

        char startBracket = range.charAt(0);
        char endBracket = range.charAt(range.length() - 1);
        String inner = range.substring(1, range.length() - 1);

        String[] parts = inner.split(",");
        String lower = parts[0].trim();
        String upper = parts.length > 1 ? parts[1].trim() : null;

        boolean minInclusive = (startBracket == '[');
        boolean maxInclusive = (endBracket == ']');

        return new RangeBounds(
            lower.isEmpty() ? null : lower,
            upper == null || upper.isEmpty() ? null : upper,
            minInclusive,
            maxInclusive
        );
    }

    /**
     * Parses npm caret range into bounds.
     */
    private static RangeBounds parseCaretRangeToBounds(@NotNull String baseVersion) {
        String baseStable = baseVersion.split("-")[0];
        String[] baseParts = baseStable.split("\\.");

        int baseMajor = Integer.parseInt(baseParts[0]);

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

        return new RangeBounds(minVersion, maxVersion, true, false);
    }

    /**
     * Parses npm tilde range into bounds.
     */
    private static RangeBounds parseTildeRangeToBounds(@NotNull String baseVersion) {
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

        return new RangeBounds(minVersion, maxVersion, true, false);
    }

    /**
     * Parses compound range into bounds by finding the intersection of all constraints.
     */
    private static RangeBounds parseCompoundRangeToBounds(@NotNull String range) {
        String[] constraints = range.split("\\s+");
        RangeBounds result = new RangeBounds(null, null, false, false);

        for (String constraint : constraints) {
            RangeBounds bounds = parseRangeToBounds(constraint);
            result = intersectBounds(result, bounds);
        }

        return result;
    }

    /**
     * Computes the intersection of two range bounds.
     */
    private static RangeBounds intersectBounds(@NotNull RangeBounds b1, @NotNull RangeBounds b2) {
        String newMin;
        String newMax;
        boolean newMinInclusive;
        boolean newMaxInclusive;

        // For minimum bound, take the larger one
        if (b1.min == null) {
            newMin = b2.min;
            newMinInclusive = b2.minInclusive;
        } else if (b2.min == null) {
            newMin = b1.min;
            newMinInclusive = b1.minInclusive;
        } else {
            int cmp = compareVersions(b1.min, b2.min);
            if (cmp > 0) {
                newMin = b1.min;
                newMinInclusive = b1.minInclusive;
            } else if (cmp < 0) {
                newMin = b2.min;
                newMinInclusive = b2.minInclusive;
            } else {
                newMin = b1.min;
                newMinInclusive = b1.minInclusive && b2.minInclusive;
            }
        }

        // For maximum bound, take the smaller one
        if (b1.max == null) {
            newMax = b2.max;
            newMaxInclusive = b2.maxInclusive;
        } else if (b2.max == null) {
            newMax = b1.max;
            newMaxInclusive = b1.maxInclusive;
        } else {
            int cmp = compareVersions(b1.max, b2.max);
            if (cmp < 0) {
                newMax = b1.max;
                newMaxInclusive = b1.maxInclusive;
            } else if (cmp > 0) {
                newMax = b2.max;
                newMaxInclusive = b2.maxInclusive;
            } else {
                newMax = b1.max;
                newMaxInclusive = b1.maxInclusive && b2.maxInclusive;
            }
        }

        return new RangeBounds(newMin, newMax, newMinInclusive, newMaxInclusive);
    }

    /**
     * Checks if two range bounds overlap.
     */
    private static boolean boundsOverlap(@NotNull RangeBounds b1, @NotNull RangeBounds b2) {
        // If either is unbounded, they overlap
        if (b1.min == null && b1.max == null) return true;
        if (b2.min == null && b2.max == null) return true;

        // Check if b1 is entirely before b2
        if (b1.max != null && b2.min != null) {
            int cmp = compareVersions(b1.max, b2.min);
            if (cmp < 0) return false; // b1.max < b2.min
            if (cmp == 0 && !(b1.maxInclusive && b2.minInclusive)) return false; // b1.max == b2.min but not both inclusive
        }

        // Check if b2 is entirely before b1
        if (b2.max != null && b1.min != null) {
            int cmp = compareVersions(b2.max, b1.min);
            if (cmp < 0) return false; // b2.max < b1.min
            if (cmp == 0 && !(b2.maxInclusive && b1.minInclusive)) return false; // b2.max == b1.min but not both inclusive
        }

        return true;
    }

    /**
     * Internal record representing the min and max bounds of a version range.
     */
    private record RangeBounds(
        String min,
        String max,
        boolean minInclusive,
        boolean maxInclusive
    ) {}

    /**
     * Validates if a range syntax is valid for the given ecosystem.
     *
     * @param range version range to validate
     * @param ecosystem ecosystem (npm, maven, python, etc.)
     * @return true if range syntax is valid
     */
    public static boolean isValidRangeSyntax(@NotNull String range, @NotNull String ecosystem) {
        try {
            range = range.trim();

            // Empty range is invalid
            if (range.isEmpty()) {
                return false;
            }

            switch (ecosystem.toLowerCase()) {
                case "npm":
                    return isValidNpmRange(range);
                case "maven":
                    return isValidMavenRange(range);
                case "python":
                    return isValidPythonRange(range);
                case "jvm":
                case "system":
                    return isValidSystemRange(range);
                default:
                    // For unknown ecosystems, be permissive
                    return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates npm-style range syntax.
     */
    private static boolean isValidNpmRange(@NotNull String range) {
        // npm supports: exact, ^, ~, >=, <=, >, <, compound ranges
        if (range.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
            return true; // exact version
        }
        if (range.matches("^\\^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
            return true; // caret range
        }
        if (range.matches("^~\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
            return true; // tilde range
        }
        if (range.matches("^(>=|<=|>|<)\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$")) {
            return true; // operator range with full semver
        }
        if (range.matches("^(>=|<=|>|<)\\d+$")) {
            return true; // operator range with single number (e.g., <5, >=21)
        }
        if (range.contains(" ")) {
            String[] parts = range.split("\\s+");
            for (String part : parts) {
                if (!isValidNpmRange(part)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Validates Maven-style range syntax.
     */
    private static boolean isValidMavenRange(@NotNull String range) {
        // Maven bracket notation: [1.0,2.0), (1.0,2.0], [1.0,), (,2.0]
        if (range.matches("^[\\[\\(][^,]*,[^,]*[\\]\\)]$") ||
            range.matches("^[\\[\\(][^,]*,[\\]\\)]$") ||
            range.matches("^[\\[\\(],[^,]*[\\]\\)]$")) {
            return true;
        }
        // Handle compound ranges (space-separated constraints)
        if (range.contains(" ")) {
            String[] parts = range.split("\\s+");
            for (String part : parts) {
                if (!isValidMavenRange(part)) {
                    return false;
                }
            }
            return true;
        }
        // Also accept operator syntax for Maven (e.g., >=21, <5)
        return range.matches("^(>=|<=|>|<)\\d+(\\.\\d+)*$") ||
               range.matches("^\\d+(\\.\\d+)*$");
    }

    /**
     * Validates Python PEP 440 style range syntax.
     */
    private static boolean isValidPythonRange(@NotNull String range) {
        // Python supports: ~=, ==, !=, <=, >=, <, >, ===
        return range.matches("^(~=|==|!=|<=|>=|<|>|===).+");
    }

    /**
     * Validates system/runtime range syntax.
     */
    private static boolean isValidSystemRange(@NotNull String range) {
        // System ranges typically use simple operators, exact versions, compound ranges, or Ruby-style ranges
        if (range.contains(" ")) {
            // Compound range - validate each part
            String[] parts = range.split("\\s+");
            for (String part : parts) {
                if (!isValidSystemRange(part)) {
                    return false;
                }
            }
            return true;
        }
        // Ruby-style range (e.g., "17..21")
        if (range.matches("^\\d+(\\.\\d+)*\\.\\.\\d+(\\.\\d+)*$")) {
            return true;
        }
        // Single constraint
        return range.matches("^(>=|<=|>|<)?\\d+(\\.\\d+)*") ||
               range.matches("^\\d+(\\.\\d+)*");
    }
}
