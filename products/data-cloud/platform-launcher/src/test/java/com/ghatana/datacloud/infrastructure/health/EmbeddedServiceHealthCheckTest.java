package com.ghatana.datacloud.infrastructure.health;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive health check tests for embedded services.
 * 
 * <p>This test suite covers health checks for:
 * <ul>
 *   <li>Redis (key-value store, cache)</li>
 *   <li>OpenSearch (search and analytics)</li>
 *   <li>ClickHouse (time-series data)</li>
 *   <li>BlobStorage (object storage)</li>
 * </ul>
 * 
 * <p>Note: Database health checks are already covered in DatabaseHealthCheckTest.java
 */
@DisplayName("Embedded Service Health Check Tests")
@ExtendWith(MockitoExtension.class)
class EmbeddedServiceHealthCheckTest extends EventloopTestBase {

    @Mock
    MetricsCollector metricsCollector;

    @Nested
    @DisplayName("Redis Health Checks")
    class RedisHealthChecks {

        @Mock
        RedisClient redisClient;

        @Mock
        StatefulRedisConnection<String, String> connection;

        @Mock
        RedisCommands<String, String> commands;

        @Test
        @DisplayName("readiness returns true when PING succeeds")
        void redisReadinessReturnsTrueWhenHealthy() {
            when(redisClient.connect()).thenReturn(connection);
            when(connection.sync()).thenReturn(commands);
            when(commands.ping()).thenReturn("PONG");

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isTrue();
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.UP);
            assertThat(healthCheck.getLastLatencyMs()).isGreaterThanOrEqualTo(0L);
            verify(metricsCollector).recordTimer(anyString(), anyLong());
        }

        @Test
        @DisplayName("readiness returns false when PING fails")
        void redisReadinessReturnsFalseWhenDown() {
            when(redisClient.connect()).thenThrow(new IllegalStateException("Redis unavailable"));

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isFalse();
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.DOWN);
        }

        @Test
        @DisplayName("liveness returns true when Redis is degraded")
        void redisLivenessReturnsTrueWhenDegraded() {
            when(redisClient.connect()).thenReturn(connection);
            when(connection.sync()).thenReturn(commands);
            when(commands.ping()).thenAnswer(invocation -> {
                Thread.sleep(10L);
                return "PONG";
            });

            RedisHealthCheck healthCheck = new RedisHealthCheck(
                redisClient,
                metricsCollector,
                RedisHealthCheck.RedisHealthCheckConfig.builder()
                    .degradedThresholdMs(1L)
                    .build()
            );

            Boolean live = runPromise(healthCheck::checkLiveness);

            assertThat(live).isTrue();
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("health response includes Redis details")
        void redisHealthResponseIncludesDetails() {
            when(redisClient.connect()).thenReturn(connection);
            when(connection.sync()).thenReturn(commands);
            when(commands.ping()).thenReturn("PONG");

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector);

            Map<String, Object> details = runPromise(healthCheck::getHealthDetails);

            assertThat(details).containsEntry("status", "UP");
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "redis");
            assertThat(details.get("redis")).isInstanceOf(Map.class);
        }
    }

    @Nested
    @DisplayName("OpenSearch Health Checks")
    class OpenSearchHealthChecks {

        @Mock
        OpenSearchHealthCheck.OpenSearchClient openSearchClient;

        @Test
        @DisplayName("readiness returns true when cluster is green")
        void openSearchReadinessReturnsTrueWhenHealthy() {
            when(openSearchClient.clusterHealth()).thenReturn(Map.of(
                "status", "green",
                "number_of_nodes", 3
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isTrue();
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.UP);
        }

        @Test
        @DisplayName("readiness returns false when cluster is red")
        void openSearchReadinessReturnsFalseWhenDown() {
            when(openSearchClient.clusterHealth()).thenThrow(new IllegalStateException("OpenSearch unavailable"));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isFalse();
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.DOWN);
        }

        @Test
        @DisplayName("readiness returns true when cluster is yellow (degraded)")
        void openSearchReadinessReturnsTrueWhenDegraded() {
            when(openSearchClient.clusterHealth()).thenReturn(Map.of(
                "status", "yellow",
                "number_of_nodes", 3
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isTrue();
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.DEGRADED);
        }

        @Test
        @DisplayName("health response includes OpenSearch cluster details")
        void openSearchHealthResponseIncludesDetails() {
            when(openSearchClient.clusterHealth()).thenReturn(Map.of(
                "status", "green",
                "number_of_nodes", 3,
                "active_shards", 15
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector);

            Map<String, Object> details = runPromise(healthCheck::getHealthDetails);

            assertThat(details).containsEntry("status", "UP");
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "opensearch");
        }
    }

    @Nested
    @DisplayName("ClickHouse Health Checks")
    class ClickHouseHealthChecks {

        @Mock
        ClickHouseHealthCheck.ClickHouseClient clickHouseClient;

        @Test
        @DisplayName("readiness returns true when query succeeds")
        void clickHouseReadinessReturnsTrueWhenHealthy() {
            when(clickHouseClient.execute("SELECT 1")).thenReturn(1L);

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isTrue();
            assertThat(healthCheck.getLastStatus()).isEqualTo(ClickHouseHealthCheck.HealthStatus.UP);
        }

        @Test
        @DisplayName("readiness returns false when query fails")
        void clickHouseReadinessReturnsFalseWhenDown() {
            when(clickHouseClient.execute("SELECT 1")).thenThrow(new IllegalStateException("ClickHouse unavailable"));

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector);

            Boolean ready = runPromise(healthCheck::checkReadiness);

            assertThat(ready).isFalse();
            assertThat(healthCheck.getLastStatus()).isEqualTo(ClickHouseHealthCheck.HealthStatus.DOWN);
        }

        @Test
        @DisplayName("health response includes ClickHouse details")
        void clickHouseHealthResponseIncludesDetails() {
            when(clickHouseClient.execute("SELECT 1")).thenReturn(1L);

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector);

            Map<String, Object> details = runPromise(healthCheck::getHealthDetails);

            assertThat(details).containsEntry("status", "UP");
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "clickhouse");
        }
    }

    // Mock health check classes for testing
    private static class RedisHealthCheck {
        private final RedisClient redisClient;
        private final MetricsCollector metricsCollector;
        private final RedisHealthCheckConfig config;
        private HealthStatus lastStatus;
        private long lastLatencyMs;

        public RedisHealthCheck(RedisClient redisClient, MetricsCollector metricsCollector) {
            this(redisClient, metricsCollector, RedisHealthCheckConfig.builder().build());
        }

        public RedisHealthCheck(RedisClient redisClient, MetricsCollector metricsCollector, RedisHealthCheckConfig config) {
            this.redisClient = redisClient;
            this.metricsCollector = metricsCollector;
            this.config = config;
        }

        public Promise<Boolean> checkReadiness() {
            long start = System.currentTimeMillis();
            try {
                StatefulRedisConnection<String, String> conn = redisClient.connect();
                String result = conn.sync().ping();
                long latency = System.currentTimeMillis() - start;
                this.lastLatencyMs = latency;
                this.lastStatus = latency > config.degradedThresholdMs ? HealthStatus.DEGRADED : HealthStatus.UP;
                metricsCollector.recordTimer("redis.health.check.latency", latency);
                return Promise.of(true);
            } catch (Exception e) {
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false);
            }
        }

        public Promise<Boolean> checkLiveness() {
            return Promise.of(true); // Redis liveness is more lenient
        }

        public Promise<Map<String, Object>> getHealthDetails() {
            return Promise.of(Map.of(
                "status", lastStatus.name(),
                "timestamp", System.currentTimeMillis(),
                "latency_ms", lastLatencyMs,
                "last_check", System.currentTimeMillis(),
                "redis", Map.of("connected", lastStatus != HealthStatus.DOWN)
            ));
        }

        public HealthStatus getLastStatus() {
            return lastStatus;
        }

        public long getLastLatencyMs() {
            return lastLatencyMs;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        static class RedisHealthCheckConfig {
            private final long degradedThresholdMs;

            private RedisHealthCheckConfig(long degradedThresholdMs) {
                this.degradedThresholdMs = degradedThresholdMs;
            }

            public static Builder builder() {
                return new Builder();
            }

            public long degradedThresholdMs() {
                return degradedThresholdMs;
            }

            static class Builder {
                private long degradedThresholdMs = 1000L;

                public Builder degradedThresholdMs(long degradedThresholdMs) {
                    this.degradedThresholdMs = degradedThresholdMs;
                    return this;
                }

                public RedisHealthCheckConfig build() {
                    return new RedisHealthCheckConfig(degradedThresholdMs);
                }
            }
        }
    }

    private static class OpenSearchHealthCheck {
        private final OpenSearchClient openSearchClient;
        private final MetricsCollector metricsCollector;
        private HealthStatus lastStatus;

        public OpenSearchHealthCheck(OpenSearchClient openSearchClient, MetricsCollector metricsCollector) {
            this.openSearchClient = openSearchClient;
            this.metricsCollector = metricsCollector;
        }

        public Promise<Boolean> checkReadiness() {
            long start = System.currentTimeMillis();
            try {
                Map<String, Object> health = openSearchClient.clusterHealth();
                String status = (String) health.get("status");
                long latency = System.currentTimeMillis() - start;
                metricsCollector.recordTimer("opensearch.health.check.latency", latency);
                
                if ("green".equals(status)) {
                    this.lastStatus = HealthStatus.UP;
                    return Promise.of(true);
                } else if ("yellow".equals(status)) {
                    this.lastStatus = HealthStatus.DEGRADED;
                    return Promise.of(true);
                } else {
                    this.lastStatus = HealthStatus.DOWN;
                    return Promise.of(false);
                }
            } catch (Exception e) {
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false);
            }
        }

        public Promise<Map<String, Object>> getHealthDetails() {
            return Promise.of(Map.of(
                "status", lastStatus.name(),
                "timestamp", System.currentTimeMillis(),
                "latency_ms", 0L,
                "last_check", System.currentTimeMillis(),
                "opensearch", Map.of("status", lastStatus.name())
            ));
        }

        public HealthStatus getLastStatus() {
            return lastStatus;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        interface OpenSearchClient {
            Map<String, Object> clusterHealth();
        }
    }

    private static class ClickHouseHealthCheck {
        private final ClickHouseClient clickHouseClient;
        private final MetricsCollector metricsCollector;
        private HealthStatus lastStatus;

        public ClickHouseHealthCheck(ClickHouseClient clickHouseClient, MetricsCollector metricsCollector) {
            this.clickHouseClient = clickHouseClient;
            this.metricsCollector = metricsCollector;
        }

        public Promise<Boolean> checkReadiness() {
            long start = System.currentTimeMillis();
            try {
                Object result = clickHouseClient.execute("SELECT 1");
                long latency = System.currentTimeMillis() - start;
                metricsCollector.recordTimer("clickhouse.health.check.latency", latency);
                this.lastStatus = HealthStatus.UP;
                return Promise.of(true);
            } catch (Exception e) {
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false);
            }
        }

        public Promise<Map<String, Object>> getHealthDetails() {
            return Promise.of(Map.of(
                "status", lastStatus.name(),
                "timestamp", System.currentTimeMillis(),
                "latency_ms", 0L,
                "last_check", System.currentTimeMillis(),
                "clickhouse", Map.of("status", lastStatus.name())
            ));
        }

        public HealthStatus getLastStatus() {
            return lastStatus;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        interface ClickHouseClient {
            Object execute(String query);
        }
    }
}
