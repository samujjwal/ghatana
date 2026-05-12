/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.promotion;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Evidence collected during memory promotion to validate readiness for active use.
 *
 * @doc.type record
 * @doc.purpose Promotion evidence for memory items
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record PromotionEvidence(
        @NotNull String evidenceId,
        @NotNull String memoryItemId,
        @NotNull PromotionState state,
        @NotNull String validationSource,
        @NotNull Instant validatedAt,
        @NotNull Map<String, String> metadata
) {
    public PromotionEvidence {
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
        Objects.requireNonNull(memoryItemId, "memoryItemId must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(validationSource, "validationSource must not be null");
        Objects.requireNonNull(validatedAt, "validatedAt must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates promotion evidence with validation state.
     *
     * @param memoryItemId memory item identifier
     * @param state promotion state
     * @param validationSource validation source
     * @return promotion evidence
     */
    @NotNull
    public static PromotionEvidence of(
            @NotNull String memoryItemId,
            @NotNull PromotionState state,
            @NotNull String validationSource
    ) {
        return new PromotionEvidence(
                java.util.UUID.randomUUID().toString(),
                memoryItemId,
                state,
                validationSource,
                Instant.now(),
                Map.of()
        );
    }

    /**
     * Returns true if the promotion is approved.
     *
     * @return true if approved
     */
    public boolean isApproved() {
        return state == PromotionState.APPROVED;
    }

    /**
     * Returns true if the promotion is rejected.
     *
     * @return true if rejected
     */
    public boolean isRejected() {
        return state == PromotionState.REJECTED;
    }

    /**
     * Returns true if the promotion is pending validation.
     *
     * @return true if pending
     */
    public boolean isPending() {
        return state == PromotionState.PENDING_VALIDATION;
    }
}
