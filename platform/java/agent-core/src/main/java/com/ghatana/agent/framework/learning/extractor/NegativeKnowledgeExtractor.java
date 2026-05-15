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
 * Extracts negative knowledge (what does not work) from episodes.
 * Phase 5 FIX: Typed negative knowledge extractor to replace heuristic synthesis.
 *
 * @doc.type class
 * @doc.purpose Extracts negative knowledge from episodes
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class NegativeKnowledgeExtractor implements LearningExtractor {

    private static final int MIN_SAMPLE_SIZE = 2;
    private static final double MIN_CONFIDENCE = 0.5;

    @Override
    @NotNull
    public List<LearningCandidate> extract(@NotNull String agentId, @NotNull List<Episode> episodes) {
        Map<String, NegativePattern> patterns = new HashMap<>();

        // Extract negative patterns from episodes with negative outcomes
        for (Episode ep : episodes) {
            if (ep.getAction() == null) continue;
            if (ep.getReward() == null || ep.getReward() >= 0) continue; // Only negative outcomes

            String negativeKey = extractNegativeKey(ep);
            if (negativeKey == null) continue;

            patterns.computeIfAbsent(negativeKey, k -> new NegativePattern())
                    .addSample(ep);
        }

        // Convert patterns to learning candidates
        List<LearningCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, NegativePattern> entry : patterns.entrySet()) {
            NegativePattern pattern = entry.getValue();
            if (pattern.sampleCount < MIN_SAMPLE_SIZE) continue;

            double confidence = pattern.calculateConfidence();
            if (confidence < MIN_CONFIDENCE) continue;

            candidates.add(LearningCandidate.negativeKnowledge(
                    "when: " + entry.getKey(),
                    "do: avoid this action pattern",
                    buildNegativeContent(pattern),
                    confidence
            ));
        }

        return candidates;
    }

    @Override
    @NotNull
    public LearningType type() {
        return LearningType.NEGATIVE_KNOWLEDGE;
    }

    /**
     * Extracts a negative pattern key from an episode with negative reward.
     */
    @Nullable
    private String extractNegativeKey(@NotNull Episode ep) {
        String action = ep.getAction();
        String observation = ep.getOutput();

        // Combine action and observation for negative pattern
        StringBuilder key = new StringBuilder();
        if (action != null && !action.isBlank()) {
            key.append("action: ").append(action);
        }
        if (observation != null && !observation.isBlank()) {
            if (key.length() > 0) key.append(", ");
            key.append("observation: ").append(observation);
        }

        return key.length() > 0 ? key.toString() : null;
    }

    /**
     * Builds negative knowledge content from pattern samples.
     */
    @NotNull
    private String buildNegativeContent(@NotNull NegativePattern pattern) {
        StringBuilder content = new StringBuilder();
        content.append("Negative pattern extracted from ").append(pattern.sampleCount).append(" episodes. ");
        content.append("Action to avoid: ").append(pattern.action);
        if (pattern.avgReward < 0) {
            content.append(", Average reward: ").append(String.format("%.2f", pattern.avgReward));
        }
        content.append(". This pattern consistently produces negative outcomes.");
        return content.toString();
    }

    /**
     * Internal pattern tracking for negative knowledge extraction.
     */
    private static class NegativePattern {
        int sampleCount = 0;
        String action = "";
        double totalReward = 0.0;
        int negativeCount = 0;

        void addSample(@NotNull Episode ep) {
            sampleCount++;
            if (action.isBlank() && ep.getAction() != null) {
                action = ep.getAction();
            }
            if (ep.getReward() != null) {
                totalReward += ep.getReward();
                if (ep.getReward() < 0) {
                    negativeCount++;
                }
            }
        }

        double calculateConfidence() {
            if (sampleCount == 0) return 0.0;
            avgReward = totalReward / sampleCount;
            // Confidence based on consistency of negative outcomes
            return (double) negativeCount / sampleCount;
        }

        double avgReward = 0.0;
    }
}
