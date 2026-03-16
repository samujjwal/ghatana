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
 * @doc.purpose Statistical bias detection across three fairness criteria: Demographic Parity
 *              (equal positive prediction rates across groups), Equalized Odds (equal TPR and
 *              FPR across groups), and Predictive Parity (equal PPV across groups). Operates on
 *              the prediction audit log segmented by protected attributes (gender, ethnicity,
 *              age group, etc.). Emits BiasDetected events and can block model deployment when
 *              bias exceeds configured tolerance. Satisfies STORY-K09-007.
 * @doc.layer   Kernel
 * @doc.pattern Three fairness metrics; protected attribute segmentation; BiasDetected event;
 *              deployment gate via BiasGatePort; biasDetected/deploymentBlocked Counters.
 */
public class BiasDetectionEngineService {

    // Bias tolerance: disparity > this triggers BiasDetected event
    private static final double DEFAULT_BIAS_TOLERANCE = 0.1;

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final BiasEventPort     biasEventPort;
    private final BiasGatePort      biasGatePort;
    private final Counter           biasDetectedCounter;
    private final Counter           deploymentBlockedCounter;

    public BiasDetectionEngineService(HikariDataSource dataSource, Executor executor,
                                       BiasEventPort biasEventPort,
                                       BiasGatePort biasGatePort,
                                       MeterRegistry registry) {
        this.dataSource               = dataSource;
        this.executor                 = executor;
        this.biasEventPort            = biasEventPort;
        this.biasGatePort             = biasGatePort;
        this.biasDetectedCounter      = Counter.builder("aigovernance.bias.detected_total").register(registry);
        this.deploymentBlockedCounter = Counter.builder("aigovernance.bias.deployment_blocked_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** Emits BiasDetected events for downstream consumers. */
    public interface BiasEventPort {
        void publishBiasDetected(String modelId, String version, BiasReport report);
    }

    /** Deployment gate: blocks deployment if unresolved bias above tolerance. */
    public interface BiasGatePort {
        void blockDeployment(String modelId, String version, String reason);
        void clearBlock(String modelId, String version);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum FairnessMetric { DEMOGRAPHIC_PARITY, EQUALIZED_ODDS, PREDICTIVE_PARITY }
    public enum BiasLevel { NONE, LOW, MEDIUM, HIGH }

    public record GroupStats(
        String attributeName, String groupValue,
        long totalPredictions, long positives,
        double tpr, double fpr, double ppv,
        double positiveRate
    ) {}

    public record BiasReport(
        String reportId, String modelId, String modelVersion,
        String protectedAttribute,
        FairnessMetric metric, double disparity,
        double tolerance, BiasLevel level,
        List<GroupStats> groupStats,
        Instant evaluatedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Run bias detection for the given model version across a protected attribute.
     * Reads from bias_evaluation_data table (joined prediction logs with ground truth).
     */
    public Promise<BiasReport> evaluateBias(String modelId, String version,
                                             String protectedAttribute,
                                             FairnessMetric metric) {
        return Promise.ofBlocking(executor, () -> {
            List<GroupStats> groupStats = loadGroupStats(modelId, version, protectedAttribute);
            double disparity = computeDisparity(groupStats, metric);
            BiasLevel level  = classifyBias(disparity);

            String reportId = UUID.randomUUID().toString();
            BiasReport report = new BiasReport(
                reportId, modelId, version, protectedAttribute,
                metric, disparity, DEFAULT_BIAS_TOLERANCE, level,
                groupStats, Instant.now()
            );

            persistReport(report);

            if (disparity > DEFAULT_BIAS_TOLERANCE) {
                biasDetectedCounter.increment();
                biasEventPort.publishBiasDetected(modelId, version, report);
            }

            return report;
        });
    }

    /**
     * Gate deployment: checks all stored bias reports for this model version and blocks
     * deployment if any HIGH or MEDIUM level bias is unresolved.
     */
    public Promise<Boolean> checkDeploymentGate(String modelId, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM bias_reports " +
                     "WHERE model_id = ? AND model_version = ? " +
                     "AND bias_level IN ('HIGH', 'MEDIUM') AND resolved = FALSE")) {
                ps.setString(1, modelId);
                ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    long blockers = rs.next() ? rs.getLong(1) : 0L;
                    if (blockers > 0) {
                        biasGatePort.blockDeployment(modelId, version,
                            blockers + " unresolved bias report(s)");
                        deploymentBlockedCounter.increment();
                        return false;
                    }
                    return true;
                }
            }
        });
    }

    /**
     * Mark a bias report as resolved (remediation applied, re-evaluated, acceptable).
     */
    public Promise<Void> resolveReport(String reportId, String resolvedBy, String notes) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE bias_reports SET resolved = TRUE, resolved_by = ?, " +
                     "resolution_notes = ?, resolved_at = NOW() WHERE report_id = ?")) {
                ps.setString(1, resolvedBy);
                ps.setString(2, notes);
                ps.setString(3, reportId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<GroupStats> loadGroupStats(String modelId, String version,
                                             String protectedAttribute) throws SQLException {
        List<GroupStats> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT group_value, total_predictions, positives, true_positives, " +
                 "false_positives, true_negatives, false_negatives " +
                 "FROM bias_evaluation_data " +
                 "WHERE model_id = ? AND model_version = ? AND attribute_name = ?")) {
            ps.setString(1, modelId);
            ps.setString(2, version);
            ps.setString(3, protectedAttribute);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long total  = rs.getLong("total_predictions");
                    long pos    = rs.getLong("positives");
                    long tp     = rs.getLong("true_positives");
                    long fp     = rs.getLong("false_positives");
                    long tn     = rs.getLong("true_negatives");
                    long fn     = rs.getLong("false_negatives");
                    double tpr  = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
                    double fpr  = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0.0;
                    double ppv  = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
                    results.add(new GroupStats(protectedAttribute, rs.getString("group_value"),
                        total, pos, tpr, fpr, ppv, total > 0 ? (double) pos / total : 0.0));
                }
            }
        }
        return results;
    }

    private double computeDisparity(List<GroupStats> groups, FairnessMetric metric) {
        if (groups.size() < 2) return 0.0;
        double[] values = switch (metric) {
            case DEMOGRAPHIC_PARITY -> groups.stream().mapToDouble(GroupStats::positiveRate).toArray();
            case EQUALIZED_ODDS     -> groups.stream().mapToDouble(g -> (g.tpr() + g.fpr()) / 2).toArray();
            case PREDICTIVE_PARITY  -> groups.stream().mapToDouble(GroupStats::ppv).toArray();
        };
        double max = Arrays.stream(values).max().orElse(0.0);
        double min = Arrays.stream(values).min().orElse(0.0);
        return max - min;
    }

    private BiasLevel classifyBias(double disparity) {
        if (disparity < 0.05) return BiasLevel.NONE;
        if (disparity < 0.10) return BiasLevel.LOW;
        if (disparity < 0.20) return BiasLevel.MEDIUM;
        return BiasLevel.HIGH;
    }

    private void persistReport(BiasReport report) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO bias_reports " +
                 "(report_id, model_id, model_version, protected_attribute, " +
                 "fairness_metric, disparity, bias_level, resolved, evaluated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, NOW())")) {
            ps.setString(1, report.reportId());
            ps.setString(2, report.modelId());
            ps.setString(3, report.modelVersion());
            ps.setString(4, report.protectedAttribute());
            ps.setString(5, report.metric().name());
            ps.setDouble(6, report.disparity());
            ps.setString(7, report.level().name());
            ps.executeUpdate();
        }
    }
}
