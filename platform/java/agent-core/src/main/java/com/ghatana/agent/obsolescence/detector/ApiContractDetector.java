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
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Detector for API contract changes that cause obsolescence.
 * Phase 7 FIX: Concrete detector for API contract changes.
 *
 * @doc.type class
 * @doc.purpose Detects obsolescence due to API contract changes
 * @doc.layer agent-core
 * @doc.pattern Detector
 */
public final class ApiContractDetector implements ObsolescenceDetector {

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> detect(
            @NotNull MasteryItem item,
            @NotNull EnvironmentFingerprint env
    ) {
        List<ObsolescenceEvent> events = new ArrayList<>();

        // Check environment for API contract changes using frameworks field
        Map<String, String> frameworks = env.frameworks();
        if (frameworks == null || frameworks.isEmpty()) {
            return Promise.of(events);
        }

        // Check if mastery item's API signatures match current environment
        String storedSignature = item.labels().get("apiSignature");
        if (storedSignature != null && !storedSignature.isBlank()) {
            String currentSignature = frameworks.get(item.skillId());
            if (currentSignature != null && !currentSignature.equals(storedSignature)) {
                events.add(createApiContractChangeEvent(item, storedSignature, currentSignature));
            }
        }

        return Promise.of(events);
    }

    @Override
    @NotNull
    public Promise<List<ObsolescenceEvent>> scanAll(@NotNull String tenantId, @NotNull EnvironmentFingerprint env) {
        return Promise.of(List.of());
    }

    /**
     * Creates an obsolescence event for API contract change.
     */
    @NotNull
    private ObsolescenceEvent createApiContractChangeEvent(
            @NotNull MasteryItem item,
            @NotNull String oldSignature,
            @NotNull String newSignature
    ) {
        return ObsolescenceEvent.of(
                item.masteryId(),
                item.tenantId(),
                ObsolescenceReason.API_CHANGE,
                String.format("API contract changed for skill %s: signature mismatch detected",
                        item.skillId()),
                List.of(),
                Map.of(
                        "skillId", item.skillId(),
                        "oldSignature", oldSignature,
                        "newSignature", newSignature
                ),
                ObsolescenceEvent.Severity.HIGH,
                com.ghatana.agent.mastery.MasteryState.MAINTENANCE_ONLY
        );
    }
}
