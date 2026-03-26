package com.ghatana.kernel.ai;

import java.util.List;
import java.util.Map;

/**
 * Framework for evaluating AI agent performance.
 *
 * <p>Provides comprehensive evaluation capabilities for AI agents including
 * performance metrics, quality assessment, and comparative analysis.</p>
 *
 * @doc.type interface
 * @doc.purpose AI agent evaluation and performance assessment
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface AIEvaluationFramework {

    /**
     * Evaluates an agent against criteria.
     *
     * @param agent the agent to evaluate
     * @param criteria the evaluation criteria
     * @return evaluation result
     */
    EvaluationResult evaluateAgent(AgentOrchestrator.KernelAgent agent, EvaluationCriteria criteria);

    /**
     * Records evaluation metrics for an agent.
     *
     * @param agentId the agent identifier
     * @param metrics the evaluation metrics
     */
    void recordEvaluationMetrics(String agentId, EvaluationMetrics metrics);

    /**
     * Compares multiple agents.
     *
     * @param agentIds the agent identifiers to compare
     * @param criteria the comparison criteria
     * @return comparison report
     */
    ComparisonReport compareAgents(List<String> agentIds, ComparisonCriteria criteria);

    /**
     * Gets evaluation history for an agent.
     *
     * @param agentId the agent identifier
     * @return list of evaluation results
     */
    List<EvaluationResult> getEvaluationHistory(String agentId);

    /**
     * Evaluation result.
     */
    class EvaluationResult {
        private final String agentId;
        private final boolean passed;
        private final double accuracy;
        private final double precision;
        private final double recall;
        private final double f1Score;
        private final long latencyMillis;
        private final Map<String, Double> customMetrics;
        private final String feedback;
        private final long timestamp;

        private EvaluationResult(Builder builder) {
            this.agentId = builder.agentId;
            this.passed = builder.passed;
            this.accuracy = builder.accuracy;
            this.precision = builder.precision;
            this.recall = builder.recall;
            this.f1Score = builder.f1Score;
            this.latencyMillis = builder.latencyMillis;
            this.customMetrics = builder.customMetrics;
            this.feedback = builder.feedback;
            this.timestamp = builder.timestamp;
        }

        public String getAgentId() { return agentId; }
        public boolean isPassed() { return passed; }
        public double getAccuracy() { return accuracy; }
        public double getPrecision() { return precision; }
        public double getRecall() { return recall; }
        public double getF1Score() { return f1Score; }
        public long getLatencyMillis() { return latencyMillis; }
        public Map<String, Double> getCustomMetrics() { return customMetrics; }
        public String getFeedback() { return feedback; }
        public long getTimestamp() { return timestamp; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String agentId;
            private boolean passed;
            private double accuracy;
            private double precision;
            private double recall;
            private double f1Score;
            private long latencyMillis;
            private Map<String, Double> customMetrics = Map.of();
            private String feedback;
            private long timestamp = System.currentTimeMillis();

            public Builder agentId(String agentId) {
                this.agentId = agentId;
                return this;
            }

            public Builder passed(boolean passed) {
                this.passed = passed;
                return this;
            }

            public Builder accuracy(double accuracy) {
                this.accuracy = accuracy;
                return this;
            }

            public Builder precision(double precision) {
                this.precision = precision;
                return this;
            }

            public Builder recall(double recall) {
                this.recall = recall;
                return this;
            }

            public Builder f1Score(double f1Score) {
                this.f1Score = f1Score;
                return this;
            }

            public Builder latencyMillis(long latencyMillis) {
                this.latencyMillis = latencyMillis;
                return this;
            }

            public Builder customMetrics(Map<String, Double> customMetrics) {
                this.customMetrics = customMetrics;
                return this;
            }

            public Builder feedback(String feedback) {
                this.feedback = feedback;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public EvaluationResult build() {
                return new EvaluationResult(this);
            }
        }
    }

    /**
     * Evaluation criteria.
     */
    class EvaluationCriteria {
        private final double accuracyThreshold;
        private final long performanceThreshold;
        private final boolean complianceCheck;
        private final Map<String, Object> customCriteria;

        private EvaluationCriteria(Builder builder) {
            this.accuracyThreshold = builder.accuracyThreshold;
            this.performanceThreshold = builder.performanceThreshold;
            this.complianceCheck = builder.complianceCheck;
            this.customCriteria = builder.customCriteria;
        }

        public double getAccuracyThreshold() { return accuracyThreshold; }
        public long getPerformanceThreshold() { return performanceThreshold; }
        public boolean isComplianceCheck() { return complianceCheck; }
        public Map<String, Object> getCustomCriteria() { return customCriteria; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private double accuracyThreshold = 0.9;
            private long performanceThreshold = 1000;
            private boolean complianceCheck = true;
            private Map<String, Object> customCriteria = Map.of();

            public Builder withAccuracyThreshold(double threshold) {
                this.accuracyThreshold = threshold;
                return this;
            }

            public Builder withPerformanceThreshold(long threshold) {
                this.performanceThreshold = threshold;
                return this;
            }

            public Builder withComplianceCheck(boolean check) {
                this.complianceCheck = check;
                return this;
            }

            public Builder withCustomCriteria(Map<String, Object> criteria) {
                this.customCriteria = criteria;
                return this;
            }

            public EvaluationCriteria build() {
                return new EvaluationCriteria(this);
            }
        }
    }

    /**
     * Evaluation metrics.
     */
    interface EvaluationMetrics {
        double getAccuracy();
        double getPrecision();
        double getRecall();
        long getLatency();
        Map<String, Double> getCustomMetrics();
    }

    /**
     * Comparison criteria for agents.
     */
    interface ComparisonCriteria {
        List<String> getMetrics();
        String getSortBy();
        boolean isAscending();
    }

    /**
     * Comparison report for multiple agents.
     */
    interface ComparisonReport {
        List<String> getAgentIds();
        Map<String, EvaluationResult> getResults();
        String getBestAgent();
        Map<String, Object> getSummary();
    }
}
