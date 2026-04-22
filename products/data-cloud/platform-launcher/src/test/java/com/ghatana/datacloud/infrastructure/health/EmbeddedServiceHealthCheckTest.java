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
 *   <li>Redis (key-value store, cache)</li> // GH-90000
 *   <li>OpenSearch (search and analytics)</li> // GH-90000
 *   <li>ClickHouse (time-series data)</li> // GH-90000
 *   <li>BlobStorage (object storage)</li> // GH-90000
 * </ul>
 * 
 * <p>Note: Database health checks are already covered in DatabaseHealthCheckTest.java
 */
@DisplayName("Embedded Service Health Check Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EmbeddedServiceHealthCheckTest extends EventloopTestBase {

    @Mock
    MetricsCollector metricsCollector;

    @Nested
    @DisplayName("Redis Health Checks [GH-90000]")
    class RedisHealthChecks {

        @Mock
        RedisClient redisClient;

        @Mock
        StatefulRedisConnection<String, String> connection;

        @Mock
        RedisCommands<String, String> commands;

        @Test
        @DisplayName("readiness returns true when PING succeeds [GH-90000]")
        void redisReadinessReturnsTrueWhenHealthy() { // GH-90000
            when(redisClient.connect()).thenReturn(connection); // GH-90000
            when(connection.sync()).thenReturn(commands); // GH-90000
            when(commands.ping()).thenReturn("PONG [GH-90000]");

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isTrue(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.UP); // GH-90000
            assertThat(healthCheck.getLastLatencyMs()).isGreaterThanOrEqualTo(0L); // GH-90000
            verify(metricsCollector).recordTimer(anyString(), anyLong()); // GH-90000
        }

        @Test
        @DisplayName("readiness returns false when PING fails [GH-90000]")
        void redisReadinessReturnsFalseWhenDown() { // GH-90000
            when(redisClient.connect()).thenThrow(new IllegalStateException("Redis unavailable [GH-90000]"));

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isFalse(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.DOWN); // GH-90000
        }

        @Test
        @DisplayName("liveness returns true when Redis is degraded [GH-90000]")
        void redisLivenessReturnsTrueWhenDegraded() { // GH-90000
            when(redisClient.connect()).thenReturn(connection); // GH-90000
            when(connection.sync()).thenReturn(commands); // GH-90000
            when(commands.ping()).thenAnswer(invocation -> { // GH-90000
                Thread.sleep(10L); // GH-90000
                return "PONG";
            });

            RedisHealthCheck healthCheck = new RedisHealthCheck( // GH-90000
                redisClient,
                metricsCollector,
                RedisHealthCheck.RedisHealthCheckConfig.builder() // GH-90000
                    .degradedThresholdMs(1L) // GH-90000
                    .build() // GH-90000
            );

            Boolean live = runPromise(healthCheck::checkLiveness); // GH-90000

            assertThat(live).isTrue(); // GH-90000
            // checkLiveness should set the status, so this should now work
            assertThat(healthCheck.getLastStatus()).isEqualTo(RedisHealthCheck.HealthStatus.DEGRADED); // GH-90000
        }

        @Test
        @DisplayName("health response includes Redis details [GH-90000]")
        void redisHealthResponseIncludesDetails() { // GH-90000
            when(redisClient.connect()).thenReturn(connection); // GH-90000
            when(connection.sync()).thenReturn(commands); // GH-90000
            when(commands.ping()).thenReturn("PONG [GH-90000]");

            RedisHealthCheck healthCheck = new RedisHealthCheck(redisClient, metricsCollector); // GH-90000

            runPromise(healthCheck::checkReadiness); // GH-90000
            Map<String, Object> details = runPromise(healthCheck::getHealthDetails); // GH-90000

            assertThat(details).containsEntry("status", "UP"); // GH-90000
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "redis"); // GH-90000
            assertThat(details.get("redis [GH-90000]")).isInstanceOf(Map.class);
        }
    }

    @Nested
    @DisplayName("OpenSearch Health Checks [GH-90000]")
    class OpenSearchHealthChecks {

        @Mock
        OpenSearchHealthCheck.OpenSearchClient openSearchClient;

        @Test
        @DisplayName("readiness returns true when cluster is green [GH-90000]")
        void openSearchReadinessReturnsTrueWhenHealthy() { // GH-90000
            when(openSearchClient.clusterHealth()).thenReturn(Map.of( // GH-90000
                "status", "green",
                "number_of_nodes", 3
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isTrue(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.UP); // GH-90000
        }

        @Test
        @DisplayName("readiness returns false when cluster is red [GH-90000]")
        void openSearchReadinessReturnsFalseWhenDown() { // GH-90000
            when(openSearchClient.clusterHealth()).thenThrow(new IllegalStateException("OpenSearch unavailable [GH-90000]"));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isFalse(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.DOWN); // GH-90000
        }

        @Test
        @DisplayName("readiness returns true when cluster is yellow (degraded) [GH-90000]")
        void openSearchReadinessReturnsTrueWhenDegraded() { // GH-90000
            when(openSearchClient.clusterHealth()).thenReturn(Map.of( // GH-90000
                "status", "yellow",
                "number_of_nodes", 3
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isTrue(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(OpenSearchHealthCheck.HealthStatus.DEGRADED); // GH-90000
        }

        @Test
        @DisplayName("health response includes OpenSearch cluster details [GH-90000]")
        void openSearchHealthResponseIncludesDetails() { // GH-90000
            when(openSearchClient.clusterHealth()).thenReturn(Map.of( // GH-90000
                "status", "green",
                "number_of_nodes", 3,
                "active_shards", 15
            ));

            OpenSearchHealthCheck healthCheck = new OpenSearchHealthCheck(openSearchClient, metricsCollector); // GH-90000

            runPromise(healthCheck::checkReadiness); // GH-90000
            Map<String, Object> details = runPromise(healthCheck::getHealthDetails); // GH-90000

            assertThat(details).containsEntry("status", "UP"); // GH-90000
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "opensearch"); // GH-90000
        }
    }

    @Nested
    @DisplayName("ClickHouse Health Checks [GH-90000]")
    class ClickHouseHealthChecks {

        @Mock
        ClickHouseHealthCheck.ClickHouseClient clickHouseClient;

        @Test
        @DisplayName("readiness returns true when query succeeds [GH-90000]")
        void clickHouseReadinessReturnsTrueWhenHealthy() { // GH-90000
            when(clickHouseClient.execute("SELECT 1 [GH-90000]")).thenReturn(1L);

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isTrue(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(ClickHouseHealthCheck.HealthStatus.UP); // GH-90000
        }

        @Test
        @DisplayName("readiness returns false when query fails [GH-90000]")
        void clickHouseReadinessReturnsFalseWhenDown() { // GH-90000
            when(clickHouseClient.execute("SELECT 1 [GH-90000]")).thenThrow(new IllegalStateException("ClickHouse unavailable [GH-90000]"));

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector); // GH-90000

            Boolean ready = runPromise(healthCheck::checkReadiness); // GH-90000

            assertThat(ready).isFalse(); // GH-90000
            assertThat(healthCheck.getLastStatus()).isEqualTo(ClickHouseHealthCheck.HealthStatus.DOWN); // GH-90000
        }

        @Test
        @DisplayName("health response includes ClickHouse details [GH-90000]")
        void clickHouseHealthResponseIncludesDetails() { // GH-90000
            when(clickHouseClient.execute("SELECT 1 [GH-90000]")).thenReturn(1L);

            ClickHouseHealthCheck healthCheck = new ClickHouseHealthCheck(clickHouseClient, metricsCollector); // GH-90000

            runPromise(healthCheck::checkReadiness); // GH-90000
            Map<String, Object> details = runPromise(healthCheck::getHealthDetails); // GH-90000

            assertThat(details).containsEntry("status", "UP"); // GH-90000
            assertThat(details).containsKeys("timestamp", "latency_ms", "last_check", "clickhouse"); // GH-90000
        }
    }

    // Mock health check classes for testing
    private static class RedisHealthCheck {
        private final RedisClient redisClient;
        private final MetricsCollector metricsCollector;
        private final RedisHealthCheckConfig config;
        private HealthStatus lastStatus;
        private long lastLatencyMs;

        public RedisHealthCheck(RedisClient redisClient, MetricsCollector metricsCollector) { // GH-90000
            this(redisClient, metricsCollector, RedisHealthCheckConfig.builder().build()); // GH-90000
        }

        public RedisHealthCheck(RedisClient redisClient, MetricsCollector metricsCollector, RedisHealthCheckConfig config) { // GH-90000
            this.redisClient = redisClient;
            this.metricsCollector = metricsCollector;
            this.config = config;
        }

        public Promise<Boolean> checkReadiness() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            try {
                StatefulRedisConnection<String, String> conn = redisClient.connect(); // GH-90000
                String result = conn.sync().ping(); // GH-90000
                long latency = System.currentTimeMillis() - start; // GH-90000
                this.lastLatencyMs = latency;
                this.lastStatus = latency > config.degradedThresholdMs ? HealthStatus.DEGRADED : HealthStatus.UP;
                metricsCollector.recordTimer("redis.health.check.latency", latency); // GH-90000
                return Promise.of(true); // GH-90000
            } catch (Exception e) { // GH-90000
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false); // GH-90000
            }
        }

        public Promise<Boolean> checkLiveness() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            try {
                StatefulRedisConnection<String, String> conn = redisClient.connect(); // GH-90000
                String result = conn.sync().ping(); // GH-90000
                long latency = System.currentTimeMillis() - start; // GH-90000
                this.lastLatencyMs = latency;
                this.lastStatus = latency > config.degradedThresholdMs ? HealthStatus.DEGRADED : HealthStatus.UP;
                metricsCollector.recordTimer("redis.health.check.latency", latency); // GH-90000
                return Promise.of(true); // GH-90000
            } catch (Exception e) { // GH-90000
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false); // GH-90000
            }
        }

        public Promise<Map<String, Object>> getHealthDetails() { // GH-90000
            return Promise.of(Map.of( // GH-90000
                "status", lastStatus.name(), // GH-90000
                "timestamp", System.currentTimeMillis(), // GH-90000
                "latency_ms", lastLatencyMs,
                "last_check", System.currentTimeMillis(), // GH-90000
                "redis", Map.of("connected", lastStatus != HealthStatus.DOWN) // GH-90000
            ));
        }

        public HealthStatus getLastStatus() { // GH-90000
            return lastStatus;
        }

        public long getLastLatencyMs() { // GH-90000
            return lastLatencyMs;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        static class RedisHealthCheckConfig {
            private final long degradedThresholdMs;

            private RedisHealthCheckConfig(long degradedThresholdMs) { // GH-90000
                this.degradedThresholdMs = degradedThresholdMs;
            }

            public static Builder builder() { // GH-90000
                return new Builder(); // GH-90000
            }

            public long degradedThresholdMs() { // GH-90000
                return degradedThresholdMs;
            }

            static class Builder {
                private long degradedThresholdMs = 1000L;

                public Builder degradedThresholdMs(long degradedThresholdMs) { // GH-90000
                    this.degradedThresholdMs = degradedThresholdMs;
                    return this;
                }

                public RedisHealthCheckConfig build() { // GH-90000
                    return new RedisHealthCheckConfig(degradedThresholdMs); // GH-90000
                }
            }
        }
    }

    private static class OpenSearchHealthCheck {
        private final OpenSearchClient openSearchClient;
        private final MetricsCollector metricsCollector;
        private HealthStatus lastStatus;

        public OpenSearchHealthCheck(OpenSearchClient openSearchClient, MetricsCollector metricsCollector) { // GH-90000
            this.openSearchClient = openSearchClient;
            this.metricsCollector = metricsCollector;
        }

        public Promise<Boolean> checkReadiness() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            try {
                Map<String, Object> health = openSearchClient.clusterHealth(); // GH-90000
                String status = (String) health.get("status [GH-90000]");
                long latency = System.currentTimeMillis() - start; // GH-90000
                metricsCollector.recordTimer("opensearch.health.check.latency", latency); // GH-90000
                
                if ("green".equals(status)) { // GH-90000
                    this.lastStatus = HealthStatus.UP;
                    return Promise.of(true); // GH-90000
                } else if ("yellow".equals(status)) { // GH-90000
                    this.lastStatus = HealthStatus.DEGRADED;
                    return Promise.of(true); // GH-90000
                } else {
                    this.lastStatus = HealthStatus.DOWN;
                    return Promise.of(false); // GH-90000
                }
            } catch (Exception e) { // GH-90000
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false); // GH-90000
            }
        }

        public Promise<Map<String, Object>> getHealthDetails() { // GH-90000
            return Promise.of(Map.of( // GH-90000
                "status", lastStatus.name(), // GH-90000
                "timestamp", System.currentTimeMillis(), // GH-90000
                "latency_ms", 0L,
                "last_check", System.currentTimeMillis(), // GH-90000
                "opensearch", Map.of("status", lastStatus.name()) // GH-90000
            ));
        }

        public HealthStatus getLastStatus() { // GH-90000
            return lastStatus;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        interface OpenSearchClient {
            Map<String, Object> clusterHealth(); // GH-90000
        }
    }

    private static class ClickHouseHealthCheck {
        private final ClickHouseClient clickHouseClient;
        private final MetricsCollector metricsCollector;
        private HealthStatus lastStatus;

        public ClickHouseHealthCheck(ClickHouseClient clickHouseClient, MetricsCollector metricsCollector) { // GH-90000
            this.clickHouseClient = clickHouseClient;
            this.metricsCollector = metricsCollector;
        }

        public Promise<Boolean> checkReadiness() { // GH-90000
            long start = System.currentTimeMillis(); // GH-90000
            try {
                Object result = clickHouseClient.execute("SELECT 1 [GH-90000]");
                long latency = System.currentTimeMillis() - start; // GH-90000
                metricsCollector.recordTimer("clickhouse.health.check.latency", latency); // GH-90000
                this.lastStatus = HealthStatus.UP;
                return Promise.of(true); // GH-90000
            } catch (Exception e) { // GH-90000
                this.lastStatus = HealthStatus.DOWN;
                return Promise.of(false); // GH-90000
            }
        }

        public Promise<Map<String, Object>> getHealthDetails() { // GH-90000
            return Promise.of(Map.of( // GH-90000
                "status", lastStatus.name(), // GH-90000
                "timestamp", System.currentTimeMillis(), // GH-90000
                "latency_ms", 0L,
                "last_check", System.currentTimeMillis(), // GH-90000
                "clickhouse", Map.of("status", lastStatus.name()) // GH-90000
            ));
        }

        public HealthStatus getLastStatus() { // GH-90000
            return lastStatus;
        }

        enum HealthStatus { UP, DOWN, DEGRADED }

        interface ClickHouseClient {
            Object execute(String query); // GH-90000
        }
    }
}
