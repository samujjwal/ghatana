/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.governance;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * API for assessing the quality of agent memory retrieval operations.
 *
 * <p>Provides precision/recall metrics, query quality scoring, and relevance
 * assessment for memory recall queries. Used by the promotion and governance
 * pipeline to evaluate whether episodic memories are reliable candidates for
 * procedural promotion.
 *
 * @doc.type interface
 * @doc.purpose Memory retrieval quality assessment: precision, recall, and relevance scoring
 * @doc.layer product
 * @doc.pattern Service Interface
 */
public interface RetrievalQualityService {

    /**
     * Scores a set of recalled memory entries against an expected relevance set.
     *
     * <p>Computes precision and recall compared to the {@code expectedIds} set,
     * returning a {@link QualityReport} that can inform promotion gate decisions.
     *
     * @param agentId     the agent whose recall results are being assessed
     * @param tenantId    the tenant scope
     * @param recalledIds list of memory entry IDs that were recalled
     * @param expectedIds list of memory entry IDs that were expected
     * @return a quality report with precision, recall, and F1 score
     */
    Promise<QualityReport> score(
            String agentId,
            String tenantId,
            List<String> recalledIds,
            List<String> expectedIds);

    /**
     * Returns the rolling average retrieval quality for a given agent.
     *
     * <p>Aggregates recent {@link QualityReport} records to produce a summary
     * suitable for dashboards and governance alerts.
     *
     * @param agentId  the agent to aggregate
     * @param tenantId the tenant scope
     * @param window   number of recent reports to include in the rolling average
     * @return an aggregated quality summary
     */
    Promise<QualitySummary> rollingAverage(String agentId, String tenantId, int window);

    // ─── Value types ──────────────────────────────────────────────────────────

    /**
     * Precision/recall quality report for a single recall operation.
     *
     * @param agentId   agent being assessed
     * @param precision fraction of recalled items that are relevant (TP / (TP + FP))
     * @param recall    fraction of relevant items that were recalled (TP / (TP + FN))
     * @param f1Score   harmonic mean of precision and recall
     * @param truePositiveCount  count of correctly recalled items
     * @param falsePositiveCount count of incorrectly recalled items
     * @param falseNegativeCount count of missed relevant items
     */
    record QualityReport(
            @NotNull String agentId,
            double precision,
            double recall,
            double f1Score,
            int truePositiveCount,
            int falsePositiveCount,
            int falseNegativeCount
    ) {
        /**
         * Returns {@code true} if both precision and recall exceed the given threshold.
         *
         * @param threshold minimum acceptable value for both metrics
         */
        public boolean meetsThreshold(double threshold) {
            return precision >= threshold && recall >= threshold;
        }
    }

    /**
     * Aggregated quality summary across multiple recall operations.
     *
     * @param agentId          agent being summarised
     * @param averagePrecision rolling average precision
     * @param averageRecall    rolling average recall
     * @param averageF1Score   rolling average F1 score
     * @param reportsConsidered number of reports included in the average
     */
    record QualitySummary(
            @NotNull String agentId,
            double averagePrecision,
            double averageRecall,
            double averageF1Score,
            int reportsConsidered
    ) {}
}
