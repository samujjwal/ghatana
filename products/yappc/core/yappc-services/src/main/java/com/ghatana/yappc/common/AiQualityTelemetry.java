package com.ghatana.yappc.common;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.platform.observability.MetricsCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared AI quality telemetry helpers.
 *
 * @doc.type class
 * @doc.purpose Records confidence, token, fallback, and estimated-cost telemetry for AI operations
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class AiQualityTelemetry {

    private AiQualityTelemetry() {
    }

    public static void recordCompletion(
            MetricsCollector metrics,
            String metricPrefix,
            CompletionResult result,
            Map<String, String> tags) {
        Map<String, String> safeTags = tags == null ? Map.of() : tags;
        double confidence = estimateConfidence(result);
        double estimatedCostUsd = estimateCostUsd(result);

        metrics.incrementCounter(metricPrefix + ".request", safeTags);
        metrics.increment(metricPrefix + ".tokens.total", result.getTokensUsed(), safeTags);
        metrics.increment(metricPrefix + ".tokens.prompt", result.getPromptTokens(), safeTags);
        metrics.increment(metricPrefix + ".tokens.completion", result.getCompletionTokens(), safeTags);
        metrics.increment(metricPrefix + ".cost.estimated_usd", estimatedCostUsd, safeTags);
        metrics.recordConfidenceScore(metricPrefix + ".confidence", confidence);
    }

    public static void recordFallback(
            MetricsCollector metrics,
            String metricPrefix,
            Throwable throwable,
            Map<String, String> tags) {
        Map<String, String> mergedTags = new HashMap<>();
        if (tags != null) {
            mergedTags.putAll(tags);
        }
        mergedTags.put("fallback", "true");
        mergedTags.put("error", throwable == null ? "unknown" : throwable.getClass().getSimpleName());
        metrics.incrementCounter(metricPrefix + ".fallback", mergedTags);
    }

    public static double estimateConfidence(CompletionResult result) {
        if (result == null) {
            return 0.0;
        }

        double score = 0.4;
        int textLength = result.text() == null ? 0 : result.text().trim().length();
        if (textLength > 80) {
            score += 0.2;
        }
        if (textLength > 300) {
            score += 0.1;
        }
        if (result.getFinishReason() != null && "stop".equalsIgnoreCase(result.getFinishReason())) {
            score += 0.2;
        }
        if (result.getTokensUsed() > 0) {
            score += 0.1;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    public static double estimateCostUsd(CompletionResult result) {
        if (result == null) {
            return 0.0;
        }

        String model = result.getModelUsed() == null ? "" : result.getModelUsed().toLowerCase();

        double promptCostPer1k;
        double completionCostPer1k;

        if (model.contains("gpt-4o")) {
            promptCostPer1k = 0.005;
            completionCostPer1k = 0.015;
        } else if (model.contains("gpt-4")) {
            promptCostPer1k = 0.01;
            completionCostPer1k = 0.03;
        } else if (model.contains("claude")) {
            promptCostPer1k = 0.008;
            completionCostPer1k = 0.024;
        } else {
            // Ollama/local or unknown provider defaults to zero billable estimate.
            promptCostPer1k = 0.0;
            completionCostPer1k = 0.0;
        }

        return (result.getPromptTokens() / 1000.0) * promptCostPer1k
                + (result.getCompletionTokens() / 1000.0) * completionCostPer1k;
    }
}
