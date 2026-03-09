package com.ghatana.flashit.agent.util;

import com.ghatana.flashit.agent.dto.MomentData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared utilities for building LLM prompt content from moment data.
 *
 * <p>Consolidates the duplicated {@code buildMomentsSummary} logic that was
 * previously copy-pasted across RecommendationService, KnowledgeGraphService,
 * IntelligenceAccumulationService, and ReflectionService.
 *
 * @doc.type class
 * @doc.purpose Shared prompt-building utilities for moment data
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class PromptUtils {

    private PromptUtils() {
    }

    /**
     * Builds a human-readable summary of moments for inclusion in LLM prompts.
     *
     * @param moments list of moment data to summarize
     * @param emptyMessage message to return when moments list is empty/null
     * @return formatted summary string
     */
    public static String buildMomentsSummary(List<MomentData> moments, String emptyMessage) {
        if (moments == null || moments.isEmpty()) {
            return emptyMessage;
        }
        return moments.stream()
                .map(m -> String.format("[%s] %s (emotions: %s, tags: %s)",
                        m.capturedAt(),
                        m.content() != null ? m.content() : m.transcript(),
                        m.emotions() != null ? String.join(", ", m.emotions()) : "none",
                        m.tags() != null ? String.join(", ", m.tags()) : "none"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Builds a moment summary with default empty message.
     *
     * @param moments list of moment data
     * @return formatted summary string
     */
    public static String buildMomentsSummary(List<MomentData> moments) {
        return buildMomentsSummary(moments, "No moments provided.");
    }
}
