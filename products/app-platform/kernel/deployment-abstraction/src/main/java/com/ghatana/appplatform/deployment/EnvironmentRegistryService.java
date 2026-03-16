package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type    DomainService
 * @doc.purpose Registry for deployment environments (dev, staging, production, dr-prod,
 *              etc.). Each environment record captures: K-02 config_profile_id, namespace,
 *              cluster, resource_quotas (JSONB), and provisioning status. Infrastructure
 *              provisioning is delegated to an InfraProvisionPort (Terraform/Pulumi).
 *              ON CONFLICT (name, namespace) DO NOTHING ensures idempotent registration.
 *              Satisfies STORY-K10-004.
 * @doc.layer   Kernel
 * @doc.pattern Environment registry; InfraProvisionPort; K-02 config_profile link;
 *              ON CONFLICT DO NOTHING; environmentsGauge; Counter for provisions.
 */
public class EnvironmentRegistryService {

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final InfraProvisionPort infraPort;
    private final EventPort          eventPort;
    private final Counter            environmentsProvisionedCounter;
    private final AtomicInteger      activeEnvironmentsCount = new AtomicInteger(0);

    public EnvironmentRegistryService(HikariDataSource dataSource, Executor executor,
                                       InfraProvisionPort infraPort, EventPort eventPort,
                                       MeterRegistry registry) {
        this.dataSource                    = dataSource;
        this.executor                      = executor;
        this.infraPort                     = infraPort;
        this.eventPort                     = eventPort;
        this.environmentsProvisionedCounter = Counter.builder("deploy.environments.provisioned_total")
                .register(registry);
        Gauge.builder("deploy.environments.active", activeEnvironmentsCount, AtomicInteger::get)
             .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface InfraProvisionPort {
        String provision(String templateId, Map<String, String> variables);
        ProvisionStatus getStatus(String provisionJobId);

        enum ProvisionStatus { PENDING, RUNNING, COMPLETED, FAILED }
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record EnvironmentRecord(String envId, String name, String namespace, String cluster,
                                     String tier, String configProfileId,
                                     String resourceQuotasJson, String provisionStatus,
                                     String provisionJobId, LocalDateTime registeredAt) {}

    public record ProvisionRequest(String templateId, String name, String namespace,
                                    String cluster, String tier, String configProfileId,
                                    Map<String, String> resourceQuotas,
                                    Map<String, String> extraVars) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /** Register an already-existing environment without provisioning. */
    public Promise<EnvironmentRecord> register(String name, String namespace, String cluster,
                                                String tier, String configProfileId,
                                                Map<String, String> resourceQuotas) {
        return Promise.ofBlocking(executor, () -> {
            EnvironmentRecord env = insert(name, namespace, cluster, tier,
                    configProfileId, toJson(resourceQuotas), "REGISTERED", null);
            activeEnvironmentsCount.incrementAndGet();
            eventPort.publish("deployments", "EnvironmentRegistered",
                    Map.of("name", name, "namespace", namespace, "cluster", cluster));
            return env;
        });
    }

    /** Register + provision via InfraProvisionPort. Returns immediately with PENDING status. */
    public Promise<EnvironmentRecord> provision(ProvisionRequest request) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, String> vars = new HashMap<>(request.extraVars());
            vars.put("name", request.name());
            vars.put("namespace", request.namespace());
            vars.put("cluster", request.cluster());
            vars.putAll(request.resourceQuotas());

            String jobId = infraPort.provision(request.templateId(), vars);
            EnvironmentRecord env = insert(request.name(), request.namespace(), request.cluster(),
                    request.tier(), request.configProfileId(),
                    toJson(request.resourceQuotas()), "PROVISIONING", jobId);
            environmentsProvisionedCounter.increment();
            activeEnvironmentsCount.incrementAndGet();
            eventPort.publish("deployments", "EnvironmentProvisioningStarted",
                    Map.of("name", request.name(), "jobId", jobId));
            return env;
        });
    }

    /** Check and update provisioning status from InfraProvisionPort. */
    public Promise<EnvironmentRecord> syncProvisionStatus(String envId) {
        return Promise.ofBlocking(executor, () -> {
            EnvironmentRecord env = loadById(envId);
            if (env.provisionJobId() == null) return env;

            InfraProvisionPort.ProvisionStatus status = infraPort.getStatus(env.provisionJobId());
            String newStatus = switch (status) {
                case COMPLETED -> "ACTIVE";
                case FAILED -> "PROVISION_FAILED";
                default -> env.provisionStatus();
            };
            updateStatus(envId, newStatus);

            if (status == InfraProvisionPort.ProvisionStatus.COMPLETED) {
                eventPort.publish("deployments", "EnvironmentProvisioned",
                        Map.of("envId", envId, "name", env.name()));
            } else if (status == InfraProvisionPort.ProvisionStatus.FAILED) {
                activeEnvironmentsCount.decrementAndGet();
                eventPort.publish("deployments", "EnvironmentProvisionFailed",
                        Map.of("envId", envId, "name", env.name()));
            }
            return loadById(envId);
        });
    }

    public Promise<Optional<EnvironmentRecord>> findByName(String name, String namespace) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM environments WHERE name=? AND namespace=?")) {
                ps.setString(1, name); ps.setString(2, namespace);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            }
        });
    }

    public Promise<List<EnvironmentRecord>> listByTier(String tier) {
        return Promise.ofBlocking(executor, () -> {
            List<EnvironmentRecord> list = new ArrayList<>();
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT * FROM environments WHERE tier=? ORDER BY name")) {
                ps.setString(1, tier);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapRow(rs));
                }
            }
            return list;
        });
    }

    // ─── Persistence helpers ──────────────────────────────────────────────────

    private EnvironmentRecord insert(String name, String namespace, String cluster, String tier,
                                      String configProfileId, String quotasJson,
                                      String status, String jobId) throws SQLException {
        String sql = """
                INSERT INTO environments
                    (env_id, name, namespace, cluster, tier, config_profile_id,
                     resource_quotas, provision_status, provision_job_id, registered_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::jsonb, ?, ?, NOW())
                ON CONFLICT (name, namespace) DO NOTHING
                RETURNING *
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name); ps.setString(2, namespace);
            ps.setString(3, cluster); ps.setString(4, tier);
            ps.setString(5, configProfileId); ps.setString(6, quotasJson);
            ps.setString(7, status); ps.setString(8, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                // Row already existed — load it
                return findByName(name, namespace).get().orElseThrow();
            }
        }
    }

    private void updateStatus(String envId, String status) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE environments SET provision_status=? WHERE env_id=?")) {
            ps.setString(1, status); ps.setString(2, envId); ps.executeUpdate();
        }
    }

    private EnvironmentRecord loadById(String envId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM environments WHERE env_id=?")) {
            ps.setString(1, envId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Env not found: " + envId);
                return mapRow(rs);
            }
        }
    }

    private EnvironmentRecord mapRow(ResultSet rs) throws SQLException {
        return new EnvironmentRecord(rs.getString("env_id"), rs.getString("name"),
                rs.getString("namespace"), rs.getString("cluster"), rs.getString("tier"),
                rs.getString("config_profile_id"), rs.getString("resource_quotas"),
                rs.getString("provision_status"), rs.getString("provision_job_id"),
                rs.getObject("registered_at", LocalDateTime.class));
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append('"').append(k).append("\":\"").append(v).append("\","));
        sb.setLength(sb.length() - 1); sb.append('}');
        return sb.toString();
    }
}
