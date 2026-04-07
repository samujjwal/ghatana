package com.ghatana.finance.integration;

import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @doc.type Service
 * @doc.purpose Performance baseline service for finance fraud inference throughput, latency, and regression tracking
 * @doc.layer Integration Testing
 * @doc.pattern Port-Adapter
 */
public class FraudInferencePerformanceBaselineService {

    public interface FraudInferencePort {
        FraudDetectionResult detect(String modelId, Map<String, Object> features) throws Exception;
    }

    public interface MetricsCollectionPort {
        double getP99LatencyMs() throws Exception;
        double getCpuUsagePct() throws Exception;
        long getMemoryUsedMb() throws Exception;
    }

    private static final int SUSTAINED_BATCH = 500;
    private static final long P99_LIMIT_MS = 150L;
    private static final double ERROR_RATE_LIMIT = 0.001;

    private final javax.sql.DataSource dataSource;
    private final FraudInferencePort inferencePort;
    private final MetricsCollectionPort metricsPort;
    private final AuditBusPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public FraudInferencePerformanceBaselineService(
        javax.sql.DataSource dataSource,
        FraudInferencePort inferencePort,
        MetricsCollectionPort metricsPort,
        AuditBusPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        this.inferencePort = Objects.requireNonNull(inferencePort, "inferencePort cannot be null");
        this.metricsPort = Objects.requireNonNull(metricsPort, "metricsPort cannot be null");
        this.audit = Objects.requireNonNull(audit, "audit cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
        this.suitesPassed = Counter.builder("integration.perf.fraud.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.perf.fraud.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("fraud_sustained_load", this::sustainedLoad));
            results.add(runScenario("fraud_p99_latency", this::p99LatencyCheck));
            results.add(runScenario("fraud_error_rate", this::errorRateCheck));
            results.add(runScenario("fraud_cpu_memory", this::cpuMemoryCheck));
            results.add(runScenario("fraud_trend_vs_previous", this::trendVsPrevious));

            long passed = results.stream().filter(ScenarioResult::passed).count();
            long failed = results.size() - passed;
            if (failed == 0) {
                suitesPassed.increment();
            } else {
                suitesFailed.increment();
            }

            audit.emit(AuditEvent.builder()
                .tenantId("integration")
                .eventType("FRAUD_PERF_SUITE")
                .principal("integration-suite")
                .resourceType("fraud-model")
                .resourceId("fraud-detection-v2")
                .success(failed == 0)
                .detail("passed", passed)
                .detail("failed", failed)
                .build());

            return new SuiteResult("FraudInferencePerformanceBaseline", results, passed, failed);
        });
    }

    private void sustainedLoad(String runId) throws Exception {
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        for (int index = 0; index < SUSTAINED_BATCH; index++) {
            try {
                FraudDetectionResult result = inferencePort.detect("fraud-detection-v2", sampleFeatures(index));
                if (result != null) {
                    successCount.incrementAndGet();
                }
            } catch (Exception exception) {
                errorCount.incrementAndGet();
            }
        }

        assertStep(
            runId,
            "fraud_sustained_acceptance",
            "all fraud inference requests complete successfully",
            ">= 99%",
            successCount.get() >= (int) (SUSTAINED_BATCH * 0.99),
            successCount.get() + "/" + SUSTAINED_BATCH
        );
        persistPerformanceRecord(runId, "fraud_sustained_load", successCount.get(), Math.round(metricsPort.getP99LatencyMs()), errorCount.get());
    }

    private void p99LatencyCheck(String runId) throws Exception {
        double p99 = metricsPort.getP99LatencyMs();
        assertStep(
            runId,
            "fraud_p99_threshold",
            "fraud inference p99 latency stays below threshold",
            "< " + P99_LIMIT_MS,
            p99 < P99_LIMIT_MS,
            p99 + "ms"
        );
    }

    private void errorRateCheck(String runId) throws Exception {
        AtomicInteger errors = new AtomicInteger();
        for (int index = 0; index < 200; index++) {
            try {
                inferencePort.detect("fraud-detection-v2", sampleFeatures(index + 10_000));
            } catch (Exception exception) {
                errors.incrementAndGet();
            }
        }
        double errorRate = (double) errors.get() / 200;
        assertStep(
            runId,
            "fraud_error_rate",
            "fraud inference error rate remains below threshold",
            "<= " + ERROR_RATE_LIMIT,
            errorRate <= ERROR_RATE_LIMIT,
            errorRate
        );
    }

    private void cpuMemoryCheck(String runId) throws Exception {
        double cpu = metricsPort.getCpuUsagePct();
        long memoryMb = metricsPort.getMemoryUsedMb();
        assertStep(runId, "fraud_cpu_below_80pct", "CPU usage < 80%", "< 80", cpu < 80.0, cpu + "%");
        assertStep(runId, "fraud_memory_below_2gb", "memory used < 2048 MB", "< 2048", memoryMb < 2048, memoryMb + "MB");
    }

    private void trendVsPrevious(String runId) throws Exception {
        double p99 = metricsPort.getP99LatencyMs();
        persistPerformanceRecord(runId, "fraud_trend_baseline", 0, Math.round(p99), 0);
        Long previousP99 = queryPreviousP99();
        if (previousP99 != null) {
            assertStep(
                runId,
                "fraud_trend_regression",
                "p99 not regressed >10% vs previous baseline",
                "<= " + (previousP99 * 1.10),
                p99 <= previousP99 * 1.10,
                p99 + "ms (prev=" + previousP99 + "ms)"
            );
        }
    }

    private Map<String, Object> sampleFeatures(int index) {
        return Map.of(
            "amount_factor", 0.18 + ((index % 5) * 0.02),
            "velocity_score", 4.0 + (index % 7),
            "merchant_risk", 0.11,
            "counterparty_risk", 0.12,
            "time_risk", 0.09,
            "geolocation_risk", 0.08
        );
    }

    private void persistPerformanceRecord(String runId, String scenario, int count, long p99Ms, int errors) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO perf_baselines (run_id,suite,scenario,order_count,p99_ms,error_count,measured_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)"
             )) {
            statement.setString(1, runId);
            statement.setString(2, "FraudInferenceBaseline");
            statement.setString(3, scenario);
            statement.setInt(4, count);
            statement.setLong(5, p99Ms);
            statement.setInt(6, errors);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private Long queryPreviousP99() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT p99_ms FROM perf_baselines WHERE suite=? AND scenario=? ORDER BY measured_at DESC LIMIT 1 OFFSET 1"
             )) {
            statement.setString(1, "FraudInferenceBaseline");
            statement.setString(2, "fraud_trend_baseline");
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : null;
            }
        } catch (SQLException ignored) {
            return null;
        }
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> scenarioRunner) {
        long startedAt = System.currentTimeMillis();
        try {
            String runId = insertRun(name);
            scenarioRunner.accept(runId);
            markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - startedAt);
        } catch (AssertionError assertionError) {
            return new ScenarioResult(name, false, assertionError.getMessage(), System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            return new ScenarioResult(name, false, exception.getMessage(), System.currentTimeMillis() - startedAt);
        }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)"
             )) {
            statement.setString(1, runId);
            statement.setString(2, step);
            statement.setString(3, assertion);
            statement.setString(4, expected);
            statement.setString(5, String.valueOf(actual));
            statement.setBoolean(6, passed);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }

        if (!passed) {
            throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
        }
    }

    private String insertRun(String scenario) throws SQLException {
        String runId = UUID.randomUUID().toString();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO e2e_test_runs (run_id,suite_name,scenario,status,recorded_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)"
             )) {
            statement.setString(1, runId);
            statement.setString(2, "FraudInferencePerformanceBaseline");
            statement.setString(3, scenario);
            statement.setString(4, "RUNNING");
            statement.executeUpdate();
        }
        return runId;
    }

    private void markRunStatus(String runId, String status) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE e2e_test_runs SET status=? WHERE run_id=?"
             )) {
            statement.setString(1, status);
            statement.setString(2, runId);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @FunctionalInterface
    interface ThrowingConsumer<T> {
        void accept(T value) throws Exception;
    }

    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}

    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}