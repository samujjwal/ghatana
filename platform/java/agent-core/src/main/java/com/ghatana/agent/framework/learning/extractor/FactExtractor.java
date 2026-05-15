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
 * Extracts semantic facts from episodes.
 * Phase 5 FIX: Typed fact extractor to replace heuristic synthesis.
 *
 * @doc.type class
 * @doc.purpose Extracts semantic facts from episodes
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class FactExtractor implements LearningExtractor {

    private static final int MIN_SAMPLE_SIZE = 3;
    private static final double MIN_CONFIDENCE = 0.6;

    @Override
    @NotNull
    public List<LearningCandidate> extract(@NotNull String agentId, @NotNull List<Episode> episodes) {
        Map<String, FactPattern> patterns = new HashMap<>();

        // Extract fact patterns from episodes
        for (Episode ep : episodes) {
            if (ep.getAction() == null || ep.getOutput() == null) continue;
            if (ep.getReward() == null || ep.getReward() <= 0) continue; // Only positive outcomes

            String factKey = extractFactKey(ep);
            if (factKey == null) continue;

            patterns.computeIfAbsent(factKey, k -> new FactPattern())
                    .addSample(ep);
        }

        // Convert patterns to learning candidates
        List<LearningCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, FactPattern> entry : patterns.entrySet()) {
            FactPattern pattern = entry.getValue();
            if (pattern.sampleCount < MIN_SAMPLE_SIZE) continue;

            double confidence = pattern.calculateConfidence();
            if (confidence < MIN_CONFIDENCE) continue;

            candidates.add(LearningCandidate.fact(
                    "when: " + entry.getKey(),
                    "do: record as semantic fact",
                    buildFactContent(pattern),
                    confidence
            ));
        }

        return candidates;
    }

    @Override
    @NotNull
    public LearningType type() {
        return LearningType.SEMANTIC_FACT;
    }

    /**
     * Extracts a fact key from an episode observation.
     */
    @Nullable
    private String extractFactKey(@NotNull Episode ep) {
        String observation = ep.getOutput();
        if (observation == null || observation.isBlank()) return null;

        // Simple heuristic: extract first sentence or key-value pattern
        String[] sentences = observation.split("[.!?]");
        if (sentences.length > 0 && !sentences[0].isBlank()) {
            return sentences[0].trim();
        }

        // Fallback: use action as fact key
        return ep.getAction();
    }

    /**
     * Builds fact content from pattern samples.
     */
    @NotNull
    private String buildFactContent(@NotNull FactPattern pattern) {
        StringBuilder content = new StringBuilder();
        content.append("Fact extracted from ").append(pattern.sampleCount).append(" episodes. ");
        content.append("Sample observation: ").append(pattern.firstObservation);
        return content.toString();
    }

    /**
     * Internal pattern tracking for fact extraction.
     */
    private static class FactPattern {
        int sampleCount = 0;
        String firstObservation = "";
        int positiveCount = 0;

        void addSample(@NotNull Episode ep) {
            sampleCount++;
            if (firstObservation.isBlank() && ep.getOutput() != null) {
                firstObservation = ep.getOutput();
            }
            if (ep.getReward() != null && ep.getReward() > 0) {
                positiveCount++;
            }
        }

        double calculateConfidence() {
            if (sampleCount == 0) return 0.0;
            return (double) positiveCount / sampleCount;
        }
    }
}
