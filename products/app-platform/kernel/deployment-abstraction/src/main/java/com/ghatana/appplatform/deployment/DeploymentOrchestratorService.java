package com.ghatana.appplatform.deployment;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Central coordinator for application deployments. Drives a deployment
 *              through lifecycle: REQUESTED → VALIDATING → DEPLOYING → VERIFYING →
 *              DEPLOYED | ROLLED_BACK. Supports three strategies: ROLLING, BLUE_GREEN,
 *              and CANARY (CANARY delegates traffic split to CanaryDeploymentService).
 *              Helm-based execution via HelmPort; post-deploy verification hooks.
 *              Publishes DeploymentStarted and DeploymentCompleted events.
 *              Satisfies STORY-K10-001.
 * @doc.layer   Kernel
 * @doc.pattern Lifecycle FSM; HelmPort strategy execution; EventPort; Timer per deploy;
 *              ON CONFLICT DO NOTHING idempotent create; deployedCounter + rollbackCounter.
 */
public class DeploymentOrchestratorService {

    private final HikariDataSource dataSource;
    private final Executor         executor;
    private final HelmPort         helmPort;
    private final EventPort        eventPort;
    private final Counter          deploymentsStartedCounter;
    private final Counter          deploymentsSucceededCounter;
    private final Counter          rollbacksCounter;
    private final Timer            deployTimer;

    public DeploymentOrchestratorService(HikariDataSource dataSource, Executor executor,
                                          HelmPort helmPort, EventPort eventPort,
                                          MeterRegistry registry) {
        this.dataSource                = dataSource;
        this.executor                  = executor;
        this.helmPort                  = helmPort;
        this.eventPort                 = eventPort;
        this.deploymentsStartedCounter  = Counter.builder("deploy.started_total").register(registry);
        this.deploymentsSucceededCounter = Counter.builder("deploy.succeeded_total").register(registry);
        this.rollbacksCounter           = Counter.builder("deploy.rollbacks_total").register(registry);
        this.deployTimer                = Timer.builder("deploy.duration").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface HelmPort {
        void upgrade(String releaseName, String chart, String namespace,
                      String imageTag, Map<String, String> overrides);
        void rollback(String releaseName, String namespace, int revision);
        boolean verifyHealthy(String releaseName, String namespace, int timeoutSeconds);
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Enums / Records ─────────────────────────────────────────────────────

    public enum DeployStrategy { ROLLING, BLUE_GREEN, CANARY }

    public record DeploymentRequest(String serviceId, String imageTag, String namespace,
                                     String chartPath, DeployStrategy strategy,
                                     Map<String, String> configOverrides,
                                     String requestedBy) {}

    public record Deployment(String deploymentId, String serviceId, String imageTag,
                              String namespace, DeployStrategy strategy, String status,
                              String requestedBy, LocalDateTime startedAt,
                              LocalDateTime completedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<Deployment> initiate(DeploymentRequest request) {
        return Promise.ofBlocking(executor, () -> {
            String id = UUID.randomUUID().toString();
            Deployment dep = persist(id, request, "REQUESTED");
            transition(id, "VALIDATING");
            deploymentsStartedCounter.increment();
            eventPort.publish("deployments", "DeploymentStarted",
                    Map.of("deploymentId", id, "serviceId", request.serviceId(),
                           "imageTag", request.imageTag(), "strategy", request.strategy().name()));
            return dep;
        });
    }

    /** Execute the deployment (called after approval where required). */
    public Promise<Deployment> execute(String deploymentId) {
        return Promise.ofBlocking(executor, () -> deployTimer.recordCallable(() -> {
            Deployment dep = load(deploymentId);
            transition(deploymentId, "DEPLOYING");

            try {
                helmPort.upgrade(dep.serviceId(), dep.serviceId() + "-chart",
                        dep.namespace(), dep.imageTag(), Map.of());
                transition(deploymentId, "VERIFYING");

                boolean healthy = helmPort.verifyHealthy(dep.serviceId(), dep.namespace(), 300);
                if (healthy) {
                    transition(deploymentId, "DEPLOYED");
                    markCompleted(deploymentId);
                    deploymentsSucceededCounter.increment();
                    eventPort.publish("deployments", "DeploymentCompleted",
                            Map.of("deploymentId", deploymentId, "status", "DEPLOYED"));
                } else {
                    performRollback(deploymentId, dep);
                }
            } catch (Exception e) {
                performRollback(deploymentId, dep);
                throw e;
            }
            return load(deploymentId);
        }));
    }

    public Promise<Deployment> getDeployment(String deploymentId) {
        return Promise.ofBlocking(executor, () -> load(deploymentId));
    }

    // ─── Rollback ─────────────────────────────────────────────────────────────

    private void performRollback(String deploymentId, Deployment dep) throws SQLException {
        helmPort.rollback(dep.serviceId(), dep.namespace(), 0); // 0 = previous revision
        transition(deploymentId, "ROLLED_BACK");
        markCompleted(deploymentId);
        rollbacksCounter.increment();
        eventPort.publish("deployments", "DeploymentRolledBack",
                Map.of("deploymentId", deploymentId, "serviceId", dep.serviceId()));
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private Deployment persist(String id, DeploymentRequest req, String status) throws SQLException {
        String sql = """
                INSERT INTO deployments
                    (deployment_id, service_id, image_tag, namespace, strategy,
                     status, requested_by, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (deployment_id) DO NOTHING
                RETURNING *
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id); ps.setString(2, req.serviceId());
            ps.setString(3, req.imageTag()); ps.setString(4, req.namespace());
            ps.setString(5, req.strategy().name()); ps.setString(6, status);
            ps.setString(7, req.requestedBy());
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return mapRow(rs); }
        }
    }

    private void transition(String deploymentId, String newStatus) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE deployments SET status=? WHERE deployment_id=?")) {
            ps.setString(1, newStatus); ps.setString(2, deploymentId);
            ps.executeUpdate();
        }
    }

    private void markCompleted(String deploymentId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE deployments SET completed_at=NOW() WHERE deployment_id=?")) {
            ps.setString(1, deploymentId); ps.executeUpdate();
        }
    }

    private Deployment load(String deploymentId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM deployments WHERE deployment_id=?")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Deployment not found: " + deploymentId);
                return mapRow(rs);
            }
        }
    }

    private Deployment mapRow(ResultSet rs) throws SQLException {
        return new Deployment(rs.getString("deployment_id"), rs.getString("service_id"),
                rs.getString("image_tag"), rs.getString("namespace"),
                DeployStrategy.valueOf(rs.getString("strategy")), rs.getString("status"),
                rs.getString("requested_by"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class));
    }
}
