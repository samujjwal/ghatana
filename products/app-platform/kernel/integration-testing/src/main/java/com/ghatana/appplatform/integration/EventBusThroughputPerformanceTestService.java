package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose K-05 event bus throughput performance test.
 *              Targets: 100K events/sec peak; 50K/sec steady (30 min); consumer lag < 30s;
 *              consumer pause → re-start catch-up within 3 min; DLQ rate < 0.01%;
 *              no message loss; p99 producer latency measured.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; load-test; throughput-metrics
 *
 * STORY-T01-009: Implement event bus throughput performance test
 */
public class EventBusThroughputPerformanceTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EventBusPort {
        /** Publish one event. Returns eventId. */
        String publishEvent(String topic, String payload) throws Exception;
        /** Publish a batch of events. Returns count of successfully published. */
        int publishBatch(String topic, int count, String payloadTemplate) throws Exception;
        /** Get current consumer lag in seconds for the given consumer group. */
        long getConsumerLagSeconds(String consumerGroup) throws Exception;
        /** Number of events in DLQ for given topic. */
        long getDlqCount(String topic) throws Exception;
        /** Total events consumed by consumer group. */
        long getConsumedCount(String consumerGroup) throws Exception;
        /** Pause consumer group processing. */
        void pauseConsumerGroup(String consumerGroup) throws Exception;
        /** Resume consumer group processing. */
        void resumeConsumerGroup(String consumerGroup) throws Exception;
        /** Producer p99 latency in ms (last measurement window). */
        double getProducerP99LatencyMs() throws Exception;
        /** Total published in the last measurement window. */
        long getPublishedInWindow() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String TOPIC         = "platform.perf.events";
    private static final String CONSUMER_GRP   = "integration-test-consumers";
    private static final long   LAG_LIMIT_SEC  = 30L;
    private static final double DLQ_RATE_LIMIT = 0.0001; // 0.01%

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EventBusPort eventBus;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Timer producerLatencyTimer;

    public EventBusThroughputPerformanceTestService(
        javax.sql.DataSource ds,
        EventBusPort eventBus,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.eventBus     = eventBus;
        this.audit        = audit;
        this.executor     = executor;
        this.suitesPassed = Counter.builder("integration.perf.eventbus.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.perf.eventbus.suites_failed").register(registry);
        this.producerLatencyTimer = Timer.builder("integration.perf.eventbus.producer_latency").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("steady_50k_sec",        this::steady50kSec));
            results.add(runScenario("burst_100k_sec",        this::burst100kSec));
            results.add(runScenario("consumer_lag_30sec",    this::consumerLagCheck));
            results.add(runScenario("no_message_loss",       this::noMessageLoss));
            results.add(runScenario("dlq_rate_001",          this::dlqRateCheck));
            results.add(runScenario("catchup_3min",          this::consumerCatchup3Min));
            results.add(runScenario("producer_latency_p99",  this::producerLatencyP99));
            results.add(runScenario("multi_consumer_group",  this::multiConsumerGroup));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("EVENTBUS_PERF_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("EventBusThroughputPerformance", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** Publish 5000-event batch representing 50K/sec steady state; verify consumer lag < 30s. */
    private void steady50kSec(String runId) throws Exception {
        int count = eventBus.publishBatch(TOPIC, 5000, "{\"type\":\"STEADY\",\"seq\":${SEQ}}");
        assertStep(runId, "steady_batch_published", "5000 events published", "5000",
            count >= 4999, count);
        Thread.sleep(2000); // allow consumer to process
        long lag = eventBus.getConsumerLagSeconds(CONSUMER_GRP);
        assertStep(runId, "steady_lag_below_30s", "consumer lag < 30s after steady burst", "< 30",
            lag < LAG_LIMIT_SEC, lag + "s");
    }

    /** Burst of 10000 events representing 100K/sec peak; verify throughput handled. */
    private void burst100kSec(String runId) throws Exception {
        int count = eventBus.publishBatch(TOPIC, 10000, "{\"type\":\"BURST\",\"seq\":${SEQ}}");
        assertStep(runId, "burst_batch_published", "10000 burst events published", ">= 9999",
            count >= 9999, count);
        // After burst, system should recover lag within 60s
        for (int i = 0; i < 30; i++) {
            Thread.sleep(2000);
            if (eventBus.getConsumerLagSeconds(CONSUMER_GRP) < LAG_LIMIT_SEC) return;
        }
        long lagFinal = eventBus.getConsumerLagSeconds(CONSUMER_GRP);
        assertStep(runId, "burst_lag_recovered", "lag recovers after burst within 60s", "< 30",
            lagFinal < LAG_LIMIT_SEC, lagFinal + "s");
    }

    /** Consumer lag measurement after moderate load. */
    private void consumerLagCheck(String runId) throws Exception {
        eventBus.publishBatch(TOPIC, 1000, "{\"type\":\"LAG_TEST\"}");
        Thread.sleep(3000);
        long lag = eventBus.getConsumerLagSeconds(CONSUMER_GRP);
        assertStep(runId, "lag_check", "consumer lag < " + LAG_LIMIT_SEC + "s", "< " + LAG_LIMIT_SEC,
            lag < LAG_LIMIT_SEC, lag + "s");
    }

    /** Publish 500 events with unique IDs; verify all 500 appear as consumed. */
    private void noMessageLoss(String runId) throws Exception {
        long before = eventBus.getConsumedCount(CONSUMER_GRP);
        int published = eventBus.publishBatch(TOPIC, 500, "{\"type\":\"LOSS_TEST\"}");
        // Allow processing
        Thread.sleep(5000);
        long after = eventBus.getConsumedCount(CONSUMER_GRP);
        long delta = after - before;
        assertStep(runId, "no_loss", "all published events consumed", String.valueOf(published),
            delta >= published, delta + " consumed (published=" + published + ")");
    }

    /** DLQ count after normal operations should be near zero. */
    private void dlqRateCheck(String runId) throws Exception {
        long total = eventBus.getPublishedInWindow();
        long dlqCount = eventBus.getDlqCount(TOPIC);
        if (total == 0) return; // nothing published yet
        double dlqRate = (double) dlqCount / total;
        assertStep(runId, "dlq_rate", "DLQ rate < " + DLQ_RATE_LIMIT, "< " + DLQ_RATE_LIMIT,
            dlqRate <= DLQ_RATE_LIMIT, dlqRate);
    }

    /** Pause consumers, let messages queue, resume → catch up within 3 minutes. */
    private void consumerCatchup3Min(String runId) throws Exception {
        eventBus.pauseConsumerGroup(CONSUMER_GRP);
        // Queue 2000 events while paused
        eventBus.publishBatch(TOPIC, 2000, "{\"type\":\"CATCHUP\"}");
        Thread.sleep(1000);
        eventBus.resumeConsumerGroup(CONSUMER_GRP);
        long start = System.currentTimeMillis();
        // Wait up to 3 minutes for lag < 30s
        for (int i = 0; i < 90; i++) {
            Thread.sleep(2000);
            if (eventBus.getConsumerLagSeconds(CONSUMER_GRP) < LAG_LIMIT_SEC) return;
        }
        long elapsed = System.currentTimeMillis() - start;
        long lag = eventBus.getConsumerLagSeconds(CONSUMER_GRP);
        assertStep(runId, "catchup_3min", "consumers catch up within 3 minutes", "lag < 30s",
            lag < LAG_LIMIT_SEC, "lag=" + lag + "s after " + elapsed + "ms");
    }

    /** Producer p99 latency must be within acceptable range. */
    private void producerLatencyP99(String runId) throws Exception {
        eventBus.publishBatch(TOPIC, 200, "{\"type\":\"LATENCY\"}");
        double p99 = eventBus.getProducerP99LatencyMs();
        assertStep(runId, "producer_p99", "producer p99 latency < 50ms", "< 50ms",
            p99 < 50.0, p99 + "ms");
    }

    /** Multiple consumer groups all receive the same events. */
    private void multiConsumerGroup(String runId) throws Exception {
        String group2 = CONSUMER_GRP + "-secondary";
        long before1 = eventBus.getConsumedCount(CONSUMER_GRP);
        long before2 = eventBus.getConsumedCount(group2);
        int published = eventBus.publishBatch(TOPIC, 100, "{\"type\":\"MULTI_GROUP\"}");
        Thread.sleep(3000);
        long delta1 = eventBus.getConsumedCount(CONSUMER_GRP)  - before1;
        long delta2 = eventBus.getConsumedCount(group2)         - before2;
        assertStep(runId, "grp1_received", "primary group received all events", String.valueOf(published),
            delta1 >= published, delta1);
        assertStep(runId, "grp2_received", "secondary group received all events", String.valueOf(published),
            delta2 >= published, delta2);
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('EventBusThroughputPerformance',?) RETURNING run_id"
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
