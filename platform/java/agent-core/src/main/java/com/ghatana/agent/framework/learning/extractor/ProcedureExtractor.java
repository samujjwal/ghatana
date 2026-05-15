/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.learning.extractor;

import com.ghatana.agent.framework.memory.Episode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts procedural skills from episodes.
 * Phase 5 FIX: Typed procedure extractor to replace heuristic synthesis.
 *
 * @doc.type class
 * @doc.purpose Extracts procedural skills from episodes
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class ProcedureExtractor implements LearningExtractor {

    private static final int MIN_SAMPLE_SIZE = 3;
    private static final double MIN_CONFIDENCE = 0.5;

    @Override
    @NotNull
    public List<LearningCandidate> extract(@NotNull String agentId, @NotNull List<Episode> episodes) {
        Map<String, ProcedurePattern> patterns = new HashMap<>();

        // Extract procedure patterns from episodes
        for (Episode ep : episodes) {
            if (ep.getAction() == null) continue;

            String procedureKey = extractProcedureKey(ep);
            if (procedureKey == null) continue;

            patterns.computeIfAbsent(procedureKey, k -> new ProcedurePattern())
                    .addSample(ep);
        }

        // Convert patterns to learning candidates
        List<LearningCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, ProcedurePattern> entry : patterns.entrySet()) {
            ProcedurePattern pattern = entry.getValue();
            if (pattern.sampleCount < MIN_SAMPLE_SIZE) continue;

            double confidence = pattern.calculateConfidence();
            if (confidence < MIN_CONFIDENCE) continue;

            candidates.add(LearningCandidate.procedure(
                    "when: " + entry.getKey(),
                    "do: " + pattern.action,
                    buildProcedureContent(pattern),
                    confidence
            ));
        }

        return candidates;
    }

    @Override
    @NotNull
    public LearningType type() {
        return LearningType.PROCEDURAL_SKILL;
    }

    /**
     * Extracts a procedure key from an episode action.
     */
    @Nullable
    private String extractProcedureKey(@NotNull Episode ep) {
        String action = ep.getAction();
        if (action == null || action.isBlank()) return null;

        // Simple heuristic: use action as procedure key
        // In production, this would use more sophisticated NLP to extract procedure patterns
        return action;
    }

    /**
     * Builds procedure content from pattern samples.
     */
    @NotNull
    private String buildProcedureContent(@NotNull ProcedurePattern pattern) {
        StringBuilder content = new StringBuilder();
        content.append("Procedure extracted from ").append(pattern.sampleCount).append(" episodes. ");
        content.append("Action: ").append(pattern.action);
        if (pattern.avgReward > 0) {
            content.append(", Average reward: ").append(String.format("%.2f", pattern.avgReward));
        }
        return content.toString();
    }

    /**
     * Internal pattern tracking for procedure extraction.
     */
    private static class ProcedurePattern {
        int sampleCount = 0;
        String action = "";
        double totalReward = 0.0;
        int positiveCount = 0;

        void addSample(@NotNull Episode ep) {
            sampleCount++;
            if (action.isBlank() && ep.getAction() != null) {
                action = ep.getAction();
            }
            if (ep.getReward() != null) {
                totalReward += ep.getReward();
                if (ep.getReward() > 0) {
                    positiveCount++;
                }
            }
        }

        double calculateConfidence() {
            if (sampleCount == 0) return 0.0;
            avgReward = totalReward / sampleCount;
            return (double) positiveCount / sampleCount;
        }

        double avgReward = 0.0;
    }
}
