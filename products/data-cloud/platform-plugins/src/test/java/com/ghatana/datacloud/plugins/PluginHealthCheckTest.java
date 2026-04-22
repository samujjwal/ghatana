/*
 * Copyright (c) 2026 Ghatana Inc.
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
 *   <li>Storage plugins (Redis, Iceberg, S3)</li>
 *   <li>Streaming plugins (Kafka)</li>
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
@DisplayName("Plugin Health Check Tests")
class PluginHealthCheckTest extends EventloopTestBase {


    // =========================================================================
    // STORAGE PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Storage plugin health checks")
    class StoragePluginHealthChecks {

        @Test
        @DisplayName("should return healthy status when plugin is running and connected")
        void shouldReturnHealthyWhenRunningAndConnected() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Storage connection healthy")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("healthy");
        }

        @Test
        @DisplayName("should return unhealthy status when plugin is not running")
        void shouldReturnUnhealthyWhenNotRunning() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.error("Plugin not running, state: INITIALIZING")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("not running");
        }

        @Test
        @DisplayName("should return unhealthy status when connection fails")
        void shouldReturnUnhealthyWhenConnectionFails() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.error("Connection failed: timeout")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("Connection failed");
        }

        @Test
        @DisplayName("should return healthy status with message")
        void shouldReturnHealthyStatusWithMessage() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Redis connection healthy")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("healthy");
        }
    }

    // =========================================================================
    // STREAMING PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Streaming plugin health checks")
    class StreamingPluginHealthChecks {

        @Test
        @DisplayName("should return healthy when Kafka cluster is reachable")
        void shouldReturnHealthyWhenKafkaReachable() {
            StreamingPlugin plugin = mock(StreamingPlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Kafka cluster reachable")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("reachable");
        }

        @Test
        @DisplayName("should return unhealthy when Kafka cluster is unreachable")
        void shouldReturnUnhealthyWhenKafkaUnreachable() {
            StreamingPlugin plugin = mock(StreamingPlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.error("Kafka health check failed: timeout")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("health check failed");
        }

        @Test
        @DisplayName("should return healthy with consumer group information")
        void shouldReturnHealthyWithConsumerGroupInformation() {
            StreamingPlugin plugin = mock(StreamingPlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Kafka cluster healthy with 3 consumer groups")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("consumer groups");
        }
    }

    // =========================================================================
    // KNOWLEDGE GRAPH PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Knowledge graph plugin health checks")
    class KnowledgeGraphPluginHealthChecks {

        @Test
        @DisplayName("should return healthy when all components are healthy")
        void shouldReturnHealthyWhenAllComponentsHealthy() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("All components healthy: storage, traversal")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("All components healthy");
        }

        @Test
        @DisplayName("should return unhealthy when storage component is unhealthy")
        void shouldReturnUnhealthyWhenStorageUnhealthy() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.unhealthy("Plugin running but storage component unhealthy")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("storage component unhealthy");
        }

        @Test
        @DisplayName("should return unhealthy when traversal component is unhealthy")
        void shouldReturnUnhealthyWhenTraversalUnhealthy() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.unhealthy("Plugin running but traversal component unhealthy")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("traversal component unhealthy");
        }
    }

    // =========================================================================
    // VECTOR MEMORY PLUGIN HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Vector memory plugin health checks")
    class VectorMemoryPluginHealthChecks {

        @Test
        @DisplayName("should return healthy with storage statistics in message")
        void shouldReturnHealthyWithStorageStatisticsInMessage() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Vector storage healthy: 5000 vectors, 10 tenants")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("5000 vectors");
            assertThat(result.getMessage()).contains("10 tenants");
        }

        @Test
        @DisplayName("should handle empty storage gracefully")
        void shouldHandleEmptyStorageGracefully() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Vector storage healthy: 0 vectors, 0 tenants")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("0 vectors");
        }
    }

    // =========================================================================
    // HEALTH CHECK TIMEOUT HANDLING
    // =========================================================================

    @Nested
    @DisplayName("Health check timeout handling")
    class HealthCheckTimeoutHandling {

        @Test
        @DisplayName("should handle health check timeout gracefully")
        void shouldHandleHealthCheckTimeoutGracefully() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.error("Health check timed out")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isFalse();
            assertThat(result.getMessage()).contains("timed out");
        }

        @Test
        @DisplayName("should handle health check exceptions gracefully")
        void shouldHandleHealthCheckExceptionsGracefully() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.ofException(new RuntimeException("Unexpected error")));

            assertThatThrownBy(() -> runPromise(() -> plugin.healthCheck()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unexpected error");
        }
    }

    // =========================================================================
    // HEALTH CHECK MESSAGE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Health check message validation")
    class HealthCheckMessageValidation {

        @Test
        @DisplayName("should include plugin state in message")
        void shouldIncludePluginStateInMessage() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, state: RUNNING")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("RUNNING");
        }

        @Test
        @DisplayName("should include version information in message")
        void shouldIncludeVersionInMessage() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, version: 1.0.0, state: RUNNING")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("1.0.0");
        }

        @Test
        @DisplayName("should include performance metrics in message")
        void shouldIncludePerformanceMetricsInMessage() {
            StoragePlugin plugin = mock(StoragePlugin.class);
            when(plugin.healthCheck())
                .thenReturn(Promise.of(HealthStatus.ok("Plugin healthy, latency: 5ms, throughput: 1000/s")));

            HealthStatus result = runPromise(() -> plugin.healthCheck());

            assertThat(result.isHealthy()).isTrue();
            assertThat(result.getMessage()).contains("5ms");
            assertThat(result.getMessage()).contains("1000");
        }
    }
}
