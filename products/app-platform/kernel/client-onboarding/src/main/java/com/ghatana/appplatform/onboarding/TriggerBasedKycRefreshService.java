package com.ghatana.appplatform.onboarding;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Handle event-driven KYC refresh triggers.
 *              Supported trigger types:
 *                SANCTIONS_HIT       – D-14 post-onboarding sanctions alert
 *                RISK_SCORE_CHANGE   – material score delta (≥10 points)
 *                SUSPICIOUS_ACTIVITY – D-08 SAR referral
 *                CLIENT_DATA_CHANGE  – client self-reported data update
 *              For each trigger, a lightweight re-verification workflow is launched
 *              (not the full initial onboarding flow). Full audit trail recorded.
 * @doc.layer   Client Onboarding (W-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W02-013: Trigger-based KYC refresh
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS kyc_refresh_triggers (
 *   trigger_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   client_id       TEXT NOT NULL,
 *   instance_id     TEXT NOT NULL,
 *   trigger_type    TEXT NOT NULL,
 *   trigger_source  TEXT NOT NULL,
 *   detail          JSONB,
 *   workflow_ref    TEXT,
 *   status          TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | LAUNCHED | SUPPRESSED | FAILED
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   launched_at     TIMESTAMPTZ
 * );
 * </pre>
 */
public class TriggerBasedKycRefreshService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface KycRefreshWorkflowPort {
        /**
         * Launch a lightweight KYC re-verification workflow.
         * Returns the new workflow instance reference.
         */
        String launchRefresh(String clientId, String existingInstanceId, String triggerType, String detail) throws Exception;
    }

    public interface AuditPort {
        void record(String clientId, String action, String detail) throws Exception;
    }

    public interface DuplicateSuppressPort {
        /**
         * Return true if an equivalent refresh workflow is already in-progress
         * (same client, same trigger type, launched within suppressWindowMinutes).
         */
        boolean isAlreadyInProgress(String clientId, String triggerType, int suppressWindowMinutes) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum TriggerType {
        SANCTIONS_HIT,
        RISK_SCORE_CHANGE,
        SUSPICIOUS_ACTIVITY,
        CLIENT_DATA_CHANGE
    }

    public record TriggerEvent(
        String clientId,
        String instanceId,
        TriggerType triggerType,
        String triggerSource,   // e.g. "D-14", "D-08", "CLIENT_PORTAL"
        Map<String, Object> detail
    ) {}

    public record TriggerResult(
        String triggerId,
        TriggerType type,
        String status,
        String workflowRef
    ) {}

    // Suppress duplicate triggers within this window (per type per client)
    private static final int SUPPRESS_WINDOW_MINUTES = 60;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final KycRefreshWorkflowPort refreshWorkflow;
    private final AuditPort audit;
    private final DuplicateSuppressPort duplicateSuppress;
    private final Executor executor;
    private final Counter launchedCounter;
    private final Counter suppressedCounter;

    public TriggerBasedKycRefreshService(
        javax.sql.DataSource ds,
        KycRefreshWorkflowPort refreshWorkflow,
        AuditPort audit,
        DuplicateSuppressPort duplicateSuppress,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                = ds;
        this.refreshWorkflow   = refreshWorkflow;
        this.audit             = audit;
        this.duplicateSuppress = duplicateSuppress;
        this.executor          = executor;
        this.launchedCounter   = Counter.builder("kyc.refresh.launched")
            .tag("trigger", "any").register(registry);
        this.suppressedCounter = Counter.builder("kyc.refresh.suppressed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Accept an incoming KYC refresh trigger and, if not already in-progress,
     * launch the lightweight re-verification workflow.
     */
    public Promise<TriggerResult> handleTrigger(TriggerEvent event) {
        return Promise.ofBlocking(executor, () -> {
            String detailJson = detailJson(event.detail());
            String triggerId  = insertTrigger(event, detailJson);

            // Suppress duplicate in-progress refresh
            boolean inProgress = duplicateSuppress.isAlreadyInProgress(
                event.clientId(), event.triggerType().name(), SUPPRESS_WINDOW_MINUTES
            );
            if (inProgress) {
                markSuppressed(triggerId);
                suppressedCounter.increment();
                audit.record(event.clientId(), "KYC_REFRESH_SUPPRESSED",
                    "TriggerType=" + event.triggerType() + " suppressed within " + SUPPRESS_WINDOW_MINUTES + "min window");
                return new TriggerResult(triggerId, event.triggerType(), "SUPPRESSED", null);
            }

            // Launch lightweight re-verification
            String workflowRef = refreshWorkflow.launchRefresh(
                event.clientId(), event.instanceId(), event.triggerType().name(), detailJson
            );
            markLaunched(triggerId, workflowRef);
            launchedCounter.increment();
            audit.record(event.clientId(), "KYC_REFRESH_LAUNCHED",
                "TriggerType=" + event.triggerType() + " Source=" + event.triggerSource() + " Ref=" + workflowRef);

            return new TriggerResult(triggerId, event.triggerType(), "LAUNCHED", workflowRef);
        });
    }

    /**
     * Query the audit trail of triggers for a given client.
     */
    public Promise<List<TriggerResult>> listTriggers(String clientId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT trigger_id, trigger_type, status, workflow_ref " +
                     "FROM kyc_refresh_triggers WHERE client_id = ? ORDER BY created_at DESC"
                 )) {
                ps.setString(1, clientId);
                List<TriggerResult> results = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new TriggerResult(
                            rs.getString("trigger_id"),
                            TriggerType.valueOf(rs.getString("trigger_type")),
                            rs.getString("status"),
                            rs.getString("workflow_ref")
                        ));
                    }
                }
                return results;
            }
        });
    }

    // ── DB helpers ────────────────────────────────────────────────────────────

    private String insertTrigger(TriggerEvent event, String detailJson) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO kyc_refresh_triggers " +
                 "(client_id, instance_id, trigger_type, trigger_source, detail) " +
                 "VALUES (?,?,?,?,?::jsonb) RETURNING trigger_id"
             )) {
            ps.setString(1, event.clientId());
            ps.setString(2, event.instanceId());
            ps.setString(3, event.triggerType().name());
            ps.setString(4, event.triggerSource());
            ps.setString(5, detailJson);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markLaunched(String triggerId, String workflowRef) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE kyc_refresh_triggers SET status = 'LAUNCHED', workflow_ref = ?, launched_at = NOW() " +
                 "WHERE trigger_id = ?"
             )) {
            ps.setString(1, workflowRef); ps.setString(2, triggerId); ps.executeUpdate();
        }
    }

    private void markSuppressed(String triggerId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE kyc_refresh_triggers SET status = 'SUPPRESSED' WHERE trigger_id = ?"
             )) {
            ps.setString(1, triggerId); ps.executeUpdate();
        }
    }

    private String detailJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        detail.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }
}
