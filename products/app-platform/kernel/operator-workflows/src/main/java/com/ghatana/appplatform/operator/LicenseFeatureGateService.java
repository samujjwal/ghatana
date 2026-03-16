package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage tenant licenses and feature-flag gates.
 *              License type controls which features a tenant may activate.
 *              Supported features: ALGO_TRADING, MULTI_CURRENCY, SANCTIONS_SCREENING, ADVANCED_RISK.
 *              On license expiry, a 7-day grace period is granted before gates hard-close.
 *              Feature gate enforcement operates at both API entry and event routing layer.
 *              Issues LicenseExpired event when grace period ends.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-004: License management and feature gates
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS tenant_licenses (
 *   license_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL UNIQUE,
 *   license_type   TEXT NOT NULL,           -- BROKER | ASSET_MANAGER | CUSTODIAN
 *   issued_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   expires_at     TIMESTAMPTZ NOT NULL,
 *   status         TEXT NOT NULL DEFAULT 'ACTIVE'  -- ACTIVE | GRACE | EXPIRED | REVOKED
 * );
 * CREATE TABLE IF NOT EXISTS tenant_feature_gates (
 *   gate_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   feature        TEXT NOT NULL,           -- ALGO_TRADING | MULTI_CURRENCY | SANCTIONS_SCREENING | ADVANCED_RISK
 *   enabled        BOOLEAN NOT NULL DEFAULT FALSE,
 *   granted_at     TIMESTAMPTZ,
 *   revoked_at     TIMESTAMPTZ,
 *   UNIQUE(tenant_id, feature)
 * );
 * </pre>
 */
public class LicenseFeatureGateService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum Feature {
        ALGO_TRADING, MULTI_CURRENCY, SANCTIONS_SCREENING, ADVANCED_RISK
    }

    public record TenantLicense(
        String licenseId, String tenantId, String licenseType,
        Instant issuedAt, Instant expiresAt, String status
    ) {}

    /** Default features enabled per license type. */
    private static final Map<String, Set<Feature>> LICENSE_DEFAULTS = Map.of(
        "BROKER",         Set.of(Feature.MULTI_CURRENCY, Feature.SANCTIONS_SCREENING),
        "ASSET_MANAGER",  Set.of(Feature.MULTI_CURRENCY, Feature.ALGO_TRADING),
        "CUSTODIAN",      Set.of(Feature.MULTI_CURRENCY, Feature.SANCTIONS_SCREENING, Feature.ADVANCED_RISK)
    );

    private static final int GRACE_DAYS = 7;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EventPublishPort events;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter licenseExpiredCounter;
    private final Counter featureGrantCounter;
    private final Counter featureRevokeCounter;

    public LicenseFeatureGateService(
        javax.sql.DataSource ds,
        EventPublishPort events,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.events              = events;
        this.audit               = audit;
        this.executor            = executor;
        this.licenseExpiredCounter = Counter.builder("operator.license.expired").register(registry);
        this.featureGrantCounter   = Counter.builder("operator.feature.granted").register(registry);
        this.featureRevokeCounter  = Counter.builder("operator.feature.revoked").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Issue a license for a tenant, initialising feature gates from license-type defaults.
     */
    public Promise<TenantLicense> issueLicense(
        String tenantId, String licenseType, Instant expiresAt, String operatorId
    ) {
        return Promise.ofBlocking(executor, () -> {
            if (!LICENSE_DEFAULTS.containsKey(licenseType)) {
                throw new IllegalArgumentException("Unknown license type: " + licenseType);
            }
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                String licenseId;
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO tenant_licenses (tenant_id, license_type, expires_at) " +
                    "VALUES (?,?,?) ON CONFLICT (tenant_id) DO UPDATE " +
                    "SET license_type=EXCLUDED.license_type, expires_at=EXCLUDED.expires_at, " +
                    "status='ACTIVE', issued_at=NOW() RETURNING license_id, issued_at"
                )) {
                    ps.setString(1, tenantId); ps.setString(2, licenseType);
                    ps.setTimestamp(3, Timestamp.from(expiresAt));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        licenseId = rs.getString("license_id");
                    }
                }
                // initialise feature gates from defaults (do not overwrite existing grants)
                for (Feature f : LICENSE_DEFAULTS.getOrDefault(licenseType, Set.of())) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO tenant_feature_gates (tenant_id, feature, enabled, granted_at) " +
                        "VALUES (?,?,TRUE,NOW()) ON CONFLICT (tenant_id, feature) DO NOTHING"
                    )) {
                        ps.setString(1, tenantId); ps.setString(2, f.name());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                audit.record(operatorId, "LICENSE_ISSUED",
                    "tenant=" + tenantId + " type=" + licenseType + " expires=" + expiresAt);
                return new TenantLicense(licenseId, tenantId, licenseType,
                    Instant.now(), expiresAt, "ACTIVE");
            }
        });
    }

    /**
     * Check if a feature is currently enabled for a tenant.
     * Returns false if grace period has ended (hard-close).
     */
    public Promise<Boolean> isEnabled(String tenantId, Feature feature) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                // Check license status first
                String licenseStatus = getLicenseStatus(c, tenantId);
                if ("EXPIRED".equals(licenseStatus) || "REVOKED".equals(licenseStatus)) return false;

                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT enabled FROM tenant_feature_gates WHERE tenant_id=? AND feature=?"
                )) {
                    ps.setString(1, tenantId); ps.setString(2, feature.name());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return false;
                        return rs.getBoolean("enabled");
                    }
                }
            }
        });
    }

    /**
     * Operator grants or revokes an individual feature for a tenant.
     */
    public Promise<Void> setFeature(String tenantId, Feature feature, boolean enabled, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     enabled
                     ? "INSERT INTO tenant_feature_gates (tenant_id, feature, enabled, granted_at) " +
                       "VALUES (?,?,TRUE,NOW()) ON CONFLICT (tenant_id, feature) DO UPDATE SET enabled=TRUE, granted_at=NOW(), revoked_at=NULL"
                     : "UPDATE tenant_feature_gates SET enabled=FALSE, revoked_at=NOW() WHERE tenant_id=? AND feature=?"
                 )) {
                ps.setString(1, tenantId); ps.setString(2, feature.name());
                ps.executeUpdate();
            }
            if (enabled) featureGrantCounter.increment(); else featureRevokeCounter.increment();
            audit.record(operatorId, enabled ? "FEATURE_GRANTED" : "FEATURE_REVOKED",
                "tenant=" + tenantId + " feature=" + feature.name());
            return null;
        });
    }

    /**
     * Run expiry evaluation. Called by scheduled job.
     * Transitions ACTIVE → GRACE → EXPIRED and fires LicenseExpired event.
     */
    public Promise<Void> evaluateExpiryBatch() {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                // ACTIVE → GRACE
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE tenant_licenses SET status='GRACE' " +
                    "WHERE status='ACTIVE' AND expires_at < NOW() RETURNING tenant_id"
                )) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tid = rs.getString("tenant_id");
                            events.publish("LicenseEnteredGrace", Map.of("tenantId", tid));
                        }
                    }
                }
                // GRACE → EXPIRED (after 7 days)
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE tenant_licenses SET status='EXPIRED' " +
                    "WHERE status='GRACE' AND expires_at < NOW() - INTERVAL '" + GRACE_DAYS + " days' " +
                    "RETURNING tenant_id"
                )) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String tid = rs.getString("tenant_id");
                            events.publish("LicenseExpired", Map.of("tenantId", tid));
                            licenseExpiredCounter.increment();
                        }
                    }
                }
            }
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getLicenseStatus(Connection c, String tenantId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT status FROM tenant_licenses WHERE tenant_id=?"
        )) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "NONE";
                return rs.getString("status");
            }
        }
    }
}
