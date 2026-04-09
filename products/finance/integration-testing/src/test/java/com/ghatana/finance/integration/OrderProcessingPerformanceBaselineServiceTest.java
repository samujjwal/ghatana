package com.ghatana.finance.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.audit.AuditBusPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @doc.type class
 * @doc.purpose Verifies the order-processing performance baseline suite against an in-memory harness
 * @doc.layer product
 * @doc.pattern Test
 */
class OrderProcessingPerformanceBaselineServiceTest extends EventloopTestBase {

    @Test
    void runAllPersistsPassingBaselineScenarios() throws Exception {
        DataSource dataSource = PerformanceBaselineTestSupport.createDataSource("order-perf-test");
        InMemoryOrderSubmissionPort orderPort = new InMemoryOrderSubmissionPort();
        OrderProcessingPerformanceBaselineService.MetricsCollectionPort metricsPort = new OrderProcessingPerformanceBaselineService.MetricsCollectionPort() {
            @Override
            public long getOrdersAcceptedCount() {
                return 3_600;
            }

            @Override
            public double getP99LatencyMs() {
                return 140.0;
            }

            @Override
            public double getCpuUsagePct() {
                return 55.0;
            }

            @Override
            public long getMemoryUsedMb() {
                return 1024;
            }
        };
        AuditBusPort audit = event -> {
        };

        OrderProcessingPerformanceBaselineService service = new OrderProcessingPerformanceBaselineService(
            dataSource,
            orderPort,
            metricsPort,
            audit,
            new SimpleMeterRegistry(),
            Runnable::run
        );

        OrderProcessingPerformanceBaselineService.SuiteResult result = runPromise(service::runAll);

        assertEquals(7, result.results().size());
        assertEquals(0, result.failedCount());
        assertEquals(7L, countRows(dataSource, "e2e_test_runs"));
        assertEquals(3L, countRows(dataSource, "perf_baselines"));
    }

    private static long countRows(DataSource dataSource, String tableName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static final class InMemoryOrderSubmissionPort implements OrderProcessingPerformanceBaselineService.OrderSubmissionPort {

        private final Map<String, String> statuses = new ConcurrentHashMap<>();

        @Override
        public String submitOrder(String clientId, String symbol, int qty, double price) {
            String orderId = UUID.randomUUID().toString();
            statuses.put(orderId, "ACCEPTED");
            return orderId;
        }

        @Override
        public String getOrderStatus(String orderId) {
            return statuses.get(orderId);
        }
    }
}
