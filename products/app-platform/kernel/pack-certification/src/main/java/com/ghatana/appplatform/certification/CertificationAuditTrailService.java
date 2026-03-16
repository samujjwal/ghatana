package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Immutable, append-only audit trail for the entire certification lifecycle.
 *              Every step — static scan, dynamic sandbox, checklist review, certification
 *              decisions, revocations — is persisted via K-07 audit SDK (delegated to
 *              AuditPort) and also stored locally for fast lookup.
 *              Public transparency log: installed-plugin users can verify the certification
 *              fingerprint without seeing internal reviewer notes.
 *              Certificate verification API: given a fingerprint → cert details + valid/revoked.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; append-only
 *
 * STORY-P01-010: Full immutable certification audit trail
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS certification_trail_entries (
 *   entry_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   version        TEXT NOT NULL,
 *   event_type     TEXT NOT NULL,  -- SCAN_COMPLETE | DYNAMIC_TEST | CHECKLIST_REVIEWED | CERTIFIED | REVOKED
 *   actor_id       TEXT NOT NULL,
 *   summary        TEXT NOT NULL,
 *   internal_notes TEXT,           -- reviewer-only; excluded from public transparency log
 *   occurred_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS plugin_certificates (
 *   cert_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   version        TEXT NOT NULL,
 *   fingerprint    TEXT NOT NULL UNIQUE,   -- SHA-256 of pluginId+version+certId
 *   tier           TEXT NOT NULL,
 *   issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   expires_at     TIMESTAMPTZ,
 *   status         TEXT NOT NULL DEFAULT 'VALID',  -- VALID | REVOKED
 *   revoked_at     TIMESTAMPTZ,
 *   revoke_reason  TEXT,
 *   UNIQUE (plugin_id, version)
 * );
 * </pre>
 */
public class CertificationAuditTrailService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface K07AuditPort {
        void append(String pluginId, String version, String eventType, String actor, String summary) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final K07AuditPort k07Audit;
    private final Executor executor;
    private final Counter certificatesIssued;
    private final Counter verificationChecks;

    public CertificationAuditTrailService(
        javax.sql.DataSource ds,
        K07AuditPort k07Audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.k07Audit            = k07Audit;
        this.executor            = executor;
        this.certificatesIssued  = Counter.builder("certification.trail.certificates_issued").register(registry);
        this.verificationChecks  = Counter.builder("certification.trail.verification_checks").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Append a lifecycle event to the immutable trail (K-07 + local store). */
    public Promise<Void> append(String pluginId, String version, String eventType,
                                 String actorId, String summary, String internalNotes) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO certification_trail_entries (plugin_id, version, event_type, actor_id, summary, internal_notes) " +
                     "VALUES (?,?,?,?,?,?)"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version); ps.setString(3, eventType);
                ps.setString(4, actorId);  ps.setString(5, summary); ps.setString(6, internalNotes);
                ps.executeUpdate();
            }
            k07Audit.append(pluginId, version, eventType, actorId, summary);
            return null;
        });
    }

    /** Issue the official plugin certificate after successful certification. Returns fingerprint. */
    public Promise<String> issueCertificate(String pluginId, String version, String tier, String issuedBy) {
        return Promise.ofBlocking(executor, () -> {
            String fingerprint = computeFingerprint(pluginId, version);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO plugin_certificates (plugin_id, version, fingerprint, tier, expires_at) " +
                     "VALUES (?,?,?,?, NOW() + INTERVAL '2 years') " +
                     "ON CONFLICT (plugin_id, version) DO UPDATE SET fingerprint=EXCLUDED.fingerprint, status='VALID', revoked_at=NULL, revoke_reason=NULL " +
                     "RETURNING cert_id"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version);
                ps.setString(3, fingerprint); ps.setString(4, tier);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); }
            }
            k07Audit.append(pluginId, version, "CERTIFIED", issuedBy, "Certificate issued fingerprint=" + fingerprint);
            certificatesIssued.increment();
            return fingerprint;
        });
    }

    /**
     * Public certificate verification by fingerprint.
     * Returns a sanitised view — no internal reviewer notes.
     */
    public Promise<Map<String, Object>> verifyCertificate(String fingerprint) {
        return Promise.ofBlocking(executor, () -> {
            verificationChecks.increment();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT plugin_id, version, tier, issued_at, expires_at, status, revoked_at, revoke_reason " +
                     "FROM plugin_certificates WHERE fingerprint=?"
                 )) {
                ps.setString(1, fingerprint);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Map.of("valid", false, "reason", "Unknown fingerprint");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("fingerprint", fingerprint);
                    result.put("pluginId",    rs.getString("plugin_id"));
                    result.put("version",     rs.getString("version"));
                    result.put("tier",        rs.getString("tier"));
                    result.put("issuedAt",    rs.getTimestamp("issued_at").toInstant().toString());
                    result.put("expiresAt",   rs.getTimestamp("expires_at").toInstant().toString());
                    String status = rs.getString("status");
                    result.put("valid",  "VALID".equals(status));
                    result.put("status", status);
                    if ("REVOKED".equals(status)) {
                        result.put("revokedAt",    rs.getTimestamp("revoked_at").toInstant().toString());
                        result.put("revokeReason", rs.getString("revoke_reason"));
                    }
                    return result;
                }
            }
        });
    }

    /** Full internal trail (reviewer-visible) ordered chronologically. */
    public Promise<List<Map<String, Object>>> getFullTrail(String pluginId, String version) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> entries = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT entry_id, event_type, actor_id, summary, internal_notes, occurred_at " +
                     "FROM certification_trail_entries WHERE plugin_id=? AND version=? ORDER BY occurred_at"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> e = new LinkedHashMap<>();
                        e.put("entryId",       rs.getString("entry_id"));
                        e.put("eventType",     rs.getString("event_type"));
                        e.put("actorId",       rs.getString("actor_id"));
                        e.put("summary",       rs.getString("summary"));
                        e.put("internalNotes", rs.getString("internal_notes"));
                        e.put("occurredAt",    rs.getTimestamp("occurred_at").toInstant().toString());
                        entries.add(e);
                    }
                }
            }
            return entries;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String computeFingerprint(String pluginId, String version) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest((pluginId + ":" + version + ":" + System.nanoTime()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
