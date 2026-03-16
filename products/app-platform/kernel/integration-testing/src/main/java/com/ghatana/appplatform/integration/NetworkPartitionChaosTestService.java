package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Network partition chaos test service (T-02).
 *              Injects network partitions (split-brain, latency, packet-drop) between
 *              core services and validates: circuit breaker behavior; event delivery guarantees;
 *              no split-brain data divergence; recovery after partition healed.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering; resilience
 *
 * STORY-T02-002: Implement network partition chaos tests
 */
public class NetworkPartitionChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NetworkChaosPort {
        /** Inject full network partition between two services. Returns partitionId. */
        String partitionServices(String serviceA, String serviceB) throws Exception;
        /** Inject packet loss (0–100%) between services. Returns partitionId. */
        String injectPacketLoss(String serviceA, String serviceB, int lossPercent) throws Exception;
        /** Inject network latency (ms) between services. Returns partitionId. */
        String injectLatency(String serviceA, String serviceB, long latencyMs) throws Exception;
        /** Heal the partition/chaos. */
        void heal(String partitionId) throws Exception;
        /** Check if two services can communicate. */
        boolean canCommunicate(String serviceA, String serviceB) throws Exception;
    }

    public interface ResiliencePort {
        boolean isCircuitBreakerOpen(String service) throws Exception;
        boolean hasRetried(String service, String operationType, int minTimes) throws Exception;
        boolean hasFallbackActivated(String service) throws Exception;
    }

    public interface DataConsistencyPort {
        /** Check that OMS and EMS have the same count of orders (no split-brain divergence). */
        boolean isDataConsistent(String entityType) throws Exception;
        /** Check that events queued during partition were delivered after heal. */
        long getPendingEventsAfterHeal(String topic) throws Exception;
    }

    public interface OrderPort {
        String submitOrder(String clientId, String symbol, int qty) throws Exception;
        String getOrderStatus(String orderId) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NetworkChaosPort networkChaos;
    private final ResiliencePort resilience;
    private final DataConsistencyPort dataConsistency;
    private final OrderPort orderPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public NetworkPartitionChaosTestService(
        javax.sql.DataSource ds,
        NetworkChaosPort networkChaos,
        ResiliencePort resilience,
        DataConsistencyPort dataConsistency,
        OrderPort orderPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.networkChaos     = networkChaos;
        this.resilience       = resilience;
        this.dataConsistency  = dataConsistency;
        this.orderPort        = orderPort;
        this.audit            = audit;
        this.executor         = executor;
        this.suitesPassed     = Counter.builder("integration.chaos.network.suites_passed").register(registry);
        this.suitesFailed     = Counter.builder("integration.chaos.network.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("oms_ems_partition",        this::omsEmsPartition));
            results.add(runScenario("event_bus_partition",      this::eventBusPartition));
            results.add(runScenario("packet_loss_50pct",        this::packetLoss50pct));
            results.add(runScenario("high_latency_injection",   this::highLatencyInjection));
            results.add(runScenario("no_split_brain",           this::noSplitBrain));
            results.add(runScenario("events_delivered_on_heal", this::eventsDeliveredOnHeal));
            results.add(runScenario("circuit_breaker_on_partition", this::circuitBreakerOnPartition));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("NETWORK_PARTITION_CHAOS_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("NetworkPartitionChaos", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    private void omsEmsPartition(String runId) throws Exception {
        String partId = networkChaos.partitionServices("oms", "ems");
        try {
            Thread.sleep(500);
            boolean canComm = networkChaos.canCommunicate("oms", "ems");
            assertStep(runId, "partition_active", "OMS→EMS communication blocked", "false", !canComm, canComm);
            boolean fallback = resilience.hasFallbackActivated("oms");
            assertStep(runId, "fallback_activated", "OMS activates fallback during partition", "true", fallback, fallback);
        } finally { networkChaos.heal(partId); }
        // After heal, communication restored
        Thread.sleep(1000);
        boolean healed = networkChaos.canCommunicate("oms", "ems");
        assertStep(runId, "partition_healed", "OMS→EMS communication restored after heal", "true", healed, healed);
    }

    private void eventBusPartition(String runId) throws Exception {
        String partId = networkChaos.partitionServices("oms", "event-bus");
        try {
            Thread.sleep(500);
            // OMS should still accept orders (local operation) even if event bus is unreachable
            String orderId = orderPort.submitOrder("NET-CHAOS-001", "NABIL", 100);
            assertStep(runId, "order_accepted_during_partition", "order accepted despite event bus partition", "not null",
                orderId != null, orderId);
        } finally { networkChaos.heal(partId); }
    }

    /** 50% packet loss: circuit breaker should eventually open or retries should succeed. */
    private void packetLoss50pct(String runId) throws Exception {
        String partId = networkChaos.injectPacketLoss("oms", "ems", 50);
        try {
            Thread.sleep(2000); // allow retries
            boolean retried = resilience.hasRetried("oms", "SEND_ORDER_TO_EMS", 2);
            assertStep(runId, "retry_on_packet_loss", "OMS retries at least 2x under 50% packet loss", "true",
                retried, retried);
        } finally { networkChaos.heal(partId); }
    }

    /** 500ms latency injection: circuit breaker or timeout should activate. */
    private void highLatencyInjection(String runId) throws Exception {
        String partId = networkChaos.injectLatency("oms", "ems", 500);
        try {
            Thread.sleep(2000);
            boolean cbOpen = resilience.isCircuitBreakerOpen("ems");
            boolean fallback = resilience.hasFallbackActivated("oms");
            assertStep(runId, "cb_or_fallback_on_latency", "circuit breaker or fallback activates under 500ms latency", "true",
                cbOpen || fallback, "cb=" + cbOpen + " fallback=" + fallback);
        } finally { networkChaos.heal(partId); }
    }

    /** After partition, data consistency: OMS and EMS agree on order counts. */
    private void noSplitBrain(String runId) throws Exception {
        String partId = networkChaos.partitionServices("oms", "ems");
        orderPort.submitOrder("NET-CHAOS-002", "NTC", 50);
        networkChaos.heal(partId);
        Thread.sleep(2000); // allow replication/reconciliation
        boolean consistent = dataConsistency.isDataConsistent("orders");
        assertStep(runId, "no_split_brain", "no split-brain data divergence after partition", "true", consistent, consistent);
    }

    /** Events queued during partition are delivered after heal (at-least-once). */
    private void eventsDeliveredOnHeal(String runId) throws Exception {
        String partId = networkChaos.partitionServices("oms", "event-bus");
        orderPort.submitOrder("NET-CHAOS-003", "NLIC", 200); // generates events
        networkChaos.heal(partId);
        Thread.sleep(3000); // allow event delivery
        long pending = dataConsistency.getPendingEventsAfterHeal("platform.orders.created");
        assertStep(runId, "events_delivered", "pending events delivered after partition healed", "0",
            pending == 0, pending + " still pending");
    }

    /** Full partition between OMS and EMS → circuit breaker opens on OMS side. */
    private void circuitBreakerOnPartition(String runId) throws Exception {
        String partId = networkChaos.partitionServices("oms", "ems");
        try {
            Thread.sleep(3000); // allow CB to detect failures
            boolean cbOpen = resilience.isCircuitBreakerOpen("ems");
            assertStep(runId, "cb_opens_on_partition", "circuit breaker opens after sustained partition", "true",
                cbOpen, cbOpen);
        } finally { networkChaos.heal(partId); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            fn.accept(runId);
            markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) {
            return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return new ScenarioResult(name, false, ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('NetworkPartitionChaos',?) RETURNING run_id"
             )) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET status=? WHERE run_id=?"
             )) { ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
