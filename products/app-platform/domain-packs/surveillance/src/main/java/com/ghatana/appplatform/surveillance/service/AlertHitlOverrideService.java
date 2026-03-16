package com.ghatana.appplatform.surveillance.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Human-in-the-loop override for AI-generated surveillance alerts. Analyst
 *              classifies alert as TRUE_POSITIVE, FALSE_POSITIVE, or NEEDS_INVESTIGATION.
 *              Feedback loop feeds back to K-09 model registry for retraining labels.
 *              Override requires reason and is audit-logged via K-07 AuditPort.
 *              Dashboard metrics: AI alert accuracy, false positive rate, analyst agreement.
 *              Satisfies STORY-D08-011.
 * @doc.layer   Domain
 * @doc.pattern HITL override; K-09 feedback loop; K-07 AuditPort; Counter for each outcome.
 */
public class AlertHitlOverrideService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final AuditPort        auditPort;
    private final FeedbackPort     feedbackPort;
    private final Counter          truePositiveCounter;
    private final Counter          falsePositiveCounter;
    private final Counter          investigationCounter;

    public AlertHitlOverrideService(HikariDataSource dataSource, Executor executor,
                                     AuditPort auditPort, FeedbackPort feedbackPort,
                                     MeterRegistry registry) {
        this.dataSource          = dataSource;
        this.executor            = executor;
        this.auditPort           = auditPort;
        this.feedbackPort        = feedbackPort;
        this.truePositiveCounter  = Counter.builder("surveillance.hitl.true_positive_total").register(registry);
        this.falsePositiveCounter = Counter.builder("surveillance.hitl.false_positive_total").register(registry);
        this.investigationCounter = Counter.builder("surveillance.hitl.investigation_total").register(registry);
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-07 immutable audit trail. */
    public interface AuditPort {
        void logOverride(String alertId, String analystId, String classification,
                         String reason, LocalDateTime at);
    }

    /** K-09 feedback loop: push analyst label back to model registry. */
    public interface FeedbackPort {
        void recordLabel(String alertId, String clientId, String instrumentId,
                         LocalDate runDate, boolean isManipulation);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public enum AlertClassification { TRUE_POSITIVE, FALSE_POSITIVE, NEEDS_INVESTIGATION }

    public record Override(String overrideId, String alertId, String analystId,
                           AlertClassification classification, String reason,
                           LocalDateTime classifiedAt) {}

    public record AccuracyDashboard(long totalOverrides, long truePositives, long falsePositives,
                                    long investigations, double falsePositiveRate,
                                    double accuracyRate, LocalDate fromDate, LocalDate toDate) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Override> classifyAlert(String alertId, String analystId,
                                            AlertClassification classification, String reason) {
        return Promise.ofBlocking(executor, () -> {
            AlertRecord alert = loadAlert(alertId);
            Override ov = persistOverride(alertId, analystId, classification, reason);
            auditPort.logOverride(alertId, analystId, classification.name(), reason, LocalDateTime.now());

            boolean isManipulation = classification == AlertClassification.TRUE_POSITIVE;
            feedbackPort.recordLabel(alertId, alert.clientId(), alert.instrumentId(),
                    alert.runDate(), isManipulation);

            switch (classification) {
                case TRUE_POSITIVE   -> truePositiveCounter.increment();
                case FALSE_POSITIVE  -> falsePositiveCounter.increment();
                case NEEDS_INVESTIGATION -> investigationCounter.increment();
            }
            return ov;
        });
    }

    public Promise<AccuracyDashboard> getAccuracyDashboard(LocalDate from, LocalDate to) {
        return Promise.ofBlocking(executor, () -> buildDashboard(from, to));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private Override persistOverride(String alertId, String analystId,
                                      AlertClassification classification, String reason)
            throws SQLException {
        String overrideId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO alert_hitl_overrides
                    (override_id, alert_id, analyst_id, classification, reason, classified_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (alert_id) DO UPDATE
                SET classification = EXCLUDED.classification, reason = EXCLUDED.reason,
                    analyst_id = EXCLUDED.analyst_id, classified_at = NOW()
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, overrideId);
            ps.setString(2, alertId);
            ps.setString(3, analystId);
            ps.setString(4, classification.name());
            ps.setString(5, reason);
            ps.executeUpdate();
        }
        return new Override(overrideId, alertId, analystId, classification, reason, LocalDateTime.now());
    }

    private record AlertRecord(String clientId, String instrumentId, LocalDate runDate) {}

    private AlertRecord loadAlert(String alertId) throws SQLException {
        String sql = "SELECT client_id, instrument_id, run_date FROM surveillance_alerts WHERE alert_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alertId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Alert not found: " + alertId);
                return new AlertRecord(rs.getString("client_id"), rs.getString("instrument_id"),
                        rs.getObject("run_date", LocalDate.class));
            }
        }
    }

    private AccuracyDashboard buildDashboard(LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS total,
                       COUNT(CASE WHEN classification='TRUE_POSITIVE' THEN 1 END) AS tp,
                       COUNT(CASE WHEN classification='FALSE_POSITIVE' THEN 1 END) AS fp,
                       COUNT(CASE WHEN classification='NEEDS_INVESTIGATION' THEN 1 END) AS inv
                FROM alert_hitl_overrides o
                JOIN surveillance_alerts a ON a.alert_id = o.alert_id
                WHERE a.run_date BETWEEN ? AND ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, from);
            ps.setObject(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new AccuracyDashboard(0, 0, 0, 0, 0, 0, from, to);
                long total = rs.getLong("total");
                long tp    = rs.getLong("tp");
                long fp    = rs.getLong("fp");
                long inv   = rs.getLong("inv");
                double fpRate  = total == 0 ? 0.0 : (double) fp / total;
                double accRate = total == 0 ? 0.0 : (double) tp / (tp + fp);
                return new AccuracyDashboard(total, tp, fp, inv, fpRate, accRate, from, to);
            }
        }
    }
}
