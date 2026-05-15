/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence.detector;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.obsolescence.ObsolescenceDetector;
import com.ghatana.agent.obsolescence.ObsolescenceEvent;
import com.ghatana.agent.obsolescence.ObsolescenceReason;
import com.ghatana.agent.obsolescence.SecurityAdvisory;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detector for security advisories that cause obsolescence.
 * Phase 7 FIX: Concrete detector for security advisory detection.
 *
 * @doc.type class
 * @doc.purpose Detects obsolescence due to security advisories
 * @doc.layer agent-core
 * @doc.pattern Detector
 */
public final class SecurityAdvisoryDetector implements ObsolescenceDetector {

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env
    ) {
        List<ObsolescenceEvent> events = new ArrayList<>();

        // Check environment for security advisories using evidenceRefs
        List<String> evidenceRefs = env.evidenceRefs();
        if (evidenceRefs == null || evidenceRefs.isEmpty()) {
            return Promise.of(events);
        }

        // For now, return empty list as we don't have actual security advisory data
        // In production, this would parse security advisories from evidenceRefs
        return Promise.of(events);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> scanAll(@NotNull String tenantId, @NotNull EnvironmentFingerprint env) {
        return Promise.of(List.of());
    }

    /**
     * Checks if a mastery item is affected by a security advisory.
     */
    private boolean isAffected(@NotNull MasteryItem item, @NotNull SecurityAdvisory advisory) {
        // Check if the advisory affects any dependencies in the mastery item's version scope
        if (item.versionScope() != null) {
            for (var constraint : item.versionScope().active()) {
                if (advisory.affectedComponent().equals(constraint.name())) {
                    return true;
                }
            }
            for (var constraint : item.versionScope().maintenance()) {
                if (advisory.affectedComponent().equals(constraint.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates an obsolescence event for security advisory.
     */
    @NotNull
    private ObsolescenceEvent createSecurityAdvisoryEvent(
            @NotNull MasteryItem item,
            @NotNull SecurityAdvisory advisory
    ) {
        return ObsolescenceEvent.of(
                item.masteryId(),
                item.tenantId(),
                ObsolescenceReason.VERSION_MISMATCH,
                String.format("Security advisory %s affects mastery item component: %s",
                        advisory.advisoryId(), advisory.affectedComponent()),
                List.of(),
                Map.of(
                        "advisoryId", advisory.advisoryId(),
                        "severity", advisory.severity().name(),
                        "cveId", advisory.cveId() != null ? advisory.cveId() : "none",
                        "affectedComponent", advisory.affectedComponent()
                ),
                advisory.severity() == SecurityAdvisory.Severity.CRITICAL ? ObsolescenceEvent.Severity.CRITICAL : ObsolescenceEvent.Severity.HIGH,
                advisory.severity() == SecurityAdvisory.Severity.CRITICAL ? com.ghatana.agent.mastery.MasteryState.QUARANTINED : com.ghatana.agent.mastery.MasteryState.OBSOLETE,
                List.of(),
                List.of(),
                List.of(advisory),
                List.of()
        );
    }
}
