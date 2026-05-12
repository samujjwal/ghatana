/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

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
}
