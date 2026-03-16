package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Schedule and enforce platform maintenance windows.
 *              Window entity: start_time, end_time, affected_components[], expected_impact,
 *              notification_mins_before (default 60). Supports emergency unscheduled windows.
 *              During an active window: affected service gates return 503 with Retry-After.
 *              BS calendar integration via K-15 for scheduling in Nepali calendar.
 *              Post-maintenance: triggers automated health check probe sequence.
 *              Tenant admins notified on window open + close.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-008: Platform maintenance window management
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS maintenance_windows (
 *   window_id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   title                  TEXT NOT NULL,
 *   start_time             TIMESTAMPTZ NOT NULL,
 *   end_time               TIMESTAMPTZ NOT NULL,
 *   affected_components    JSONB NOT NULL DEFAULT '[]',
 *   expected_impact        TEXT NOT NULL,
 *   notification_mins_before INT NOT NULL DEFAULT 60,
 *   emergency              BOOLEAN NOT NULL DEFAULT FALSE,
 *   status                 TEXT NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED | ACTIVE | COMPLETED | CANCELLED
 *   created_by             TEXT NOT NULL,
 *   created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS maintenance_health_checks (
 *   check_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   window_id    TEXT NOT NULL,
 *   component    TEXT NOT NULL,
 *   status       TEXT NOT NULL,  -- PASS | FAIL
 *   detail       TEXT,
 *   checked_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class MaintenanceWindowManagementService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NotificationPort {
        void notifyAllTenants(String subject, String body) throws Exception;
        void notifyTenant(String tenantId, String subject, String body) throws Exception;
    }

    public interface HealthProbePort {
        /** Run health probe for a component. Returns true if healthy. */
        boolean probe(String component) throws Exception;
    }

    public interface CalendarPort {
        /** Format an instant in BS calendar notation (e.g., "2082-06-01 10:00"). */
        String formatBs(java.time.Instant instant) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record MaintenanceWindow(
        String windowId, String title, java.time.Instant startTime, java.time.Instant endTime,
        List<String> affectedComponents, String expectedImpact, int notificationMinsBefore,
        boolean emergency, String status, String createdBy
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NotificationPort notifications;
    private final HealthProbePort healthProbe;
    private final CalendarPort calendar;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter windowsCreatedCounter;
    private final Counter healthCheckFailuresCounter;

    public MaintenanceWindowManagementService(
        javax.sql.DataSource ds,
        NotificationPort notifications,
        HealthProbePort healthProbe,
        CalendarPort calendar,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                       = ds;
        this.notifications            = notifications;
        this.healthProbe              = healthProbe;
        this.calendar                 = calendar;
        this.audit                    = audit;
        this.executor                 = executor;
        this.windowsCreatedCounter    = Counter.builder("operator.maintenance.windows_created").register(registry);
        this.healthCheckFailuresCounter = Counter.builder("operator.maintenance.health_check_failures").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Schedule a planned maintenance window. Returns windowId. */
    public Promise<String> schedule(String title, java.time.Instant startTime, java.time.Instant endTime,
                                    List<String> affectedComponents, String expectedImpact,
                                    int notificationMinsBefore, String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("end_time must be after start_time");

            String windowId = createWindow(title, startTime, endTime, affectedComponents,
                expectedImpact, notificationMinsBefore, false, createdBy);
            windowsCreatedCounter.increment();
            audit.record(createdBy, "MAINTENANCE_WINDOW_SCHEDULED",
                "windowId=" + windowId + " start=" + startTime + " components=" + affectedComponents);

            // Notify tenants immediately if window is within 24h
            long minutesUntilStart = java.time.Duration.between(java.time.Instant.now(), startTime).toMinutes();
            if (minutesUntilStart <= notificationMinsBefore) {
                sendWindowNotification(windowId, title, startTime, endTime, affectedComponents, expectedImpact, false);
            }
            return windowId;
        });
    }

    /** Declare an emergency unscheduled maintenance window. Notification sent immediately. */
    public Promise<String> emergencyWindow(String title, java.time.Instant endTime,
                                            List<String> affectedComponents, String expectedImpact,
                                            String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            java.time.Instant now = java.time.Instant.now();
            String windowId = createWindow(title, now, endTime, affectedComponents,
                expectedImpact, 0, true, createdBy);
            activateWindow(windowId);
            windowsCreatedCounter.increment();
            audit.record(createdBy, "EMERGENCY_MAINTENANCE_STARTED",
                "windowId=" + windowId + " components=" + affectedComponents);
            sendWindowNotification(windowId, title, now, endTime, affectedComponents, expectedImpact, true);
            return windowId;
        });
    }

    /** Activate a SCHEDULED window (called by scheduler at start_time). */
    public Promise<Void> activate(String windowId) {
        return Promise.ofBlocking(executor, () -> {
            activateWindow(windowId);
            MaintenanceWindow w = loadWindow(windowId);
            sendWindowNotification(windowId, w.title(), w.startTime(), w.endTime(),
                w.affectedComponents(), w.expectedImpact(), w.emergency());
            audit.record("system", "MAINTENANCE_WINDOW_ACTIVATED", "windowId=" + windowId);
            return null;
        });
    }

    /**
     * Check if a component is currently under maintenance.
     * Called by service gateways to return 503 during windows.
     */
    public Promise<Boolean> isUnderMaintenance(String component) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM maintenance_windows WHERE status='ACTIVE' " +
                     "AND affected_components @> ?::jsonb AND NOW() BETWEEN start_time AND end_time"
                 )) {
                ps.setString(1, "[\"" + component + "\"]");
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        });
    }

    /** Complete a window and run post-maintenance health checks. */
    public Promise<List<Map<String, String>>> complete(String windowId, String completedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE maintenance_windows SET status='COMPLETED' WHERE window_id=? AND status='ACTIVE'"
                 )) {
                ps.setString(1, windowId); ps.executeUpdate();
            }

            MaintenanceWindow w = loadWindow(windowId);
            List<Map<String, String>> results = runHealthChecks(windowId, w.affectedComponents());
            audit.record(completedBy, "MAINTENANCE_WINDOW_COMPLETED", "windowId=" + windowId);

            boolean allHealthy = results.stream().allMatch(r -> "PASS".equals(r.get("status")));
            String body = allHealthy
                ? "Maintenance completed. All components healthy."
                : "Maintenance completed. Some components may require attention. Details in operator portal.";
            notifications.notifyAllTenants("[Platform] Maintenance Complete: " + w.title(), body);
            return results;
        });
    }

    /** Cancel a SCHEDULED window before it starts. */
    public Promise<Void> cancel(String windowId, String cancelledBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE maintenance_windows SET status='CANCELLED' WHERE window_id=? AND status='SCHEDULED'"
                 )) {
                ps.setString(1, windowId);
                if (ps.executeUpdate() == 0) throw new IllegalStateException("Window not in SCHEDULED status");
            }
            audit.record(cancelledBy, "MAINTENANCE_WINDOW_CANCELLED", "windowId=" + windowId);
            notifications.notifyAllTenants("[Platform] Maintenance Cancelled",
                "The scheduled maintenance window has been cancelled.");
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String createWindow(String title, java.time.Instant startTime, java.time.Instant endTime,
                                 List<String> components, String impact, int notifMins,
                                 boolean emergency, String createdBy) throws SQLException {
        String componentsJson = componentsToJson(components);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO maintenance_windows (title, start_time, end_time, affected_components, " +
                 "expected_impact, notification_mins_before, emergency, created_by) " +
                 "VALUES (?,?,?,?::jsonb,?,?,?,?) RETURNING window_id"
             )) {
            ps.setString(1, title);
            ps.setTimestamp(2, Timestamp.from(startTime));
            ps.setTimestamp(3, Timestamp.from(endTime));
            ps.setString(4, componentsJson); ps.setString(5, impact);
            ps.setInt(6, notifMins); ps.setBoolean(7, emergency); ps.setString(8, createdBy);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("window_id"); }
        }
    }

    private void activateWindow(String windowId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE maintenance_windows SET status='ACTIVE' WHERE window_id=?"
             )) {
            ps.setString(1, windowId); ps.executeUpdate();
        }
    }

    private MaintenanceWindow loadWindow(String windowId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT window_id, title, start_time, end_time, expected_impact, " +
                 "notification_mins_before, emergency, status, created_by FROM maintenance_windows WHERE window_id=?"
             )) {
            ps.setString(1, windowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Window not found: " + windowId);
                return new MaintenanceWindow(rs.getString("window_id"), rs.getString("title"),
                    rs.getTimestamp("start_time").toInstant(), rs.getTimestamp("end_time").toInstant(),
                    List.of(), rs.getString("expected_impact"),
                    rs.getInt("notification_mins_before"), rs.getBoolean("emergency"),
                    rs.getString("status"), rs.getString("created_by"));
            }
        }
    }

    private List<Map<String, String>> runHealthChecks(String windowId, List<String> components) throws Exception {
        List<Map<String, String>> results = new ArrayList<>();
        for (String component : components) {
            boolean healthy = healthProbe.probe(component);
            if (!healthy) healthCheckFailuresCounter.increment();
            Map<String, String> r = new LinkedHashMap<>();
            r.put("component", component);
            r.put("status", healthy ? "PASS" : "FAIL");
            results.add(r);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO maintenance_health_checks (window_id, component, status) VALUES (?,?,?)"
                 )) {
                ps.setString(1, windowId); ps.setString(2, component);
                ps.setString(3, healthy ? "PASS" : "FAIL");
                ps.executeUpdate();
            }
        }
        return results;
    }

    private void sendWindowNotification(String windowId, String title, java.time.Instant start,
                                         java.time.Instant end, List<String> components,
                                         String impact, boolean emergency) throws Exception {
        String prefix = emergency ? "[EMERGENCY] " : "[Scheduled] ";
        String subject = prefix + "Platform Maintenance: " + title;
        String body = String.format("Maintenance: %s\nStart: %s\nEnd: %s\nAffected: %s\nExpected Impact: %s",
            title, start, end, components, impact);
        notifications.notifyAllTenants(subject, body);
    }

    private String componentsToJson(List<String> components) {
        if (components.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (String c : components) sb.append("\"").append(c).append("\",");
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }
}
