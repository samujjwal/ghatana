package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Message poison-pill chaos test service (T-02).
 *              Scenarios: malformed JSON → DLQ; business logic reject → DLQ;
 *              exception retry exhausted → DLQ + alert; one poison pill does not block
 *              other messages; K-19 DLQ routing; K-06 alert on DLQ spike;
 *              consumer continues after poison pill; batch with poison pill.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering
 *
 * STORY-T02-006: Implement message poison pill chaos tests
 */
public class MessagePoisonPillChaosTestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EventBusPort {
        /** Publish a raw bytes payload (for malformed injection). Returns msgId. */
        String publishRaw(String topic, byte[] payload) throws Exception;
        /** Publish a structured event. Returns msgId. */
        String publishEvent(String topic, String eventType, String body) throws Exception;
        /** Publish a batch: some valid, some with poison. Returns list of msgIds. */
        List<String> publishBatch(String topic, List<String> bodies) throws Exception;
        /** Get the DLQ entry for a given msgId. May return null if not yet in DLQ. */
        DlqEntry getDlqEntry(String msgId) throws Exception;
        /** Number of messages consumed successfully (excluding DLQ). */
        long getConsumedCount(String topic) throws Exception;
    }

    public interface AlertPort {
        boolean hasK06DlqAlert(String topic) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    public record DlqEntry(String msgId, String reason, int retryCount) {}

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String ORDER_TOPIC = "platform.orders";
    private static final int    MAX_RETRIES = 3;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final EventBusPort eventBus;
    private final AlertPort alertPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public MessagePoisonPillChaosTestService(
        javax.sql.DataSource ds,
        EventBusPort eventBus,
        AlertPort alertPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.eventBus    = eventBus;
        this.alertPort   = alertPort;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.chaos.poison.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.chaos.poison.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("malformed_json_dlq",          this::malformedJsonDlq));
            results.add(runScenario("business_logic_reject_dlq",   this::businessLogicRejectDlq));
            results.add(runScenario("exception_retry_exhausted",   this::exceptionRetryExhausted));
            results.add(runScenario("no_blocking",                 this::noBlocking));
            results.add(runScenario("k19_dlq_routing",             this::k19DlqRouting));
            results.add(runScenario("k06_alert",                   this::k06Alert));
            results.add(runScenario("consumer_continues",          this::consumerContinues));
            results.add(runScenario("batch_with_poison",           this::batchWithPoison));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("POISON_PILL_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("MessagePoisonPillChaos", results, passed, failed);
        });
    }

    /** Malformed JSON body: schema validation rejects → DLQ, not crash. */
    private void malformedJsonDlq(String runId) throws Exception {
        byte[] malformed = "{broken json: [}".getBytes();
        String msgId = eventBus.publishRaw(ORDER_TOPIC, malformed);
        awaitDlq(runId, "malformed_reaches_dlq", msgId, "SCHEMA_VALIDATION");
    }

    /** Valid JSON but negative quantity (business logic violation) → DLQ. */
    private void businessLogicRejectDlq(String runId) throws Exception {
        String body = "{\"orderId\":\"BIZ-REJECT-001\",\"quantity\":-100,\"symbol\":\"AAPL\"}";
        String msgId = eventBus.publishEvent(ORDER_TOPIC, "OrderCreated", body);
        awaitDlq(runId, "business_rule_reject_reaches_dlq", msgId, "BUSINESS_VALIDATION");
    }

    /** Consumer throws exception → retries MAX_RETRIES times → DLQ + alert. */
    private void exceptionRetryExhausted(String runId) throws Exception {
        // A body that causes the consumer to throw (e.g., unknown event type)
        String body = "{\"orderId\":\"EX-RETRY-001\",\"type\":\"__CHAOS_THROW__\"}";
        String msgId = eventBus.publishEvent(ORDER_TOPIC, "UNKNOWN_EVENT_TYPE", body);
        awaitDlq(runId, "exception_msg_reaches_dlq_after_retries", msgId, null);
        DlqEntry entry = eventBus.getDlqEntry(msgId);
        assertStep(runId, "retry_count_exhausted", "retry count == MAX_RETRIES on DLQ",
            String.valueOf(MAX_RETRIES), entry != null && entry.retryCount() >= MAX_RETRIES,
            entry == null ? "null" : entry.retryCount());
    }

    /** One poison pill (malformed) must not block good messages on same topic. */
    private void noBlocking(String runId) throws Exception {
        long beforeCount = eventBus.getConsumedCount(ORDER_TOPIC);
        byte[] malformed = "{\"broken}".getBytes();
        eventBus.publishRaw(ORDER_TOPIC, malformed);
        // Then publish two good messages
        String good1 = "{\"orderId\":\"GOOD-001\",\"quantity\":10,\"symbol\":\"MSFT\"}";
        String good2 = "{\"orderId\":\"GOOD-002\",\"quantity\":5,\"symbol\":\"GOOG\"}";
        eventBus.publishEvent(ORDER_TOPIC, "OrderCreated", good1);
        eventBus.publishEvent(ORDER_TOPIC, "OrderCreated", good2);
        Thread.sleep(2000);
        long afterCount = eventBus.getConsumedCount(ORDER_TOPIC);
        // At least the 2 good messages must be consumed
        assertStep(runId, "good_messages_consumed", "good messages processed after poison pill",
            ">= " + (beforeCount + 2), afterCount >= beforeCount + 2, afterCount);
    }

    /** K-19: DLQ routing metadata is correct (msgId matches). */
    private void k19DlqRouting(String runId) throws Exception {
        byte[] malformed = "[notjson".getBytes();
        String msgId = eventBus.publishRaw(ORDER_TOPIC, malformed);
        awaitDlq(runId, "dlq_routing_has_msgid", msgId, null);
        DlqEntry entry = eventBus.getDlqEntry(msgId);
        assertStep(runId, "dlq_entry_has_correct_msgid", "DLQ entry msgId matches published msgId",
            msgId, entry != null && msgId.equals(entry.msgId()), entry == null ? "null" : entry.msgId());
    }

    /** K-06 alert fires when DLQ spike is detected. */
    private void k06Alert(String runId) throws Exception {
        // Publish several poison pills rapidly to trigger DLQ spike alert
        for (int i = 0; i < 5; i++) {
            eventBus.publishRaw(ORDER_TOPIC, ("{bad_" + i + "}").getBytes());
        }
        Thread.sleep(2000);
        boolean alert = alertPort.hasK06DlqAlert(ORDER_TOPIC);
        assertStep(runId, "k06_dlq_alert_fired", "K-06 DLQ spike alert fired", "true", alert, alert);
    }

    /** Consumer continues serving valid messages after encountering poison pill. */
    private void consumerContinues(String runId) throws Exception {
        eventBus.publishRaw(ORDER_TOPIC, "{bad}".getBytes());
        Thread.sleep(500);
        long before = eventBus.getConsumedCount(ORDER_TOPIC);
        String body = "{\"orderId\":\"CONTINUE-001\",\"quantity\":1,\"symbol\":\"TSLA\"}";
        eventBus.publishEvent(ORDER_TOPIC, "OrderCreated", body);
        Thread.sleep(2000);
        long after = eventBus.getConsumedCount(ORDER_TOPIC);
        assertStep(runId, "consumer_continues_after_poison", "consumer consumed message after poison pill",
            "> " + before, after > before, after);
    }

    /** Batch publish: mix of valid and poison → poisons go to DLQ, valids are consumed. */
    private void batchWithPoison(String runId) throws Exception {
        List<String> bodies = List.of(
            "{\"orderId\":\"BATCH-001\",\"quantity\":10,\"symbol\":\"AAPL\"}",
            "{broken_poison}",
            "{\"orderId\":\"BATCH-002\",\"quantity\":5,\"symbol\":\"MSFT\"}",
            "[invalid",
            "{\"orderId\":\"BATCH-003\",\"quantity\":3,\"symbol\":\"GOOG\"}"
        );
        long before = eventBus.getConsumedCount(ORDER_TOPIC);
        List<String> msgIds = eventBus.publishBatch(ORDER_TOPIC, bodies);
        Thread.sleep(3000);
        long after = eventBus.getConsumedCount(ORDER_TOPIC);
        // 3 valid messages should be consumed
        assertStep(runId, "batch_valid_consumed", "3 valid batch messages consumed", ">= " + (before + 3),
            after >= before + 3, after);
        // 2 poison ones should be in DLQ
        int dlqCount = 0;
        for (String msgId : msgIds) {
            DlqEntry e = eventBus.getDlqEntry(msgId);
            if (e != null) dlqCount++;
        }
        assertStep(runId, "batch_poison_in_dlq", "2 poison batch messages in DLQ",
            "2", dlqCount >= 2, dlqCount);
    }

    private void awaitDlq(String runId, String step, String msgId, String expectedReason) throws Exception {
        DlqEntry entry = null;
        for (int i = 0; i < 15; i++) {
            Thread.sleep(1000);
            entry = eventBus.getDlqEntry(msgId);
            if (entry != null) break;
        }
        assertStep(runId, step, "message routed to DLQ within 15s", "DLQ entry present",
            entry != null, entry == null ? "null" : entry.msgId());
        if (expectedReason != null && entry != null) {
            boolean reasonMatch = entry.reason() != null && entry.reason().contains(expectedReason);
            assertStep(runId, step + "_reason", "DLQ reason contains " + expectedReason,
                expectedReason, reasonMatch, entry.reason());
        }
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name); fn.accept(runId); markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) { return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex)      { return new ScenarioResult(name, false, ex.getMessage(),  System.currentTimeMillis() - start); }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('MessagePoisonPillChaos',?) RETURNING run_id")) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE e2e_test_runs SET status=? WHERE run_id=?")) {
            ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
