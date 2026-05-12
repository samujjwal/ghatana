/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningTarget;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapper for converting LearningDelta to/from Data Cloud entity data maps.
 *
 * @doc.type class
 * @doc.purpose Mapper for LearningDelta serialization/deserialization
 * @doc.layer data-cloud
 * @doc.pattern Mapper
 */
public final class LearningDeltaMapper {

    // Data Cloud entity field names
    private static final String FIELD_DELTA_ID = "deltaId";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_AGENT_ID = "agentId";
    private static final String FIELD_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String FIELD_SKILL_ID = "skillId";
    private static final String FIELD_PROCEDURE_ID = "procedureId";
    private static final String FIELD_SEMANTIC_FACT_ID = "semanticFactId";
    private static final String FIELD_NEGATIVE_KNOWLEDGE_ID = "negativeKnowledgeId";
    private static final String FIELD_CONTENT_DIGEST = "contentDigest";
    private static final String FIELD_PROPOSED_CONTENT = "proposedContent";
    private static final String FIELD_EVIDENCE_REFS = "evidenceRefs";
    private static final String FIELD_EVALUATION_REFS = "evaluationRefs";
    private static final String FIELD_SOURCE_EPISODE_IDS = "sourceEpisodeIds";
    private static final String FIELD_ROLLBACK_REF = "rollbackRef";
    private static final String FIELD_CONFIDENCE_BEFORE = "confidenceBefore";
    private static final String FIELD_CONFIDENCE_AFTER = "confidenceAfter";
    private static final String FIELD_REQUIRES_HUMAN_REVIEW = "requiresHumanReview";
    private static final String FIELD_PROPOSED_BY = "proposedBy";
    private static final String FIELD_PROPOSED_AT = "proposedAt";
    private static final String FIELD_EVALUATED_AT = "evaluatedAt";
    private static final String FIELD_PROMOTED_AT = "promotedAt";
    private static final String FIELD_REJECTED_AT = "rejectedAt";
    private static final String FIELD_LABELS = "labels";
    private static final String FIELD_REJECTION_REASON = "rejectionReason";

    private LearningDeltaMapper() {
        // Utility class
    }

    /**
     * Converts a LearningDelta to a Data Cloud entity data map.
     *
     * @param delta learning delta
     * @return data map for Data Cloud entity
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull LearningDelta delta) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_DELTA_ID, delta.deltaId());
        data.put(FIELD_TYPE, delta.type().name());
        data.put(FIELD_TARGET, delta.target().name());
        data.put(FIELD_STATE, delta.state().name());
        data.put(FIELD_AGENT_ID, delta.agentId());
        data.put(FIELD_AGENT_RELEASE_ID, delta.agentReleaseId());
        data.put(FIELD_SKILL_ID, delta.skillId());
        data.put(FIELD_PROCEDURE_ID, delta.procedureId());
        data.put(FIELD_SEMANTIC_FACT_ID, delta.semanticFactId());
        data.put(FIELD_NEGATIVE_KNOWLEDGE_ID, delta.negativeKnowledgeId());
        data.put(FIELD_CONTENT_DIGEST, delta.contentDigest());
        data.put(FIELD_PROPOSED_CONTENT, new HashMap<>(delta.proposedContent()));
        data.put(FIELD_EVIDENCE_REFS, List.copyOf(delta.evidenceRefs()));
        data.put(FIELD_EVALUATION_REFS, List.copyOf(delta.evaluationRefs()));
        data.put(FIELD_SOURCE_EPISODE_IDS, List.copyOf(delta.sourceEpisodeIds()));
        data.put(FIELD_ROLLBACK_REF, delta.rollbackRef());
        data.put(FIELD_CONFIDENCE_BEFORE, delta.confidenceBefore());
        data.put(FIELD_CONFIDENCE_AFTER, delta.confidenceAfter());
        data.put(FIELD_REQUIRES_HUMAN_REVIEW, delta.requiresHumanReview());
        data.put(FIELD_PROPOSED_BY, delta.proposedBy());
        data.put(FIELD_PROPOSED_AT, toEpochMilli(delta.proposedAt()));
        data.put(FIELD_EVALUATED_AT, toEpochMilli(delta.evaluatedAt()));
        data.put(FIELD_PROMOTED_AT, toEpochMilli(delta.promotedAt()));
        data.put(FIELD_REJECTED_AT, toEpochMilli(delta.rejectedAt()));
        data.put(FIELD_LABELS, new HashMap<>(delta.labels()));
        data.put(FIELD_REJECTION_REASON, delta.rejectionReason());
        return data;
    }

    /**
     * Converts a Data Cloud entity data map to a LearningDelta.
     *
     * @param data data map from Data Cloud entity
     * @return learning delta
     */
    @NotNull
    public static LearningDelta fromDataMap(@NotNull Map<String, Object> data) {
        String deltaId = (String) Objects.requireNonNull(data.get(FIELD_DELTA_ID), FIELD_DELTA_ID);
        LearningDeltaType type = LearningDeltaType.valueOf((String) Objects.requireNonNull(data.get(FIELD_TYPE), FIELD_TYPE));
        LearningTarget target = LearningTarget.valueOf((String) Objects.requireNonNull(data.get(FIELD_TARGET), FIELD_TARGET));
        LearningDeltaState state = LearningDeltaState.valueOf((String) Objects.requireNonNull(data.get(FIELD_STATE), FIELD_STATE));
        String agentId = (String) Objects.requireNonNull(data.get(FIELD_AGENT_ID), FIELD_AGENT_ID);
        String agentReleaseId = (String) Objects.requireNonNull(data.get(FIELD_AGENT_RELEASE_ID), FIELD_AGENT_RELEASE_ID);
        String skillId = (String) Objects.requireNonNull(data.get(FIELD_SKILL_ID), FIELD_SKILL_ID);
        String procedureId = (String) data.get(FIELD_PROCEDURE_ID);
        String semanticFactId = (String) data.get(FIELD_SEMANTIC_FACT_ID);
        String negativeKnowledgeId = (String) data.get(FIELD_NEGATIVE_KNOWLEDGE_ID);
        String contentDigest = (String) Objects.requireNonNull(data.get(FIELD_CONTENT_DIGEST), FIELD_CONTENT_DIGEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> proposedContent = toMap(data.get(FIELD_PROPOSED_CONTENT));
        @SuppressWarnings("unchecked")
        List<String> evidenceRefs = toStringList(data.get(FIELD_EVIDENCE_REFS));
        @SuppressWarnings("unchecked")
        List<String> evaluationRefs = toStringList(data.get(FIELD_EVALUATION_REFS));
        @SuppressWarnings("unchecked")
        List<String> sourceEpisodeIds = toStringList(data.get(FIELD_SOURCE_EPISODE_IDS));
        String rollbackRef = (String) data.get(FIELD_ROLLBACK_REF);
        double confidenceBefore = ((Number) Objects.requireNonNull(data.get(FIELD_CONFIDENCE_BEFORE), FIELD_CONFIDENCE_BEFORE)).doubleValue();
        double confidenceAfter = ((Number) Objects.requireNonNull(data.get(FIELD_CONFIDENCE_AFTER), FIELD_CONFIDENCE_AFTER)).doubleValue();
        boolean requiresHumanReview = (Boolean) data.getOrDefault(FIELD_REQUIRES_HUMAN_REVIEW, false);
        String proposedBy = (String) Objects.requireNonNull(data.get(FIELD_PROPOSED_BY), FIELD_PROPOSED_BY);
        Instant proposedAt = toInstant(data.get(FIELD_PROPOSED_AT));
        Instant evaluatedAt = toInstant(data.get(FIELD_EVALUATED_AT));
        Instant promotedAt = toInstant(data.get(FIELD_PROMOTED_AT));
        Instant rejectedAt = toInstant(data.get(FIELD_REJECTED_AT));
        @SuppressWarnings("unchecked")
        Map<String, String> labels = toStringMap(data.get(FIELD_LABELS));
        String rejectionReason = (String) data.get(FIELD_REJECTION_REASON);

        return new LearningDelta(
                deltaId,
                type,
                target,
                state,
                agentId,
                agentReleaseId,
                skillId,
                procedureId,
                semanticFactId,
                negativeKnowledgeId,
                contentDigest,
                proposedContent,
                evidenceRefs,
                evaluationRefs,
                sourceEpisodeIds,
                rollbackRef,
                confidenceBefore,
                confidenceAfter,
                requiresHumanReview,
                proposedBy,
                proposedAt,
                evaluatedAt,
                promotedAt,
                rejectedAt,
                labels,
                rejectionReason
        );
    }

    // Utility methods for type conversion

    private static Long toEpochMilli(Instant instant) {
        return instant != null ? instant.toEpochMilli() : null;
    }

    private static Instant toInstant(Object epochMilli) {
        if (epochMilli == null) {
            return null;
        }
        if (epochMilli instanceof Instant) {
            return (Instant) epochMilli;
        }
        return Instant.ofEpochMilli(((Number) epochMilli).longValue());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map) {
            return new HashMap<>((Map<String, Object>) value);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map) {
            return new HashMap<>((Map<String, String>) value);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List) {
            return List.copyOf((List<String>) value);
        }
        return List.of();
    }
}
