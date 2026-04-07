package com.ghatana.finance.integration.performance;

import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.finance.integration.FraudInferencePerformanceBaselineService;
import com.ghatana.platform.audit.AuditBusPort;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for finance fraud inference performance baseline.
 *
 * @doc.type class
 * @doc.purpose Baseline benchmark for finance fraud inference scenario
 * @doc.layer product
 * @doc.pattern Benchmark
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class FraudInferenceScenarioBenchmark {

    private FraudInferencePerformanceBaselineService service;

    @Setup
    public void setUp() throws Exception {
        javax.sql.DataSource dataSource = com.ghatana.finance.integration.PerformanceBaselineTestSupport.createDataSource("fraud-bench");
        FraudInferencePerformanceBaselineService.FraudInferencePort inferencePort = (modelId, features) -> FraudDetectionResult.scored(
            "trade-bench",
            "account-bench",
            "velocity-anomaly",
            0.76,
            "HIGH",
            true,
            0.91,
            0.94,
            features,
            18L,
            "REMOTE",
            "2026.04",
            18L
        );
        FraudInferencePerformanceBaselineService.MetricsCollectionPort metricsPort = new FraudInferencePerformanceBaselineService.MetricsCollectionPort() {
            @Override
            public double getP99LatencyMs() {
                return 90.0;
            }

            @Override
            public double getCpuUsagePct() {
                return 51.0;
            }

            @Override
            public long getMemoryUsedMb() {
                return 896;
            }
        };
        AuditBusPort audit = event -> {
        };

        service = new FraudInferencePerformanceBaselineService(
            dataSource,
            inferencePort,
            metricsPort,
            audit,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
            Runnable::run
        );
    }

    @Benchmark
    public boolean runScenario() {
        FraudInferencePerformanceBaselineService.SuiteResult result = service.runAll().toCompletableFuture().join();
        return result.failedCount() == 0;
    }
}