/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for converting between MasteryEvidence domain model and Data Cloud entity format.
 *
 * @doc.type class
 * @doc.purpose Mapper for MasteryEvidence to/from Data Cloud entities
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class MasteryEvidenceMapper {

    private static final String FIELD_EVIDENCE_ID = "evidenceId";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_REF = "ref";
    private static final String FIELD_DIGEST = "digest";
    private static final String FIELD_CREATED_AT = "createdAt";
    private static final String FIELD_CREATED_BY = "createdBy";
    private static final String FIELD_WEIGHT = "weight";
    private static final String FIELD_LABELS = "labels";
    private static final String FIELD_TENANT_ID = "tenantId";

    private MasteryEvidenceMapper() {
        // Utility class
    }

    /**
     * Converts a MasteryEvidence to a Data Cloud entity data map.
     *
     * @param evidence mastery evidence
     * @return data map for Data Cloud entity
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull MasteryEvidence evidence) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_EVIDENCE_ID, evidence.evidenceId());
        data.put(FIELD_TYPE, evidence.type().name());
        data.put(FIELD_REF, evidence.ref());
        data.put(FIELD_DIGEST, evidence.digest());
        data.put(FIELD_CREATED_AT, evidence.createdAt().toString());
        data.put(FIELD_CREATED_BY, evidence.createdBy());
        data.put(FIELD_WEIGHT, evidence.weight());
        data.put(FIELD_LABELS, new HashMap<>(evidence.labels()));
        return data;
    }

    /**
     * Converts a Data Cloud entity data map to a MasteryEvidence.
     *
     * @param data data map from Data Cloud entity
     * @return mastery evidence
     */
    @NotNull
    public static MasteryEvidence fromDataMap(@NotNull Map<String, Object> data) {
        String type = (String) data.get(FIELD_TYPE);
        String ref = (String) data.get(FIELD_REF);
        String digest = (String) data.get(FIELD_DIGEST);
        String createdBy = (String) data.get(FIELD_CREATED_BY);
        double weight = ((Number) data.getOrDefault(FIELD_WEIGHT, 1.0)).doubleValue();

        @SuppressWarnings("unchecked")
        Map<String, String> labels = data.get(FIELD_LABELS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        return new MasteryEvidence(
                (String) data.get(FIELD_EVIDENCE_ID),
                MasteryEvidenceType.valueOf(type),
                ref,
                digest,
                parseInstant(data.get(FIELD_CREATED_AT)),
                createdBy,
                weight,
                labels
        );
    }

    /**
     * Parses an instant from an object.
     */
    private static Instant parseInstant(@Nullable Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof String s) {
            return Instant.parse(s);
        }
        return Instant.now();
    }
}
