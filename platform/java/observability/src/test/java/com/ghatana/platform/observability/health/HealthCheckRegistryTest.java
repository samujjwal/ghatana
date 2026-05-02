package com.ghatana.platform.observability.health;

import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HealthCheckRegistry} — CP-001.2.
 *
 * <p>Verifies the unified health check framework: registration, deregistration,
 * liveness vs. readiness semantics, and aggregation correctness.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the unified health check registry
 * @doc.layer observability
 * @doc.pattern Test
 */
@DisplayName("HealthCheckRegistry")
class HealthCheckRegistryTest extends EventloopTestBase {

    private HealthCheckRegistry registry;

    @BeforeEach
    void setUp() throws Exception { 
        // Reset singleton for test isolation
        resetSingleton(); 
        MetricsRegistry metricsRegistry = MetricsRegistry.initialize( 
            new SimpleMeterRegistry(), 
            io.opentelemetry.api.OpenTelemetry.noop(), 
            "test-service",
            "test",
            "1.0.0"
        );
        registry = HealthCheckRegistry.initialize(metricsRegistry); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        resetSingleton(); 
    }

    private static void resetSingleton() throws Exception { 
        Field instanceField = HealthCheckRegistry.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true); 
        ((AtomicReference<?>) instanceField.get(null)).set(null); 
    }

    // ─── getInstance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance: returns the initialized registry")
    void getInstance_returnsRegistry() { 
        assertThat(HealthCheckRegistry.getInstance()).isSameAs(registry); 
    }

    @Test
    @DisplayName("getInstance: throws if not initialized")
    void getInstance_notInitialized_throws() throws Exception { 
        resetSingleton(); 
        assertThatThrownBy(HealthCheckRegistry::getInstance) 
                .isInstanceOf(IllegalStateException.class) 
                .hasMessageContaining("not initialized");
    }

    // ─── register / unregister ────────────────────────────────────────────────

    @Nested
    @DisplayName("register() / unregister()")
    class RegisterUnregister {

        @Test
        @DisplayName("registered check appears in getHealthCheckNames()")
        void register_checkVisible() { 
            registry.register(stubCheck("db", true, true)); 
            assertThat(registry.getHealthCheckNames()).contains("db");
        }

        @Test
        @DisplayName("unregistered check is removed from getHealthCheckNames()")
        void unregister_checkRemoved() { 
            registry.register(stubCheck("cache", false, true)); 
            registry.unregister("cache");
            assertThat(registry.getHealthCheckNames()).doesNotContain("cache");
        }

        @Test
        @DisplayName("multiple checks can be registered simultaneously")
        void register_multiple() { 
            registry.register(stubCheck("db", true, true)); 
            registry.register(stubCheck("redis", false, true)); 
            assertThat(registry.getHealthCheckNames()).containsExactlyInAnyOrder("db", "redis"); 
        }
    }

    // ─── readiness ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("readiness()")
    class Readiness {

        @Test
        @DisplayName("all healthy checks → overall UP status")
        void readiness_allHealthy_statusUp() { 
            registry.register(stubCheck("s1", false, true)); 
            registry.register(stubCheck("s2", false, true)); 

            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); 

            assertThat(result.isHealthy()).isTrue(); 
            assertThat(result.getStatus()).isEqualTo(HealthCheck.Status.UP); 
        }

        @Test
        @DisplayName("one unhealthy check → overall DOWN status")
        void readiness_oneUnhealthy_statusDown() { 
            registry.register(stubCheck("s1", false, true)); 
            registry.register(unhealthyCheck("s2"));

            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); 

            assertThat(result.isUnhealthy()).isTrue(); 
            assertThat(result.getMessage()).contains("s2");
        }

        @Test
        @DisplayName("empty registry → healthy (no failing checks)")
        void readiness_empty_healthy() { 
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); 
            assertThat(result.isHealthy()).isTrue(); 
        }

        @Test
        @DisplayName("check type is 'readiness'")
        void readiness_checkType_correct() { 
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); 
            assertThat(result.getCheckType()).isEqualTo("readiness");
        }
    }

    // ─── liveness ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("liveness()")
    class Liveness {

        @Test
        @DisplayName("critical internal check healthy → liveness UP")
        void liveness_criticalHealthy_up() { 
            registry.register(stubCheck("jvm-health", true, true)); // critical, healthy 
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.liveness()); 
            assertThat(result.isHealthy()).isTrue(); 
        }

        @Test
        @DisplayName("non-critical checks are excluded from liveness")
        void liveness_nonCritical_excluded() { 
            // Only non-critical check is unhealthy — liveness should still pass
            registry.register(stubNonCriticalUnhealthyCheck("database"));
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.liveness()); 
            assertThat(result.isHealthy()).isTrue(); 
        }
    }

    // ─── runAllChecks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("runAllChecks: returns map keyed by check name")
    void runAllChecks_returnsMap() { 
        registry.register(stubCheck("alpha", false, true)); 
        registry.register(stubCheck("beta", false, true)); 

        java.util.Map<String, HealthCheck.HealthCheckResult> checks =
                runPromise(() -> registry.runAllChecks()); 

        assertThat(checks).containsKeys("alpha", "beta"); 
    }

    // ─── getLastResult ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLastResult: populated after a check runs")
    void getLastResult_afterRun_present() { 
        registry.register(stubCheck("srv", false, true)); 
        runPromise(() -> registry.health()); 
        assertThat(registry.getLastResult("srv")).isPresent();
    }

    @Test
    @DisplayName("getLastResult: empty for unregistered name")
    void getLastResult_unknown_empty() { 
        assertThat(registry.getLastResult("nonexistent")).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static HealthCheck stubCheck(String name, boolean critical, boolean healthy) { 
        return new HealthCheck() { 
            @Override public String getName() { return name; } 
            @Override public boolean isCritical() { return critical; } 
            @Override
            public Promise<HealthCheckResult> check() { 
                return Promise.of(healthy 
                        ? HealthCheckResult.healthy("OK")
                        : HealthCheckResult.unhealthy("FAIL"));
            }
        };
    }

    private static HealthCheck unhealthyCheck(String name) { 
        return stubCheck(name, false, false); 
    }

    private static HealthCheck stubNonCriticalUnhealthyCheck(String name) { 
        return new HealthCheck() { 
            @Override public String getName() { return name; } 
            @Override public boolean isCritical() { return false; } 
            @Override
            public Promise<HealthCheckResult> check() { 
                return Promise.of(HealthCheckResult.unhealthy("FAIL"));
            }
        };
    }
}
