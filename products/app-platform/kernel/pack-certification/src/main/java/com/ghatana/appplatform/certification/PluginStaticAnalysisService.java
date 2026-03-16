package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Orchestrate static analysis for plugin bundles before certification.
 *              Runs Semgrep (custom ghatana ruleset), Bandit (Python), and ESLint-security (JS/TS).
 *              Checks: hardcoded credentials, command injection, SQL injection,
 *                      unsafe deserialization, eval() usage.
 *              Any CRITICAL finding blocks certification; result persisted for audit.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-003: Static code analysis for plugins
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS plugin_scan_results (
 *   scan_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   version        TEXT NOT NULL,
 *   tool           TEXT NOT NULL,          -- SEMGREP | BANDIT | ESLINT_SECURITY
 *   severity       TEXT NOT NULL,          -- CRITICAL | HIGH | MEDIUM | LOW | INFO
 *   rule_id        TEXT NOT NULL,
 *   file_path      TEXT,
 *   line_number    INT,
 *   message        TEXT NOT NULL,
 *   scanned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE INDEX IF NOT EXISTS idx_psr_plugin ON plugin_scan_results(plugin_id, version);
 * </pre>
 */
public class PluginStaticAnalysisService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    /** Adapter that invokes an external scan tool and returns raw findings. */
    public interface ScanToolPort {
        List<RawFinding> scan(String pluginId, String version, byte[] bundleBytes) throws Exception;
        String toolName();
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record RawFinding(
        String severity, String ruleId, String filePath, int lineNumber, String message
    ) {}

    public record ScanSummary(
        String pluginId, String version,
        int critical, int high, int medium, int low, int info,
        boolean certificationBlocked
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final List<ScanToolPort> tools;
    private final EventPublishPort events;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter blockedCounter;
    private final Counter criticalFindingCounter;

    public PluginStaticAnalysisService(
        javax.sql.DataSource ds,
        List<ScanToolPort> tools,
        EventPublishPort events,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.tools                = tools;
        this.events               = events;
        this.audit                = audit;
        this.executor             = executor;
        this.blockedCounter       = Counter.builder("certification.scan.blocked").register(registry);
        this.criticalFindingCounter = Counter.builder("certification.scan.critical_findings").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run all configured scan tools against a plugin bundle.
     * Persists findings, fires PluginScanBlocked event if CRITICAL findings exist.
     */
    public Promise<ScanSummary> scan(String pluginId, String version, byte[] bundleBytes, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            int critical = 0, high = 0, medium = 0, low = 0, info = 0;

            for (ScanToolPort tool : tools) {
                List<RawFinding> findings = tool.scan(pluginId, version, bundleBytes);
                for (RawFinding f : findings) {
                    persistFinding(pluginId, version, tool.toolName(), f);
                    switch (f.severity()) {
                        case "CRITICAL" -> { critical++; criticalFindingCounter.increment(); }
                        case "HIGH"     -> high++;
                        case "MEDIUM"   -> medium++;
                        case "LOW"      -> low++;
                        default         -> info++;
                    }
                }
            }

            boolean blocked = critical > 0;
            if (blocked) {
                blockedCounter.increment();
                events.publish("PluginScanBlocked", Map.of(
                    "pluginId", pluginId, "version", version,
                    "criticalCount", critical, "highCount", high));
            }

            audit.record(requestedBy, "PLUGIN_STATIC_ANALYSIS",
                "plugin=" + pluginId + " version=" + version +
                " critical=" + critical + " blocked=" + blocked);

            return new ScanSummary(pluginId, version, critical, high, medium, low, info, blocked);
        });
    }

    /**
     * Retrieve scan findings for a plugin version grouped by severity.
     */
    public Promise<List<RawFinding>> getFindings(String pluginId, String version, String severityFilter) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT tool, severity, rule_id, file_path, line_number, message " +
                         "FROM plugin_scan_results WHERE plugin_id=? AND version=?" +
                         (severityFilter != null ? " AND severity=?" : "") +
                         " ORDER BY CASE severity " +
                         "WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 " +
                         "WHEN 'LOW' THEN 4 ELSE 5 END";
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, pluginId); ps.setString(2, version);
                if (severityFilter != null) ps.setString(3, severityFilter);
                List<RawFinding> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new RawFinding(
                            rs.getString("severity"), rs.getString("rule_id"),
                            rs.getString("file_path"), rs.getInt("line_number"),
                            rs.getString("message")));
                    }
                }
                return results;
            }
        });
    }

    /**
     * Check whether any CRITICAL findings exist for a plugin (used by cert gate).
     */
    public Promise<Boolean> hasCriticalFindings(String pluginId, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT EXISTS(SELECT 1 FROM plugin_scan_results " +
                     "WHERE plugin_id=? AND version=? AND severity='CRITICAL')"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void persistFinding(String pluginId, String version, String tool, RawFinding f) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO plugin_scan_results (plugin_id, version, tool, severity, rule_id, file_path, line_number, message) " +
                 "VALUES (?,?,?,?,?,?,?,?)"
             )) {
            ps.setString(1, pluginId); ps.setString(2, version); ps.setString(3, tool);
            ps.setString(4, f.severity()); ps.setString(5, f.ruleId()); ps.setString(6, f.filePath());
            ps.setInt(7, f.lineNumber()); ps.setString(8, f.message());
            ps.executeUpdate();
        }
    }
}
