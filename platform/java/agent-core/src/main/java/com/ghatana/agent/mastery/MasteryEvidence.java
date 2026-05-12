/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Evidence supporting a mastery state transition.
 *
 * @doc.type record
 * @doc.purpose Evidence for mastery transitions
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryEvidence(
        @NotNull String evidenceId,
        @NotNull MasteryEvidenceType type,
        @NotNull String ref,
        @NotNull String digest,
        @NotNull Instant createdAt,
        @NotNull String createdBy,
        double weight,
        @NotNull Map<String, String> labels
) {
    public MasteryEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(digest, "digest must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(createdBy, "createdBy must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        if (weight < 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be between 0.0 and 1.0");
        }
        labels = Map.copyOf(labels);
    }

    /**
     * Creates a new evidence with a generated ID and current timestamp.
     *
     * @param type evidence type
     * @param ref reference to the evidence source
     * @param createdBy creator of the evidence
     * @return new mastery evidence
     */
    @NotNull
    public static MasteryEvidence create(
            @NotNull MasteryEvidenceType type,
            @NotNull String ref,
            @NotNull String createdBy
    ) {
        return new MasteryEvidence(
                java.util.UUID.randomUUID().toString(),
                type,
                ref,
                "", // digest would be computed from actual content
                Instant.now(),
                createdBy,
                1.0,
                Map.of()
        );
    }

    /**
     * Creates a new evidence with custom weight.
     *
     * @param type evidence type
     * @param ref reference to the evidence source
     * @param createdBy creator of the evidence
     * @param weight evidence weight (0.0 to 1.0)
     * @return new mastery evidence
     */
    @NotNull
    public static MasteryEvidence create(
            @NotNull MasteryEvidenceType type,
            @NotNull String ref,
            @NotNull String createdBy,
            double weight
    ) {
        return new MasteryEvidence(
                java.util.UUID.randomUUID().toString(),
                type,
                ref,
                "",
                Instant.now(),
                createdBy,
                weight,
                Map.of()
        );
    }
}
