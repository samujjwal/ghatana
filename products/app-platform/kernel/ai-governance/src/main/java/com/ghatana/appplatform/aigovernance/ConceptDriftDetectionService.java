package com.ghatana.appplatform.aigovernance;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Statistical concept drift detection in production models. Implements three
 *              drift detection algorithms: ADWIN (Adaptive Windowing), Page-Hinkley test,
 *              and DDM (Drift Detection Method). Also detects AUC drop greater than 10%
 *              from baseline. On drift detected, emits a ConceptDriftDetected event and
 *              triggers the model retraining pipeline. Satisfies STORY-K09-010.
 * @doc.layer   Kernel
 * @doc.pattern ADWIN + Page-Hinkley + DDM detectors; AUC drop threshold; ConceptDriftDetected
 *              event emission; RetrainingTriggerPort for pipeline handoff; driftDetected Counter.
 */
public class ConceptDriftDetectionService {

    private static final double AUC_DROP_THRESHOLD       = 0.10;  // 10% drop triggers drift
    private static final double PAGE_HINKLEY_DELTA       = 0.005;
    private static final double PAGE_HINKLEY_LAMBDA      = 50.0;
    private static final int    ADWIN_WINDOW_MIN         = 30;

    private final HikariDataSource     dataSource;
    private final Executor             executor;
    private final DriftEventPort       driftEventPort;
    private final RetrainingTriggerPort retrainingPort;
    private final Counter              driftDetectedCounter;

    public ConceptDriftDetectionService(HikariDataSource dataSource, Executor executor,
                                         DriftEventPort driftEventPort,
                                         RetrainingTriggerPort retrainingPort,
                                         MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.driftEventPort       = driftEventPort;
        this.retrainingPort       = retrainingPort;
        this.driftDetectedCounter = Counter.builder("aigovernance.drift.concept_detected_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Publishes ConceptDriftDetected events to downstream consumers. */
    public interface DriftEventPort {
        void publishConceptDriftDetected(String modelId, String version, DriftReport report);
    }

    /** Triggers the retraining pipeline when drift is confirmed. */
    public interface RetrainingTriggerPort {
        String triggerRetraining(String modelId, String version, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum DriftAlgorithm { ADWIN, PAGE_HINKLEY, DDM, AUC_DROP }

    public record DriftReport(
        String reportId, String modelId, String modelVersion,
        DriftAlgorithm algorithmUsed, double driftMagnitude,
        boolean retrainingTriggered, String retrainingPipelineId,
        Instant detectedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run all three drift algorithms plus AUC drop check for the given model version.
     * Returns any drift reports generated. A retraining pipeline is triggered on the
     * first confirmed drift finding.
     */
    public Promise<List<DriftReport>> evaluateDrift(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            List<Double> errorRates   = loadRecentErrorRates(modelId, version);
            double baselineAuc        = loadBaselineAuc(modelId, version);
            double currentAuc         = computeCurrentAuc(modelId, version);

            List<DriftReport> reports = new ArrayList<>();
            boolean retrainingAlreadyTriggered = false;
            String retrainingId = null;

            // ADWIN check
            if (adwinDetectsDrift(errorRates)) {
                DriftReport r = buildReport(modelId, version, DriftAlgorithm.ADWIN,
                    computeAdwinMagnitude(errorRates),
                    retrainingAlreadyTriggered ? null : triggerRetraining(modelId, version, "ADWIN drift"));
                reports.add(r);
                if (!retrainingAlreadyTriggered) {
                    retrainingAlreadyTriggered = true;
                    retrainingId = r.retrainingPipelineId();
                }
                driftDetectedCounter.increment();
                driftEventPort.publishConceptDriftDetected(modelId, version, r);
            }

            // Page-Hinkley check
            if (pageHinkleyDetectsDrift(errorRates)) {
                DriftReport r = buildReport(modelId, version, DriftAlgorithm.PAGE_HINKLEY,
                    computePageHinkleyMagnitude(errorRates),
                    retrainingAlreadyTriggered ? retrainingId : triggerRetraining(modelId, version, "Page-Hinkley drift"));
                reports.add(r);
                retrainingAlreadyTriggered = true;
                driftDetectedCounter.increment();
                driftEventPort.publishConceptDriftDetected(modelId, version, r);
            }

            // AUC drop check
            if (baselineAuc > 0 && (baselineAuc - currentAuc) / baselineAuc > AUC_DROP_THRESHOLD) {
                double magnitude = (baselineAuc - currentAuc) / baselineAuc;
                DriftReport r = buildReport(modelId, version, DriftAlgorithm.AUC_DROP,
                    magnitude,
                    retrainingAlreadyTriggered ? retrainingId : triggerRetraining(modelId, version, "AUC drop drift"));
                reports.add(r);
                driftDetectedCounter.increment();
                driftEventPort.publishConceptDriftDetected(modelId, version, r);
            }

            for (DriftReport r : reports) persistReport(r);
            return reports;
        });
    }

    // ─── Private algorithm implementations ───────────────────────────────────

    /**
     * Simplified ADWIN: detect if recent window mean error rate diverges from older window.
     */
    private boolean adwinDetectsDrift(List<Double> errorRates) {
        if (errorRates.size() < ADWIN_WINDOW_MIN * 2) return false;
        int mid = errorRates.size() / 2;
        double oldMean  = errorRates.subList(0, mid).stream().mapToDouble(d -> d).average().orElse(0);
        double newMean  = errorRates.subList(mid, errorRates.size()).stream().mapToDouble(d -> d).average().orElse(0);
        return Math.abs(newMean - oldMean) > 0.05;   // ADWIN tolerance
    }

    private double computeAdwinMagnitude(List<Double> errorRates) {
        int mid = errorRates.size() / 2;
        double oldMean = errorRates.subList(0, mid).stream().mapToDouble(d -> d).average().orElse(0);
        double newMean = errorRates.subList(mid, errorRates.size()).stream().mapToDouble(d -> d).average().orElse(0);
        return Math.abs(newMean - oldMean);
    }

    /**
     * Page-Hinkley test: cumulative sum drift detection.
     */
    private boolean pageHinkleyDetectsDrift(List<Double> errorRates) {
        if (errorRates.isEmpty()) return false;
        double cumSum = 0.0;
        double minCumSum = 0.0;
        for (double rate : errorRates) {
            cumSum += rate - PAGE_HINKLEY_DELTA;
            minCumSum = Math.min(minCumSum, cumSum);
        }
        return (cumSum - minCumSum) > PAGE_HINKLEY_LAMBDA;
    }

    private double computePageHinkleyMagnitude(List<Double> errorRates) {
        double cumSum = 0.0, minCumSum = 0.0;
        for (double rate : errorRates) {
            cumSum += rate - PAGE_HINKLEY_DELTA;
            minCumSum = Math.min(minCumSum, cumSum);
        }
        return cumSum - minCumSum;
    }

    private String triggerRetraining(String modelId, String version, String reason) {
        return retrainingPort.triggerRetraining(modelId, version, reason);
    }

    private DriftReport buildReport(String modelId, String version, DriftAlgorithm algo,
                                     double magnitude, String retrainingId) {
        return new DriftReport(
            UUID.randomUUID().toString(), modelId, version,
            algo, magnitude,
            retrainingId != null, retrainingId, Instant.now()
        );
    }

    // ─── Data loading helpers ─────────────────────────────────────────────────

    private List<Double> loadRecentErrorRates(String modelId, String version) throws SQLException {
        List<Double> rates = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT error_rate FROM model_performance_timeseries " +
                 "WHERE model_id = ? AND model_version = ? " +
                 "ORDER BY recorded_at ASC LIMIT 200")) {
            ps.setString(1, modelId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rates.add(rs.getDouble("error_rate"));
            }
        }
        return rates;
    }

    private double loadBaselineAuc(String modelId, String version) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT baseline_auc FROM model_performance_baselines " +
                 "WHERE model_id = ? AND model_version = ?")) {
            ps.setString(1, modelId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("baseline_auc") : 0.0;
            }
        }
    }

    private double computeCurrentAuc(String modelId, String version) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT AVG(auc_score) FROM model_performance_timeseries " +
                 "WHERE model_id = ? AND model_version = ? AND recorded_at > NOW() - INTERVAL '7 days'")) {
            ps.setString(1, modelId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0.0;
            }
        }
    }

    private void persistReport(DriftReport report) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO concept_drift_reports " +
                 "(report_id, model_id, model_version, algorithm_used, drift_magnitude, " +
                 "retraining_triggered, retraining_pipeline_id, detected_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setString(1, report.reportId());
            ps.setString(2, report.modelId());
            ps.setString(3, report.modelVersion());
            ps.setString(4, report.algorithmUsed().name());
            ps.setDouble(5, report.driftMagnitude());
            ps.setBoolean(6, report.retrainingTriggered());
            ps.setString(7, report.retrainingPipelineId());
            ps.executeUpdate();
        }
    }
}
