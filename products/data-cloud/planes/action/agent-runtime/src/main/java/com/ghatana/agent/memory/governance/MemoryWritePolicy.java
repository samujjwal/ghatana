/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Strict write gate for learned memory artifacts.
 *
 * <p>Enforces governance constraints on learned memory writes:
 * <ul>
 *   <li>Typed metadata validation (labels and metadata Map<String, Object>)</li>
 *   <li>PROCEDURAL_SKILL requires skillId, masteryState, and provenance for L2+</li>
 *   <li>NEGATIVE_KNOWLEDGE requires justification and evidence</li>
 *   <li>PROMPT_TEMPLATE/PLANNER_POLICY require evaluation and rollout references</li>
 *   <li>MODEL_ADAPTER cannot self-activate (offline-only)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Strict write gate for learned memory artifacts
 * @doc.layer data-cloud
 * @doc.pattern Policy
 */
public final class MemoryWritePolicy {

    private MemoryWritePolicy() {}

    /**
     * Validates semantic fact writes.
     */
    public static void validateFact(@NotNull EnhancedFact fact) {
        Map<String, String> labels = labels(fact);
        Map<String, Object> metadata = metadata(fact);

        // Negative knowledge requires evidence and justification
        if ("NEGATIVE_KNOWLEDGE".equals(labels.get("learningTarget"))) {
            if (isBlank(labels.get("evidenceRef")) && isBlank(String.valueOf(metadata.get("evidenceRef")))) {
                throw new IllegalStateException("negative knowledge requires evidenceRef in labels or metadata");
            }
            if (isBlank(String.valueOf(metadata.get("justification")))) {
                throw new IllegalStateException("negative knowledge requires justification in metadata");
            }
        }

        // Validation state check
        if (!"true".equalsIgnoreCase(labels.get("validated"))
                && !"VALIDATED".equalsIgnoreCase(labels.get("validationState"))
                && !"VALIDATED".equalsIgnoreCase(String.valueOf(metadata.get("validationState")))) {
            throw new IllegalStateException("semantic memory writes require validationState=VALIDATED in labels or metadata");
        }
    }

    /**
     * Validates procedural skill writes with stricter PROCEDURAL_SKILL validation.
     */
    public static void validateProcedure(@NotNull EnhancedProcedure procedure) {
        Map<String, String> labels = labels(procedure);
        Map<String, Object> metadata = metadata(procedure);

        // Check for learning target
        String learningTarget = labels.get("learningTarget");
        if (learningTarget == null) {
            learningTarget = metadata.get("learningTarget") != null 
                    ? metadata.get("learningTarget").toString() 
                    : null;
        }

        // Stricter validation for PROCEDURAL_SKILL
        if ("PROCEDURAL_SKILL".equals(learningTarget)) {
            // skillId is required
            String skillId = labels.get("skillId");
            if (skillId == null) {
                skillId = metadata.get("skillId") != null 
                        ? metadata.get("skillId").toString() 
                        : null;
            }
            if (isBlank(skillId)) {
                throw new IllegalStateException("PROCEDURAL_SKILL requires skillId in labels or metadata");
            }

            // masteryState is required
            String masteryState = labels.get("masteryState");
            if (masteryState == null) {
                masteryState = metadata.get("masteryState") != null 
                        ? metadata.get("masteryState").toString() 
                        : null;
            }
            if (isBlank(masteryState)) {
                throw new IllegalStateException("PROCEDURAL_SKILL requires masteryState in labels or metadata");
            }

            // Validate masteryState is a valid enum value
            try {
                MasteryState.valueOf(masteryState);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid masteryState: " + masteryState, e);
            }

            // provenance is required for L2+ learning
            String provenanceRequired = labels.get("provenanceRequired");
            if (provenanceRequired == null) {
                provenanceRequired = metadata.get("provenanceRequired") != null 
                        ? metadata.get("provenanceRequired").toString() 
                        : null;
            }
            if ("true".equalsIgnoreCase(provenanceRequired)) {
                String provenance = labels.get("provenance");
                if (provenance == null) {
                    provenance = metadata.get("provenance") != null 
                            ? metadata.get("provenance").toString() 
                            : null;
                }
                if (isBlank(provenance)) {
                    throw new IllegalStateException("PROCEDURAL_SKILL with provenanceRequired=true requires provenance in labels or metadata");
                }
            }
        }

        // Legacy promotion state check (for backward compatibility)
        if (!"ACTIVE".equalsIgnoreCase(labels.get("promotionState"))
                && !"ACTIVE".equalsIgnoreCase(String.valueOf(metadata.get("promotionState")))) {
            if (!"PROCEDURAL_SKILL".equals(learningTarget)) {
                throw new IllegalStateException("procedural memory writes require promotionState=ACTIVE in labels or metadata");
            }
        }
    }

    /**
     * Validates typed artifact writes.
     */
    public static void validateArtifact(@NotNull TypedArtifact artifact) {
        Map<String, String> labels = labels(artifact);
        Map<String, Object> metadata = metadata(artifact);
        
        String rawTarget = labels.get("learningTarget");
        if (isBlank(rawTarget)) {
            rawTarget = metadata.get("learningTarget") != null 
                    ? metadata.get("learningTarget").toString() 
                    : null;
        }
        if (isBlank(rawTarget)) {
            return;
        }
        
        LearningTarget target = LearningTarget.valueOf(rawTarget);
        switch (target) {
            case RETRIEVAL_POLICY, CONFIDENCE_THRESHOLD, ROUTING_POLICY -> {
                String approvedBy = labels.get("approvedBy");
                if (isBlank(approvedBy)) {
                    approvedBy = metadata.get("approvedBy") != null 
                            ? metadata.get("approvedBy").toString() 
                            : null;
                }
                if (isBlank(approvedBy)) {
                    throw new IllegalStateException("policy learned artifacts require approvedBy in labels or metadata");
                }
            }
            case PROMPT_TEMPLATE, PLANNER_POLICY -> {
                String evaluationRef = labels.get("evaluationRef");
                if (isBlank(evaluationRef)) {
                    evaluationRef = metadata.get("evaluationRef") != null 
                            ? metadata.get("evaluationRef").toString() 
                            : null;
                }
                String rolloutRef = labels.get("rolloutRef");
                if (isBlank(rolloutRef)) {
                    rolloutRef = metadata.get("rolloutRef") != null 
                            ? metadata.get("rolloutRef").toString() 
                            : null;
                }
                if (isBlank(evaluationRef) || isBlank(rolloutRef)) {
                    throw new IllegalStateException("prompt/planner artifacts require evaluationRef and rolloutRef in labels or metadata");
                }
            }
            case MODEL_ADAPTER -> throw new IllegalStateException("model adapter artifacts cannot self-activate");
            default -> {
                // EPISODIC, SEMANTIC, PROCEDURAL are governed by their typed stores.
            }
        }
    }

    /**
     * Generic validation that dispatches to the appropriate typed validator.
     */
    public static void validate(@NotNull MemoryItem item) {
        switch (item.getType()) {
            case FACT -> validateFact((EnhancedFact) item);
            case PROCEDURE -> validateProcedure((EnhancedProcedure) item);
            case ARTIFACT -> validateArtifact((TypedArtifact) item);
            default -> {
                // EPISODE and other transient tiers are append-only here.
            }
        }
    }

    private static Map<String, String> labels(MemoryItem item) {
        return item.getLabels() == null ? Map.of() : item.getLabels();
    }

    private static Map<String, Object> metadata(MemoryItem item) {
        // MemoryItem only has getLabels(), so we convert labels to metadata format
        Map<String, String> labels = item.getLabels();
        Map<String, Object> result = new java.util.HashMap<>();
        if (labels != null) {
            result.putAll(labels);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
