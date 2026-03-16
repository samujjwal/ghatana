package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Configurable escalation matrix for reconciliation breaks. Routes breaks to
 *              the appropriate resolver based on type, severity, and age:
 *              Level 1 – Operations team (0-2 days),
 *              Level 2 – Finance manager (3-5 days),
 *              Level 3 – Compliance officer (6-10 days),
 *              Level 4 – Senior management (10+ days).
 *              Escalation path per break type configurable via K-02.
 *              Satisfies STORY-D13-014.
 * @doc.layer   Domain
 * @doc.pattern K-02 ConfigPort for escalation matrix; notification via NotificationPort;
 *              INSERT-only escalation_events table.
 */
public class BreakEscalationService {

    private static final Logger log = LoggerFactory.getLogger(BreakEscalationService.class);

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final ConfigPort       configPort;
    private final NotificationPort notifications;
    private final Counter          escalationCounter;

    public BreakEscalationService(HikariDataSource dataSource, Executor executor,
                                  ConfigPort configPort, NotificationPort notifications,
                                  MeterRegistry registry) {
        this.dataSource        = dataSource;
        this.executor          = executor;
        this.configPort        = configPort;
        this.notifications     = notifications;
        this.escalationCounter = registry.counter("recon.breaks.escalated");
    }

    // ─── Inner ports ──────────────────────────────────────────────────────────

    /** K-02 ConfigPort — escalation matrix configuration. */
    public interface ConfigPort {
        int getLevel1Days(String breakType);  // default 2
        int getLevel2Days(String breakType);  // default 5
        int getLevel3Days(String breakType);  // default 10
        String getLevel1Queue(String breakType);   // e.g. "ops-team"
        String getLevel2Queue(String breakType);   // e.g. "finance-manager"
        String getLevel3Queue(String breakType);   // e.g. "compliance-officer"
        String getLevel4Queue(String breakType);   // e.g. "senior-management"
    }

    public interface NotificationPort {
        void sendEscalationNotification(String queue, String breakId, String breakType,
                                        int ageDays, String severity);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Scheduled daily: route all open breaks that need escalation. */
    public Promise<Integer> runDailyEscalation(LocalDate runDate) {
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            String sql = """
                    SELECT break_id, break_type, severity, age_days, aging_tier,
                           COALESCE(current_assignee_queue,'') AS current_queue
                    FROM recon_breaks
                    WHERE status = 'OPEN'
                    ORDER BY age_days DESC
                    """;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String breakId   = rs.getString("break_id");
                    String breakType = rs.getString("break_type");
                    int ageDays      = rs.getInt("age_days");
                    String severity  = rs.getString("severity");
                    String curQueue  = rs.getString("current_queue");

                    String targetQueue = resolveQueue(breakType, ageDays);
                    if (!targetQueue.equals(curQueue)) {
                        assignToQueue(conn, breakId, targetQueue, runDate);
                        notifications.sendEscalationNotification(targetQueue, breakId, breakType, ageDays, severity);
                        escalationCounter.increment();
                        count++;
                    }
                }
            }
            return count;
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String resolveQueue(String breakType, int ageDays) {
        if (ageDays > 10) return configPort.getLevel4Queue(breakType);
        if (ageDays > 5)  return configPort.getLevel3Queue(breakType);
        if (ageDays > 2)  return configPort.getLevel2Queue(breakType);
        return configPort.getLevel1Queue(breakType);
    }

    private void assignToQueue(Connection conn, String breakId, String queue, LocalDate assignDate)
            throws SQLException {
        // Update current assignment
        String updateSql = """
                UPDATE recon_breaks
                SET current_assignee_queue = ?, last_escalated_at = NOW()
                WHERE break_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, queue);
            ps.setString(2, breakId);
            ps.executeUpdate();
        }
        // Insert escalation event
        String eventSql = """
                INSERT INTO break_escalation_events
                    (event_id, break_id, escalation_date, new_tier, assignee_queue)
                VALUES (?, ?, ?, 'ESCALATED', ?)
                ON CONFLICT DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(eventSql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, breakId);
            ps.setObject(3, assignDate);
            ps.setString(4, queue);
            ps.executeUpdate();
        }
    }
}
