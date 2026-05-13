/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Event indicating that a mastery item has been detected as obsolete.
 *
 * @doc.type record
 * @doc.purpose Event for obsolescence detection
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ObsolescenceEvent(
        @NotNull String eventId,
        @NotNull String masteryId,
        @NotNull String tenantId,
        @NotNull ObsolescenceReason reason,
        @NotNull String description,
        @NotNull Instant detectedAt,
        @NotNull List<ObsolescenceEvidenceRef> evidenceRefs,
        @NotNull Map<String, String> metadata,
        @NotNull Severity severity,
        @NotNull MasteryState recommendedTransition
) {
    public ObsolescenceEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(recommendedTransition, "recommendedTransition must not be null");
        evidenceRefs = List.copyOf(evidenceRefs);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Severity levels for obsolescence events.
     */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Creates an obsolescence event for a mastery item.
     *
     * @param masteryId mastery item identifier
     * @param tenantId tenant identifier
     * @param reason obsolescence reason
     * @param description description of the obsolescence
     * @return obsolescence event
     */
    @NotNull
    public static ObsolescenceEvent of(
            @NotNull String masteryId,
            @NotNull String tenantId,
            @NotNull ObsolescenceReason reason,
            @NotNull String description
    ) {
        return new ObsolescenceEvent(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                tenantId,
                reason,
                description,
                Instant.now(),
                List.of(),
                Map.of(),
                Severity.MEDIUM,
                MasteryState.MAINTENANCE_ONLY
        );
    }

    /**
     * Creates an obsolescence event with evidence references.
     *
     * @param masteryId mastery item identifier
     * @param tenantId tenant identifier
     * @param reason obsolescence reason
     * @param description description of the obsolescence
     * @param evidenceRefs evidence references supporting the detection
     * @return obsolescence event
     */
    @NotNull
    public static ObsolescenceEvent of(
            @NotNull String masteryId,
            @NotNull String tenantId,
            @NotNull ObsolescenceReason reason,
            @NotNull String description,
            @NotNull List<ObsolescenceEvidenceRef> evidenceRefs
    ) {
        return new ObsolescenceEvent(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                tenantId,
                reason,
                description,
                Instant.now(),
                evidenceRefs,
                Map.of(),
                Severity.MEDIUM,
                MasteryState.MAINTENANCE_ONLY
        );
    }

    /**
     * Creates an obsolescence event with severity and recommended transition.
     *
     * @param masteryId mastery item identifier
     * @param tenantId tenant identifier
     * @param reason obsolescence reason
     * @param description description of the obsolescence
     * @param evidenceRefs evidence references supporting the detection
     * @param metadata additional metadata
     * @param severity severity level
     * @param recommendedTransition recommended mastery state transition
     * @return obsolescence event
     */
    @NotNull
    public static ObsolescenceEvent of(
            @NotNull String masteryId,
            @NotNull String tenantId,
            @NotNull ObsolescenceReason reason,
            @NotNull String description,
            @NotNull List<ObsolescenceEvidenceRef> evidenceRefs,
            @NotNull Map<String, String> metadata,
            @NotNull Severity severity,
            @NotNull MasteryState recommendedTransition
    ) {
        return new ObsolescenceEvent(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                tenantId,
                reason,
                description,
                Instant.now(),
                evidenceRefs,
                metadata,
                severity,
                recommendedTransition
        );
    }
}
