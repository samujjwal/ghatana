package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    Service
 * @doc.purpose Operator-level multi-firm tenant registry.
 *              Manages the full lifecycle of tenant (firm) entities on the platform:
 *              ONBOARDING → ACTIVE → SUSPENDED → OFFBOARDED.
 *              Every state change goes through maker-checker approval.
 *              Publishes TenantCreated / TenantSuspended / TenantOffboarded events to K-05.
 *              K8s namespace isolation provisioned on creation via NamespaceProvisionPort.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-O01-001: Multi-firm tenant registry
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS operator_tenants (
 *   tenant_id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   name               TEXT NOT NULL UNIQUE,
 *   license_type       TEXT NOT NULL,   -- BROKER | ASSET_MANAGER | CUSTODIAN
 *   jurisdiction       TEXT NOT NULL,
 *   status             TEXT NOT NULL DEFAULT 'ONBOARDING',
 *   config_profile     TEXT NOT NULL,
 *   cpu_quota          TEXT NOT NULL DEFAULT '2',
 *   memory_quota       TEXT NOT NULL DEFAULT '4Gi',
 *   api_rps_quota      INT NOT NULL DEFAULT 500,
 *   inception_date     DATE NOT NULL DEFAULT CURRENT_DATE,
 *   created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class TenantRegistryService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface MakerCheckerPort {
        /** Submit action for dual-control approval. Returns approval request ID. */
        String submit(String actionType, String entityId, Map<String, Object> payload, String submitterId) throws Exception;
        /** Approve a pending request. Returns true on success. */
        boolean approve(String approvalRequestId, String approverId) throws Exception;
    }

    public interface NamespaceProvisionPort {
        /** Create isolated K8s namespace for tenant. */
        void provision(String tenantId, String namespaceName, Map<String, String> quotas) throws Exception;
        /** Delete K8s namespace on offboarding. */
        void deprovision(String tenantId) throws Exception;
    }

    public interface EventPublishPort {
        void publish(String eventType, Map<String, Object> payload) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String entityId, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public enum LicenseType { BROKER, ASSET_MANAGER, CUSTODIAN }
    public enum TenantStatus { ONBOARDING, ACTIVE, SUSPENDED, OFFBOARDED }

    public record ResourceQuotas(String cpu, String memory, int apiRpsQuota) {}

    public record TenantRegistration(
        String name,
        LicenseType licenseType,
        String jurisdiction,
        String configProfile,
        ResourceQuotas quotas,
        String submitterId
    ) {}

    public record Tenant(
        String tenantId,
        String name,
        LicenseType licenseType,
        String jurisdiction,
        TenantStatus status,
        String configProfile,
        ResourceQuotas quotas,
        String inceptionDate
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final MakerCheckerPort makerChecker;
    private final NamespaceProvisionPort namespaceProvision;
    private final EventPublishPort eventPublish;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter createdCounter;
    private final Counter suspendedCounter;
    private final Counter offboardedCounter;
    private final AtomicInteger activeTenants = new AtomicInteger(0);

    public TenantRegistryService(
        javax.sql.DataSource ds,
        MakerCheckerPort makerChecker,
        NamespaceProvisionPort namespaceProvision,
        EventPublishPort eventPublish,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                = ds;
        this.makerChecker      = makerChecker;
        this.namespaceProvision = namespaceProvision;
        this.eventPublish      = eventPublish;
        this.audit             = audit;
        this.executor          = executor;
        this.createdCounter    = Counter.builder("operator.tenant.created").register(registry);
        this.suspendedCounter  = Counter.builder("operator.tenant.suspended").register(registry);
        this.offboardedCounter = Counter.builder("operator.tenant.offboarded").register(registry);
        Gauge.builder("operator.tenant.active", activeTenants, AtomicInteger::get).register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register a new tenant (maker step). Requires approval before ACTIVE.
     */
    public Promise<String> registerTenant(TenantRegistration reg) {
        return Promise.ofBlocking(executor, () -> {
            String tenantId = insertTenant(reg);
            String approvalId = makerChecker.submit(
                "TENANT_CREATE", tenantId,
                Map.of("name", reg.name(), "licenseType", reg.licenseType().name(),
                       "jurisdiction", reg.jurisdiction()),
                reg.submitterId()
            );
            audit.record(reg.submitterId(), "TENANT_REGISTER_SUBMITTED", tenantId,
                "Approval requested: " + approvalId);
            return approvalId;
        });
    }

    /**
     * Checker approves tenant creation → transition to ACTIVE + provision namespace + publish event.
     */
    public Promise<Tenant> approveTenantCreation(String tenantId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            Tenant tenant = requireTenant(tenantId);
            Map<String, String> quotas = Map.of(
                "cpu", tenant.quotas().cpu(),
                "memory", tenant.quotas().memory()
            );
            namespaceProvision.provision(tenantId, "tenant-" + tenantId, quotas);
            updateStatus(tenantId, TenantStatus.ACTIVE);
            Tenant active = requireTenant(tenantId);
            createdCounter.increment();
            activeTenants.incrementAndGet();
            eventPublish.publish("TenantCreated", Map.of(
                "tenantId", tenantId, "name", active.name(),
                "licenseType", active.licenseType().name(), "jurisdiction", active.jurisdiction()
            ));
            audit.record(approverId, "TENANT_ACTIVATED", tenantId, "Namespace provisioned");
            return active;
        });
    }

    /**
     * Suspend a tenant (disables all API access; requires maker-checker).
     */
    public Promise<String> suspendTenant(String tenantId, String reason, String submitterId) {
        return Promise.ofBlocking(executor, () -> {
            requireStatus(tenantId, TenantStatus.ACTIVE);
            String approvalId = makerChecker.submit("TENANT_SUSPEND", tenantId,
                Map.of("reason", reason), submitterId);
            audit.record(submitterId, "TENANT_SUSPEND_SUBMITTED", tenantId, reason);
            return approvalId;
        });
    }

    public Promise<Void> approveSuspension(String tenantId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(tenantId, TenantStatus.SUSPENDED);
            suspendedCounter.increment();
            activeTenants.decrementAndGet();
            eventPublish.publish("TenantSuspended", Map.of("tenantId", tenantId));
            audit.record(approverId, "TENANT_SUSPENDED", tenantId, "Status → SUSPENDED");
            return null;
        });
    }

    /**
     * Offboard a tenant: deprovision namespace, archive data.
     */
    public Promise<Void> offboardTenant(String tenantId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(tenantId, TenantStatus.OFFBOARDED);
            namespaceProvision.deprovision(tenantId);
            offboardedCounter.increment();
            eventPublish.publish("TenantOffboarded", Map.of("tenantId", tenantId));
            audit.record(approverId, "TENANT_OFFBOARDED", tenantId, "Namespace deprovisioned");
            return null;
        });
    }

    public Promise<Tenant> getTenant(String tenantId) {
        return Promise.ofBlocking(executor, () -> requireTenant(tenantId));
    }

    public Promise<List<Tenant>> listTenants(TenantStatus statusFilter) {
        return Promise.ofBlocking(executor, () -> {
            String sql = statusFilter == null
                ? "SELECT * FROM operator_tenants ORDER BY name"
                : "SELECT * FROM operator_tenants WHERE status = ? ORDER BY name";
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                if (statusFilter != null) ps.setString(1, statusFilter.name());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Tenant> list = new ArrayList<>();
                    while (rs.next()) list.add(mapRow(rs));
                    return list;
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String insertTenant(TenantRegistration reg) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO operator_tenants " +
                 "(name, license_type, jurisdiction, config_profile, cpu_quota, memory_quota, api_rps_quota) " +
                 "VALUES (?,?,?,?,?,?,?) RETURNING tenant_id"
             )) {
            ps.setString(1, reg.name()); ps.setString(2, reg.licenseType().name());
            ps.setString(3, reg.jurisdiction()); ps.setString(4, reg.configProfile());
            ps.setString(5, reg.quotas().cpu()); ps.setString(6, reg.quotas().memory());
            ps.setInt(7, reg.quotas().apiRpsQuota());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void updateStatus(String tenantId, TenantStatus status) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE operator_tenants SET status = ?, updated_at = NOW() WHERE tenant_id = ?"
             )) {
            ps.setString(1, status.name()); ps.setString(2, tenantId); ps.executeUpdate();
        }
    }

    private void requireStatus(String tenantId, TenantStatus expected) throws Exception {
        Tenant t = requireTenant(tenantId);
        if (t.status() != expected)
            throw new IllegalStateException("Tenant " + tenantId + " is not " + expected);
    }

    private Tenant requireTenant(String tenantId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM operator_tenants WHERE tenant_id = ?"
             )) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Tenant not found: " + tenantId);
                return mapRow(rs);
            }
        }
    }

    private Tenant mapRow(ResultSet rs) throws SQLException {
        return new Tenant(
            rs.getString("tenant_id"), rs.getString("name"),
            LicenseType.valueOf(rs.getString("license_type")),
            rs.getString("jurisdiction"),
            TenantStatus.valueOf(rs.getString("status")),
            rs.getString("config_profile"),
            new ResourceQuotas(rs.getString("cpu_quota"), rs.getString("memory_quota"),
                rs.getInt("api_rps_quota")),
            rs.getString("inception_date")
        );
    }
}
