package com.ghatana.finance.integration;

import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Verifies the fraud-inference performance baseline suite against an in-memory harness
 * @doc.layer product
 * @doc.pattern Test
 */
class FraudInferencePerformanceBaselineServiceTest extends EventloopTestBase {

    @Test
    void runAllPersistsPassingFraudBaselineScenarios() throws Exception {
        DataSource dataSource = PerformanceBaselineTestSupport.createDataSource("fraud-perf-test");
        FraudInferencePerformanceBaselineService.FraudInferencePort inferencePort = (modelId, features) -> FraudDetectionResult.scored(
            "trade-1",
            "account-1",
            "velocity-anomaly",
            0.82,
            "HIGH",
            true,
            0.95,
            0.93,
            features,
            24L,
            "REMOTE",
            "2026.04",
            24L
        );
        FraudInferencePerformanceBaselineService.MetricsCollectionPort metricsPort = new FraudInferencePerformanceBaselineService.MetricsCollectionPort() {
            @Override
            public double getP99LatencyMs() {
                return 96.0;
            }

            @Override
            public double getCpuUsagePct() {
                return 48.0;
            }

            @Override
            public long getMemoryUsedMb() {
                return 768;
            }
        };
        AuditBusPort audit = event -> {
        };

        FraudInferencePerformanceBaselineService service = new FraudInferencePerformanceBaselineService(
            dataSource,
            inferencePort,
            metricsPort,
            audit,
            new SimpleMeterRegistry(),
            Runnable::run
        );

        FraudInferencePerformanceBaselineService.SuiteResult result = runPromise(service::runAll);

        assertEquals(5, result.results().size());
        assertEquals(0, result.failedCount());
        assertEquals(5L, countRows(dataSource, "e2e_test_runs"));
        assertEquals(2L, countRows(dataSource, "perf_baselines"));
    }

    private static long countRows(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
