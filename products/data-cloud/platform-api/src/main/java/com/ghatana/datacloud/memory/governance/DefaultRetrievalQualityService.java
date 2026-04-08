/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.governance;

import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Default implementation of {@link RetrievalQualityService}.
 *
 * <p>Computes precision, recall, and F1 score using set-intersection logic, and
 * maintains an in-memory rolling history for the {@link #rollingAverage} query.
 *
 * @doc.type class
 * @doc.purpose Precision/recall computation and rolling average for memory retrieval quality
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DefaultRetrievalQualityService implements RetrievalQualityService {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final ConcurrentLinkedDeque<ReportRecord> history = new ConcurrentLinkedDeque<>();
    private final int historyLimit;

    /**
     * Creates a service with the default rolling history size of {@value #DEFAULT_HISTORY_LIMIT}.
     */
    public DefaultRetrievalQualityService() {
        this(DEFAULT_HISTORY_LIMIT);
    }

    /**
     * Creates a service with a custom rolling history size.
     *
     * @param historyLimit maximum number of quality reports to retain in memory
     */
    public DefaultRetrievalQualityService(int historyLimit) {
        if (historyLimit <= 0) {
            throw new IllegalArgumentException("historyLimit must be > 0");
        }
        this.historyLimit = historyLimit;
    }

    @Override
    public Promise<QualityReport> score(
            String agentId,
            String tenantId,
            List<String> recalledIds,
            List<String> expectedIds) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recalledIds, "recalledIds");
        Objects.requireNonNull(expectedIds, "expectedIds");

        Set<String> recalled  = new HashSet<>(recalledIds);
        Set<String> expected  = new HashSet<>(expectedIds);

        int truePositives  = (int) recalled.stream().filter(expected::contains).count();
        int falsePositives = recalled.size() - truePositives;
        int falseNegatives = (int) expected.stream().filter(id -> !recalled.contains(id)).count();

        double precision = recalled.isEmpty() ? 0.0 : (double) truePositives / recalled.size();
        double recall    = expected.isEmpty()  ? 0.0 : (double) truePositives / expected.size();
        double f1Score   = (precision + recall) == 0.0 ? 0.0
                : 2.0 * (precision * recall) / (precision + recall);

        QualityReport report = new QualityReport(
                agentId, precision, recall, f1Score, truePositives, falsePositives, falseNegatives);

        storeInHistory(new ReportRecord(agentId, tenantId, precision, recall, f1Score));
        return Promise.of(report);
    }

    @Override
    public Promise<QualitySummary> rollingAverage(String agentId, String tenantId, int window) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        if (window <= 0) {
            return Promise.ofException(new IllegalArgumentException("window must be > 0"));
        }

        List<ReportRecord> matching = history.stream()
                .filter(r -> r.agentId.equals(agentId) && r.tenantId.equals(tenantId))
                .toList();

        int limit = Math.min(window, matching.size());
        List<ReportRecord> recent = matching.subList(matching.size() - limit, matching.size());

        if (recent.isEmpty()) {
            return Promise.of(new QualitySummary(agentId, 0.0, 0.0, 0.0, 0));
        }

        double avgPrecision = recent.stream().mapToDouble(r -> r.precision).average().orElse(0.0);
        double avgRecall    = recent.stream().mapToDouble(r -> r.recall).average().orElse(0.0);
        double avgF1        = recent.stream().mapToDouble(r -> r.f1).average().orElse(0.0);

        return Promise.of(new QualitySummary(agentId, avgPrecision, avgRecall, avgF1, recent.size()));
    }

    // ─── Internal history management ──────────────────────────────────────────

    private void storeInHistory(ReportRecord record) {
        history.addLast(record);
        while (history.size() > historyLimit) {
            history.pollFirst();
        }
    }

    private record ReportRecord(
            String agentId,
            String tenantId,
            double precision,
            double recall,
            double f1
    ) {}
}
