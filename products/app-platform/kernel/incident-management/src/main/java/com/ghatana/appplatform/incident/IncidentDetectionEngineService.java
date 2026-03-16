package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Automatically detect platform incidents from K-06 alert streams.
 *              Detection rule: N alerts of the same type within T minutes → incident.
 *              Alert correlation joins related alerts into a single incident.
 *              Incident classification: PLATFORM (infra-wide), TENANT (single tenant), SERVICE (microservice).
 *              Publishes IncidentDetected event consumed by runbook, communication, and status-page services.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-001: Automated incident detection engine
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS platform_incidents (
 *   incident_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   title          TEXT NOT NULL,
 *   severity       TEXT NOT NULL,          -- P1 | P2 | P3 | P4
 *   status         TEXT NOT NULL DEFAULT 'INVESTIGATING',
 *   classification TEXT NOT NULL,          -- PLATFORM | TENANT | SERVICE
 *   affected_scope JSONB NOT NULL DEFAULT '[]',   -- tenant IDs or service names
 *   detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   resolved_at    TIMESTAMPTZ,
 *   correlation_key TEXT                   -- groups related alerts
 * );
 * CREATE TABLE IF NOT EXISTS incident_alerts (
 *   id             TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   incident_id    TEXT REFERENCES platform_incidents(incident_id),
 *   alert_id       TEXT NOT NULL,
 *   alert_name     TEXT NOT NULL,
 *   alert_source   TEXT NOT NULL,
 *   severity       TEXT NOT NULL,
 *   labels         JSONB NOT NULL DEFAULT '{}',
 *   fired_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS alert_correlation_window (
 *   window_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   correlation_key TEXT NOT NULL,
 *   alert_count    INT NOT NULL DEFAULT 1,
 *   first_seen     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   last_seen      TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class IncidentDetectionEngineService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    /** Incoming alert from K-06. */
    public record IncomingAlert(
        String alertId, String alertName, String source,
        String severity, Map<String, String> labels
    ) {}

    public record DetectedIncident(
        String incidentId, String title, String severity,
        String classification, List<String> affectedScope, Instant detectedAt
    ) {}

    // ── Detection thresholds ──────────────────────────────────────────────────

    /** N same-type alerts in T minutes triggers an incident. */
    private static final int ALERT_COUNT_THRESHOLD = 3;
    private static final int WINDOW_MINUTES = 5;

    // ── Severity mapping: alert severity → incident severity ──────────────────
    private static final Map<String, String> SEVERITY_MAP = Map.of(
        "CRITICAL", "P1",
        "HIGH",     "P2",
        "WARNING",  "P3",
        "INFO",     "P4"
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EventPublishPort events;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter incidentCreatedCounter;
    private final Counter alertReceivedCounter;

    public IncidentDetectionEngineService(
        javax.sql.DataSource ds,
        EventPublishPort events,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                    = ds;
        this.events                = events;
        this.audit                 = audit;
        this.executor              = executor;
        this.incidentCreatedCounter = Counter.builder("incident.created").register(registry);
        this.alertReceivedCounter  = Counter.builder("incident.alerts_received").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Process an incoming K-06 alert. Evaluates correlation window and may open an incident.
     */
    public Promise<Optional<DetectedIncident>> processAlert(IncomingAlert alert) {
        return Promise.ofBlocking(executor, () -> {
            alertReceivedCounter.increment();

            String correlationKey = buildCorrelationKey(alert);
            int windowCount = updateCorrelationWindow(correlationKey);

            // Check if an incident already exists for this correlation key
            if (incidentExistsForKey(correlationKey)) {
                linkAlertToExistingIncident(alert, correlationKey);
                return Optional.empty();
            }

            if (windowCount < ALERT_COUNT_THRESHOLD) return Optional.empty();

            // Threshold reached — create incident
            String incidentSeverity = SEVERITY_MAP.getOrDefault(alert.severity(), "P4");
            String classification   = classifyIncident(alert);
            List<String> scope      = extractScope(alert);
            String title            = buildTitle(alert, classification);

            DetectedIncident incident = createIncident(title, incidentSeverity, classification, scope, correlationKey);
            linkAlertToIncident(alert, incident.incidentId());

            incidentCreatedCounter.increment();
            events.publish("IncidentDetected", Map.of(
                "incidentId", incident.incidentId(),
                "severity", incidentSeverity,
                "classification", classification,
                "title", title,
                "affectedScope", scope));
            audit.record("system", "INCIDENT_AUTO_DETECTED", "incidentId=" + incident.incidentId());

            return Optional.of(incident);
        });
    }

    /**
     * Mark an incident as resolved.
     */
    public Promise<Void> resolve(String incidentId, String resolvedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE platform_incidents SET status='RESOLVED', resolved_at=NOW() WHERE incident_id=?"
                 )) {
                ps.setString(1, incidentId); ps.executeUpdate();
            }
            events.publish("IncidentResolved", Map.of("incidentId", incidentId));
            audit.record(resolvedBy, "INCIDENT_RESOLVED", "incidentId=" + incidentId);
            return null;
        });
    }

    /**
     * Get open incidents.
     */
    public Promise<List<DetectedIncident>> listOpen() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT incident_id, title, severity, classification, affected_scope::text, detected_at " +
                     "FROM platform_incidents WHERE status<>'RESOLVED' ORDER BY " +
                     "CASE severity WHEN 'P1' THEN 1 WHEN 'P2' THEN 2 WHEN 'P3' THEN 3 ELSE 4 END, detected_at"
                 )) {
                List<DetectedIncident> result = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new DetectedIncident(
                            rs.getString("incident_id"), rs.getString("title"),
                            rs.getString("severity"), rs.getString("classification"),
                            List.of(), rs.getTimestamp("detected_at").toInstant()));
                    }
                }
                return result;
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildCorrelationKey(IncomingAlert a) {
        String tenantLabel = a.labels().getOrDefault("tenant_id", "platform");
        return a.alertName() + "|" + tenantLabel;
    }

    private int updateCorrelationWindow(String key) throws SQLException {
        try (Connection c = ds.getConnection()) {
            // Clean old windows older than WINDOW_MINUTES
            try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM alert_correlation_window WHERE last_seen < NOW() - INTERVAL '" + WINDOW_MINUTES + " minutes'"
            )) { ps.executeUpdate(); }

            // Upsert window
            try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO alert_correlation_window (correlation_key) VALUES (?) " +
                "ON CONFLICT (correlation_key) DO UPDATE " +  // note: no UNIQUE yet — handled via SELECT+INSERT
                "SET alert_count=alert_correlation_window.alert_count+1, last_seen=NOW() " +
                "RETURNING alert_count"
            )) {
                // Simplify: use SELECT first
                try (PreparedStatement sel = c.prepareStatement(
                    "SELECT window_id, alert_count FROM alert_correlation_window WHERE correlation_key=? " +
                    "AND last_seen >= NOW() - INTERVAL '" + WINDOW_MINUTES + " minutes' LIMIT 1"
                )) {
                    sel.setString(1, key);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (rs.next()) {
                            String windowId = rs.getString("window_id");
                            int count = rs.getInt("alert_count") + 1;
                            try (PreparedStatement upd = c.prepareStatement(
                                "UPDATE alert_correlation_window SET alert_count=?, last_seen=NOW() WHERE window_id=?"
                            )) {
                                upd.setInt(1, count); upd.setString(2, windowId); upd.executeUpdate();
                            }
                            return count;
                        } else {
                            try (PreparedStatement ins = c.prepareStatement(
                                "INSERT INTO alert_correlation_window (correlation_key) VALUES (?)"
                            )) {
                                ins.setString(1, key); ins.executeUpdate();
                            }
                            return 1;
                        }
                    }
                }
            }
        }
    }

    private boolean incidentExistsForKey(String key) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT EXISTS(SELECT 1 FROM platform_incidents WHERE correlation_key=? AND status<>'RESOLVED')"
             )) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    private String classifyIncident(IncomingAlert alert) {
        if (alert.labels().containsKey("tenant_id")) return "TENANT";
        if (alert.labels().containsKey("service"))   return "SERVICE";
        return "PLATFORM";
    }

    private List<String> extractScope(IncomingAlert alert) {
        String tenant  = alert.labels().get("tenant_id");
        String service = alert.labels().get("service");
        if (tenant  != null) return List.of(tenant);
        if (service != null) return List.of(service);
        return List.of("ALL");
    }

    private String buildTitle(IncomingAlert alert, String classification) {
        return classification + " — " + alert.alertName() + " threshold exceeded";
    }

    private DetectedIncident createIncident(String title, String severity, String classification,
                                             List<String> scope, String correlationKey) throws SQLException {
        String scopeJson = "[" + String.join(",", scope.stream().map(s -> "\"" + s + "\"").toList()) + "]";
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO platform_incidents (title, severity, classification, affected_scope, correlation_key) " +
                 "VALUES (?,?,?,?::jsonb,?) RETURNING incident_id, detected_at"
             )) {
            ps.setString(1, title); ps.setString(2, severity); ps.setString(3, classification);
            ps.setString(4, scopeJson); ps.setString(5, correlationKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new DetectedIncident(rs.getString("incident_id"), title, severity, classification,
                    scope, rs.getTimestamp("detected_at").toInstant());
            }
        }
    }

    private void linkAlertToIncident(IncomingAlert alert, String incidentId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO incident_alerts (incident_id, alert_id, alert_name, alert_source, severity) VALUES (?,?,?,?,?)"
             )) {
            ps.setString(1, incidentId); ps.setString(2, alert.alertId());
            ps.setString(3, alert.alertName()); ps.setString(4, alert.source()); ps.setString(5, alert.severity());
            ps.executeUpdate();
        }
    }

    private void linkAlertToExistingIncident(IncomingAlert alert, String correlationKey) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement sel = c.prepareStatement(
                 "SELECT incident_id FROM platform_incidents WHERE correlation_key=? AND status<>'RESOLVED' LIMIT 1"
             )) {
            sel.setString(1, correlationKey);
            try (ResultSet rs = sel.executeQuery()) {
                if (rs.next()) linkAlertToIncident(alert, rs.getString("incident_id"));
            }
        }
    }
}
