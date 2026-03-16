package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Maintain component health statuses for internal and external status pages.
 *              Statuses: OPERATIONAL / DEGRADED / PARTIAL_OUTAGE / MAJOR_OUTAGE.
 *              Auto-updates on incident creation/resolution by severity mapping:
 *                P1 → MAJOR_OUTAGE, P2 → PARTIAL_OUTAGE, P3 → DEGRADED.
 *              Manual operator overrides supported.
 *              Status history retained for 90 days.
 *              All status changes persisted and exposed via public-facing API.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-004: Status page integration
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS status_page_components (
 *   component_id  TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   name          TEXT NOT NULL UNIQUE,
 *   category      TEXT NOT NULL,          -- API | DATA_PIPELINE | STORAGE | AUTH | WEBHOOKS | UI
 *   status        TEXT NOT NULL DEFAULT 'OPERATIONAL',
 *   incident_id   TEXT,                   -- currently linked incident
 *   override_at   TIMESTAMPTZ,
 *   override_by   TEXT,
 *   updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS status_component_history (
 *   history_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   component_id  TEXT NOT NULL,
 *   incident_id   TEXT,
 *   from_status   TEXT NOT NULL,
 *   to_status     TEXT NOT NULL,
 *   changed_by    TEXT NOT NULL,          -- 'system' or operator id
 *   changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX IF NOT EXISTS idx_status_history_component ON status_component_history(component_id, changed_at DESC);
 * </pre>
 */
public class StatusPageService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EventPublishPort {
        void publish(String eventType, Map<String, String> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum ComponentStatus {
        OPERATIONAL, DEGRADED, PARTIAL_OUTAGE, MAJOR_OUTAGE
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record Component(
        String componentId, String name, String category, String status,
        String incidentId, String updatedAt
    ) {}

    public record StatusSnapshot(
        List<Component> components, String asOf
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EventPublishPort events;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter degradationsCounter;

    public StatusPageService(
        javax.sql.DataSource ds,
        EventPublishPort events,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                = ds;
        this.events            = events;
        this.audit             = audit;
        this.executor          = executor;
        this.degradationsCounter = Counter.builder("incident.statuspage.degradations").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called by IncidentDetectionEngineService when a new incident is created.
     * Maps severity to the appropriate degradation level and updates affected components.
     */
    public Promise<Void> onIncidentCreated(String incidentId, String severity, List<String> componentNames) {
        return Promise.ofBlocking(executor, () -> {
            String targetStatus = mapSeverityToStatus(severity);
            if ("OPERATIONAL".equals(targetStatus)) return null;

            for (String name : componentNames) {
                String componentId = resolveComponentByName(name);
                if (componentId == null) continue;
                String current = currentStatus(componentId);
                if (worseThan(targetStatus, current)) {
                    changeStatus(componentId, incidentId, current, targetStatus, "system");
                    degradationsCounter.increment();
                }
            }
            audit.record("system", "STATUS_INCIDENT_DEGRADATION",
                "incidentId=" + incidentId + " severity=" + severity);
            return null;
        });
    }

    /**
     * Called by IncidentDetectionEngineService when an incident is resolved.
     * Restores linked components back to OPERATIONAL.
     */
    public Promise<Void> onIncidentResolved(String incidentId) {
        return Promise.ofBlocking(executor, () -> {
            List<String> linked = componentsLinkedToIncident(incidentId);
            for (String componentId : linked) {
                String current = currentStatus(componentId);
                changeStatus(componentId, null, current, "OPERATIONAL", "system");
            }
            audit.record("system", "STATUS_INCIDENT_RESOLVED", "incidentId=" + incidentId);
            return null;
        });
    }

    /**
     * Manual operator override of a component's status.
     */
    public Promise<Void> overrideStatus(String componentId, String newStatus, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            ComponentStatus.valueOf(newStatus); // validate
            String current = currentStatus(componentId);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE status_page_components SET status=?, incident_id=NULL, override_at=NOW(), override_by=?, updated_at=NOW() " +
                     "WHERE component_id=?"
                 )) {
                ps.setString(1, newStatus); ps.setString(2, operatorId); ps.setString(3, componentId);
                ps.executeUpdate();
            }
            recordHistory(componentId, null, current, newStatus, operatorId);
            events.publish("ComponentStatusOverridden",
                Map.of("componentId", componentId, "status", newStatus, "by", operatorId));
            audit.record(operatorId, "STATUS_OVERRIDE",
                "componentId=" + componentId + " from=" + current + " to=" + newStatus);
            return null;
        });
    }

    /** Returns the current status snapshot for all components. */
    public Promise<StatusSnapshot> getSnapshot() {
        return Promise.ofBlocking(executor, () -> {
            List<Component> components = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT component_id, name, category, status, incident_id, updated_at::text FROM status_page_components ORDER BY category, name"
                 );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    components.add(new Component(rs.getString("component_id"), rs.getString("name"),
                        rs.getString("category"), rs.getString("status"),
                        rs.getString("incident_id"), rs.getString("updated_at")));
                }
            }
            return new StatusSnapshot(components, Timestamp.from(java.time.Instant.now()).toString());
        });
    }

    /** Returns last 90 days of status history for a component. */
    public Promise<List<Map<String, String>>> getHistory(String componentId) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT history_id, incident_id, from_status, to_status, changed_by, changed_at::text " +
                     "FROM status_component_history WHERE component_id=? AND changed_at > NOW() - INTERVAL '90 days' " +
                     "ORDER BY changed_at DESC"
                 )) {
                ps.setString(1, componentId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("historyId",   rs.getString("history_id"));
                        row.put("incidentId",  rs.getString("incident_id"));
                        row.put("fromStatus",  rs.getString("from_status"));
                        row.put("toStatus",    rs.getString("to_status"));
                        row.put("changedBy",   rs.getString("changed_by"));
                        row.put("changedAt",   rs.getString("changed_at"));
                        rows.add(row);
                    }
                }
            }
            return rows;
        });
    }

    /** Register a new component on the status page. */
    public Promise<String> registerComponent(String name, String category) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO status_page_components (name, category) VALUES (?,?) RETURNING component_id"
                 )) {
                ps.setString(1, name); ps.setString(2, category);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("component_id"); }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String mapSeverityToStatus(String severity) {
        return switch (severity) {
            case "P1" -> "MAJOR_OUTAGE";
            case "P2" -> "PARTIAL_OUTAGE";
            case "P3" -> "DEGRADED";
            default   -> "OPERATIONAL";
        };
    }

    /** Returns true if candidate is worse than current. */
    private boolean worseThan(String candidate, String current) {
        List<String> ordered = List.of("OPERATIONAL", "DEGRADED", "PARTIAL_OUTAGE", "MAJOR_OUTAGE");
        return ordered.indexOf(candidate) > ordered.indexOf(current);
    }

    private String currentStatus(String componentId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT status FROM status_page_components WHERE component_id=?"
             )) {
            ps.setString(1, componentId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("status") : "OPERATIONAL"; }
        }
    }

    private String resolveComponentByName(String name) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT component_id FROM status_page_components WHERE name=?"
             )) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("component_id") : null; }
        }
    }

    private List<String> componentsLinkedToIncident(String incidentId) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT component_id FROM status_page_components WHERE incident_id=?"
             )) {
            ps.setString(1, incidentId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(rs.getString("component_id")); }
        }
        return ids;
    }

    private void changeStatus(String componentId, String incidentId,
                               String from, String to, String changedBy) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement upd = c.prepareStatement(
                "UPDATE status_page_components SET status=?, incident_id=?, updated_at=NOW() WHERE component_id=?"
            )) {
                upd.setString(1, to); upd.setString(2, incidentId); upd.setString(3, componentId);
                upd.executeUpdate();
            }
            recordHistoryConn(c, componentId, incidentId, from, to, changedBy);
            c.commit();
        }
    }

    private void recordHistory(String componentId, String incidentId,
                                String from, String to, String changedBy) throws SQLException {
        try (Connection c = ds.getConnection()) {
            recordHistoryConn(c, componentId, incidentId, from, to, changedBy);
        }
    }

    private void recordHistoryConn(Connection c, String componentId, String incidentId,
                                    String from, String to, String changedBy) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO status_component_history (component_id, incident_id, from_status, to_status, changed_by) VALUES (?,?,?,?,?)"
        )) {
            ps.setString(1, componentId); ps.setString(2, incidentId);
            ps.setString(3, from); ps.setString(4, to); ps.setString(5, changedBy);
            ps.executeUpdate();
        }
    }
}
