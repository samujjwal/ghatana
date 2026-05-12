/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.promotion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata tracking the promotion lifecycle of a memory item.
 *
 * @doc.type record
 * @doc.purpose Promotion metadata for memory items
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MemoryPromotionMetadata(
        @NotNull String metadataId,
        @NotNull String memoryItemId,
        @NotNull ValidationState validationState,
        @NotNull PromotionState promotionState,
        @NotNull String promotedBy,
        @NotNull Instant proposedAt,
        @Nullable Instant validatedAt,
        @Nullable Instant promotedAt,
        @Nullable String rejectionReason,
        @NotNull Map<String, String> labels
) {
    public MemoryPromotionMetadata {
        Objects.requireNonNull(metadataId, "metadataId must not be null");
        Objects.requireNonNull(memoryItemId, "memoryItemId must not be null");
        Objects.requireNonNull(validationState, "validationState must not be null");
        Objects.requireNonNull(promotionState, "promotionState must not be null");
        Objects.requireNonNull(promotedBy, "promotedBy must not be null");
        Objects.requireNonNull(proposedAt, "proposedAt must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        labels = Map.copyOf(labels);
    }

    /**
     * Creates new promotion metadata for a proposed memory item.
     *
     * @param memoryItemId memory item identifier
     * @param promotedBy user or system proposing the promotion
     * @return promotion metadata
     */
    @NotNull
    public static MemoryPromotionMetadata proposed(@NotNull String memoryItemId, @NotNull String promotedBy) {
        return new MemoryPromotionMetadata(
                java.util.UUID.randomUUID().toString(),
                memoryItemId,
                ValidationState.NOT_STARTED,
                PromotionState.PENDING_VALIDATION,
                promotedBy,
                Instant.now(),
                null,
                null,
                null,
                Map.of()
        );
    }

    /**
     * Returns true if the promotion is complete (either approved or rejected).
     *
     * @return true if promotion is complete
     */
    public boolean isComplete() {
        return promotionState == PromotionState.APPROVED || promotionState == PromotionState.REJECTED;
    }

    /**
     * Returns true if the promotion is approved.
     *
     * @return true if approved
     */
    public boolean isApproved() {
        return promotionState == PromotionState.APPROVED;
    }

    /**
     * Returns true if validation has passed.
     *
     * @return true if validation passed
     */
    public boolean isValidationPassed() {
        return validationState == ValidationState.PASSED;
    }
}
