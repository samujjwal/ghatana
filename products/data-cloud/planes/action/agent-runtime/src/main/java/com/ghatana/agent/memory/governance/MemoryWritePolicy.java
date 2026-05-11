/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.governance;

import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.model.artifact.TypedArtifact;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Strict write gate for learned memory artifacts.
 */
public final class MemoryWritePolicy {

    private MemoryWritePolicy() {}

    public static void validateFact(@NotNull EnhancedFact fact) {
        Map<String, String> labels = labels(fact);
        if ("NEGATIVE_KNOWLEDGE".equals(labels.get("learningTarget"))
                && isBlank(labels.get("evidenceRef"))) {
            throw new IllegalStateException("negative knowledge requires evidenceRef");
        }
        if (!"true".equalsIgnoreCase(labels.get("validated"))
                && !"VALIDATED".equalsIgnoreCase(labels.get("validationState"))) {
            throw new IllegalStateException("semantic memory writes require validationState=VALIDATED");
        }
    }

    public static void validateProcedure(@NotNull EnhancedProcedure procedure) {
        Map<String, String> labels = labels(procedure);
        if (!"ACTIVE".equalsIgnoreCase(labels.get("promotionState"))
                || isBlank(labels.get("promotionEvidenceId"))) {
            throw new IllegalStateException("procedural memory writes require active promotion evidence");
        }
    }

    public static void validateArtifact(@NotNull TypedArtifact artifact) {
        Map<String, String> labels = labels(artifact);
        String rawTarget = labels.get("learningTarget");
        if (isBlank(rawTarget)) {
            return;
        }
        LearningTarget target = LearningTarget.valueOf(rawTarget);
        switch (target) {
            case RETRIEVAL_POLICY, CONFIDENCE_THRESHOLD, ROUTING_POLICY -> {
                if (isBlank(labels.get("approvedBy"))) {
                    throw new IllegalStateException("policy learned artifacts require approvedBy");
                }
            }
            case PROMPT_TEMPLATE, PLANNER_POLICY -> {
                if (isBlank(labels.get("evaluationRef")) || isBlank(labels.get("rolloutRef"))) {
                    throw new IllegalStateException("prompt/planner artifacts require evaluationRef and rolloutRef");
                }
            }
            case MODEL_ADAPTER -> throw new IllegalStateException("model adapter artifacts cannot self-activate");
            default -> {
                // EPISODIC, SEMANTIC, PROCEDURAL are governed by their typed stores.
            }
        }
    }

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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
