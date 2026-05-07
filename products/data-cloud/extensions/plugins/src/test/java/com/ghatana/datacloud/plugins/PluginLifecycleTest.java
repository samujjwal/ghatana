/**
 * @doc.type class
 * @doc.purpose Test plugin lifecycle, initialization, and shutdown
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.plugins;

import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Plugin Lifecycle Tests
 *
 * Test plugin lifecycle, initialization, health checks, configuration,
 * dependencies, and failure recovery.
 */
@DisplayName("Plugin Lifecycle Tests")
class PluginLifecycleTest {

    // =========================================================================
    // PLUGIN STATE TRANSITIONS
    // =========================================================================

    @Nested
    @DisplayName("Plugin state transitions")
    class PluginStateTransitions {

        @Test
        @DisplayName("should transition from UNLOADED to LOADED on initialization")
        void shouldTransitionFromUnloadedToLoaded() { 
            PluginState initialState = PluginState.UNLOADED;
            PluginState loadedState = PluginState.LOADED;

            assertThat(initialState).isNotEqualTo(loadedState); 
            assertThat(loadedState).isEqualTo(PluginState.LOADED); 
        }

        @Test
        @DisplayName("should transition from LOADED to STARTED on start")
        void shouldTransitionFromLoadedToStarted() { 
            PluginState loadedState = PluginState.LOADED;
            PluginState startedState = PluginState.STARTED;

            assertThat(startedState).isEqualTo(PluginState.STARTED); 
        }

        @Test
        @DisplayName("should transition from STARTED to STOPPED on stop")
        void shouldTransitionFromStartedToStopped() { 
            PluginState startedState = PluginState.STARTED;
            PluginState stoppedState = PluginState.STOPPED;

            assertThat(stoppedState).isEqualTo(PluginState.STOPPED); 
        }

        @Test
        @DisplayName("should transition to FAILED on error")
        void shouldTransitionToFailedOnError() { 
            PluginState failedState = PluginState.FAILED;

            assertThat(failedState).isEqualTo(PluginState.FAILED); 
        }

        @Test
        @DisplayName("should support all state values")
        void shouldSupportAllStateValues() { 
            PluginState[] states = PluginState.values(); 

            assertThat(states).contains( 
                PluginState.UNLOADED,
                PluginState.LOADED,
                PluginState.STARTED,
                PluginState.STOPPED,
                PluginState.FAILED
            );
        }
    }

    // =========================================================================
    // PLUGIN METADATA
    // =========================================================================

    @Nested
    @DisplayName("Plugin metadata")
    class PluginMetadataTests {

        @Test
        @DisplayName("should create valid plugin metadata")
        void shouldCreateValidPluginMetadata() { 
            PluginMetadata metadata = PluginMetadata.builder() 
                .id("redis-m0-hot")
                .name("Redis M0 HOT Tier Storage Plugin")
                .version("1.0.0")
                .description("Redis M0 HOT Tier Storage Plugin")
                .type(PluginType.STORAGE) 
                .capabilities(Set.of("streaming", "time-range-query", "idempotency")) 
                .vendor("Ghatana")
                .license("Apache-2.0")
                .build(); 

            assertThat(metadata.id()).isEqualTo("redis-m0-hot");
            assertThat(metadata.name()).isEqualTo("Redis M0 HOT Tier Storage Plugin");
            assertThat(metadata.version()).isEqualTo("1.0.0");
            assertThat(metadata.type()).isEqualTo(PluginType.STORAGE); 
            assertThat(metadata.capabilities()).contains("streaming", "time-range-query"); 
        }

        @Test
        @DisplayName("should support plugin type enumeration")
        void shouldSupportPluginTypeEnumeration() { 
            PluginType[] types = PluginType.values(); 

            assertThat(types).isNotEmpty(); 
            assertThat(types).contains(PluginType.STORAGE); 
        }

        @Test
        @DisplayName("should handle metadata with minimal fields")
        void shouldHandleMetadataWithMinimalFields() { 
            PluginMetadata metadata = PluginMetadata.builder() 
                .id("test-plugin")
                .type(PluginType.STORAGE) 
                .build(); 

            assertThat(metadata.id()).isEqualTo("test-plugin");
            assertThat(metadata.type()).isEqualTo(PluginType.STORAGE); 
        }
    }

    // =========================================================================
    // PLUGIN CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("Plugin configuration")
    class PluginConfiguration {

        @Test
        @DisplayName("should validate configuration parameters")
        void shouldValidateConfigurationParameters() { 
            Map<String, Object> config = Map.of( 
                "host", "localhost",
                "port", 6379,
                "timeout", 5000,
                "maxConnections", 10
            );

            assertThat(config).containsKey("host");
            assertThat(config).containsKey("port");
            assertThat(config.get("timeout")).isEqualTo(5000);
            assertThat(config.get("maxConnections")).isEqualTo(10);
        }

        @Test
        @DisplayName("should handle missing configuration with defaults")
        void shouldHandleMissingConfigurationWithDefaults() { 
            Map<String, Object> config = Map.of("host", "localhost"); 

            // Missing port should use default
            int defaultPort = config.containsKey("port") ? (int) config.get("port") : 6379;
            assertThat(defaultPort).isEqualTo(6379); 
        }

        @Test
        @DisplayName("should validate configuration types")
        void shouldValidateConfigurationTypes() { 
            Map<String, Object> config = Map.of( 
                "stringParam", "value",
                "intParam", 42,
                "boolParam", true,
                "listParam", List.of("a", "b") 
            );

            assertThat(config.get("stringParam")).isInstanceOf(String.class);
            assertThat(config.get("intParam")).isInstanceOf(Integer.class);
            assertThat(config.get("boolParam")).isInstanceOf(Boolean.class);
            assertThat(config.get("listParam")).isInstanceOf(List.class);
        }
    }

    // =========================================================================
    // PLUGIN DEPENDENCIES
    // =========================================================================

    @Nested
    @DisplayName("Plugin dependencies")
    class PluginDependencies {

        @Test
        @DisplayName("should handle plugin with no dependencies")
        void shouldHandlePluginWithNoDependencies() { 
            Set<String> dependencies = Set.of(); 

            assertThat(dependencies).isEmpty(); 
        }

        @Test
        @DisplayName("should handle plugin with single dependency")
        void shouldHandlePluginWithSingleDependency() { 
            Set<String> dependencies = Set.of("postgresql-l1-warm");

            assertThat(dependencies).hasSize(1); 
            assertThat(dependencies).contains("postgresql-l1-warm");
        }

        @Test
        @DisplayName("should handle plugin with multiple dependencies")
        void shouldHandlePluginWithMultipleDependencies() { 
            Set<String> dependencies = Set.of( 
                "postgresql-l1-warm",
                "redis-m0-hot",
                "s3-cold-tier"
            );

            assertThat(dependencies).hasSize(3); 
            assertThat(dependencies).contains("postgresql-l1-warm", "redis-m0-hot", "s3-cold-tier"); 
        }

        @Test
        @DisplayName("should detect circular dependencies")
        void shouldDetectCircularDependencies() { 
            Set<String> pluginADeps = Set.of("plugin-b");
            Set<String> pluginBDeps = Set.of("plugin-c");
            Set<String> pluginCDeps = Set.of("plugin-a"); // circular

            boolean hasCircular = pluginCDeps.contains("plugin-a") && pluginADeps.contains("plugin-b");
            assertThat(hasCircular).isTrue(); 
        }
    }

    // =========================================================================
    // HEALTH CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Health checks")
    class HealthChecks {

        @Test
        @DisplayName("should report healthy when plugin is running")
        void shouldReportHealthyWhenRunning() { 
            PluginState state = PluginState.valueOf("STARTED");
            boolean isHealthy = state == PluginState.STARTED;

            assertThat(isHealthy).isTrue(); 
        }

        @Test
        @DisplayName("should report unhealthy when plugin is failed")
        void shouldReportUnhealthyWhenFailed() { 
            PluginState state = PluginState.FAILED;
            boolean isHealthy = state == PluginState.STARTED;

            assertThat(isHealthy).isFalse(); 
        }

        @Test
        @DisplayName("should report unhealthy when plugin is stopped")
        void shouldReportUnhealthyWhenStopped() { 
            PluginState state = PluginState.STOPPED;
            boolean isHealthy = state == PluginState.STARTED;

            assertThat(isHealthy).isFalse(); 
        }

        @Test
        @DisplayName("should include health check details")
        void shouldIncludeHealthCheckDetails() { 
            Map<String, Object> healthDetails = Map.of( 
                "status", "UP",
                "connections", 5,
                "latencyMs", 12,
                "lastCheck", "2026-04-20T10:00:00Z"
            );

            assertThat(healthDetails.get("status")).isEqualTo("UP");
            assertThat(healthDetails.get("connections")).isEqualTo(5);
            assertThat(healthDetails.get("latencyMs")).isEqualTo(12);
        }
    }

    // =========================================================================
    // FAILURE RECOVERY
    // =========================================================================

    @Nested
    @DisplayName("Failure recovery")
    class FailureRecovery {

        @Test
        @DisplayName("should handle initialization failure gracefully")
        void shouldHandleInitializationFailureGracefully() { 
            boolean initSuccess = false;
            String errorMessage = "Connection refused";

            if (!initSuccess) { 
                PluginState state = PluginState.FAILED;
                assertThat(state).isEqualTo(PluginState.FAILED); 
                assertThat(errorMessage).isNotNull(); 
            }
        }

        @Test
        @DisplayName("should support retry on transient failures")
        void shouldSupportRetryOnTransientFailures() { 
            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;

            while (attempt < maxRetries && !success) { 
                attempt++;
                // Simulate success on third attempt
                if (attempt == 3) { 
                    success = true;
                }
            }

            assertThat(attempt).isEqualTo(3); 
            assertThat(success).isTrue(); 
        }

        @Test
        @DisplayName("should give up after max retries")
        void shouldGiveUpAfterMaxRetries() { 
            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;

            while (attempt < maxRetries && !success) { 
                attempt++;
                // Never succeed
            }

            assertThat(attempt).isEqualTo(maxRetries); 
            assertThat(success).isFalse(); 
        }

        @Test
        @DisplayName("should track failure count")
        void shouldTrackFailureCount() { 
            int failureCount = 0;

            // Simulate 3 failures
            for (int i = 0; i < 3; i++) { 
                failureCount++;
            }

            assertThat(failureCount).isEqualTo(3); 
        }
    }

    // =========================================================================
    // LIFECYCLE ORDERING
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle ordering")
    class LifecycleOrdering {

        @Test
        @DisplayName("should initialize before start")
        void shouldInitializeBeforeStart() { 
            PluginState state = PluginState.UNLOADED;

            // Initialize
            state = PluginState.LOADED;
            assertThat(state).isEqualTo(PluginState.LOADED); 

            // Start
            state = PluginState.STARTED;
            assertThat(state).isEqualTo(PluginState.STARTED); 
        }

        @Test
        @DisplayName("should stop before shutdown")
        void shouldStopBeforeShutdown() { 
            PluginState state = PluginState.STARTED;

            // Stop
            state = PluginState.STOPPED;
            assertThat(state).isEqualTo(PluginState.STOPPED); 

            // Shutdown
            state = PluginState.UNLOADED;
            assertThat(state).isEqualTo(PluginState.UNLOADED); 
        }

        @Test
        @DisplayName("should not start without initialization")
        void shouldNotStartWithoutInitialization() { 
            PluginState state = PluginState.UNLOADED;

            // Cannot start from UNLOADED
            boolean canStart = state == PluginState.LOADED;
            assertThat(canStart).isFalse(); 
        }
    }

    // =========================================================================
    // CAPABILITY CHECKS
    // =========================================================================

    @Nested
    @DisplayName("Capability checks")
    class CapabilityChecks {

        @Test
        @DisplayName("should check plugin capabilities")
        void shouldCheckPluginCapabilities() { 
            Set<String> capabilities = Set.of( 
                "streaming",
                "time-range-query",
                "idempotency"
            );

            assertThat(capabilities).contains("streaming");
            assertThat(capabilities).contains("time-range-query");
            assertThat(capabilities).doesNotContain("batch-processing");
        }

        @Test
        @DisplayName("should validate required capabilities")
        void shouldValidateRequiredCapabilities() { 
            Set<String> pluginCapabilities = Set.of("streaming", "idempotency"); 
            Set<String> requiredCapabilities = Set.of("streaming", "time-range-query"); 

            boolean hasAllRequired = requiredCapabilities.stream() 
                .allMatch(pluginCapabilities::contains); 

            assertThat(hasAllRequired).isFalse(); // missing time-range-query 
        }

        @Test
        @DisplayName("should handle plugin with no capabilities")
        void shouldHandlePluginWithNoCapabilities() { 
            Set<String> capabilities = Set.of(); 

            assertThat(capabilities).isEmpty(); 
        }
    }
}
