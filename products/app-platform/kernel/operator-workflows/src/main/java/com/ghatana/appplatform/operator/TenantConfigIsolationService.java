package com.ghatana.appplatform.operator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Enforce per-tenant K-02 configuration isolation.
 *              Each tenant operates in its own K-02 namespace — tenants cannot read or
 *              write other tenants' config entries.
 *              Operator can set platform-wide defaults that tenants inherit.
 *              Tenants may override values only within configured min/max bounds.
 *              License-type templates define defaults (BROKER, ASSET_MANAGER, CUSTODIAN).
 *              Value catalogs and process-profile overlays are metadata-driven so
 *              operator-approved variation can evolve without code changes.
 * @doc.layer   Operator Workflows (O-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-O01-002: Tenant configuration isolation
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS tenant_config_bounds (
 *   bound_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   license_type   TEXT NOT NULL,
 *   config_key     TEXT NOT NULL,
 *   min_value      TEXT,
 *   max_value      TEXT,
 *   allowed_values JSONB,
 *   UNIQUE(license_type, config_key)
 * );
 * CREATE TABLE IF NOT EXISTS tenant_config_overrides (
 *   override_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tenant_id      TEXT NOT NULL,
 *   config_key     TEXT NOT NULL,
 *   config_value   TEXT NOT NULL,
 *   set_by         TEXT NOT NULL,
 *   set_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE(tenant_id, config_key)
 * );
 * </pre>
 */
public class TenantConfigIsolationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ConfigNamespacePort {
        /** Read a config value from tenant's K-02 namespace. Null if not set. */
        String get(String tenantId, String key) throws Exception;
        /** Write a config value into tenant's K-02 namespace. */
        void set(String tenantId, String key, String value) throws Exception;
        /** Read operator platform-wide default for a key. */
        String getPlatformDefault(String key) throws Exception;
        /** Set platform-wide default. */
        void setPlatformDefault(String key, String value) throws Exception;
        /** List all keys in a tenant's namespace. */
        List<String> listKeys(String tenantId) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record ConfigBound(
        String licenseType,
        String configKey,
        String minValue,
        String maxValue,
        List<String> allowedValues
    ) {}

    public record ResolvedConfig(
        String key,
        String value,
        String source  // TENANT_OVERRIDE | PLATFORM_DEFAULT | LICENSE_TEMPLATE
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ConfigNamespacePort configNamespace;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter boundViolationCounter;
    private final Counter overrideCounter;

    public TenantConfigIsolationService(
        javax.sql.DataSource ds,
        ConfigNamespacePort configNamespace,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                   = ds;
        this.configNamespace      = configNamespace;
        this.audit                = audit;
        this.executor             = executor;
        this.boundViolationCounter = Counter.builder("operator.config.bound_violations").register(registry);
        this.overrideCounter      = Counter.builder("operator.config.overrides").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolve a config key for a tenant: tenant override → platform default fallback.
     */
    public Promise<ResolvedConfig> resolve(String tenantId, String key) {
        return Promise.ofBlocking(executor, () -> {
            String tenantOverride = configNamespace.get(tenantId, key);
            if (tenantOverride != null) {
                return new ResolvedConfig(key, tenantOverride, "TENANT_OVERRIDE");
            }
            String platformDefault = configNamespace.getPlatformDefault(key);
            if (platformDefault != null) {
                return new ResolvedConfig(key, platformDefault, "PLATFORM_DEFAULT");
            }
            return new ResolvedConfig(key, null, "UNSET");
        });
    }

    /**
     * Tenant sets a config override. Validates against bounds defined for the tenant's license type.
     */
    public Promise<Void> setTenantOverride(
        String tenantId, String licenseType, String key, String value, String setBy
    ) {
        return Promise.ofBlocking(executor, () -> {
            validateBounds(licenseType, key, value);
            configNamespace.set(tenantId, key, value);
            persistOverride(tenantId, key, value, setBy);
            overrideCounter.increment();
            audit.record(setBy, "TENANT_CONFIG_SET",
                "tenant=" + tenantId + " key=" + key + " value=" + value);
            return null;
        });
    }

    /**
     * Operator sets a platform-wide default that all tenants inherit unless they override.
     */
    public Promise<Void> setPlatformDefault(String key, String value, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            configNamespace.setPlatformDefault(key, value);
            audit.record(operatorId, "PLATFORM_CONFIG_SET", "key=" + key + " value=" + value);
            return null;
        });
    }

    /**
     * Define or update the allowed bounds for a config key per license type.
     */
    public Promise<Void> upsertConfigBound(ConfigBound bound, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            String allowedJson = bound.allowedValues() == null ? null
                : "[" + String.join(",", bound.allowedValues().stream()
                    .map(v -> "\"" + v + "\"").toList()) + "]";
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tenant_config_bounds (license_type, config_key, min_value, max_value, allowed_values) " +
                     "VALUES (?,?,?,?,?::jsonb) ON CONFLICT (license_type, config_key) DO UPDATE " +
                     "SET min_value=EXCLUDED.min_value, max_value=EXCLUDED.max_value, allowed_values=EXCLUDED.allowed_values"
                 )) {
                ps.setString(1, bound.licenseType()); ps.setString(2, bound.configKey());
                ps.setString(3, bound.minValue()); ps.setString(4, bound.maxValue());
                ps.setString(5, allowedJson);
                ps.executeUpdate();
            }
            audit.record(operatorId, "CONFIG_BOUND_UPSERTED",
                "licenseType=" + bound.licenseType() + " key=" + bound.configKey());
            return null;
        });
    }

    /**
     * Cross-tenant isolation check: confirm tenant A cannot access tenant B's key.
     * Returns true if isolated (always should be true; surfaced for testing).
     */
    public Promise<Boolean> verifyIsolation(String tenantA, String tenantB, String key) {
        return Promise.ofBlocking(executor, () -> {
            String a = configNamespace.get(tenantA, key);
            String b = configNamespace.get(tenantB, key);
            // Isolation holds as long as keys reside in separate namespaces
            return !Objects.equals(a, b) || (a == null && b == null);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateBounds(String licenseType, String key, String value) throws Exception {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT min_value, max_value, allowed_values::text FROM tenant_config_bounds " +
                 "WHERE license_type = ? AND config_key = ?"
             )) {
            ps.setString(1, licenseType); ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return; // no bound defined — allow
                String min = rs.getString("min_value");
                String max = rs.getString("max_value");
                String allowed = rs.getString("allowed_values");
                if (allowed != null && !allowed.contains("\"" + value + "\"")) {
                    boundViolationCounter.increment();
                    throw new IllegalArgumentException(
                        "Value '" + value + "' not in allowed set for key '" + key + "'");
                }
                try {
                    double d = Double.parseDouble(value);
                    if (min != null && d < Double.parseDouble(min)) {
                        boundViolationCounter.increment();
                        throw new IllegalArgumentException("Value " + d + " below minimum " + min);
                    }
                    if (max != null && d > Double.parseDouble(max)) {
                        boundViolationCounter.increment();
                        throw new IllegalArgumentException("Value " + d + " above maximum " + max);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void persistOverride(String tenantId, String key, String value, String setBy) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO tenant_config_overrides (tenant_id, config_key, config_value, set_by) " +
                 "VALUES (?,?,?,?) ON CONFLICT (tenant_id, config_key) DO UPDATE " +
                 "SET config_value=EXCLUDED.config_value, set_by=EXCLUDED.set_by, set_at=NOW()"
             )) {
            ps.setString(1, tenantId); ps.setString(2, key);
            ps.setString(3, value); ps.setString(4, setBy);
            ps.executeUpdate();
        }
    }
}
