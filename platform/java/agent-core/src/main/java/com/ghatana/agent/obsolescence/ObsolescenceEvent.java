/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

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
        @NotNull ObsolescenceReason reason,
        @NotNull String description,
        @NotNull Instant detectedAt,
        @NotNull List<ObsolescenceEvidenceRef> evidenceRefs,
        @NotNull Map<String, String> metadata
) {
    public ObsolescenceEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        Objects.requireNonNull(evidenceRefs, "evidenceRefs must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        evidenceRefs = List.copyOf(evidenceRefs);
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates an obsolescence event for a mastery item.
     *
     * @param masteryId mastery item identifier
     * @param reason obsolescence reason
     * @param description description of the obsolescence
     * @return obsolescence event
     */
    @NotNull
    public static ObsolescenceEvent of(
            @NotNull String masteryId,
            @NotNull ObsolescenceReason reason,
            @NotNull String description
    ) {
        return new ObsolescenceEvent(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                reason,
                description,
                Instant.now(),
                List.of(),
                Map.of()
        );
    }

    /**
     * Creates an obsolescence event with evidence references.
     *
     * @param masteryId mastery item identifier
     * @param reason obsolescence reason
     * @param description description of the obsolescence
     * @param evidenceRefs evidence references supporting the detection
     * @return obsolescence event
     */
    @NotNull
    public static ObsolescenceEvent of(
            @NotNull String masteryId,
            @NotNull ObsolescenceReason reason,
            @NotNull String description,
            @NotNull List<ObsolescenceEvidenceRef> evidenceRefs
    ) {
        return new ObsolescenceEvent(
                java.util.UUID.randomUUID().toString(),
                masteryId,
                reason,
                description,
                Instant.now(),
                evidenceRefs,
                Map.of()
        );
    }
}
