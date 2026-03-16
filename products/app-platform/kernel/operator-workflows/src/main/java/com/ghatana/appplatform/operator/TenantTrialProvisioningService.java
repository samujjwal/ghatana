package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Automate trial tenant provisioning and lifecycle management.
 *              Flow: trial request → auto-provision sandbox tenant with TRIAL license →
 *              30-day trial period tracking → conversion to PAID or auto-offboarding.
 *              Trial limitations: capped API quota, sandbox environment only.
 *              Conversion: upgrades license_type, lifts restrictions, sends welcome email.
 *              Automated offboarding of expired unconverted trials.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-O01-012: Tenant trial and provisioning automation
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS tenant_trials (
 *   trial_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL UNIQUE,
 *   requester_name TEXT NOT NULL,
 *   requester_email TEXT NOT NULL,
 *   started_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   expires_at     TIMESTAMPTZ NOT NULL,
 *   status         TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | CONVERTED | EXPIRED | OFFBOARDED
 *   converted_at   TIMESTAMPTZ,
 *   converted_to   TEXT,   -- paid license_type
 *   grace_expires_at TIMESTAMPTZ
 * );
 * </pre>
 */
public class TenantTrialProvisioningService {

    private static final int TRIAL_DAYS   = 30;
    private static final int GRACE_DAYS   = 7;
    // Trial tenants limited to 10% of normal quotas
    private static final String TRIAL_QUOTA_PROFILE = "TRIAL_SANDBOX";

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NamespaceProvisionPort {
        void provisionSandbox(String tenantId) throws Exception;
        void deprovision(String tenantId) throws Exception;
        void liftSandboxRestrictions(String tenantId) throws Exception;
    }

    public interface EmailPort {
        void sendWelcome(String toEmail, String name, String tenantId, String credentials) throws Exception;
        void sendTrialExpiring(String toEmail, String name, int daysLeft) throws Exception;
        void sendOffboarded(String toEmail, String name) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record TrialRecord(
        String trialId, String tenantId, String requesterEmail,
        java.time.Instant expiresAt, String status
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NamespaceProvisionPort namespace;
    private final EmailPort emailPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter trialsStartedCounter;
    private final Counter trialsConvertedCounter;
    private final Counter trialsOffboardedCounter;

    public TenantTrialProvisioningService(
        javax.sql.DataSource ds,
        NamespaceProvisionPort namespace,
        EmailPort emailPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                     = ds;
        this.namespace              = namespace;
        this.emailPort              = emailPort;
        this.audit                  = audit;
        this.executor               = executor;
        this.trialsStartedCounter   = Counter.builder("operator.trial.started").register(registry);
        this.trialsConvertedCounter = Counter.builder("operator.trial.converted").register(registry);
        this.trialsOffboardedCounter = Counter.builder("operator.trial.offboarded").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Auto-provision a new sandbox trial tenant. Provisions namespace, creates trial record,
     * sends welcome email. Returns tenantId.
     */
    public Promise<String> startTrial(String requesterName, String requesterEmail,
                                       String companyName, String jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            // Create tenant record (sandbox status)
            String tenantId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO operator_tenants (name, license_type, jurisdiction, status, config_profile) " +
                     "VALUES (?,?,?,?,?) RETURNING tenant_id"
                 )) {
                ps.setString(1, companyName + "_TRIAL"); ps.setString(2, "TRIAL");
                ps.setString(3, jurisdiction); ps.setString(4, "ONBOARDING");
                ps.setString(5, TRIAL_QUOTA_PROFILE);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); tenantId = rs.getString("tenant_id"); }
            }

            // Create trial record
            java.time.Instant expiresAt = java.time.Instant.now().plus(java.time.Duration.ofDays(TRIAL_DAYS));
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tenant_trials (tenant_id, requester_name, requester_email, expires_at) VALUES (?,?,?,?)"
                 )) {
                ps.setString(1, tenantId); ps.setString(2, requesterName);
                ps.setString(3, requesterEmail); ps.setTimestamp(4, Timestamp.from(expiresAt));
                ps.executeUpdate();
            }

            // Activate tenant
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE operator_tenants SET status='ACTIVE' WHERE tenant_id=?"
                 )) {
                ps.setString(1, tenantId); ps.executeUpdate();
            }

            namespace.provisionSandbox(tenantId);
            emailPort.sendWelcome(requesterEmail, requesterName, tenantId, "See portal for credentials");
            trialsStartedCounter.increment();
            audit.record("system", "TRIAL_STARTED", "tenantId=" + tenantId + " requester=" + requesterEmail);
            return tenantId;
        });
    }

    /** Convert a trial tenant to a paid plan. */
    public Promise<Void> convertToPaid(String tenantId, String targetLicenseType, String convertedBy) {
        return Promise.ofBlocking(executor, () -> {
            // Upgrade license
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE operator_tenants SET license_type=?, config_profile='STANDARD' WHERE tenant_id=?"
                 )) {
                ps.setString(1, targetLicenseType); ps.setString(2, tenantId); ps.executeUpdate();
            }
            // Mark trial converted
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE tenant_trials SET status='CONVERTED', converted_at=NOW(), converted_to=? WHERE tenant_id=?"
                 )) {
                ps.setString(1, targetLicenseType); ps.setString(2, tenantId); ps.executeUpdate();
            }
            namespace.liftSandboxRestrictions(tenantId);
            trialsConvertedCounter.increment();
            audit.record(convertedBy, "TRIAL_CONVERTED", "tenantId=" + tenantId + " to=" + targetLicenseType);
            return null;
        });
    }

    /**
     * Batch job: expire trials past their date. Sends grace period warnings.
     * Called daily by scheduler.
     */
    public Promise<Integer> processExpiries() {
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            // Find trials in grace that have expired grace period → offboard
            List<String[]> toOffboard = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT trial_id, tenant_id, requester_email, requester_name " +
                     "FROM tenant_trials WHERE status='ACTIVE' AND grace_expires_at < NOW()"
                 );
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    toOffboard.add(new String[]{rs.getString("trial_id"), rs.getString("tenant_id"),
                        rs.getString("requester_email"), rs.getString("requester_name")});
                }
            }
            for (String[] row : toOffboard) {
                offboardTrial(row[1], row[2], row[3]);
                count++;
            }

            // Activate grace for trials past expires_at
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE tenant_trials SET grace_expires_at = expires_at + INTERVAL '" + GRACE_DAYS + " days' " +
                     "WHERE status='ACTIVE' AND expires_at < NOW() AND grace_expires_at IS NULL"
                 )) {
                ps.executeUpdate();
            }
            return count;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void offboardTrial(String tenantId, String email, String name) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE tenant_trials SET status='OFFBOARDED' WHERE tenant_id=?"
             )) {
            ps.setString(1, tenantId); ps.executeUpdate();
        }
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE operator_tenants SET status='OFFBOARDED' WHERE tenant_id=?"
             )) {
            ps.setString(1, tenantId); ps.executeUpdate();
        }
        namespace.deprovision(tenantId);
        emailPort.sendOffboarded(email, name);
        trialsOffboardedCounter.increment();
        audit.record("system", "TRIAL_OFFBOARDED", "tenantId=" + tenantId);
    }
}
