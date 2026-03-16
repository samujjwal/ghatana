package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Automated incident playbooks that trigger pre-defined response actions
 *              when an alert matches a playbook's trigger condition.
 *              Actions: AUTO_SCALE, CIRCUIT_BREAKER, ALERT_SUPPRESSION, RESTART_SERVICE,
 *              PAGE_ONCALL, CREATE_INCIDENT.
 *              Integration: K-18 alert bus for receiving triggers; emits action events back.
 *              Each playbook execution is logged with outcome (SUCCESS|FAILED).
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-009: Automated incident playbook matching and execution
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_playbooks (
 *   playbook_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   name             TEXT NOT NULL,
 *   trigger_condition TEXT NOT NULL,   -- simple pattern: 'alert.name == X AND alert.severity == P1'
 *   actions          JSONB NOT NULL,   -- ordered array of {action, params}
 *   enabled          BOOLEAN NOT NULL DEFAULT TRUE,
 *   created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS playbook_executions (
 *   exec_id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   playbook_id      TEXT NOT NULL,
 *   triggered_by     TEXT NOT NULL,   -- alert fingerprint or event id
 *   alert_payload    JSONB,
 *   action_results   JSONB,
 *   outcome          TEXT NOT NULL,   -- SUCCESS | PARTIAL | FAILED
 *   executed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   duration_ms      BIGINT
 * );
 * </pre>
 */
public class AutomatedIncidentPlaybookService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface K18AlertBusPort {
        /** Subscribe to alert events — called externally; this method processes one event. */
        void onAlert(Map<String, String> alert) throws Exception;
    }

    public interface AutoScalePort {
        void scaleUp(String service, int factor) throws Exception;
    }

    public interface CircuitBreakerPort {
        void open(String service) throws Exception;
    }

    public interface AlertSuppressionPort {
        void suppress(String alertPattern, int durationMinutes) throws Exception;
    }

    public interface ServiceRestartPort {
        void restart(String service) throws Exception;
    }

    public interface OncallPort {
        void page(String escalationPolicy, String summary) throws Exception;
    }

    public interface IncidentCreationPort {
        String createIncident(String title, String severity, String source) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final AutoScalePort autoScale;
    private final CircuitBreakerPort circuitBreaker;
    private final AlertSuppressionPort alertSuppression;
    private final ServiceRestartPort serviceRestart;
    private final OncallPort oncall;
    private final IncidentCreationPort incidentCreation;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter playbooksTriggered;
    private final Counter playbooksFailed;

    public AutomatedIncidentPlaybookService(
        javax.sql.DataSource ds,
        AutoScalePort autoScale,
        CircuitBreakerPort circuitBreaker,
        AlertSuppressionPort alertSuppression,
        ServiceRestartPort serviceRestart,
        OncallPort oncall,
        IncidentCreationPort incidentCreation,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                 = ds;
        this.autoScale          = autoScale;
        this.circuitBreaker     = circuitBreaker;
        this.alertSuppression   = alertSuppression;
        this.serviceRestart     = serviceRestart;
        this.oncall             = oncall;
        this.incidentCreation   = incidentCreation;
        this.audit              = audit;
        this.executor           = executor;
        this.playbooksTriggered = Counter.builder("incident.playbook.triggered").register(registry);
        this.playbooksFailed    = Counter.builder("incident.playbook.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by K-18 integration when an alert arrives. Matches and executes applicable playbooks. */
    public Promise<Integer> processAlert(Map<String, String> alert, String triggeredBy) {
        return Promise.ofBlocking(executor, () -> {
            List<Playbook> matching = findMatchingPlaybooks(alert);
            for (Playbook pb : matching) {
                executePlaybook(pb, alert, triggeredBy);
            }
            return matching.size();
        });
    }

    /** CRUD: create a playbook. Returns playbookId. */
    public Promise<String> createPlaybook(String name, String triggerCondition, String actionsJson, String createdBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO incident_playbooks (name, trigger_condition, actions) VALUES (?,?,?::jsonb) RETURNING playbook_id"
                 )) {
                ps.setString(1, name); ps.setString(2, triggerCondition); ps.setString(3, actionsJson);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); String id = rs.getString(1);
                    audit.record(createdBy, "PLAYBOOK_CREATED", "playbookId=" + id + " name=" + name);
                    return id;
                }
            }
        });
    }

    /** Enable or disable a playbook. */
    public Promise<Void> setEnabled(String playbookId, boolean enabled, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_playbooks SET enabled=? WHERE playbook_id=?"
                 )) { ps.setBoolean(1, enabled); ps.setString(2, playbookId); ps.executeUpdate(); }
            audit.record(actorId, enabled ? "PLAYBOOK_ENABLED" : "PLAYBOOK_DISABLED", "playbookId=" + playbookId);
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Playbook> findMatchingPlaybooks(Map<String, String> alert) throws SQLException {
        List<Playbook> result = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT playbook_id, trigger_condition, actions::text FROM incident_playbooks WHERE enabled=TRUE"
             )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (matches(rs.getString("trigger_condition"), alert)) {
                        result.add(new Playbook(rs.getString("playbook_id"),
                            rs.getString("trigger_condition"), rs.getString("actions")));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Simple key==value AND-chained matching.
     * Example condition: "alertName==HighCpuUsage AND severity==P1"
     */
    private boolean matches(String condition, Map<String, String> alert) {
        for (String clause : condition.split("\\s+AND\\s+")) {
            String[] parts = clause.split("==", 2);
            if (parts.length < 2) continue;
            String key = parts[0].trim();
            String expected = parts[1].trim();
            if (!expected.equals(alert.getOrDefault(key, ""))) return false;
        }
        return true;
    }

    private void executePlaybook(Playbook pb, Map<String, String> alert, String triggeredBy) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        String outcome = "SUCCESS";
        try {
            // Naive JSON array parse for action list — no external library dependency
            // Expected format: [{"action":"AUTO_SCALE","service":"x","factor":"2"}, ...]
            executeActionsFromJson(pb.actionsJson(), alert, results);
        } catch (Exception ex) {
            outcome = "FAILED";
            playbooksFailed.increment();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            persistExecution(pb.playbookId(), triggeredBy, alert, results, outcome, elapsed);
            if (!"FAILED".equals(outcome)) playbooksTriggered.increment();
        }
    }

    private void executeActionsFromJson(String actionsJson, Map<String, String> alert,
                                         List<Map<String, Object>> results) throws Exception {
        // Simple bracket-by-bracket action extraction (avoids JSON library dependency)
        // Actions are stored as: [{"action":"X","k":"v",...},...]
        int i = actionsJson.indexOf('{');
        while (i >= 0 && i < actionsJson.length()) {
            int end = actionsJson.indexOf('}', i);
            if (end < 0) break;
            String obj = actionsJson.substring(i + 1, end);
            Map<String, String> params = parseSimpleJson(obj);
            String action = params.getOrDefault("action", "");
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("action", action);
            try {
                switch (action) {
                    case "AUTO_SCALE" ->
                        autoScale.scaleUp(params.getOrDefault("service", alert.getOrDefault("service", "")),
                            Integer.parseInt(params.getOrDefault("factor", "2")));
                    case "CIRCUIT_BREAKER" ->
                        circuitBreaker.open(params.getOrDefault("service", alert.getOrDefault("service", "")));
                    case "ALERT_SUPPRESSION" ->
                        alertSuppression.suppress(params.getOrDefault("pattern", "*"),
                            Integer.parseInt(params.getOrDefault("durationMinutes", "10")));
                    case "RESTART_SERVICE" ->
                        serviceRestart.restart(params.getOrDefault("service", alert.getOrDefault("service", "")));
                    case "PAGE_ONCALL" ->
                        oncall.page(params.getOrDefault("policy", "default"),
                            alert.getOrDefault("alertName", "Unknown alert"));
                    case "CREATE_INCIDENT" ->
                        incidentCreation.createIncident(
                            params.getOrDefault("title", "Auto-created: " + alert.getOrDefault("alertName", "")),
                            params.getOrDefault("severity", alert.getOrDefault("severity", "P3")),
                            "playbook");
                }
                r.put("status", "ok");
            } catch (Exception ex) {
                r.put("status", "error"); r.put("error", ex.getMessage());
                throw ex;
            } finally {
                results.add(r);
            }
            i = actionsJson.indexOf('{', end + 1);
        }
    }

    /** Parse {"k":"v","k2":"v2"} into a Map — simple, no json library. */
    private Map<String, String> parseSimpleJson(String obj) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String pair : obj.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) m.put(kv[0].trim().replace("\"", ""), kv[1].trim().replace("\"", ""));
        }
        return m;
    }

    private void persistExecution(String playbookId, String triggeredBy, Map<String, String> alert,
                                   List<Map<String, Object>> results, String outcome, long durationMs) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO playbook_executions (playbook_id, triggered_by, alert_payload, action_results, outcome, duration_ms) " +
                 "VALUES (?,?,?::jsonb,?::jsonb,?,?)"
             )) {
            ps.setString(1, playbookId); ps.setString(2, triggeredBy);
            ps.setString(3, mapToJson(alert)); ps.setString(4, resultsToJson(results));
            ps.setString(5, outcome);          ps.setLong(6, durationMs);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    private String mapToJson(Map<String, String> m) {
        StringBuilder sb = new StringBuilder("{");
        m.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        return sb.append("}").toString();
    }

    private String resultsToJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (Map<String, Object> m : list) {
            sb.append("{");
            m.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
            sb.append("},");
        }
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }

    private record Playbook(String playbookId, String triggerCondition, String actionsJson) {}
}
