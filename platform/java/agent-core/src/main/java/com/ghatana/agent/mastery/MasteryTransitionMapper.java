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
 * Mapper for converting between MasteryTransition domain model and Data Cloud entity format.
 *
 * @doc.type class
 * @doc.purpose Mapper for MasteryTransition to/from Data Cloud entities
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class MasteryTransitionMapper {

    private static final String FIELD_TRANSITION_ID = "transitionId";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_MASTERY_ID = "masteryId";
    private static final String FIELD_AGENT_ID = "agentId";
    private static final String FIELD_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String FIELD_SKILL_ID = "skillId";
    private static final String FIELD_FROM_STATE = "fromState";
    private static final String FIELD_TO_STATE = "toState";
    private static final String FIELD_REASON = "reason";
    private static final String FIELD_INITIATED_BY = "initiatedBy";
    private static final String FIELD_TRANSITIONED_AT = "transitionedAt";
    private static final String FIELD_EVIDENCE_REFS = "evidenceRefs";
    private static final String FIELD_METADATA = "metadata";

    private MasteryTransitionMapper() {
        // Utility class
    }

    /**
     * Converts a MasteryTransition to a Data Cloud entity data map.
     *
     * @param transition mastery transition
     * @return data map for Data Cloud entity
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull MasteryTransition transition) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_TRANSITION_ID, transition.transitionId());
        data.put(FIELD_MASTERY_ID, transition.masteryId());
        data.put(FIELD_FROM_STATE, transition.fromState().name());
        data.put(FIELD_TO_STATE, transition.toState().name());
        data.put(FIELD_REASON, transition.reason());
        data.put(FIELD_INITIATED_BY, transition.initiatedBy());
        data.put(FIELD_TRANSITIONED_AT, transition.transitionedAt().toString());
        data.put(FIELD_EVIDENCE_REFS, new HashMap<>(transition.evidenceRefs()));
        data.put(FIELD_METADATA, new HashMap<>(transition.metadata()));
        return data;
    }

    /**
     * Converts a Data Cloud entity data map to a MasteryTransition.
     *
     * @param data data map from Data Cloud entity
     * @return mastery transition
     */
    @NotNull
    public static MasteryTransition fromDataMap(@NotNull Map<String, Object> data) {
        String fromState = (String) data.get(FIELD_FROM_STATE);
        String toState = (String) data.get(FIELD_TO_STATE);

        @SuppressWarnings("unchecked")
        Map<String, String> evidenceRefs = data.get(FIELD_EVIDENCE_REFS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        @SuppressWarnings("unchecked")
        Map<String, String> metadata = data.get(FIELD_METADATA) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        return new MasteryTransition(
                (String) data.get(FIELD_TRANSITION_ID),
                (String) data.get(FIELD_TENANT_ID),
                (String) data.get(FIELD_MASTERY_ID),
                (String) data.get(FIELD_AGENT_ID),
                (String) data.get(FIELD_AGENT_RELEASE_ID),
                (String) data.get(FIELD_SKILL_ID),
                MasteryState.valueOf(fromState),
                MasteryState.valueOf(toState),
                (String) data.get(FIELD_REASON),
                (String) data.get(FIELD_INITIATED_BY),
                parseInstant(data.get(FIELD_TRANSITIONED_AT)),
                evidenceRefs,
                metadata
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
