/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.mastery.MasteryState;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a promotion operation.
 *
 * @doc.type record
 * @doc.purpose Result of promotion operation
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record PromotionResult(
        @NotNull String deltaId,
        @NotNull String masteryId,
        boolean success,
        @NotNull MasteryState previousState,
        @NotNull MasteryState newState,
        @NotNull Instant promotedAt,
        @NotNull Map<String, String> metadata,
        @NotNull Optional<String> errorMessage
) {
    public PromotionResult {
        if (success && errorMessage.isPresent()) {
            throw new IllegalArgumentException("errorMessage must be empty when success is true");
        }
        Objects.requireNonNull(deltaId, "deltaId must not be null");
        Objects.requireNonNull(masteryId, "masteryId must not be null");
        Objects.requireNonNull(previousState, "previousState must not be null");
        Objects.requireNonNull(newState, "newState must not be null");
        Objects.requireNonNull(promotedAt, "promotedAt must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a successful promotion result.
     *
     * @param deltaId delta identifier
     * @param masteryId mastery item identifier
     * @param previousState previous mastery state
     * @param newState new mastery state
     * @return successful promotion result
     */
    @NotNull
    public static PromotionResult success(
            @NotNull String deltaId,
            @NotNull String masteryId,
            @NotNull MasteryState previousState,
            @NotNull MasteryState newState
    ) {
        return new PromotionResult(
                deltaId,
                masteryId,
                true,
                previousState,
                newState,
                Instant.now(),
                Map.of(),
                Optional.empty()
        );
    }

    /**
     * Creates a failed promotion result.
     *
     * @param deltaId delta identifier
     * @param masteryId mastery item identifier
     * @param previousState previous mastery state (unchanged)
     * @param errorMessage error message
     * @return failed promotion result
     */
    @NotNull
    public static PromotionResult failure(
            @NotNull String deltaId,
            @NotNull String masteryId,
            @NotNull MasteryState previousState,
            @NotNull String errorMessage
    ) {
        return new PromotionResult(
                deltaId,
                masteryId,
                false,
                previousState,
                previousState,
                Instant.now(),
                Map.of(),
                Optional.of(errorMessage)
        );
    }
}
