package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Event schema contract test suite for K-05 event bus (K-08 registry).
 *              Validates: missing required field rejected; backward compat (v1 consumer reads v2);
 *              breaking change requires version bump; unknown schema handled gracefully;
 *              schema registry gate on publish; forward compatibility; event catalog coverage.
 * @doc.layer   Integration Testing (T-01)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; schema-contract-testing
 *
 * STORY-T01-013: Implement event schema contract tests
 */
public class EventSchemaContractTestSuiteService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface SchemaRegistryPort {
        /** Attempt to publish an event. Returns false if schema validation fails. */
        boolean publishEvent(String topic, String schemaVersion, String payload) throws Exception;
        /** Register a new schema version. */
        void registerSchema(String topic, String version, String schemaJson) throws Exception;
        /** Check if a schema version is registered. */
        boolean isSchemaRegistered(String topic, String version) throws Exception;
        /** Validate backward compatibility between two versions. */
        boolean isBackwardCompatible(String topic, String oldVersion, String newVersion) throws Exception;
        /** Validate forward compatibility. */
        boolean isForwardCompatible(String topic, String oldVersion, String newVersion) throws Exception;
        /** Number of topics registered in event catalog. */
        int getEventCatalogTopicCount() throws Exception;
        /** Number of topics with registered schema. */
        int getTopicsWithSchemaCount() throws Exception;
    }

    public interface EventConsumerPort {
        /**
         * Simulate a v1 consumer reading a v2 event payload.
         * Returns true if consumer can handle it gracefully (no crash, uses known fields).
         */
        boolean v1ConsumerReadsV2Event(String topic, String v2Payload) throws Exception;
        /** Simulate consumer receiving unknown schema → returns true if handled gracefully. */
        boolean handleUnknownSchemagracefully(String topic, String unknownPayload) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final SchemaRegistryPort schemaRegistry;
    private final EventConsumerPort consumerPort;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public EventSchemaContractTestSuiteService(
        javax.sql.DataSource ds,
        SchemaRegistryPort schemaRegistry,
        EventConsumerPort consumerPort,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.schemaRegistry = schemaRegistry;
        this.consumerPort   = consumerPort;
        this.audit          = audit;
        this.executor       = executor;
        this.suitesPassed   = Counter.builder("integration.schema.suites_passed").register(registry);
        this.suitesFailed   = Counter.builder("integration.schema.suites_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("missing_field_reject",        this::missingFieldReject));
            results.add(runScenario("backward_compat_v1_reads_v2", this::backwardCompatV1ReadsV2));
            results.add(runScenario("breaking_change_version_bump",this::breakingChangeVersionBump));
            results.add(runScenario("unknown_schema_graceful",     this::unknownSchemaGraceful));
            results.add(runScenario("schema_registry_gate",        this::schemaRegistryGate));
            results.add(runScenario("forward_compat",              this::forwardCompatCheck));
            results.add(runScenario("event_catalog_coverage",      this::eventCatalogCoverage));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("EVENT_SCHEMA_CONTRACT_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("EventSchemaContract", results, passed, failed);
        });
    }

    // ── Scenarios ─────────────────────────────────────────────────────────────

    /** Event missing required field must be rejected by schema registry. */
    private void missingFieldReject(String runId) throws Exception {
        // OrderCreated schema requires: orderId, clientId, symbol, qty, price
        String payloadMissingQty = "{\"orderId\":\"ORD-001\",\"clientId\":\"CLI-001\",\"symbol\":\"NABIL\",\"price\":1500.0}";
        boolean accepted = schemaRegistry.publishEvent("platform.orders.created", "1.0", payloadMissingQty);
        assertStep(runId, "missing_field_rejected", "event with missing required field rejected", "false",
            !accepted, accepted);
    }

    /** V1 consumer reads a V2 event that adds optional fields — must work without error. */
    private void backwardCompatV1ReadsV2(String runId) throws Exception {
        // V2 adds optional field "tags" — v1 consumer should ignore it
        String v2Payload = "{\"orderId\":\"ORD-002\",\"clientId\":\"CLI-002\",\"symbol\":\"NTC\",\"qty\":100,\"price\":950.0,\"tags\":[\"algo\"]}";
        boolean handled = consumerPort.v1ConsumerReadsV2Event("platform.orders.created", v2Payload);
        assertStep(runId, "v1_reads_v2", "v1 consumer handles v2 event gracefully", "true", handled, handled);
        boolean compat = schemaRegistry.isBackwardCompatible("platform.orders.created", "1.0", "2.0");
        assertStep(runId, "schema_backward_compat", "schema registry confirms v1→v2 backward compatible", "true", compat, compat);
    }

    /** Breaking change (removing a required field) must require a version bump and not be published under old version. */
    private void breakingChangeVersionBump(String runId) throws Exception {
        // Try registering v1.1 with a removed required field (breaking) — should fail / be rejected
        String breakingSchema = "{\"type\":\"object\",\"required\":[\"orderId\",\"clientId\"],\"properties\":{" +
            "\"orderId\":{\"type\":\"string\"},\"clientId\":{\"type\":\"string\"}}}"; // symbol, qty, price removed
        boolean registered;
        try { schemaRegistry.registerSchema("platform.orders.created", "1.1", breakingSchema); registered = true; }
        catch (Exception ex) { registered = false; }
        // If registration succeeded, backward compat check should fail
        if (registered) {
            boolean compat = schemaRegistry.isBackwardCompatible("platform.orders.created", "1.0", "1.1");
            assertStep(runId, "breaking_not_compat", "breaking change schema not backward compatible with v1.0", "false",
                !compat, compat ? "compatible (wrong!)" : "not compatible");
        } else {
            // Rejection is also an acceptable outcome
            assertStep(runId, "breaking_rejected_at_register", "breaking schema rejected at registration", "true",
                true, "registration rejected");
        }
    }

    /** Consumer receives message with unknown/unregistered schema — must handle gracefully, not crash. */
    private void unknownSchemaGraceful(String runId) throws Exception {
        String unknownPayload = "{\"schemaVersion\":\"99.0\",\"data\":\"...\"}";
        boolean graceful = consumerPort.handleUnknownSchemagracefully("platform.unknown.topic", unknownPayload);
        assertStep(runId, "unknown_schema_graceful", "consumer handles unknown schema without crash", "true",
            graceful, graceful);
    }

    /** Schema registry acts as gate: publishing without registered schema fails. */
    private void schemaRegistryGate(String runId) throws Exception {
        boolean published = schemaRegistry.publishEvent("platform.unregistered.topic", "1.0", "{\"data\":\"test\"}");
        assertStep(runId, "registry_gate", "publish to unregistered schema topic rejected", "false",
            !published, published);
    }

    /** Forward compatibility: v2 consumer reads a v1 event (for migration period). */
    private void forwardCompatCheck(String runId) throws Exception {
        boolean fwdCompat = schemaRegistry.isForwardCompatible("platform.orders.created", "1.0", "2.0");
        assertStep(runId, "forward_compat", "schema is forward compatible v1→v2", "true", fwdCompat, fwdCompat);
    }

    /** All registered event catalog topics have a schema in the registry. */
    private void eventCatalogCoverage(String runId) throws Exception {
        int catalogTopics = schemaRegistry.getEventCatalogTopicCount();
        int schemaTopics  = schemaRegistry.getTopicsWithSchemaCount();
        assertStep(runId, "catalog_coverage", "all event catalog topics have a registered schema",
            String.valueOf(catalogTopics), schemaTopics >= catalogTopics,
            schemaTopics + " of " + catalogTopics + " have schema");
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('EventSchemaContract',?) RETURNING run_id"
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
