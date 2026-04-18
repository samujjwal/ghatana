/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for policy accuracy and effectiveness.
 * <p>
 * Tracks the effectiveness of auto-promoted policies, including true/false positives,
 * false negatives, and overall accuracy metrics.
 *
 * @doc.type class
 * @doc.purpose Track policy accuracy and effectiveness metrics
 * @doc.layer product
 * @doc.pattern Observer
 */
public class PolicyAccuracyMetrics {

    private static final Logger logger = LoggerFactory.getLogger(PolicyAccuracyMetrics.class);

    private final Metrics metrics;
    private final Map<String, PolicyStats> statsByPolicyId = new ConcurrentHashMap<>();

    public PolicyAccuracyMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Record a true positive policy decision (policy correctly triggered).
     *
     * @param policyId unique policy identifier
     * @param decisionType type of decision (e.g., "block", "warn", "allow")
     */
    public void recordTruePositive(String policyId, String decisionType) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementTruePositives();
        metrics.counter("policy.true_positive", "policyId", policyId, "decision", decisionType).increment();
        logger.debug("Policy true positive: policyId={}, decision={}", policyId, decisionType);
    }

    /**
     * Record a false positive policy decision (policy incorrectly triggered).
     *
     * @param policyId unique policy identifier
     * @param decisionType type of decision
     */
    public void recordFalsePositive(String policyId, String decisionType) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementFalsePositives();
        metrics.counter("policy.false_positive", "policyId", policyId, "decision", decisionType).increment();
        logger.debug("Policy false positive: policyId={}, decision={}", policyId, decisionType);
    }

    /**
     * Record a false negative (policy should have triggered but didn't).
     *
     * @param policyId unique policy identifier
     */
    public void recordFalseNegative(String policyId) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementFalseNegatives();
        metrics.counter("policy.false_negative", "policyId", policyId).increment();
        logger.debug("Policy false negative: policyId={}", policyId);
    }

    /**
     * Record a true negative (policy correctly did not trigger).
     *
     * @param policyId unique policy identifier
     */
    public void recordTrueNegative(String policyId) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementTrueNegatives();
        metrics.counter("policy.true_negative", "policyId", policyId).increment();
        logger.debug("Policy true negative: policyId={}", policyId);
    }

    /**
     * Record a policy promotion (auto-promoted from draft to active).
     *
     * @param policyId unique policy identifier
     * @param source source of promotion (e.g., "auto", "manual")
     */
    public void recordPolicyPromotion(String policyId, String source) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementPromotions();
        metrics.counter("policy.promotion", "policyId", policyId, "source", source).increment();
        logger.debug("Policy promoted: policyId={}, source={}", policyId, source);
    }

    /**
     * Record a policy demotion (demoted from active back to draft).
     *
     * @param policyId unique policy identifier
     * @param reason reason for demotion
     */
    public void recordPolicyDemotion(String policyId, String reason) {
        PolicyStats stats = statsByPolicyId.computeIfAbsent(policyId, PolicyStats::new);
        stats.incrementDemotions();
        metrics.counter("policy.demotion", "policyId", policyId, "reason", reason).increment();
        logger.debug("Policy demoted: policyId={}, reason={}", policyId, reason);
    }

    /**
     * Get statistics for a policy.
     *
     * @param policyId unique policy identifier
     * @return statistics for the policy
     */
    public PolicyStats getStats(String policyId) {
        return statsByPolicyId.getOrDefault(policyId, new PolicyStats());
    }

    /**
     * Get accuracy for a policy.
     *
     * @param policyId unique policy identifier
     * @return accuracy (0.0 to 1.0)
     */
    public double getAccuracy(String policyId) {
        PolicyStats stats = statsByPolicyId.getOrDefault(policyId, new PolicyStats());
        return stats.accuracy();
    }

    /**
     * Get precision for a policy.
     *
     * @param policyId unique policy identifier
     * @return precision (0.0 to 1.0)
     */
    public double getPrecision(String policyId) {
        PolicyStats stats = statsByPolicyId.getOrDefault(policyId, new PolicyStats());
        return stats.precision();
    }

    /**
     * Get recall for a policy.
     *
     * @param policyId unique policy identifier
     * @return recall (0.0 to 1.0)
     */
    public double getRecall(String policyId) {
        PolicyStats stats = statsByPolicyId.getOrDefault(policyId, new PolicyStats());
        return stats.recall();
    }

    /**
     * Get promotion success rate for a policy.
     *
     * @param policyId unique policy identifier
     * @return promotion success rate (0.0 to 1.0)
     */
    public double getPromotionSuccessRate(String policyId) {
        PolicyStats stats = statsByPolicyId.getOrDefault(policyId, new PolicyStats());
        long promotions = stats.promotions();
        long demotions = stats.demotions();
        if (promotions == 0) return 0.0;
        return (double) (promotions - demotions) / promotions;
    }

    /**
     * Statistics for a policy.
     */
    public static class PolicyStats {
        private final AtomicLong truePositives = new AtomicLong(0);
        private final AtomicLong falsePositives = new AtomicLong(0);
        private final AtomicLong falseNegatives = new AtomicLong(0);
        private final AtomicLong trueNegatives = new AtomicLong(0);
        private final AtomicLong promotions = new AtomicLong(0);
        private final AtomicLong demotions = new AtomicLong(0);

        public PolicyStats() {}

        public void incrementTruePositives() { truePositives.incrementAndGet(); }
        public void incrementFalsePositives() { falsePositives.incrementAndGet(); }
        public void incrementFalseNegatives() { falseNegatives.incrementAndGet(); }
        public void incrementTrueNegatives() { trueNegatives.incrementAndGet(); }
        public void incrementPromotions() { promotions.incrementAndGet(); }
        public void incrementDemotions() { demotions.incrementAndGet(); }

        public long truePositives() { return truePositives.get(); }
        public long falsePositives() { return falsePositives.get(); }
        public long falseNegatives() { return falseNegatives.get(); }
        public long trueNegatives() { return trueNegatives.get(); }
        public long promotions() { return promotions.get(); }
        public long demotions() { return demotions.get(); }

        public double precision() {
            long tp = truePositives.get();
            long fp = falsePositives.get();
            if (tp + fp == 0) return 0.0;
            return (double) tp / (tp + fp);
        }

        public double recall() {
            long tp = truePositives.get();
            long fn = falseNegatives.get();
            if (tp + fn == 0) return 0.0;
            return (double) tp / (tp + fn);
        }

        public double accuracy() {
            long tp = truePositives.get();
            long tn = trueNegatives.get();
            long fp = falsePositives.get();
            long fn = falseNegatives.get();
            long total = tp + tn + fp + fn;
            if (total == 0) return 0.0;
            return (double) (tp + tn) / total;
        }

        public double f1Score() {
            double precision = precision();
            double recall = recall();
            if (precision + recall == 0) return 0.0;
            return 2 * (precision * recall) / (precision + recall);
        }
    }
}
