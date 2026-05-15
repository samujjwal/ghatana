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
 * Extracts failure modes and their causes from episodes.
 * Phase 5 FIX: Typed failure mode extractor to replace heuristic synthesis.
 *
 * @doc.type class
 * @doc.purpose Extracts failure modes from episodes
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class FailureModeExtractor implements LearningExtractor {

    private static final int MIN_SAMPLE_SIZE = 2;
    private static final double MIN_CONFIDENCE = 0.5;

    @Override
    @NotNull
    public List<LearningCandidate> extract(@NotNull String agentId, @NotNull List<Episode> episodes) {
        Map<String, FailurePattern> patterns = new HashMap<>();

        // Extract failure patterns from episodes with negative outcomes
        for (Episode ep : episodes) {
            if (ep.getAction() == null) continue;
            if (ep.getReward() == null || ep.getReward() >= 0) continue; // Only negative outcomes

            String failureKey = extractFailureKey(ep);
            if (failureKey == null) continue;

            patterns.computeIfAbsent(failureKey, k -> new FailurePattern())
                    .addSample(ep);
        }

        // Convert patterns to learning candidates
        List<LearningCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, FailurePattern> entry : patterns.entrySet()) {
            FailurePattern pattern = entry.getValue();
            if (pattern.sampleCount < MIN_SAMPLE_SIZE) continue;

            double confidence = pattern.calculateConfidence();
            if (confidence < MIN_CONFIDENCE) continue;

            candidates.add(LearningCandidate.failureMode(
                    "when: " + entry.getKey(),
                    "do: detect and avoid this failure mode",
                    buildFailureContent(pattern),
                    confidence
            ));
        }

        return candidates;
    }

    @Override
    @NotNull
    public LearningType type() {
        return LearningType.FAILURE_MODE;
    }

    /**
     * Extracts a failure mode key from an episode with negative reward.
     */
    @Nullable
    private String extractFailureKey(@NotNull Episode ep) {
        String action = ep.getAction();
        String observation = ep.getOutput();

        // Extract failure mode from observation if available
        String failureCause = extractFailureCause(observation);
        if (failureCause != null) {
            return "failure-cause: " + failureCause + ", action: " + action;
        }

        // Fallback to action-based failure mode
        return "action: " + action;
    }

    /**
     * Extracts failure cause from observation text.
     */
    @Nullable
    private String extractFailureCause(@Nullable String observation) {
        if (observation == null || observation.isBlank()) return null;

        // Simple heuristic: look for failure-related keywords
        String[] failureKeywords = {"error", "failed", "exception", "timeout", "invalid", "denied", "blocked"};
        String lowerObs = observation.toLowerCase();

        for (String keyword : failureKeywords) {
            if (lowerObs.contains(keyword)) {
                return keyword;
            }
        }

        return null;
    }

    /**
     * Builds failure mode content from pattern samples.
     */
    @NotNull
    private String buildFailureContent(@NotNull FailurePattern pattern) {
        StringBuilder content = new StringBuilder();
        content.append("Failure mode extracted from ").append(pattern.sampleCount).append(" episodes. ");
        content.append("Action: ").append(pattern.action);
        if (pattern.cause != null && !pattern.cause.isBlank()) {
            content.append(", Cause: ").append(pattern.cause);
        }
        if (pattern.avgReward < 0) {
            content.append(", Average reward: ").append(String.format("%.2f", pattern.avgReward));
        }
        content.append(". This failure mode should be detected and avoided.");
        return content.toString();
    }

    /**
     * Internal pattern tracking for failure mode extraction.
     */
    private static class FailurePattern {
        int sampleCount = 0;
        String action = "";
        String cause = "";
        double totalReward = 0.0;
        int failureCount = 0;

        void addSample(@NotNull Episode ep) {
            sampleCount++;
            if (action.isBlank() && ep.getAction() != null) {
                action = ep.getAction();
            }
            if (cause.isBlank()) {
                FailureModeExtractor extractor = new FailureModeExtractor();
                cause = extractor.extractFailureCause(ep.getOutput());
            }
            if (ep.getReward() != null) {
                totalReward += ep.getReward();
                if (ep.getReward() < 0) {
                    failureCount++;
                }
            }
        }

        double calculateConfidence() {
            if (sampleCount == 0) return 0.0;
            avgReward = totalReward / sampleCount;
            // Confidence based on consistency of failures
            return (double) failureCount / sampleCount;
        }

        double avgReward = 0.0;
    }
}
