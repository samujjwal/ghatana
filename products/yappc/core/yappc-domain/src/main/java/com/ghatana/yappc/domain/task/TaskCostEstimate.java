package com.ghatana.products.yappc.domain.task;

/**
 * Task cost estimate.
 *
 * @param estimatedTokens    Estimated LLM tokens
 * @param estimatedCostUSD   Estimated cost in USD
 * @param estimatedDurationMs Estimated duration in milliseconds
 * @doc.type record
 * @doc.purpose Task execution cost estimate
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TaskCostEstimate(
        int estimatedTokens,
        double estimatedCostUSD,
        long estimatedDurationMs
) {
    public static TaskCostEstimate zero() {
        return new TaskCostEstimate(0, 0.0, 0);
    }

    public static TaskCostEstimate fromComplexity(TaskComplexity complexity) {
        return new TaskCostEstimate(
                complexity.getEstimatedSeconds() * 100, // Rough estimate: 100 tokens/second
                complexity.getEstimatedCost(),
                complexity.getEstimatedSeconds() * 1000L
        );
    }
}
