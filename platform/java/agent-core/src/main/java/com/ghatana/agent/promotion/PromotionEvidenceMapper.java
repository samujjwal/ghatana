/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.promotion;

import com.ghatana.agent.learning.LearningDelta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper for converting learning deltas to promotion evidence maps.
 *
 * <p>This mapper provides consistent serialization of learning delta evidence
 * for promotion transitions, ensuring all evidence types are properly mapped
 * with standardized keys.
 *
 * @doc.type class
 * @doc.purpose Mapper for learning delta to promotion evidence conversion
 * @doc.layer agent-core
 * @doc.pattern Mapper
 */
public final class PromotionEvidenceMapper {

    private PromotionEvidenceMapper() {
        // Utility class - private constructor
    }

    /**
     * Converts a learning delta to an evidence map for promotion transitions.
     *
     * <p>Maps the following evidence types with standardized keys:
     * <ul>
     *   <li>procedure → the procedure ID from the delta</li>
     *   <li>semanticFact → the semantic fact ID from the delta</li>
     *   <li>negativeKnowledge → the negative knowledge ID from the delta</li>
     *   <li>evaluation_0, evaluation_1, ... → evaluation refs from the delta</li>
     *   <li>deltaId → the delta ID for traceability</li>
     *   <li>agentId → the agent ID from the delta</li>
     *   <li>skillId → the skill ID from the delta</li>
     * </ul>
     *
     * @param delta learning delta to convert
     * @return evidence map with standardized keys
     */
    @NotNull
    public static Map<String, String> toEvidenceMap(@NotNull LearningDelta delta) {
        Map<String, String> evidenceMap = new HashMap<>();

        // Add procedure evidence if present
        if (delta.procedureId() != null) {
            evidenceMap.put("procedure", delta.procedureId());
        }

        // Add semantic fact evidence if present
        if (delta.semanticFactId() != null) {
            evidenceMap.put("semanticFact", delta.semanticFactId());
        }

        // Add negative knowledge evidence if present
        if (delta.negativeKnowledgeId() != null) {
            evidenceMap.put("negativeKnowledge", delta.negativeKnowledgeId());
        }

        // Add evaluation refs with numbered keys
        List<String> evalRefs = delta.evaluationRefs();
        for (int i = 0; i < evalRefs.size(); i++) {
            evidenceMap.put("evaluation_" + i, evalRefs.get(i));
        }

        // Add traceability metadata
        evidenceMap.put("deltaId", delta.deltaId());
        evidenceMap.put("agentId", delta.agentId());
        evidenceMap.put("skillId", delta.skillId());

        return Map.copyOf(evidenceMap);
    }

    /**
     * Converts a learning delta to metadata for promotion transitions.
     *
     * <p>Includes metadata that is not evidence but provides context for the promotion:
     * <ul>
     *   <li>deltaId → the delta ID for traceability</li>
     *   <li>confidenceBefore → confidence before the learning delta</li>
     *   <li>confidenceAfter → confidence after the learning delta</li>
     *   <li>confidenceGain → the calculated confidence gain</li>
     * </ul>
     *
     * @param delta learning delta to convert
     * @return metadata map with promotion context
     */
    @NotNull
    public static Map<String, String> toMetadata(@NotNull LearningDelta delta) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("deltaId", delta.deltaId());
        metadata.put("confidenceBefore", String.valueOf(delta.confidenceBefore()));
        metadata.put("confidenceAfter", String.valueOf(delta.confidenceAfter()));
        metadata.put("confidenceGain", String.valueOf(delta.confidenceAfter() - delta.confidenceBefore()));
        return Map.copyOf(metadata);
    }
}
