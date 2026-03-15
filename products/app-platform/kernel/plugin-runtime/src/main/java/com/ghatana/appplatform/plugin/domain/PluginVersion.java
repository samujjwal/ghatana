package com.ghatana.appplatform.plugin.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable semantic version following {@code MAJOR.MINOR.PATCH} convention.
 *
 * <p>Supports range expressions for {@code platform_version_range} checking
 * using Maven-style comparators: {@code >=2.0.0 <3.0.0}.
 *
 * @doc.type  record
 * @doc.purpose Semantic version VO used in plugin manifests and compatibility checks
 * @doc.layer  product
 * @doc.pattern ValueObject
 */
public record PluginVersion(int major, int minor, int patch) implements Comparable<PluginVersion> {

    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public PluginVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version components must be non-negative");
        }
    }

    /** Parses {@code "MAJOR.MINOR.PATCH"} strings. */
    public static PluginVersion parse(String version) {
        Objects.requireNonNull(version, "version");
        var m = SEMVER.matcher(version.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid semver: " + version);
        }
        return new PluginVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)));
    }

    /**
     * Returns {@code true} when this version satisfies the given range expression.
     *
     * <p>Supported operators: {@code >=}, {@code >}, {@code <=}, {@code <}, {@code =}.
     * Multiple constraints separated by space are ANDed together.
     *
     * @param rangeExpr e.g. {@code ">=2.0.0 <3.0.0"}
     */
    public boolean satisfies(String rangeExpr) {
        for (String constraint : rangeExpr.trim().split("\\s+")) {
            if (!satisfiesOne(constraint)) {
                return false;
            }
        }
        return true;
    }

    private boolean satisfiesOne(String constraint) {
        String op;
        String versionStr;
        if (constraint.startsWith(">=")) {
            op = ">="; versionStr = constraint.substring(2);
        } else if (constraint.startsWith(">")) {
            op = ">"; versionStr = constraint.substring(1);
        } else if (constraint.startsWith("<=")) {
            op = "<="; versionStr = constraint.substring(2);
        } else if (constraint.startsWith("<")) {
            op = "<"; versionStr = constraint.substring(1);
        } else if (constraint.startsWith("=")) {
            op = "="; versionStr = constraint.substring(1);
        } else {
            op = "="; versionStr = constraint;
        }
        PluginVersion target = parse(versionStr.trim());
        int cmp = this.compareTo(target);
        return switch (op) {
            case ">=" -> cmp >= 0;
            case ">"  -> cmp > 0;
            case "<=" -> cmp <= 0;
            case "<"  -> cmp < 0;
            default   -> cmp == 0;
        };
    }

    @Override
    public int compareTo(PluginVersion other) {
        int c = Integer.compare(this.major, other.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, other.minor);
        if (c != 0) return c;
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
