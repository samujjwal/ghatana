package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Certificate Authority that issues, tracks, and revokes plugin certificates.
 *              Every plugin-version must hold a valid certificate before it can be
 *              activated on the platform.
 *              Certificate metadata: plugin_id, version, tier (T1/T2/T3), issuer, fingerprint.
 *              Signing uses K-14 HSM so private keys never leave hardware.
 *              Chain verification is performed at installation time.
 *              CertificateIssued and CertificateRevoked events are published for audit.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-001: Plugin certification authority service
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS plugin_certificates (
 *   cert_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   version        TEXT NOT NULL,
 *   tier           TEXT NOT NULL,          -- T1 | T2 | T3
 *   issuer         TEXT NOT NULL,
 *   fingerprint    TEXT NOT NULL UNIQUE,   -- SHA-256 hex of the signed payload
 *   status         TEXT NOT NULL DEFAULT 'VALID',  -- VALID | REVOKED | EXPIRED
 *   issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   expires_at     TIMESTAMPTZ NOT NULL,
 *   revoked_at     TIMESTAMPTZ,
 *   revoke_reason  TEXT,
 *   UNIQUE(plugin_id, version)
 * );
 * </pre>
 */
public class PluginCertificationAuthorityService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    /** K-14 HSM-backed signing integration. */
    public interface HsmSigningPort {
        /** Sign payload bytes; returns hex-encoded fingerprint. */
        String sign(byte[] payload, String keyAlias) throws Exception;
        /** Verify a fingerprint against payload. */
        boolean verify(byte[] payload, String fingerprint, String keyAlias) throws Exception;
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum Tier { T1, T2, T3 }

    public record PluginCertificate(
        String certId, String pluginId, String version, Tier tier,
        String issuer, String fingerprint, String status,
        Instant issuedAt, Instant expiresAt
    ) {}

    /** Validity periods per tier (days). */
    private static final Map<Tier, Integer> VALIDITY_DAYS = Map.of(
        Tier.T1, 365,
        Tier.T2, 365,
        Tier.T3, 365   // T3 explicit 1-year per spec
    );

    private static final String HSM_KEY_ALIAS = "plugin-ca-v1";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final HsmSigningPort hsm;
    private final EventPublishPort events;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter issuedCounter;
    private final Counter revokedCounter;
    private final Counter expiredCounter;

    public PluginCertificationAuthorityService(
        javax.sql.DataSource ds,
        HsmSigningPort hsm,
        EventPublishPort events,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.hsm             = hsm;
        this.events          = events;
        this.audit           = audit;
        this.executor        = executor;
        this.issuedCounter   = Counter.builder("certification.cert.issued").register(registry);
        this.revokedCounter  = Counter.builder("certification.cert.revoked").register(registry);
        this.expiredCounter  = Counter.builder("certification.cert.expired").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Issue a certificate for a plugin version.
     * Fails if a VALID certificate already exists for the same plugin+version.
     */
    public Promise<PluginCertificate> issueCertificate(
        String pluginId, String version, Tier tier, String issuer
    ) {
        return Promise.ofBlocking(executor, () -> {
            byte[] payload = signingPayload(pluginId, version, tier, issuer);
            String fingerprint = hsm.sign(payload, HSM_KEY_ALIAS);
            Instant now       = Instant.now();
            Instant expiresAt = now.plus(VALIDITY_DAYS.get(tier), ChronoUnit.DAYS);

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO plugin_certificates (plugin_id, version, tier, issuer, fingerprint, expires_at) " +
                     "VALUES (?,?,?,?,?,?) RETURNING cert_id, issued_at"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version); ps.setString(3, tier.name());
                ps.setString(4, issuer); ps.setString(5, fingerprint);
                ps.setTimestamp(6, Timestamp.from(expiresAt));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String certId = rs.getString("cert_id");
                    Instant issuedAt = rs.getTimestamp("issued_at").toInstant();
                    PluginCertificate cert = new PluginCertificate(
                        certId, pluginId, version, tier, issuer, fingerprint, "VALID", issuedAt, expiresAt);
                    issuedCounter.increment();
                    events.publish("CertificateIssued", Map.of(
                        "certId", certId, "pluginId", pluginId, "version", version, "tier", tier.name()));
                    audit.record(issuer, "CERTIFICATE_ISSUED",
                        "plugin=" + pluginId + " version=" + version + " tier=" + tier);
                    return cert;
                }
            }
        });
    }

    /**
     * Revoke a certificate. Publishes CertificateRevoked event.
     */
    public Promise<Void> revoke(String certId, String reason, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_certificates SET status='REVOKED', revoked_at=NOW(), revoke_reason=? " +
                     "WHERE cert_id=? AND status='VALID'"
                 )) {
                ps.setString(1, reason); ps.setString(2, certId);
                int updated = ps.executeUpdate();
                if (updated == 0) throw new IllegalStateException("Certificate not found or not VALID: " + certId);
            }
            revokedCounter.increment();
            events.publish("CertificateRevoked", Map.of("certId", certId, "reason", reason));
            audit.record(operatorId, "CERTIFICATE_REVOKED", "certId=" + certId + " reason=" + reason);
            return null;
        });
    }

    /**
     * Verify a plugin certificate chain. Returns true if certificate is VALID and signature verifies.
     */
    public Promise<Boolean> verifyCertificate(String pluginId, String version) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT cert_id, tier, issuer, fingerprint, status, expires_at " +
                     "FROM plugin_certificates WHERE plugin_id=? AND version=?"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, version);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    if (!"VALID".equals(rs.getString("status"))) return false;
                    if (rs.getTimestamp("expires_at").toInstant().isBefore(Instant.now())) return false;
                    Tier tier = Tier.valueOf(rs.getString("tier"));
                    String issuer = rs.getString("issuer");
                    String fingerprint = rs.getString("fingerprint");
                    byte[] payload = signingPayload(pluginId, version, tier, issuer);
                    return hsm.verify(payload, fingerprint, HSM_KEY_ALIAS);
                }
            }
        });
    }

    /**
     * Batch expiry scan. Called by a scheduled job.
     */
    public Promise<Void> expireScan() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_certificates SET status='EXPIRED' " +
                     "WHERE status='VALID' AND expires_at < NOW() RETURNING cert_id, plugin_id, version"
                 )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expiredCounter.increment();
                        events.publish("CertificateExpired", Map.of(
                            "certId", rs.getString("cert_id"),
                            "pluginId", rs.getString("plugin_id"),
                            "version", rs.getString("version")));
                    }
                }
            }
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private byte[] signingPayload(String pluginId, String version, Tier tier, String issuer) {
        String payload = pluginId + "|" + version + "|" + tier.name() + "|" + issuer;
        return payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
