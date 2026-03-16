package com.ghatana.appplatform.governance;

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
 * @doc.purpose Quality degradation alerting and auto-remediation. Monitors quality scores per
 *              data asset; triggers alerts when score drops below threshold or when trend
 *              declines for N consecutive checks. Auto-remediation for known patterns:
 *              TRIM_WHITESPACE removes leading/trailing spaces, DEFAULT_MISSING replaces null
 *              with domain default. Manual remediation: break assigned to data steward via
 *              workflow. Satisfies STORY-K08-008.
 * @doc.layer   Kernel
 * @doc.pattern Alert threshold + trend detection; auto-remediation patterns; K-07 audit trail;
 *              RemediationWorkflowPort for steward assignment; breaksAlerted/autoRemediated counters.
 */
public class DataQualityAlertingService {

    private static final double DEFAULT_ALERT_THRESHOLD    = 80.0;
    private static final int    TREND_DECLINE_WINDOW       = 3;   // consecutive checks
    private static final int    DEFAULT_SLA_DAYS           = 5;

    private final HikariDataSource         dataSource;
    private final Executor                 executor;
    private final AuditPort                auditPort;
    private final RemediationWorkflowPort  remediationPort;
    private final Counter                  breaksAlertedCounter;
    private final Counter                  autoRemediatedCounter;
    private final Counter                  slaBreachCounter;

    public DataQualityAlertingService(HikariDataSource dataSource, Executor executor,
                                       AuditPort auditPort,
                                       RemediationWorkflowPort remediationPort,
                                       MeterRegistry registry) {
        this.dataSource           = dataSource;
        this.executor             = executor;
        this.auditPort            = auditPort;
        this.remediationPort      = remediationPort;
        this.breaksAlertedCounter  = Counter.builder("governance.dq.breaks_alerted_total").register(registry);
        this.autoRemediatedCounter = Counter.builder("governance.dq.auto_remediated_total").register(registry);
        this.slaBreachCounter      = Counter.builder("governance.dq.sla_breach_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-07 audit trail for remediation evidence. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    /** Steward assignment and escalation workflow. */
    public interface RemediationWorkflowPort {
        String assignToSteward(String assetId, String breakId, String reason);
        void escalate(String breakId, String reason);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum RemediationPattern { TRIM_WHITESPACE, DEFAULT_MISSING }
    public enum BreakStatus { OPEN, INVESTIGATING, RESOLVED, CLOSED }

    public record QualityBreak(
        String breakId, String assetId, String ruleId,
        double score, double threshold,
        BreakStatus status, Instant detectedAt,
        String assignedStewardId, Instant slaDueAt
    ) {}

    public record RemediationResult(
        String breakId, boolean autoRemediated,
        RemediationPattern patternApplied, int rowsFixed, String notes
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Evaluate latest quality score for asset; raise alert if below threshold or declining.
     * Called by DataQualityDashboardService after each quality run.
     */
    public Promise<Optional<QualityBreak>> evaluateAndAlert(String assetId, String ruleId,
                                                              double score, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            double threshold = fetchThreshold(assetId, ruleId);
            boolean isTrendDeclining = isTrendDeclining(assetId, ruleId);

            if (score >= threshold && !isTrendDeclining) {
                recordScoreHistory(assetId, ruleId, score);
                return Optional.empty();
            }

            String breakId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            Instant slaDue = now.plusSeconds((long) DEFAULT_SLA_DAYS * 86400);
            String reason = score < threshold
                ? String.format("Score %.1f below threshold %.1f", score, threshold)
                : "Score declining for " + TREND_DECLINE_WINDOW + " consecutive checks";

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO dq_quality_breaks " +
                     "(break_id, asset_id, rule_id, score, threshold, status, detected_at, sla_due_at, reason) " +
                     "VALUES (?, ?, ?, ?, ?, 'OPEN', NOW(), ?, ?) " +
                     "ON CONFLICT (asset_id, rule_id) WHERE status IN ('OPEN','INVESTIGATING') DO NOTHING")) {
                ps.setString(1, breakId);
                ps.setString(2, assetId);
                ps.setString(3, ruleId);
                ps.setDouble(4, score);
                ps.setDouble(5, threshold);
                ps.setTimestamp(6, Timestamp.from(slaDue));
                ps.setString(7, reason);
                ps.executeUpdate();
            }

            recordScoreHistory(assetId, ruleId, score);
            breaksAlertedCounter.increment();

            auditPort.log("QUALITY_BREAK_RAISED", "DataAsset", assetId,
                Map.of("breakId", breakId, "score", score, "threshold", threshold, "actor", actorId));

            QualityBreak qb = new QualityBreak(breakId, assetId, ruleId, score, threshold,
                BreakStatus.OPEN, now, null, slaDue);
            return Optional.of(qb);
        });
    }

    /**
     * Attempt auto-remediation for known patterns on the given asset.
     * TRIM_WHITESPACE: UPDATE table SET col = TRIM(col) WHERE col != TRIM(col).
     * DEFAULT_MISSING: UPDATE table SET col = defaultVal WHERE col IS NULL.
     */
    public Promise<RemediationResult> autoRemediate(String assetId, String breakId,
                                                      RemediationPattern pattern,
                                                      String targetColumn, String defaultValue) {
        return Promise.ofBlocking(executor, () -> {
            String assetTable = resolveAssetTable(assetId);
            int rowsFixed = 0;

            try (Connection c = dataSource.getConnection()) {
                switch (pattern) {
                    case TRIM_WHITESPACE -> {
                        String sql = "UPDATE " + assetTable + " SET " + targetColumn +
                                     " = TRIM(" + targetColumn + ") WHERE " + targetColumn +
                                     " IS DISTINCT FROM TRIM(" + targetColumn + ")";
                        try (PreparedStatement ps = c.prepareStatement(sql)) {
                            rowsFixed = ps.executeUpdate();
                        }
                    }
                    case DEFAULT_MISSING -> {
                        String sql = "UPDATE " + assetTable + " SET " + targetColumn +
                                     " = ? WHERE " + targetColumn + " IS NULL";
                        try (PreparedStatement ps = c.prepareStatement(sql)) {
                            ps.setString(1, defaultValue);
                            rowsFixed = ps.executeUpdate();
                        }
                    }
                }
            }

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE dq_quality_breaks SET status = 'RESOLVED', " +
                     "resolved_at = NOW(), resolution_notes = ? WHERE break_id = ?")) {
                ps.setString(1, "Auto-remediated: " + pattern + " fixed " + rowsFixed + " rows");
                ps.setString(2, breakId);
                ps.executeUpdate();
            }

            autoRemediatedCounter.increment();
            auditPort.log("AUTO_REMEDIATION_APPLIED", "DataAsset", assetId,
                Map.of("breakId", breakId, "pattern", pattern.name(), "rowsFixed", rowsFixed));

            return new RemediationResult(breakId, true, pattern, rowsFixed, "");
        });
    }

    /**
     * Assign break to a data steward for manual remediation.
     */
    public Promise<Void> assignForManualRemediation(String breakId, String assetId,
                                                      String assignedBy) {
        return Promise.ofBlocking(executor, () -> {
            String assigneeId = remediationPort.assignToSteward(assetId, breakId, "Manual quality break resolution");

            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE dq_quality_breaks SET status = 'INVESTIGATING', " +
                     "assigned_steward_id = ?, assigned_at = NOW() WHERE break_id = ?")) {
                ps.setString(1, assigneeId);
                ps.setString(2, breakId);
                ps.executeUpdate();
            }

            auditPort.log("QUALITY_BREAK_ASSIGNED", "DataAsset", assetId,
                Map.of("breakId", breakId, "assignedTo", assigneeId, "assignedBy", assignedBy));
            return null;
        });
    }

    /**
     * Check for SLA breaches and escalate overdue breaks.
     */
    public Promise<Integer> checkAndEscalateSlaBreaches() {
        return Promise.ofBlocking(executor, () -> {
            List<String> breachIds = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT break_id, asset_id FROM dq_quality_breaks " +
                     "WHERE status IN ('OPEN', 'INVESTIGATING') AND sla_due_at < NOW()")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        breachIds.add(rs.getString("break_id"));
                    }
                }
            }

            for (String breakId : breachIds) {
                remediationPort.escalate(breakId, "SLA deadline exceeded");
                slaBreachCounter.increment();
            }

            if (!breachIds.isEmpty()) {
                try (Connection c = dataSource.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE dq_quality_breaks SET escalated = TRUE, escalated_at = NOW() " +
                         "WHERE break_id = ANY(?) AND NOT escalated")) {
                    ps.setArray(1, c.createArrayOf("text", breachIds.toArray()));
                    ps.executeUpdate();
                }
            }

            return breachIds.size();
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private double fetchThreshold(String assetId, String ruleId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT threshold FROM dq_alert_thresholds WHERE asset_id = ? AND rule_id = ?")) {
            ps.setString(1, assetId);
            ps.setString(2, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("threshold");
            }
        }
        return DEFAULT_ALERT_THRESHOLD;
    }

    private boolean isTrendDeclining(String assetId, String ruleId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT score FROM dq_score_history WHERE asset_id = ? AND rule_id = ? " +
                 "ORDER BY checked_at DESC LIMIT ?")) {
            ps.setString(1, assetId);
            ps.setString(2, ruleId);
            ps.setInt(3, TREND_DECLINE_WINDOW);
            try (ResultSet rs = ps.executeQuery()) {
                List<Double> scores = new ArrayList<>();
                while (rs.next()) scores.add(rs.getDouble("score"));
                if (scores.size() < TREND_DECLINE_WINDOW) return false;
                for (int i = 0; i < scores.size() - 1; i++) {
                    if (scores.get(i) >= scores.get(i + 1)) return false;
                }
                return true;
            }
        }
    }

    private void recordScoreHistory(String assetId, String ruleId, double score) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dq_score_history (asset_id, rule_id, score, checked_at) " +
                 "VALUES (?, ?, ?, NOW())")) {
            ps.setString(1, assetId);
            ps.setString(2, ruleId);
            ps.setDouble(3, score);
            ps.executeUpdate();
        }
    }

    private String resolveAssetTable(String assetId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT physical_table FROM data_catalog_assets WHERE asset_id = ?")) {
            ps.setString(1, assetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("physical_table");
            }
        }
        throw new IllegalArgumentException("Unknown asset: " + assetId);
    }
}
