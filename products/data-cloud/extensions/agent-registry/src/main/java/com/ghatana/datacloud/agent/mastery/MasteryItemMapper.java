/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.*;
import com.ghatana.agent.mode.ExecutionMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper for converting between MasteryItem domain model and Data Cloud entity format.
 *
 * @doc.type class
 * @doc.purpose Mapper for MasteryItem to/from Data Cloud entities
 * @doc.layer data-cloud
 * @doc.pattern Mapper
 */
public final class MasteryItemMapper {

    private static final String FIELD_MASTERY_ID = "masteryId";
    private static final String FIELD_SKILL_ID = "skillId";
    private static final String FIELD_DOMAIN = "domain";
    private static final String FIELD_AGENT_ID = "agentId";
    private static final String FIELD_AGENT_RELEASE_ID = "agentReleaseId";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_VERSION_SCOPE_ACTIVE = "versionScopeActive";
    private static final String FIELD_VERSION_SCOPE_MAINTENANCE = "versionScopeMaintenance";
    private static final String FIELD_VERSION_SCOPE_OBSOLETE = "versionScopeObsolete";
    private static final String FIELD_APPLICABILITY_TENANT_ID = "applicabilityTenantId";
    private static final String FIELD_APPLICABILITY_ENVIRONMENT = "applicabilityEnvironment";
    private static final String FIELD_SCORE_CORRECTNESS = "scoreCorrectness";
    private static final String FIELD_SCORE_FRESHNESS = "scoreFreshness";
    private static final String FIELD_SCORE_APPLICABILITY = "scoreApplicability";
    private static final String FIELD_SCORE_SAFETY = "scoreSafety";
    private static final String FIELD_SCORE_TRANSFERABILITY = "scoreTransferability";
    private static final String FIELD_SCORE_EVIDENCE_STRENGTH = "scoreEvidenceStrength";
    private static final String FIELD_SCORE_REGRESSION_STABILITY = "scoreRegressionStability";
    private static final String FIELD_PROCEDURE_IDS = "procedureIds";
    private static final String FIELD_SEMANTIC_FACT_IDS = "semanticFactIds";
    private static final String FIELD_NEGATIVE_KNOWLEDGE_IDS = "negativeKnowledgeIds";
    private static final String FIELD_EVIDENCE_REFS = "evidenceRefs";
    private static final String FIELD_EVALUATION_REFS = "evaluationRefs";
    private static final String FIELD_KNOWN_FAILURE_MODE_IDS = "knownFailureModeIds";
    private static final String FIELD_LAST_VERIFIED_AT = "lastVerifiedAt";
    private static final String FIELD_STALE_AFTER = "staleAfter";
    private static final String FIELD_LABELS = "labels";

    private MasteryItemMapper() {
        // Utility class
    }

    /**
     * Converts a MasteryItem to a Data Cloud entity data map.
     *
     * @param item mastery item
     * @return data map for Data Cloud entity
     */
    @NotNull
    public static Map<String, Object> toDataMap(@NotNull MasteryItem item) {
        Map<String, Object> data = new HashMap<>();

        data.put(FIELD_MASTERY_ID, item.masteryId());
        data.put(FIELD_SKILL_ID, item.skillId());
        data.put(FIELD_DOMAIN, item.domain());
        data.put(FIELD_AGENT_ID, item.agentId());
        data.put(FIELD_AGENT_RELEASE_ID, item.agentReleaseId());
        data.put(FIELD_STATE, item.state().name());

        // Version scope
        data.put(FIELD_VERSION_SCOPE_ACTIVE, serializeVersionConstraints(item.versionScope().active()));
        data.put(FIELD_VERSION_SCOPE_MAINTENANCE, serializeVersionConstraints(item.versionScope().maintenance()));
        data.put(FIELD_VERSION_SCOPE_OBSOLETE, serializeVersionConstraints(item.versionScope().obsolete()));

        // Applicability
        data.put(FIELD_APPLICABILITY_TENANT_ID, item.applicability().tenantId());
        data.put(FIELD_APPLICABILITY_ENVIRONMENT, item.applicability().environment());

        // Confidence vector
        data.put(FIELD_SCORE_CORRECTNESS, item.score().correctness());
        data.put(FIELD_SCORE_FRESHNESS, item.score().freshness());
        data.put(FIELD_SCORE_APPLICABILITY, item.score().applicability());
        data.put(FIELD_SCORE_SAFETY, item.score().safety());
        data.put(FIELD_SCORE_TRANSFERABILITY, item.score().transferability());
        data.put(FIELD_SCORE_EVIDENCE_STRENGTH, item.score().evidenceStrength());
        data.put(FIELD_SCORE_REGRESSION_STABILITY, item.score().regressionStability());

        // IDs
        data.put(FIELD_PROCEDURE_IDS, item.procedureIds());
        data.put(FIELD_SEMANTIC_FACT_IDS, item.semanticFactIds());
        data.put(FIELD_NEGATIVE_KNOWLEDGE_IDS, item.negativeKnowledgeIds());
        data.put(FIELD_EVIDENCE_REFS, item.evidenceRefs());
        data.put(FIELD_EVALUATION_REFS, item.evaluationRefs());
        data.put(FIELD_KNOWN_FAILURE_MODE_IDS, item.knownFailureModeIds());

        // Timestamps
        data.put(FIELD_LAST_VERIFIED_AT, item.lastVerifiedAt().toString());
        data.put(FIELD_STALE_AFTER, item.staleAfter().toString());

        // Labels
        data.put(FIELD_LABELS, new HashMap<>(item.labels()));

        return data;
    }

    /**
     * Converts a Data Cloud entity data map to a MasteryItem.
     *
     * @param data data map from Data Cloud entity
     * @return mastery item
     */
    @NotNull
    public static MasteryItem fromDataMap(@NotNull Map<String, Object> data) {
        // Version scope
        List<Map<String, String>> activeConstraints = deserializeVersionConstraints(
                (List<?>) data.getOrDefault(FIELD_VERSION_SCOPE_ACTIVE, List.of()));
        List<Map<String, String>> maintenanceConstraints = deserializeVersionConstraints(
                (List<?>) data.getOrDefault(FIELD_VERSION_SCOPE_MAINTENANCE, List.of()));
        List<Map<String, String>> obsoleteConstraints = deserializeVersionConstraints(
                (List<?>) data.getOrDefault(FIELD_VERSION_SCOPE_OBSOLETE, List.of()));

        VersionScope versionScope = new VersionScope(
                toVersionConstraints(activeConstraints),
                toVersionConstraints(maintenanceConstraints),
                toVersionConstraints(obsoleteConstraints)
        );

        // Applicability
        String tenantId = (String) data.get(FIELD_APPLICABILITY_TENANT_ID);
        String environment = (String) data.get(FIELD_APPLICABILITY_ENVIRONMENT);
        ApplicabilityScope applicability = new ApplicabilityScope(
                tenantId != null ? tenantId : "",
                environment != null ? environment : "",
                Map.of()
        );

        // Confidence vector
        ConfidenceVector score = new ConfidenceVector(
                ((Number) data.getOrDefault(FIELD_SCORE_CORRECTNESS, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_FRESHNESS, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_APPLICABILITY, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_SAFETY, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_TRANSFERABILITY, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_EVIDENCE_STRENGTH, 0.0)).doubleValue(),
                ((Number) data.getOrDefault(FIELD_SCORE_REGRESSION_STABILITY, 0.0)).doubleValue()
        );

        // Convert ConfidenceVector to MasteryScore
        MasteryScore masteryScore = new MasteryScore(
                score.correctness(),
                score.freshness(),
                score.applicability(),
                score.safety(),
                score.transferability(),
                score.evidenceStrength(),
                score.regressionStability()
        );

        // IDs
        List<String> procedureIds = toStringList(data.get(FIELD_PROCEDURE_IDS));
        List<String> semanticFactIds = toStringList(data.get(FIELD_SEMANTIC_FACT_IDS));
        List<String> negativeKnowledgeIds = toStringList(data.get(FIELD_NEGATIVE_KNOWLEDGE_IDS));
        Map<String, String> evidenceRefsMap = toStringMap(data.get(FIELD_EVIDENCE_REFS));
        List<String> evidenceRefs = evidenceRefsMap.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .toList();
        List<String> evaluationRefs = toStringList(data.get(FIELD_EVALUATION_REFS));
        List<String> knownFailureModeIds = toStringList(data.get(FIELD_KNOWN_FAILURE_MODE_IDS));

        // Labels
        @SuppressWarnings("unchecked")
        Map<String, String> labels = data.get(FIELD_LABELS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        return new MasteryItem(
                (String) data.get(FIELD_MASTERY_ID),
                (String) data.get(FIELD_SKILL_ID),
                (String) data.get(FIELD_DOMAIN),
                (String) data.get(FIELD_AGENT_ID),
                (String) data.get(FIELD_AGENT_RELEASE_ID),
                MasteryState.valueOf((String) data.get(FIELD_STATE)),
                versionScope,
                applicability,
                masteryScore,
                procedureIds,
                semanticFactIds,
                negativeKnowledgeIds,
                evidenceRefs,
                evaluationRefs,
                knownFailureModeIds,
                parseInstant(data.get(FIELD_LAST_VERIFIED_AT)),
                parseInstant(data.get(FIELD_STALE_AFTER)),
                labels
        );
    }

    /**
     * Serializes version constraints to a list of maps.
     */
    private static List<Map<String, String>> serializeVersionConstraints(List<VersionConstraint> constraints) {
        return constraints.stream()
                .map(c -> Map.of(
                        "type", c.type(),
                        "constraint", c.constraint(),
                        "description", c.description()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Deserializes version constraints from a list of maps.
     */
    private static List<Map<String, String>> deserializeVersionConstraints(List<?> constraints) {
        return constraints.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> Map.of(
                        "type", (String) m.get("type"),
                        "constraint", (String) m.get("constraint"),
                        "description", (String) m.get("description")
                ))
                .collect(Collectors.toList());
    }

    /**
     * Converts serialized version constraints to VersionConstraint objects.
     */
    private static List<VersionConstraint> toVersionConstraints(List<Map<String, String>> serialized) {
        return serialized.stream()
                .map(m -> new VersionConstraint(
                        m.get("type"),
                        m.get("constraint"),
                        m.get("description")
                ))
                .collect(Collectors.toList());
    }

    /**
     * Converts an object to a list of strings.
     */
    @SuppressWarnings("unchecked")
    private static List<String> toStringList(@Nullable Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /**
     * Converts an object to a map of strings.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringMap(@Nullable Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key && entry.getValue() instanceof String val) {
                    result.put(key, val);
                }
            }
            return result;
        }
        return Map.of();
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
