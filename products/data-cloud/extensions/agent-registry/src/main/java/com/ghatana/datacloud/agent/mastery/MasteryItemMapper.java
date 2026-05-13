/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.mastery.*;
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
    private static final String FIELD_APPLICABILITY_DOMAIN_CONSTRAINTS = "applicabilityDomainConstraints";
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
    private static final String FIELD_STATE_HISTORY = "stateHistory";
    private static final String FIELD_LAST_VERIFIED_AT = "lastVerifiedAt";
    private static final String FIELD_STALE_AFTER = "staleAfter";
    private static final String FIELD_LABELS = "labels";
    private static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    private static final String CURRENT_SCHEMA_VERSION = "1.0";

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

        // Schema version for backward compatibility
        data.put(FIELD_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION);

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
        data.put(FIELD_APPLICABILITY_DOMAIN_CONSTRAINTS, new HashMap<>(item.applicability().domainConstraints()));

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

        // State history (append-only transition log)
        data.put(FIELD_STATE_HISTORY, serializeStateHistory(item.stateHistory()));

        // Timestamps
        data.put(FIELD_LAST_VERIFIED_AT, item.lastVerifiedAt().toString());
        data.put(FIELD_STALE_AFTER, item.staleAfter().toString());

        // Labels
        data.put(FIELD_LABELS, new HashMap<>(item.labels()));

        // Scalar confidence
        data.put("confidence", item.confidence());

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
        // Schema version for backward compatibility
        String schemaVersion = (String) data.getOrDefault(FIELD_SCHEMA_VERSION, "0.0");

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
        Map<String, String> domainConstraints = toStringMap(data.get(FIELD_APPLICABILITY_DOMAIN_CONSTRAINTS));
        ApplicabilityScope applicability = new ApplicabilityScope(
                tenantId != null ? tenantId : "",
                environment != null ? environment : "",
                domainConstraints
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

        // IDs — all are List<String>, not Maps
        List<String> procedureIds = toStringList(data.get(FIELD_PROCEDURE_IDS));
        List<String> semanticFactIds = toStringList(data.get(FIELD_SEMANTIC_FACT_IDS));
        List<String> negativeKnowledgeIds = toStringList(data.get(FIELD_NEGATIVE_KNOWLEDGE_IDS));
        List<String> evidenceRefs = toStringList(data.get(FIELD_EVIDENCE_REFS));
        List<String> evaluationRefs = toStringList(data.get(FIELD_EVALUATION_REFS));
        List<String> knownFailureModeIds = toStringList(data.get(FIELD_KNOWN_FAILURE_MODE_IDS));

        // State history
        List<MasteryTransition> stateHistory = deserializeStateHistory(
                data.getOrDefault(FIELD_STATE_HISTORY, List.of()));

        // Labels
        @SuppressWarnings("unchecked")
        Map<String, String> labels = data.get(FIELD_LABELS) instanceof Map<?, ?> m
                ? (Map<String, String>) m
                : Map.of();

        // Parse state with backward-compatible default
        String stateStr = strOrDefault(data, FIELD_STATE, "UNKNOWN");
        MasteryState state;
        try {
            state = MasteryState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            // Backward compatibility: unknown states default to OBSERVED
            state = MasteryState.OBSERVED;
        }

        return new MasteryItem(
                strOrDefault(data, FIELD_MASTERY_ID, "unknown-mastery-" + UUID.randomUUID()),
                tenantId != null ? tenantId : "",
                strOrDefault(data, FIELD_SKILL_ID, "unknown-skill"),
                strOrDefault(data, FIELD_DOMAIN, "unknown"),
                strOrDefault(data, FIELD_AGENT_ID, "unknown-agent"),
                strOrDefault(data, FIELD_AGENT_RELEASE_ID, ""),
                state,
                versionScope,
                applicability,
                masteryScore,
                procedureIds,
                semanticFactIds,
                negativeKnowledgeIds,
                evidenceRefs,
                evaluationRefs,
                knownFailureModeIds,
                stateHistory,
                parseInstant(data.get(FIELD_LAST_VERIFIED_AT)),
                parseInstant(data.get(FIELD_STALE_AFTER)),
                labels,
                ((Number) data.getOrDefault("confidence", 0.0)).doubleValue()
        );
    }

    /**
     * Serializes the state history (list of MasteryTransition) to a list of data maps.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> serializeStateHistory(
            @NotNull List<MasteryTransition> history) {
        return history.stream()
                .map(t -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("transitionId", t.transitionId());
                    m.put("tenantId", t.tenantId());
                    m.put("masteryId", t.masteryId());
                    m.put("agentId", t.agentId());
                    m.put("agentReleaseId", t.agentReleaseId());
                    if (t.skillId() != null) m.put("skillId", t.skillId());
                    m.put("fromState", t.fromState().name());
                    m.put("toState", t.toState().name());
                    m.put("reason", t.reason());
                    m.put("initiatedBy", t.initiatedBy());
                    m.put("transitionedAt", t.transitionedAt().toString());
                    m.put("evidenceRefs", new HashMap<>(t.evidenceRefs()));
                    m.put("metadata", new HashMap<>(t.metadata()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Deserializes the state history from a raw list of data maps.
     */
    @SuppressWarnings("unchecked")
    private static List<MasteryTransition> deserializeStateHistory(@NotNull Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MasteryTransition> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) continue;
            try {
                result.add(new MasteryTransition(
                        strOrEmpty(m, "transitionId"),
                        strOrEmpty(m, "tenantId"),
                        strOrEmpty(m, "masteryId"),
                        strOrEmpty(m, "agentId"),
                        strOrEmpty(m, "agentReleaseId"),
                        strOrNull(m, "skillId"),
                        MasteryState.valueOf(strOrDefault(m, "fromState", "UNKNOWN")),
                        MasteryState.valueOf(strOrDefault(m, "toState", "UNKNOWN")),
                        strOrEmpty(m, "reason"),
                        strOrEmpty(m, "initiatedBy"),
                        parseInstant(m.get("transitionedAt")),
                        toStringMap(m.get("evidenceRefs")),
                        toStringMap(m.get("metadata"))
                ));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed transition entries rather than failing the load
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Serializes version constraints to a list of maps.
     */
    private static List<Map<String, String>> serializeVersionConstraints(List<VersionConstraint> constraints) {
        return constraints.stream()
                .map(c -> Map.of(
                        "kind", c.kind(),
                        "name", c.name(),
                        "range", c.range(),
                        "ecosystem", c.ecosystem()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Deserializes version constraints from a list of maps.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> deserializeVersionConstraints(List<?> constraints) {
        return constraints.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> Map.of(
                        "kind", (String) m.getOrDefault("kind", ""),
                        "name", (String) m.getOrDefault("name", ""),
                        "range", (String) m.getOrDefault("range", ""),
                        "ecosystem", (String) m.getOrDefault("ecosystem", "")
                ))
                .collect(Collectors.toList());
    }

    /**
     * Converts serialized version constraints to VersionConstraint objects.
     */
    private static List<VersionConstraint> toVersionConstraints(List<Map<String, String>> serialized) {
        return serialized.stream()
                .map(m -> new VersionConstraint(
                        m.get("kind"),
                        m.get("name"),
                        m.get("range"),
                        m.get("ecosystem")
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

    /** Extracts a String from a wildcard map by key, returning {@code ""} when absent or non-String. */
    private static String strOrEmpty(@NotNull Map<?, ?> map, @NotNull String key) {
        return map.get(key) instanceof String s ? s : "";
    }

    /** Extracts a String from a wildcard map by key, returning {@code defaultValue} when absent or non-String. */
    private static String strOrDefault(@NotNull Map<?, ?> map, @NotNull String key, @NotNull String defaultValue) {
        return map.get(key) instanceof String s ? s : defaultValue;
    }

    /** Extracts a nullable String from a wildcard map by key, returning {@code null} when absent or non-String. */
    @Nullable
    private static String strOrNull(@NotNull Map<?, ?> map, @NotNull String key) {
        return map.get(key) instanceof String s ? s : null;
    }
}
