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
 * @doc.purpose Manages progressive canary traffic promotion: 5% → 25% → 50% → 100%.
 *              Each stage advance is gated by promotion criteria (error rate, p99 latency,
 *              absence of critical K-06 alerts). Promotion can be automatic (when criteria
 *              met) or manual. Auto-rollback is triggered when any criteria breach is
 *              detected at the current stage. Traffic split is applied via TrafficSplitPort
 *              (Istio VirtualService or equivalent).  Satisfies STORY-K10-002.
 * @doc.layer   Kernel
 * @doc.pattern Staged traffic promotion; criteria evaluation; TrafficSplitPort; EventPort;
 *              auto-rollback; canary_active Gauge; promotionsCounter.
 */
public class CanaryDeploymentService {

    /** Canary traffic progression stages in percentage. Final stage = full cutover. */
    private static final int[] STAGES = {5, 25, 50, 100};

    private final HikariDataSource   dataSource;
    private final Executor           executor;
    private final TrafficSplitPort   trafficSplitPort;
    private final MetricsPort        metricsPort;
    private final EventPort          eventPort;
    private final Counter            promotionsCounter;
    private final Counter            rollbacksCounter;
    private final AtomicInteger      activeCanaries = new AtomicInteger(0);

    public CanaryDeploymentService(HikariDataSource dataSource, Executor executor,
                                    TrafficSplitPort trafficSplitPort, MetricsPort metricsPort,
                                    EventPort eventPort, MeterRegistry registry) {
        this.dataSource      = dataSource;
        this.executor        = executor;
        this.trafficSplitPort = trafficSplitPort;
        this.metricsPort     = metricsPort;
        this.eventPort       = eventPort;
        this.promotionsCounter = Counter.builder("canary.promotions_total").register(registry);
        this.rollbacksCounter  = Counter.builder("canary.rollbacks_total").register(registry);
        Gauge.builder("canary.active", activeCanaries, AtomicInteger::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface TrafficSplitPort {
        void setSplit(String serviceId, String namespace, int canaryPct);
    }

    public interface MetricsPort {
        double errorRatePct(String serviceId, String version, int windowMinutes);
        double latencyP99Ms(String serviceId, String version, int windowMinutes);
        boolean hasCriticalAlerts(String serviceId);
    }

    public interface EventPort {
        void publish(String topic, String eventType, Object payload);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record CanaryConfig(double maxErrorRatePct, double maxLatencyP99Ms,
                                int evalWindowMinutes) {}

    public record CanaryState(String canaryId, String deploymentId, String serviceId,
                               String namespace, String canaryVersion, int currentStagePct,
                               String status, LocalDateTime startedAt) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<CanaryState> startCanary(String deploymentId, String serviceId,
                                             String namespace, String canaryVersion,
                                             CanaryConfig config) {
        return Promise.ofBlocking(executor, () -> {
            String canaryId = UUID.randomUUID().toString();
            int initialPct = STAGES[0];
            trafficSplitPort.setSplit(serviceId, namespace, initialPct);
            CanaryState state = persist(canaryId, deploymentId, serviceId, namespace,
                    canaryVersion, initialPct, "ACTIVE");
            activeCanaries.incrementAndGet();
            persistConfig(canaryId, config);
            eventPort.publish("deployments", "CanaryStarted",
                    Map.of("canaryId", canaryId, "serviceId", serviceId,
                           "initialPct", initialPct, "canaryVersion", canaryVersion));
            return state;
        });
    }

    /** Evaluate promotion criteria and advance stage, or rollback on breach. */
    public Promise<CanaryState> evaluate(String canaryId) {
        return Promise.ofBlocking(executor, () -> {
            CanaryState state = loadState(canaryId);
            CanaryConfig config = loadConfig(canaryId);

            double errorRate = metricsPort.errorRatePct(state.serviceId(),
                    state.canaryVersion(), config.evalWindowMinutes());
            double p99 = metricsPort.latencyP99Ms(state.serviceId(),
                    state.canaryVersion(), config.evalWindowMinutes());
            boolean criticalAlerts = metricsPort.hasCriticalAlerts(state.serviceId());

            boolean breached = errorRate > config.maxErrorRatePct()
                    || p99 > config.maxLatencyP99Ms() || criticalAlerts;

            if (breached) {
                return rollback(canaryId, state,
                        "Criteria breach: errorRate=" + errorRate + " p99=" + p99
                        + " criticalAlerts=" + criticalAlerts);
            }

            return promote(canaryId, state);
        });
    }

    /** Manual promotion to next stage, bypassing criteria check. */
    public Promise<CanaryState> promoteManually(String canaryId) {
        return Promise.ofBlocking(executor, () -> {
            CanaryState state = loadState(canaryId);
            return promote(canaryId, state);
        });
    }

    // ─── Stage management ─────────────────────────────────────────────────────

    private CanaryState promote(String canaryId, CanaryState state) throws SQLException {
        int nextPct = nextStage(state.currentStagePct());
        if (nextPct == state.currentStagePct()) {
            // Already at 100%, finalise
            return finalise(canaryId, state);
        }
        trafficSplitPort.setSplit(state.serviceId(), state.namespace(), nextPct);
        updateStage(canaryId, nextPct);
        promotionsCounter.increment();
        eventPort.publish("deployments", "CanaryPromoted",
                Map.of("canaryId", canaryId, "newPct", nextPct));
        if (nextPct == 100) return finalise(canaryId, state);
        return loadState(canaryId);
    }

    private CanaryState finalise(String canaryId, CanaryState state) throws SQLException {
        trafficSplitPort.setSplit(state.serviceId(), state.namespace(), 100);
        updateStatus(canaryId, "COMPLETED");
        activeCanaries.decrementAndGet();
        eventPort.publish("deployments", "CanaryCompleted",
                Map.of("canaryId", canaryId, "serviceId", state.serviceId()));
        return loadState(canaryId);
    }

    private CanaryState rollback(String canaryId, CanaryState state,
                                  String reason) throws SQLException {
        trafficSplitPort.setSplit(state.serviceId(), state.namespace(), 0);
        updateStatus(canaryId, "ROLLED_BACK");
        activeCanaries.decrementAndGet();
        rollbacksCounter.increment();
        eventPort.publish("deployments", "CanaryRolledBack",
                Map.of("canaryId", canaryId, "reason", reason));
        return loadState(canaryId);
    }

    private int nextStage(int current) {
        for (int stage : STAGES) { if (stage > current) return stage; }
        return current;
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private CanaryState persist(String id, String deploymentId, String serviceId,
                                 String namespace, String version, int pct,
                                 String status) throws SQLException {
        String sql = """
                INSERT INTO canary_deployments
                    (canary_id, deployment_id, service_id, namespace, canary_version,
                     current_stage_pct, status, started_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW()) RETURNING *
                """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id); ps.setString(2, deploymentId);
            ps.setString(3, serviceId); ps.setString(4, namespace);
            ps.setString(5, version); ps.setInt(6, pct); ps.setString(7, status);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return mapRow(rs); }
        }
    }

    private void persistConfig(String canaryId, CanaryConfig config) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO canary_configs (canary_id, max_error_rate_pct, " +
                     "max_latency_p99_ms, eval_window_minutes) VALUES (?,?,?,?)")) {
            ps.setString(1, canaryId); ps.setDouble(2, config.maxErrorRatePct());
            ps.setDouble(3, config.maxLatencyP99Ms()); ps.setInt(4, config.evalWindowMinutes());
            ps.executeUpdate();
        }
    }

    private void updateStage(String id, int pct) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE canary_deployments SET current_stage_pct=? WHERE canary_id=?")) {
            ps.setInt(1, pct); ps.setString(2, id); ps.executeUpdate();
        }
    }

    private void updateStatus(String id, String status) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE canary_deployments SET status=? WHERE canary_id=?")) {
            ps.setString(1, status); ps.setString(2, id); ps.executeUpdate();
        }
    }

    private CanaryState loadState(String canaryId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM canary_deployments WHERE canary_id=?")) {
            ps.setString(1, canaryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Canary not found: " + canaryId);
                return mapRow(rs);
            }
        }
    }

    private CanaryConfig loadConfig(String canaryId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM canary_configs WHERE canary_id=?")) {
            ps.setString(1, canaryId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new CanaryConfig(rs.getDouble("max_error_rate_pct"),
                        rs.getDouble("max_latency_p99_ms"), rs.getInt("eval_window_minutes"));
            }
        }
    }

    private CanaryState mapRow(ResultSet rs) throws SQLException {
        return new CanaryState(rs.getString("canary_id"), rs.getString("deployment_id"),
                rs.getString("service_id"), rs.getString("namespace"),
                rs.getString("canary_version"), rs.getInt("current_stage_pct"),
                rs.getString("status"), rs.getObject("started_at", LocalDateTime.class));
    }
}
