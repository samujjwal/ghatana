package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Manages environment-level CPU, memory, and storage quota limits. Before any
 *              deployment is applied, the caller checks whether adding the requested
 *              resources would breach the environment quota. Breaching a quota blocks the
 *              deployment and logs a quota violation event. Satisfies STORY-K10-011.
 * @doc.layer   Kernel
 * @doc.pattern Quota guard; LiveResourcePort for current usage; upsertEnvironmentQuota;
 *              quota_violations_total Counter; quota_utilization Gauge per resource type.
 */
public class ResourceQuotaManagementService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final LiveResourcePort liveResourcePort;
    private final Counter          quotaCheckPassedCounter;
    private final Counter          quotaViolationsCounter;
    private final AtomicLong       cpuQuotaUtilization    = new AtomicLong(0);
    private final AtomicLong       memoryQuotaUtilization = new AtomicLong(0);
    private final AtomicLong       storageQuotaUtilization = new AtomicLong(0);

    public ResourceQuotaManagementService(HikariDataSource dataSource, Executor executor,
                                           LiveResourcePort liveResourcePort,
                                           MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.liveResourcePort        = liveResourcePort;
        this.quotaCheckPassedCounter  = Counter.builder("deployment.quota.checks_passed_total").register(registry);
        this.quotaViolationsCounter   = Counter.builder("deployment.quota.violations_total").register(registry);
        Gauge.builder("deployment.quota.cpu_utilization_millicores",    cpuQuotaUtilization,    AtomicLong::doubleValue).register(registry);
        Gauge.builder("deployment.quota.memory_utilization_mib",        memoryQuotaUtilization, AtomicLong::doubleValue).register(registry);
        Gauge.builder("deployment.quota.storage_utilization_gib",       storageQuotaUtilization,AtomicLong::doubleValue).register(registry);
    }

    // ─── Inner port ──────────────────────────────────────────────────────────

    public interface LiveResourcePort {
        ResourceUsage getCurrentUsage(String envId);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public record ResourceUsage(long cpuMillicores, long memoryMib, long storageGib) {}

    public record EnvironmentQuota(
        String quotaId, String envId,
        long cpuLimitMillicores, long memoryLimitMib, long storageLimitGib,
        Instant updatedAt
    ) {}

    public record QuotaCheckResult(
        boolean allowed, String envId,
        ResourceUsage currentUsage, ResourceUsage requestedResources, EnvironmentQuota quota,
        List<String> violations
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Upsert resource quotas for an environment.
     */
    public Promise<Void> upsertEnvironmentQuota(String envId, long cpuLimitMillicores,
                                                 long memoryLimitMib, long storageLimitGib) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO deployment_environment_quotas " +
                     "(quota_id, env_id, cpu_limit_millicores, memory_limit_mib, storage_limit_gib, updated_at) " +
                     "VALUES (gen_random_uuid()::text, ?, ?, ?, ?, NOW()) " +
                     "ON CONFLICT (env_id) DO UPDATE SET " +
                     "cpu_limit_millicores = EXCLUDED.cpu_limit_millicores, " +
                     "memory_limit_mib = EXCLUDED.memory_limit_mib, " +
                     "storage_limit_gib = EXCLUDED.storage_limit_gib, " +
                     "updated_at = NOW()")) {
                ps.setString(1, envId);
                ps.setLong(2, cpuLimitMillicores);
                ps.setLong(3, memoryLimitMib);
                ps.setLong(4, storageLimitGib);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * Check whether adding the requested resources would breach the environment quota.
     * Must be called by the deployment orchestrator before applying any deployment.
     */
    public Promise<QuotaCheckResult> checkQuotaForDeployment(String envId,
                                                              long additionalCpuMillicores,
                                                              long additionalMemoryMib,
                                                              long additionalStorageGib) {
        return Promise.ofBlocking(executor, () -> {
            EnvironmentQuota quota = fetchQuota(envId);
            ResourceUsage current = liveResourcePort.getCurrentUsage(envId);
            ResourceUsage requested = new ResourceUsage(
                additionalCpuMillicores, additionalMemoryMib, additionalStorageGib);

            cpuQuotaUtilization.set(current.cpuMillicores());
            memoryQuotaUtilization.set(current.memoryMib());
            storageQuotaUtilization.set(current.storageGib());

            List<String> violations = new ArrayList<>();

            if (current.cpuMillicores() + additionalCpuMillicores > quota.cpuLimitMillicores()) {
                violations.add(String.format("CPU quota exceeded: current=%dm + requested=%dm > limit=%dm",
                    current.cpuMillicores(), additionalCpuMillicores, quota.cpuLimitMillicores()));
            }
            if (current.memoryMib() + additionalMemoryMib > quota.memoryLimitMib()) {
                violations.add(String.format("Memory quota exceeded: current=%dMiB + requested=%dMiB > limit=%dMiB",
                    current.memoryMib(), additionalMemoryMib, quota.memoryLimitMib()));
            }
            if (current.storageGib() + additionalStorageGib > quota.storageLimitGib()) {
                violations.add(String.format("Storage quota exceeded: current=%dGiB + requested=%dGiB > limit=%dGiB",
                    current.storageGib(), additionalStorageGib, quota.storageLimitGib()));
            }

            boolean allowed = violations.isEmpty();
            if (allowed) {
                quotaCheckPassedCounter.increment();
            } else {
                quotaViolationsCounter.increment();
                recordViolation(envId, violations.toString());
            }

            return new QuotaCheckResult(allowed, envId, current, requested, quota, violations);
        });
    }

    /** Get the current quota for an environment. */
    public Promise<EnvironmentQuota> getQuota(String envId) {
        return Promise.ofBlocking(executor, () -> fetchQuota(envId));
    }

    /** List quota violation history for an environment. */
    public Promise<List<Map<String, Object>>> getViolationHistory(String envId, int limit) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT violation_id, env_id, description, occurred_at " +
                     "FROM deployment_quota_violations WHERE env_id = ? " +
                     "ORDER BY occurred_at DESC LIMIT ?")) {
                ps.setString(1, envId);
                ps.setInt(2, Math.min(limit, 200));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("violationId",  rs.getString("violation_id"));
                        row.put("envId",        rs.getString("env_id"));
                        row.put("description",  rs.getString("description"));
                        row.put("occurredAt",   rs.getTimestamp("occurred_at").toInstant().toString());
                        results.add(row);
                    }
                }
            }
            return results;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private EnvironmentQuota fetchQuota(String envId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT quota_id, env_id, cpu_limit_millicores, memory_limit_mib, " +
                 "storage_limit_gib, updated_at FROM deployment_environment_quotas WHERE env_id = ?")) {
            ps.setString(1, envId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("No quota configured for env: " + envId);
                return new EnvironmentQuota(
                    rs.getString("quota_id"), rs.getString("env_id"),
                    rs.getLong("cpu_limit_millicores"), rs.getLong("memory_limit_mib"),
                    rs.getLong("storage_limit_gib"), rs.getTimestamp("updated_at").toInstant()
                );
            }
        }
    }

    private void recordViolation(String envId, String description) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO deployment_quota_violations (violation_id, env_id, description, occurred_at) " +
                 "VALUES (gen_random_uuid()::text, ?, ?, NOW())")) {
            ps.setString(1, envId);
            ps.setString(2, description);
            ps.executeUpdate();
        }
    }
}
