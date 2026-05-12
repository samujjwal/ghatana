/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.promotion;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Mapper for converting MemoryPromotionMetadata to/from Data Cloud entity format.
 *
 * @doc.type class
 * @doc.purpose Mapper for MemoryPromotionMetadata
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class MemoryPromotionMetadataMapper {

    private static final String FIELD_METADATA_ID = "metadataId";
    private static final String FIELD_MEMORY_ITEM_ID = "memoryItemId";
    private static final String FIELD_VALIDATION_STATE = "validationState";
    private static final String FIELD_PROMOTION_STATE = "promotionState";
    private static final String FIELD_PROMOTED_BY = "promotedBy";
    private static final String FIELD_PROPOSED_AT = "proposedAt";
    private static final String FIELD_VALIDATED_AT = "validatedAt";
    private static final String FIELD_PROMOTED_AT = "promotedAt";
    private static final String FIELD_REJECTION_REASON = "rejectionReason";
    private static final String FIELD_LABELS = "labels";

    private MemoryPromotionMetadataMapper() {
        // Utility class
    }

    /**
     * Converts MemoryPromotionMetadata to a Data Cloud entity data map.
     *
     * @param metadata promotion metadata
     * @return data map for Data Cloud entity
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull MemoryPromotionMetadata metadata) {
        Map<String, Object> data = new java.util.HashMap<>();

        data.put(FIELD_METADATA_ID, metadata.metadataId());
        data.put(FIELD_MEMORY_ITEM_ID, metadata.memoryItemId());
        data.put(FIELD_VALIDATION_STATE, metadata.validationState().name());
        data.put(FIELD_PROMOTION_STATE, metadata.promotionState().name());
        data.put(FIELD_PROMOTED_BY, metadata.promotedBy());
        data.put(FIELD_PROPOSED_AT, metadata.proposedAt().toString());

        if (metadata.validatedAt() != null) {
            data.put(FIELD_VALIDATED_AT, metadata.validatedAt().toString());
        }
        if (metadata.promotedAt() != null) {
            data.put(FIELD_PROMOTED_AT, metadata.promotedAt().toString());
        }
        if (metadata.rejectionReason() != null) {
            data.put(FIELD_REJECTION_REASON, metadata.rejectionReason());
        }

        data.put(FIELD_LABELS, new java.util.HashMap<>(metadata.labels()));

        return data;
    }

    /**
     * Converts a Data Cloud entity data map to MemoryPromotionMetadata.
     *
     * @param data data map from Data Cloud entity
     * @return promotion metadata
     */
    @NotNull
    public static MemoryPromotionMetadata fromDataMap(@NotNull Map<String, Object> data) {
        ValidationState validationState = ValidationState.valueOf((String) data.get(FIELD_VALIDATION_STATE));
        PromotionState promotionState = PromotionState.valueOf((String) data.get(FIELD_PROMOTION_STATE));

        @SuppressWarnings("unchecked")
        Map<String, String> labels = data.get(FIELD_LABELS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        return new MemoryPromotionMetadata(
                (String) data.get(FIELD_METADATA_ID),
                (String) data.get(FIELD_MEMORY_ITEM_ID),
                validationState,
                promotionState,
                (String) data.get(FIELD_PROMOTED_BY),
                parseInstant(data.get(FIELD_PROPOSED_AT)),
                parseInstant(data.get(FIELD_VALIDATED_AT)),
                parseInstant(data.get(FIELD_PROMOTED_AT)),
                (String) data.get(FIELD_REJECTION_REASON),
                labels
        );
    }

    /**
     * Parses an instant from an object.
     */
    private static java.time.Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.Instant i) {
            return i;
        }
        if (value instanceof String s) {
            return java.time.Instant.parse(s);
        }
        return java.time.Instant.now();
    }
}
