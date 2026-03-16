package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Operator-controlled provisioning of regulator access requests.
 *              Operator initiates access on behalf of a regulatory authority,
 *              supplying a mandate reference number, tenant scope, and validity window.
 *              Access auto-expires; renewal requires a new operator approval.
 *              Tenants receive notification when regulators are granted access to their data.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-002: Regulator access request and provisioning
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulator_access_grants (
 *   grant_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   user_id          TEXT NOT NULL REFERENCES regulator_users(user_id),
 *   tenant_id        TEXT NOT NULL,
 *   mandate_ref      TEXT NOT NULL,           -- regulatory mandate/order reference
 *   access_level     TEXT NOT NULL DEFAULT 'READ_ONLY',
 *   granted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   valid_until      TIMESTAMPTZ NOT NULL,
 *   revoked_at       TIMESTAMPTZ,
 *   granted_by       TEXT NOT NULL,
 *   renewal_count    INT NOT NULL DEFAULT 0
 * );
 * </pre>
 */
public class RegulatorAccessProvisioningService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NotificationPort {
        void notifyTenant(String tenantId, String message) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record AccessGrant(
        String grantId, String userId, String tenantId, String mandateRef,
        Instant grantedAt, Instant validUntil, String status, int renewalCount
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NotificationPort notification;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter grantCounter;
    private final Counter renewalCounter;
    private final Counter revokeCounter;

    public RegulatorAccessProvisioningService(
        javax.sql.DataSource ds,
        NotificationPort notification,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.notification   = notification;
        this.audit          = audit;
        this.executor       = executor;
        this.grantCounter   = Counter.builder("regulator.access.grants").register(registry);
        this.renewalCounter = Counter.builder("regulator.access.renewals").register(registry);
        this.revokeCounter  = Counter.builder("regulator.access.revocations").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Operator grants a regulator user access to a specific tenant for a time window.
     */
    public Promise<AccessGrant> grantAccess(
        String userId, String tenantId, String mandateRef, Instant validUntil, String operatorId
    ) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regulator_access_grants (user_id, tenant_id, mandate_ref, valid_until, granted_by) " +
                     "VALUES (?,?,?,?,?) RETURNING grant_id, granted_at"
                 )) {
                ps.setString(1, userId); ps.setString(2, tenantId);
                ps.setString(3, mandateRef); ps.setTimestamp(4, Timestamp.from(validUntil));
                ps.setString(5, operatorId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String grantId = rs.getString("grant_id");
                    Instant grantedAt = rs.getTimestamp("granted_at").toInstant();
                    grantCounter.increment();
                    notification.notifyTenant(tenantId,
                        "Regulator access granted (mandate: " + mandateRef + ") until " + validUntil);
                    audit.record(operatorId, "REGULATOR_ACCESS_GRANTED",
                        "user=" + userId + " tenant=" + tenantId + " mandate=" + mandateRef);
                    return new AccessGrant(grantId, userId, tenantId, mandateRef, grantedAt, validUntil, "ACTIVE", 0);
                }
            }
        });
    }

    /**
     * Operator renews an access grant for another validity window.
     */
    public Promise<Void> renewAccess(String grantId, Instant newValidUntil, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String tenantId = getTenantForGrant(grantId);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE regulator_access_grants SET valid_until=?, renewal_count=renewal_count+1, revoked_at=NULL " +
                     "WHERE grant_id=?"
                 )) {
                ps.setTimestamp(1, Timestamp.from(newValidUntil)); ps.setString(2, grantId);
                ps.executeUpdate();
            }
            renewalCounter.increment();
            notification.notifyTenant(tenantId, "Regulator access renewed until " + newValidUntil);
            audit.record(operatorId, "REGULATOR_ACCESS_RENEWED", "grantId=" + grantId + " until=" + newValidUntil);
            return null;
        });
    }

    /**
     * Operator revokes regulator access immediately.
     */
    public Promise<Void> revokeAccess(String grantId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String tenantId = getTenantForGrant(grantId);
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE regulator_access_grants SET revoked_at=NOW() WHERE grant_id=?"
                 )) {
                ps.setString(1, grantId); ps.executeUpdate();
            }
            revokeCounter.increment();
            notification.notifyTenant(tenantId, "Regulator access has been revoked.");
            audit.record(operatorId, "REGULATOR_ACCESS_REVOKED", "grantId=" + grantId);
            return null;
        });
    }

    /**
     * Check if a regulator user has active access to a tenant (used by auth gate).
     */
    public Promise<Boolean> hasActiveAccess(String userId, String tenantId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT EXISTS(SELECT 1 FROM regulator_access_grants " +
                     "WHERE user_id=? AND tenant_id=? AND revoked_at IS NULL AND valid_until > NOW())"
                 )) {
                ps.setString(1, userId); ps.setString(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
            }
        });
    }

    /**
     * List active grants for a user.
     */
    public Promise<List<AccessGrant>> listActiveGrants(String userId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT grant_id, user_id, tenant_id, mandate_ref, granted_at, valid_until, renewal_count " +
                     "FROM regulator_access_grants WHERE user_id=? AND revoked_at IS NULL AND valid_until > NOW() " +
                     "ORDER BY granted_at DESC"
                 )) {
                ps.setString(1, userId);
                List<AccessGrant> grants = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        grants.add(new AccessGrant(
                            rs.getString("grant_id"), rs.getString("user_id"),
                            rs.getString("tenant_id"), rs.getString("mandate_ref"),
                            rs.getTimestamp("granted_at").toInstant(),
                            rs.getTimestamp("valid_until").toInstant(),
                            "ACTIVE", rs.getInt("renewal_count")));
                    }
                }
                return grants;
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getTenantForGrant(String grantId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT tenant_id FROM regulator_access_grants WHERE grant_id=?"
             )) {
            ps.setString(1, grantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Grant not found: " + grantId);
                return rs.getString("tenant_id");
            }
        }
    }
}
