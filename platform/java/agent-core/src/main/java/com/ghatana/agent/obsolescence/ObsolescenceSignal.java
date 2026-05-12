/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Signal indicating potential obsolescence of a mastery item.
 *
 * @doc.type record
 * @doc.purpose Obsolescence signal for mastery items
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ObsolescenceSignal(
        @NotNull String signalId,
        @NotNull String masteryItemId,
        @NotNull ObsolescenceSignalType signalType,
        @NotNull String source,
        @NotNull Instant detectedAt,
        @NotNull String description,
        @NotNull Map<String, String> metadata,
        double severity
) {
    public ObsolescenceSignal {
        Objects.requireNonNull(signalId, "signalId must not be null");
        Objects.requireNonNull(masteryItemId, "masteryItemId must not be null");
        Objects.requireNonNull(signalType, "signalType must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates an obsolescence signal.
     *
     * @param masteryItemId mastery item identifier
     * @param signalType signal type
     * @param source signal source
     * @param description signal description
     * @return obsolescence signal
     */
    @NotNull
    public static ObsolescenceSignal of(
            @NotNull String masteryItemId,
            @NotNull ObsolescenceSignalType signalType,
            @NotNull String source,
            @NotNull String description
    ) {
        return new ObsolescenceSignal(
                java.util.UUID.randomUUID().toString(),
                masteryItemId,
                signalType,
                source,
                Instant.now(),
                description,
                Map.of(),
                0.5
        );
    }

    /**
     * Creates an obsolescence signal with custom severity.
     *
     * @param masteryItemId mastery item identifier
     * @param signalType signal type
     * @param source signal source
     * @param description signal description
     * @param severity signal severity (0.0 to 1.0)
     * @return obsolescence signal
     */
    @NotNull
    public static ObsolescenceSignal withSeverity(
            @NotNull String masteryItemId,
            @NotNull ObsolescenceSignalType signalType,
            @NotNull String source,
            @NotNull String description,
            double severity
    ) {
        return new ObsolescenceSignal(
                java.util.UUID.randomUUID().toString(),
                masteryItemId,
                signalType,
                source,
                Instant.now(),
                description,
                Map.of(),
                severity
        );
    }

    /**
     * Returns true if this signal is high severity.
     *
     * @return true if high severity
     */
    public boolean isHighSeverity() {
        return severity >= 0.7;
    }
}
