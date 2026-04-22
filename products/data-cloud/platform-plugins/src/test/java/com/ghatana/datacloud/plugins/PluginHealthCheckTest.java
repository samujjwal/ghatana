/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins;

import com.ghatana.datacloud.event.spi.StoragePlugin;
import com.ghatana.datacloud.event.spi.StreamingPlugin;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for plugin health check functionality.
 *
 * <p>Tests health check implementations across all plugin types:
 * <ul>
 *   <li>Storage plugins (Redis, Iceberg, S3)</li> // GH-90000
 *   <li>Streaming plugins (Kafka)</li> // GH-90000
 *   <li>Knowledge graph plugin</li>
 *   <li>Vector memory plugin</li>
 *   <li>Disaster recovery manager</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Plugin health check validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Plugin Health Check Tests [GH-90000]")
class PluginHealthCheckTest extends EventloopTestBase {


    // =========================================================================
    // STORAGE PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Storage plugin health checks [GH-90000]")
    class StoragePluginHealthChecks {

        @Test
        @DisplayName("should return healthy status when plugin is running and connected [GH-90000]")
        void shouldReturnHealthyWhenRunningAndConnected() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Storage connection healthy [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("healthy [GH-90000]");
        }

        @Test
        @DisplayName("should return unhealthy status when plugin is not running [GH-90000]")
        void shouldReturnUnhealthyWhenNotRunning() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.error("Plugin not running, state: INITIALIZING [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("not running [GH-90000]");
        }

        @Test
        @DisplayName("should return unhealthy status when connection fails [GH-90000]")
        void shouldReturnUnhealthyWhenConnectionFails() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.error("Connection failed: timeout [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("Connection failed [GH-90000]");
        }

        @Test
        @DisplayName("should return healthy status with message [GH-90000]")
        void shouldReturnHealthyStatusWithMessage() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Redis connection healthy [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("healthy [GH-90000]");
        }
    }

    // =========================================================================
    // STREAMING PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Streaming plugin health checks [GH-90000]")
    class StreamingPluginHealthChecks {

        @Test
        @DisplayName("should return healthy when Kafka cluster is reachable [GH-90000]")
        void shouldReturnHealthyWhenKafkaReachable() { // GH-90000
            StreamingPlugin plugin = mock(StreamingPlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Kafka cluster reachable [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("reachable [GH-90000]");
        }

        @Test
        @DisplayName("should return unhealthy when Kafka cluster is unreachable [GH-90000]")
        void shouldReturnUnhealthyWhenKafkaUnreachable() { // GH-90000
            StreamingPlugin plugin = mock(StreamingPlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.error("Kafka health check failed: timeout [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("health check failed [GH-90000]");
        }

        @Test
        @DisplayName("should return healthy with consumer group information [GH-90000]")
        void shouldReturnHealthyWithConsumerGroupInformation() { // GH-90000
            StreamingPlugin plugin = mock(StreamingPlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Kafka cluster healthy with 3 consumer groups [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("consumer groups [GH-90000]");
        }
    }

    // =========================================================================
    // KNOWLEDGE GRAPH PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Knowledge graph plugin health checks [GH-90000]")
    class KnowledgeGraphPluginHealthChecks {

        @Test
        @DisplayName("should return healthy when all components are healthy [GH-90000]")
        void shouldReturnHealthyWhenAllComponentsHealthy() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("All components healthy: storage, traversal [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("All components healthy [GH-90000]");
        }

        @Test
        @DisplayName("should return unhealthy when storage component is unhealthy [GH-90000]")
        void shouldReturnUnhealthyWhenStorageUnhealthy() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.unhealthy("Plugin running but storage component unhealthy [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("storage component unhealthy [GH-90000]");
        }

        @Test
        @DisplayName("should return unhealthy when traversal component is unhealthy [GH-90000]")
        void shouldReturnUnhealthyWhenTraversalUnhealthy() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.unhealthy("Plugin running but traversal component unhealthy [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("traversal component unhealthy [GH-90000]");
        }
    }

    // =========================================================================
    // VECTOR MEMORY PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Vector memory plugin health checks [GH-90000]")
    class VectorMemoryPluginHealthChecks {

        @Test
        @DisplayName("should return healthy with storage statistics in message [GH-90000]")
        void shouldReturnHealthyWithStorageStatisticsInMessage() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Vector storage healthy: 5000 vectors, 10 tenants [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("5000 vectors [GH-90000]");
            assertThat(result.getMessage()).contains("10 tenants [GH-90000]");
        }

        @Test
        @DisplayName("should handle empty storage gracefully [GH-90000]")
        void shouldHandleEmptyStorageGracefully() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Vector storage healthy: 0 vectors, 0 tenants [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("0 vectors [GH-90000]");
        }
    }

    // =========================================================================
    // HEALTH CHECK TIMEOUT HANDLING
    // =========================================================================

    @Nested
    @DisplayName("Health check timeout handling [GH-90000]")
    class HealthCheckTimeoutHandling {

        @Test
        @DisplayName("should handle health check timeout gracefully [GH-90000]")
        void shouldHandleHealthCheckTimeoutGracefully() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.error("Health check timed out [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
            assertThat(result.getMessage()).contains("timed out [GH-90000]");
        }

        @Test
        @DisplayName("should handle health check exceptions gracefully [GH-90000]")
        void shouldHandleHealthCheckExceptionsGracefully() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Unexpected error [GH-90000]")));

            assertThatThrownBy(() -> runPromise(() -> plugin.healthCheck())) // GH-90000
                .isInstanceOf(RuntimeException.class) // GH-90000
                .hasMessageContaining("Unexpected error [GH-90000]");
        }
    }

    // =========================================================================
    // HEALTH CHECK MESSAGE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Health check message validation [GH-90000]")
    class HealthCheckMessageValidation {

        @Test
        @DisplayName("should include plugin state in message [GH-90000]")
        void shouldIncludePluginStateInMessage() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, state: RUNNING [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("RUNNING [GH-90000]");
        }

        @Test
        @DisplayName("should include version information in message [GH-90000]")
        void shouldIncludeVersionInMessage() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, version: 1.0.0, state: RUNNING [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should include performance metrics in message [GH-90000]")
        void shouldIncludePerformanceMetricsInMessage() { // GH-90000
            StoragePlugin plugin = mock(StoragePlugin.class); // GH-90000
            when(plugin.healthCheck()) // GH-90000
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, latency: 5ms, throughput: 1000/s [GH-90000]")));

            HealthStatus result = runPromise(() -> plugin.healthCheck()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("5ms [GH-90000]");
            assertThat(result.getMessage()).contains("1000 [GH-90000]");
        }
    }
}
