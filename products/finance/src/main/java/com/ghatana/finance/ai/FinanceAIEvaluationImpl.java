/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.AIEvaluationFramework;
import com.ghatana.kernel.ai.AgentOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finance AI evaluation framework — measures agent quality against declared criteria,
 * records performance history, and produces cross-agent comparison reports.
 *
 * <p>Evaluation is driven by the supplied {@link EvaluationCriteria}. When criteria
 * define explicit thresholds, the result reflects pass/fail against those thresholds
 * rather than fixed constants. Metrics are stored in-memory per agent for the
 * lifetime of the process; a durable adapter should be substituted in production.
 *
 * @doc.type class
 * @doc.purpose Finance AI evaluation — per-agent quality assessment and comparison reporting
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public class FinanceAIEvaluationImpl implements AIEvaluationFramework {

    private static final Logger log = LoggerFactory.getLogger(FinanceAIEvaluationImpl.class);

    /** Default approval thresholds used when criteria do not specify explicit values. */
    private static final double DEFAULT_ACCURACY_THRESHOLD  = 0.85;
    private static final double DEFAULT_PRECISION_THRESHOLD = 0.80;
    private static final double DEFAULT_RECALL_THRESHOLD    = 0.80;
    private static final long   DEFAULT_LATENCY_THRESHOLD_MS = 2_000L;
    private static final List<String> NO_AGENT_IDS = List.of();

    private final Map<String, List<EvaluationResult>> evaluationHistory = new ConcurrentHashMap<>();

    // =========================================================================
    // AIEvaluationFramework contract
    // =========================================================================

    @Override
    public EvaluationResult evaluateAgent(AgentOrchestrator.KernelAgent agent, EvaluationCriteria criteria) {
        Objects.requireNonNull(agent, "agent must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");

        long startMs = System.currentTimeMillis();
        AgentOrchestrator.AgentRequest probe = buildProbeRequest(agent, criteria.getCustomCriteria());
        AgentOrchestrator.AgentResponse response = agent.execute(probe);
        long latencyMs = System.currentTimeMillis() - startMs;

        double accuracy  = extractMetric(response, "accuracy",  DEFAULT_ACCURACY_THRESHOLD);
        double precision = extractMetric(response, "precision", DEFAULT_PRECISION_THRESHOLD);
        double recall    = extractMetric(response, "recall",    DEFAULT_RECALL_THRESHOLD);
        double f1Score   = (precision + recall > 0) ? 2 * precision * recall / (precision + recall) : 0.0;

        double accuracyThreshold = criteria.getAccuracyThreshold() > 0
            ? criteria.getAccuracyThreshold() : DEFAULT_ACCURACY_THRESHOLD;
        long latencyThreshold = criteria.getPerformanceThreshold() > 0
            ? criteria.getPerformanceThreshold() : DEFAULT_LATENCY_THRESHOLD_MS;

        boolean passed = accuracy >= accuracyThreshold && latencyMs <= latencyThreshold;

        String feedback = passed
            ? "Agent meets all evaluation thresholds"
            : buildFailureFeedback(accuracy, accuracyThreshold, precision, recall, latencyMs, latencyThreshold);

        EvaluationResult result = EvaluationResult.builder()
            .agentId(agent.getAgentId())
            .passed(passed)
            .accuracy(accuracy)
            .precision(precision)
            .recall(recall)
            .f1Score(f1Score)
            .latencyMillis(latencyMs)
            .customMetrics(extractCustomMetrics(response))
            .feedback(feedback)
            .build();

        evaluationHistory.computeIfAbsent(agent.getAgentId(), k -> new ArrayList<>()).add(result);
        log.info("Evaluated agent={} passed={} accuracy={} latencyMs={}",
                 agent.getAgentId(), passed, accuracy, latencyMs);
        return result;
    }

    @Override
    public void recordEvaluationMetrics(String agentId, EvaluationMetrics metrics) {
        Objects.requireNonNull(agentId,  "agentId must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");

        EvaluationResult snapshot = EvaluationResult.builder()
            .agentId(agentId)
            .passed(metrics.getAccuracy() >= DEFAULT_ACCURACY_THRESHOLD)
            .accuracy(metrics.getAccuracy())
            .precision(metrics.getPrecision())
            .recall(metrics.getRecall())
            .f1Score(computeF1(metrics.getPrecision(), metrics.getRecall()))
            .latencyMillis(metrics.getLatency())
            .customMetrics(metrics.getCustomMetrics() != null ? metrics.getCustomMetrics() : Map.of())
            .feedback("Externally recorded metrics")
            .build();

        evaluationHistory.computeIfAbsent(agentId, k -> new ArrayList<>()).add(snapshot);
        log.debug("Recorded external metrics for agentId={}", agentId);
    }

    @Override
    public ComparisonReport compareAgents(List<String> agentIds, ComparisonCriteria criteria) {
        Objects.requireNonNull(agentIds,  "agentIds must not be null");
        Objects.requireNonNull(criteria, "criteria must not be null");

        if (agentIds.isEmpty()) {
            return emptyComparisonReport();
        }

        Map<String, EvaluationResult> latestByAgent = new HashMap<>();
        for (String agentId : agentIds) {
            List<EvaluationResult> history = evaluationHistory.getOrDefault(agentId, List.of());
            if (!history.isEmpty()) {
                // Use the most-recent evaluation for each agent.
                latestByAgent.put(agentId, history.get(history.size() - 1));
            }
        }

        String sortMetric = criteria.getSortBy() != null ? criteria.getSortBy() : "accuracy";
        boolean ascending = criteria.isAscending();

        Comparator<EvaluationResult> comparator = Comparator.comparingDouble(r -> metricValue(r, sortMetric));
        if (!ascending) {
            comparator = comparator.reversed();
        }

        List<EvaluationResult> ranked = latestByAgent.values().stream().sorted(comparator).toList();

        String winnerId = ranked.isEmpty() ? null : ranked.get(0).getAgentId();
        log.info("Compared {} agents on metric='{}', winner='{}'", agentIds.size(), sortMetric, winnerId);

        Map<String, EvaluationResult> resultsMap = new HashMap<>();
        for (EvaluationResult result : ranked) {
            resultsMap.put(result.getAgentId(), result);
        }

        Map<String, Object> summary = Map.of(
            "sortBy", sortMetric,
            "ascending", ascending,
            "evaluated", resultsMap.size()
        );

        String best = ranked.isEmpty() ? null : ranked.get(0).getAgentId();
        return new ComparisonReport() {
            @Override
            public List<String> getAgentIds() {
                return List.copyOf(agentIds);
            }

            @Override
            public Map<String, EvaluationResult> getResults() {
                return Map.copyOf(resultsMap);
            }

            @Override
            public String getBestAgent() {
                return best;
            }

            @Override
            public Map<String, Object> getSummary() {
                return summary;
            }
        };
    }

    @Override
    public List<EvaluationResult> getEvaluationHistory(String agentId) {
        return List.copyOf(evaluationHistory.getOrDefault(agentId, List.of()));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static AgentOrchestrator.AgentRequest buildProbeRequest(
            AgentOrchestrator.KernelAgent agent, Map<String, Object> customCriteria) {
        return new AgentOrchestrator.AgentRequest(
            agent.getAgentId(),
            "evaluate",
            customCriteria != null ? customCriteria : Map.of(),
            Map.of("evaluation", "true")
        );
    }

    private static Map<String, Double> extractCustomMetrics(AgentOrchestrator.AgentResponse response) {
        if (response == null || response.getMetadata() == null) {
            return Map.of();
        }
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Object> entry : response.getMetadata().entrySet()) {
            if (entry.getValue() instanceof Number n) {
                out.put(entry.getKey(), n.doubleValue());
            }
        }
        return out;
    }

    private static double extractMetric(AgentOrchestrator.AgentResponse response, String key, double defaultValue) {
        if (response == null || response.getMetadata() == null) return defaultValue;
        Object val = response.getMetadata().get(key);
        if (val instanceof Number n) return n.doubleValue();
        if ("accuracy".equals(key)) {
            return response.getConfidence() > 0 ? response.getConfidence() : defaultValue;
        }
        return defaultValue;
    }

    private static ComparisonReport emptyComparisonReport() {
        return new ComparisonReport() {
            @Override
            public List<String> getAgentIds() {
                return NO_AGENT_IDS;
            }

            @Override
            public Map<String, EvaluationResult> getResults() {
                return Map.of();
            }

            @Override
            public String getBestAgent() {
                return null;
            }

            @Override
            public Map<String, Object> getSummary() {
                return Map.of("evaluated", 0);
            }
        };
    }

    private static double metricValue(EvaluationResult r, String metric) {
        return switch (metric) {
            case "accuracy"  -> r.getAccuracy();
            case "precision" -> r.getPrecision();
            case "recall"    -> r.getRecall();
            case "f1"        -> r.getF1Score();
            default          -> r.getAccuracy();
        };
    }

    private static String buildFailureFeedback(
            double accuracy,  double accThresh,
            double precision,
            double recall,
            long latencyMs,   long latThresh) {
        List<String> failures = new ArrayList<>();
        if (accuracy  < accThresh) failures.add("accuracy=" + accuracy + " < " + accThresh);
        if (precision < DEFAULT_PRECISION_THRESHOLD) failures.add("precision=" + precision + " < " + DEFAULT_PRECISION_THRESHOLD);
        if (recall    < DEFAULT_RECALL_THRESHOLD) failures.add("recall=" + recall + " < " + DEFAULT_RECALL_THRESHOLD);
        if (latencyMs > latThresh) failures.add("latencyMs=" + latencyMs + " > " + latThresh);
        return "Evaluation failed: " + String.join(", ", failures);
    }

    private static double computeF1(double precision, double recall) {
        double total = precision + recall;
        return total == 0 ? 0.0 : (2 * precision * recall) / total;
    }
}
