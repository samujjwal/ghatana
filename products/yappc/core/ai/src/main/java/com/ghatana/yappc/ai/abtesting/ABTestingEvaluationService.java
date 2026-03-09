package com.ghatana.yappc.ai.abtesting;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * A/B Testing Service for AI Model Comparison (Java/ActiveJ)
 *
 * High-performance experiment evaluation, statistical analysis,
 * and automatic model fallback based on performance metrics.
 *
 * @doc.type class
 * @doc.purpose AI model A/B testing core evaluation engine
 * @doc.layer core
 * @doc.pattern Service
 */
public class ABTestingEvaluationService {

    // ============================================================================
    // Types & Records
    // ============================================================================

    public enum AIModelProvider {
        GPT_4("gpt-4"),
        GPT_4_TURBO("gpt-4-turbo"),
        CLAUDE_3_OPUS("claude-3-opus"),
        CLAUDE_3_SONNET("claude-3-sonnet"),
        OLLAMA_LLAMA3("ollama-llama3"),
        OLLAMA_MIXTRAL("ollama-mixtral");

        private final String value;

        AIModelProvider(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public record VariantMetrics(
            String variantId,
            AIModelProvider model,
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            double avgLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            double avgSatisfactionScore,
            long satisfactionCount,
            long totalTokens,
            double totalCost,
            double avgCostPerRequest,
            double errorRate,
            Double conversionRate
    ) {}

    public record StatisticalResult(
            String winnerId,
            double confidence,
            boolean isStatisticallySignificant,
            double effectSize,
            double pValue
    ) {}

    public record ModelPerformanceSnapshot(
            AIModelProvider model,
            double avgLatency,
            double errorRate,
            double avgSatisfaction,
            double costPerRequest,
            long sampleSize,
            Instant timestamp
    ) {}

    public record ModelRecommendation(
            AIModelProvider recommended,
            String reason,
            double confidenceScore,
            Map<AIModelProvider, Double> scores
    ) {}

    // ============================================================================
    // State
    // ============================================================================

    private final Map<String, List<InteractionData>> experimentData = new ConcurrentHashMap<>();
    private final Map<AIModelProvider, ModelPerformanceSnapshot> performanceCache = new ConcurrentHashMap<>();

    public record InteractionData(
            String variantId,
            AIModelProvider model,
            long latencyMs,
            boolean isSuccess,
            Double satisfactionScore,
            int promptTokens,
            int completionTokens,
            double cost,
            Instant timestamp
    ) {}

    // ============================================================================
    // Core Evaluation Methods
    // ============================================================================

    /**
     * Evaluate experiment results with statistical significance testing
     */
    public Promise<StatisticalResult> evaluateExperiment(
            String experimentId,
            List<VariantMetrics> variantMetrics,
            double requiredConfidence
    ) {
        return Promise.ofBlocking(Runnable::run, () -> {
            if (variantMetrics.size() < 2) {
                return new StatisticalResult(null, 0.0, false, 0.0, 1.0);
            }

            // Sort by satisfaction score (primary metric)
            List<VariantMetrics> sorted = variantMetrics.stream()
                    .sorted((a, b) -> Double.compare(b.avgSatisfactionScore(), a.avgSatisfactionScore()))
                    .toList();

            VariantMetrics best = sorted.get(0);
            VariantMetrics secondBest = sorted.get(1);

            // Calculate statistical significance using two-proportion z-test
            double p1 = best.avgSatisfactionScore() / 5.0; // Normalize to 0-1
            double p2 = secondBest.avgSatisfactionScore() / 5.0;
            long n1 = Math.max(best.satisfactionCount(), 1);
            long n2 = Math.max(secondBest.satisfactionCount(), 1);

            double pooledP = (p1 * n1 + p2 * n2) / (n1 + n2);
            double se = Math.sqrt(pooledP * (1 - pooledP) * (1.0 / n1 + 1.0 / n2));
            double zScore = se > 0 ? (p1 - p2) / se : 0;

            // Calculate p-value and confidence
            double pValue = 2 * (1 - normalCDF(Math.abs(zScore)));
            double confidence = 1 - pValue;

            // Calculate effect size (Cohen's h for proportions)
            double effectSize = 2 * (Math.asin(Math.sqrt(p1)) - Math.asin(Math.sqrt(p2)));

            boolean isSignificant = confidence >= requiredConfidence;

            return new StatisticalResult(
                    best.variantId(),
                    confidence,
                    isSignificant,
                    effectSize,
                    pValue
            );
        });
    }

    /**
     * Calculate cosine similarity between two embedding vectors
     * Used for semantic cache similarity matching
     */
    public double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have same dimension");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * Perform Thompson Sampling for multi-armed bandit variant selection
     */
    public Promise<String> selectVariantThompsonSampling(
            List<String> variantIds,
            Map<String, int[]> banditState // [successes, failures] per variant
    ) {
        return Promise.ofBlocking(Runnable::run, () -> {
            double maxSample = Double.NEGATIVE_INFINITY;
            String bestVariant = variantIds.get(0);

            for (String variantId : variantIds) {
                int[] state = banditState.getOrDefault(variantId, new int[]{1, 1});
                double sample = sampleBeta(state[0], state[1]);

                if (sample > maxSample) {
                    maxSample = sample;
                    bestVariant = variantId;
                }
            }

            return bestVariant;
        });
    }

    /**
     * Calculate percentile value from sorted list
     */
    public double calculatePercentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }

        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    /**
     * Aggregate metrics for a variant from raw interaction data
     */
    public Promise<VariantMetrics> aggregateVariantMetrics(
            String experimentId,
            String variantId,
            AIModelProvider model
    ) {
        return Promise.ofBlocking(Runnable::run, () -> {
            List<InteractionData> data = experimentData.getOrDefault(experimentId, List.of())
                    .stream()
                    .filter(d -> d.variantId().equals(variantId))
                    .toList();

            if (data.isEmpty()) {
                return new VariantMetrics(
                        variantId, model, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null
                );
            }

            long totalRequests = data.size();
            long successfulRequests = data.stream().filter(InteractionData::isSuccess).count();
            long failedRequests = totalRequests - successfulRequests;

            List<Double> latencies = data.stream()
                    .map(d -> (double) d.latencyMs())
                    .sorted()
                    .toList();

            double avgLatency = latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0);

            List<Double> satisfactionScores = data.stream()
                    .filter(d -> d.satisfactionScore() != null)
                    .map(InteractionData::satisfactionScore)
                    .toList();

            double avgSatisfaction = satisfactionScores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0);

            long totalTokens = data.stream()
                    .mapToLong(d -> d.promptTokens() + d.completionTokens())
                    .sum();

            double totalCost = data.stream().mapToDouble(InteractionData::cost).sum();

            return new VariantMetrics(
                    variantId,
                    model,
                    totalRequests,
                    successfulRequests,
                    failedRequests,
                    avgLatency,
                    calculatePercentile(latencies, 50),
                    calculatePercentile(latencies, 95),
                    calculatePercentile(latencies, 99),
                    avgSatisfaction,
                    satisfactionScores.size(),
                    totalTokens,
                    totalCost,
                    totalRequests > 0 ? totalCost / totalRequests : 0,
                    totalRequests > 0 ? (double) failedRequests / totalRequests : 0,
                    null
            );
        });
    }

    /**
     * Get model recommendation based on cost/quality tradeoff
     */
    public Promise<ModelRecommendation> getModelRecommendation(
            double qualityWeight,  // 0-1, higher = prefer quality
            double costWeight,     // 0-1, higher = prefer low cost
            double latencyWeight   // 0-1, higher = prefer low latency
    ) {
        return Promise.ofBlocking(Runnable::run, () -> {
            Map<AIModelProvider, Double> scores = new EnumMap<>(AIModelProvider.class);
            
            // Normalize weights
            double totalWeight = qualityWeight + costWeight + latencyWeight;
            double normQuality = qualityWeight / totalWeight;
            double normCost = costWeight / totalWeight;
            double normLatency = latencyWeight / totalWeight;

            // Get performance data for each model
            for (AIModelProvider model : AIModelProvider.values()) {
                ModelPerformanceSnapshot snapshot = performanceCache.get(model);
                if (snapshot == null || snapshot.sampleSize() < 10) {
                    scores.put(model, 0.0); // Insufficient data
                    continue;
                }

                // Normalize metrics (0-1 scale, higher is better)
                double qualityScore = snapshot.avgSatisfaction() / 5.0;
                double costScore = 1.0 - Math.min(snapshot.costPerRequest() / 0.10, 1.0); // $0.10 max
                double latencyScore = 1.0 - Math.min(snapshot.avgLatency() / 5000.0, 1.0); // 5s max

                // Apply error rate penalty
                double errorPenalty = 1.0 - snapshot.errorRate();

                double compositeScore = (
                        normQuality * qualityScore +
                        normCost * costScore +
                        normLatency * latencyScore
                ) * errorPenalty;

                scores.put(model, compositeScore);
            }

            // Find best model
            AIModelProvider best = scores.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(AIModelProvider.GPT_4_TURBO);

            double bestScore = scores.getOrDefault(best, 0.0);
            
            String reason = generateRecommendationReason(best, performanceCache.get(best), normQuality, normCost, normLatency);

            return new ModelRecommendation(best, reason, bestScore, scores);
        });
    }

    /**
     * Record interaction data for analysis
     */
    public void recordInteraction(String experimentId, InteractionData data) {
        experimentData.computeIfAbsent(experimentId, k -> new ArrayList<>()).add(data);
        updatePerformanceCache(data);
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private void updatePerformanceCache(InteractionData data) {
        performanceCache.compute(data.model(), (model, existing) -> {
            if (existing == null) {
                return new ModelPerformanceSnapshot(
                        model,
                        data.latencyMs(),
                        data.isSuccess() ? 0.0 : 1.0,
                        data.satisfactionScore() != null ? data.satisfactionScore() : 0.0,
                        data.cost(),
                        1,
                        Instant.now()
                );
            }

            long newSampleSize = existing.sampleSize() + 1;
            double newAvgLatency = (existing.avgLatency() * existing.sampleSize() + data.latencyMs()) / newSampleSize;
            double newErrorRate = (existing.errorRate() * existing.sampleSize() + (data.isSuccess() ? 0 : 1)) / newSampleSize;
            double newSatisfaction = data.satisfactionScore() != null
                    ? (existing.avgSatisfaction() * existing.sampleSize() + data.satisfactionScore()) / newSampleSize
                    : existing.avgSatisfaction();
            double newCost = (existing.costPerRequest() * existing.sampleSize() + data.cost()) / newSampleSize;

            return new ModelPerformanceSnapshot(
                    model,
                    newAvgLatency,
                    newErrorRate,
                    newSatisfaction,
                    newCost,
                    newSampleSize,
                    Instant.now()
            );
        });
    }

    private String generateRecommendationReason(
            AIModelProvider model,
            ModelPerformanceSnapshot snapshot,
            double qualityWeight,
            double costWeight,
            double latencyWeight
    ) {
        if (snapshot == null) {
            return "Recommended as default model due to insufficient performance data.";
        }

        StringBuilder reason = new StringBuilder();
        reason.append(String.format("Recommended %s based on: ", model.getValue()));

        List<String> factors = new ArrayList<>();
        if (qualityWeight > 0.3) {
            factors.add(String.format("%.1f/5 satisfaction", snapshot.avgSatisfaction()));
        }
        if (costWeight > 0.3) {
            factors.add(String.format("$%.4f/request cost", snapshot.costPerRequest()));
        }
        if (latencyWeight > 0.3) {
            factors.add(String.format("%.0fms avg latency", snapshot.avgLatency()));
        }
        factors.add(String.format("%.1f%% error rate", snapshot.errorRate() * 100));

        reason.append(String.join(", ", factors));
        reason.append(".");

        return reason.toString();
    }

    /**
     * Normal CDF approximation
     */
    private double normalCDF(double x) {
        double a1 = 0.254829592;
        double a2 = -0.284496736;
        double a3 = 1.421413741;
        double a4 = -1.453152027;
        double a5 = 1.061405429;
        double p = 0.3275911;

        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x) / Math.sqrt(2);

        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);

        return 0.5 * (1.0 + sign * y);
    }

    /**
     * Beta distribution sampling for Thompson Sampling
     */
    private double sampleBeta(int alpha, int beta) {
        double x = sampleGamma(alpha, 1);
        double y = sampleGamma(beta, 1);
        return x / (x + y);
    }

    /**
     * Gamma distribution sampling (Marsaglia and Tsang's method)
     */
    private double sampleGamma(double shape, double scale) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (shape < 1) {
            return sampleGamma(shape + 1, scale) * Math.pow(random.nextDouble(), 1.0 / shape);
        }

        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9 * d);

        while (true) {
            double x, v;
            do {
                x = random.nextGaussian();
                v = 1 + c * x;
            } while (v <= 0);

            v = v * v * v;
            double u = random.nextDouble();

            if (u < 1 - 0.0331 * x * x * x * x) {
                return d * v * scale;
            }

            if (Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) {
                return d * v * scale;
            }
        }
    }
}
