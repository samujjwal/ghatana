/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence.detector;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.mastery.VersionScope;
import com.ghatana.agent.mastery.VersionConstraint;
import com.ghatana.agent.mastery.VersionRangeEvaluator;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import com.ghatana.agent.obsolescence.ObsolescenceReason;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detector for dependency version changes that cause obsolescence.
 * Phase 7 FIX: Concrete detector for dependency version changes.
 *
 * @doc.type class
 * @doc.purpose Detects obsolescence due to dependency version changes
 * @doc.layer agent-core
 * @doc.pattern Detector
 */
public final class DependencyVersionDetector implements ObsolescenceDetector {

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env
    ) {
        List<ObsolescenceEvent> events = new ArrayList<>();

        // Check if mastery item has version scope constraints
        VersionScope versionScope = item.versionScope();
        if (versionScope == null) {
            return Promise.of(events);
        }

        // Check active constraints against current environment
        for (VersionConstraint constraint : versionScope.active()) {
            if (isVersionMismatch(constraint, env)) {
                events.add(createVersionMismatchEvent(item, constraint, env));
            }
        }

        // Check maintenance constraints against current environment
        for (VersionConstraint constraint : versionScope.maintenance()) {
            if (isVersionMismatch(constraint, env)) {
                events.add(createVersionMismatchEvent(item, constraint, env));
            }
        }

        return Promise.of(events);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> scanAll(@NotNull String tenantId, @NotNull EnvironmentFingerprint env) {
        // This would scan all mastery items for the tenant
        // For now, return empty list as this is a concrete detector for single items
        return Promise.of(List.of());
    }

    /**
     * Checks if a version constraint mismatches the current environment.
     */
    private boolean isVersionMismatch(@NotNull VersionConstraint constraint, @NotNull EnvironmentFingerprint env) {
        Map<String, String> dependencies = env.dependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return false;
        }

        String currentVersion = dependencies.get(constraint.name());
        if (currentVersion == null) {
            return false;
        }

        // Check if current version is outside the constraint range
        // This is a simplified check - in production, use proper version range evaluation
        String constraintRange = constraint.range();
        return !isVersionInRange(currentVersion, constraintRange);
    }

    /**
     * Version range check using VersionRangeEvaluator for proper semver range evaluation.
     */
    private boolean isVersionInRange(@NotNull String version, @NotNull String range) {
        return VersionRangeEvaluator.evaluate(version, range);
    }

    /**
     * Creates an obsolescence event for version mismatch.
     */
    @NotNull
    private ObsolescenceEvent createVersionMismatchEvent(
            @NotNull MasteryItem item,
            @NotNull VersionConstraint constraint,
            @NotNull EnvironmentFingerprint env
    ) {
        Map<String, String> dependencies = env.dependencies();
        String currentVersion = dependencies != null ? dependencies.get(constraint.name()) : "unknown";

        return ObsolescenceEvent.of(
                item.masteryId(),
                item.tenantId(),
                ObsolescenceReason.VERSION_MISMATCH,
                String.format("Dependency %s version mismatch: expected %s, found %s",
                        constraint.name(), constraint.range(), currentVersion),
                List.of(),
                Map.of(
                        "dependency", constraint.name(),
                        "expectedRange", constraint.range(),
                        "currentVersion", currentVersion,
                        "ecosystem", constraint.ecosystem()
                ),
                ObsolescenceEvent.Severity.HIGH,
                com.ghatana.agent.mastery.MasteryState.MAINTENANCE_ONLY
        );
    }
}
