package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose SLA tracking for dead-letter message resolution. Each message has a
 *              resolution SLA derived from its topic priority tier: CRITICAL=1h,
 *              HIGH=4h, NORMAL=24h, LOW=72h. Monitors SLA status (ON_TIME / AT_RISK /
 *              BREACHED), escalates breaching messages to on-call teams via NotificationPort,
 *              and reports per-topic SLA compliance percentages. Satisfies STORY-K19-012.
 * @doc.layer   Kernel
 * @doc.pattern Priority-tier SLA matrix; AT_RISK/BREACHED detection; SLA escalation;
 *              compliance % per topic; slaBreached Counter; atRiskCount Gauge.
 */
public class DlqSlaTrackingService {

    // SLA in seconds per priority tier
    private static final Map<String, Long> SLA_SECONDS = Map.of(
        "CRITICAL", 3600L,
        "HIGH",     14400L,
        "NORMAL",   86400L,
        "LOW",      259200L
    );
    private static final double AT_RISK_FRACTION = 0.75; // 75% of SLA elapsed = AT_RISK

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final NotificationPort  notificationPort;
    private final Counter           slaBreachedCounter;
    private final AtomicLong        atRiskCount = new AtomicLong(0);

    public DlqSlaTrackingService(HikariDataSource dataSource, Executor executor,
                                  NotificationPort notificationPort,
                                  MeterRegistry registry) {
        this.dataSource         = dataSource;
        this.executor           = executor;
        this.notificationPort   = notificationPort;
        this.slaBreachedCounter = Counter.builder("dlq.sla.breached_total").register(registry);
        Gauge.builder("dlq.sla.at_risk_count", atRiskCount, AtomicLong::get).register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    /** On-call escalation for SLA breaches. */
    public interface NotificationPort {
        void escalateSla(String deadLetterId, String topicName, String priority, String status);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum SlaStatus { ON_TIME, AT_RISK, BREACHED }

    public record MessageSlaStatus(
        String deadLetterId, String topicName, String priority,
        SlaStatus slaStatus, long elapsedSeconds, long slaDurationSeconds,
        double slaElapsedPercent, Instant capturedAt
    ) {}

    public record TopicSlaReport(
        String topicName, long total, long onTime, long atRisk, long breached,
        double compliancePercent
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Scan all open (DEAD + INVESTIGATING) messages, update SLA status, escalate breaches.
     */
    public Promise<Integer> runSlaCheckCycle() {
        return Promise.ofBlocking(executor, () -> {
            List<MessageSlaStatus> all = computeAllSlaStatuses();

            long atRisk    = all.stream().filter(m -> m.slaStatus() == SlaStatus.AT_RISK).count();
            long breached  = all.stream().filter(m -> m.slaStatus() == SlaStatus.BREACHED).count();
            atRiskCount.set(atRisk);

            for (MessageSlaStatus msg : all) {
                if (msg.slaStatus() == SlaStatus.BREACHED) {
                    notificationPort.escalateSla(msg.deadLetterId(), msg.topicName(),
                        msg.priority(), "BREACHED");
                    slaBreachedCounter.increment();
                    markBreached(msg.deadLetterId());
                } else if (msg.slaStatus() == SlaStatus.AT_RISK) {
                    notificationPort.escalateSla(msg.deadLetterId(), msg.topicName(),
                        msg.priority(), "AT_RISK");
                }
            }

            return (int) breached;
        });
    }

    /**
     * Get per-topic SLA compliance report.
     */
    public Promise<List<TopicSlaReport>> getTopicComplianceReport() {
        return Promise.ofBlocking(executor, () -> {
            List<TopicSlaReport> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT dl.topic_name, " +
                     "COUNT(*) AS total, " +
                     "COUNT(*) FILTER (WHERE dl.sla_status = 'ON_TIME') AS on_time, " +
                     "COUNT(*) FILTER (WHERE dl.sla_status = 'AT_RISK') AS at_risk, " +
                     "COUNT(*) FILTER (WHERE dl.sla_status = 'BREACHED') AS breached " +
                     "FROM dead_letters dl GROUP BY dl.topic_name")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long total     = rs.getLong("total");
                        long onTime    = rs.getLong("on_time");
                        long atRisk    = rs.getLong("at_risk");
                        long breachRs  = rs.getLong("breached");
                        double pct     = total > 0 ? 100.0 * onTime / total : 100.0;
                        results.add(new TopicSlaReport(rs.getString("topic_name"),
                            total, onTime, atRisk, breachRs, Math.round(pct * 100.0) / 100.0));
                    }
                }
            }
            return results;
        });
    }

    /**
     * Get current SLA status for a specific dead-letter message.
     */
    public Promise<Optional<MessageSlaStatus>> getMessageSlaStatus(String deadLetterId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT topic_name, priority, captured_at FROM dead_letters " +
                     "WHERE dead_letter_id = ? AND status IN ('DEAD', 'INVESTIGATING')")) {
                ps.setString(1, deadLetterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    String topicName = rs.getString("topic_name");
                    String priority  = rs.getString("priority");
                    Instant ca       = rs.getTimestamp("captured_at").toInstant();
                    return Optional.of(buildSlaStatus(deadLetterId, topicName, priority, ca));
                }
            }
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private List<MessageSlaStatus> computeAllSlaStatuses() throws SQLException {
        List<MessageSlaStatus> results = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dead_letter_id, topic_name, priority, captured_at " +
                 "FROM dead_letters WHERE status IN ('DEAD', 'INVESTIGATING')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(buildSlaStatus(
                        rs.getString("dead_letter_id"),
                        rs.getString("topic_name"),
                        rs.getString("priority"),
                        rs.getTimestamp("captured_at").toInstant()
                    ));
                }
            }
        }
        return results;
    }

    private MessageSlaStatus buildSlaStatus(String deadLetterId, String topicName,
                                             String priority, Instant capturedAt) {
        long slaDuration = SLA_SECONDS.getOrDefault(priority, SLA_SECONDS.get("NORMAL"));
        long elapsed     = Instant.now().getEpochSecond() - capturedAt.getEpochSecond();
        double pct       = (double) elapsed / slaDuration;
        SlaStatus status;
        if (elapsed >= slaDuration)       status = SlaStatus.BREACHED;
        else if (pct >= AT_RISK_FRACTION) status = SlaStatus.AT_RISK;
        else                               status = SlaStatus.ON_TIME;

        return new MessageSlaStatus(deadLetterId, topicName, priority,
            status, elapsed, slaDuration, Math.min(pct * 100, 100.0), capturedAt);
    }

    private void markBreached(String deadLetterId) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET sla_status = 'BREACHED', sla_breached_at = NOW() " +
                 "WHERE dead_letter_id = ? AND sla_status IS DISTINCT FROM 'BREACHED'")) {
            ps.setString(1, deadLetterId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
            // Best-effort SLA marking; core DLQ capture is unaffected
        }
    }
}
