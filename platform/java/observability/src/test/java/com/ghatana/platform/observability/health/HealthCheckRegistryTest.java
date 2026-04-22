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
@DisplayName("HealthCheckRegistry [GH-90000]")
class HealthCheckRegistryTest extends EventloopTestBase {

    private HealthCheckRegistry registry;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        // Reset singleton for test isolation
        resetSingleton(); // GH-90000
        MetricsRegistry metricsRegistry = MetricsRegistry.initialize( // GH-90000
            new SimpleMeterRegistry(), // GH-90000
            io.opentelemetry.api.OpenTelemetry.noop(), // GH-90000
            "test-service",
            "test",
            "1.0.0"
        );
        registry = HealthCheckRegistry.initialize(metricsRegistry); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        resetSingleton(); // GH-90000
    }

    private static void resetSingleton() throws Exception { // GH-90000
        Field instanceField = HealthCheckRegistry.class.getDeclaredField("INSTANCE [GH-90000]");
        instanceField.setAccessible(true); // GH-90000
        ((AtomicReference<?>) instanceField.get(null)).set(null); // GH-90000
    }

    // ─── getInstance ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInstance: returns the initialized registry [GH-90000]")
    void getInstance_returnsRegistry() { // GH-90000
        assertThat(HealthCheckRegistry.getInstance()).isSameAs(registry); // GH-90000
    }

    @Test
    @DisplayName("getInstance: throws if not initialized [GH-90000]")
    void getInstance_notInitialized_throws() throws Exception { // GH-90000
        resetSingleton(); // GH-90000
        assertThatThrownBy(HealthCheckRegistry::getInstance) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("not initialized [GH-90000]");
    }

    // ─── register / unregister ────────────────────────────────────────────────

    @Nested
    @DisplayName("register() / unregister() [GH-90000]")
    class RegisterUnregister {

        @Test
        @DisplayName("registered check appears in getHealthCheckNames() [GH-90000]")
        void register_checkVisible() { // GH-90000
            registry.register(stubCheck("db", true, true)); // GH-90000
            assertThat(registry.getHealthCheckNames()).contains("db [GH-90000]");
        }

        @Test
        @DisplayName("unregistered check is removed from getHealthCheckNames() [GH-90000]")
        void unregister_checkRemoved() { // GH-90000
            registry.register(stubCheck("cache", false, true)); // GH-90000
            registry.unregister("cache [GH-90000]");
            assertThat(registry.getHealthCheckNames()).doesNotContain("cache [GH-90000]");
        }

        @Test
        @DisplayName("multiple checks can be registered simultaneously [GH-90000]")
        void register_multiple() { // GH-90000
            registry.register(stubCheck("db", true, true)); // GH-90000
            registry.register(stubCheck("redis", false, true)); // GH-90000
            assertThat(registry.getHealthCheckNames()).containsExactlyInAnyOrder("db", "redis"); // GH-90000
        }
    }

    // ─── readiness ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("readiness() [GH-90000]")
    class Readiness {

        @Test
        @DisplayName("all healthy checks → overall UP status [GH-90000]")
        void readiness_allHealthy_statusUp() { // GH-90000
            registry.register(stubCheck("s1", false, true)); // GH-90000
            registry.register(stubCheck("s2", false, true)); // GH-90000

            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
            assertThat(result.getStatus()).isEqualTo(HealthCheck.Status.UP); // GH-90000
        }

        @Test
        @DisplayName("one unhealthy check → overall DOWN status [GH-90000]")
        void readiness_oneUnhealthy_statusDown() { // GH-90000
            registry.register(stubCheck("s1", false, true)); // GH-90000
            registry.register(unhealthyCheck("s2 [GH-90000]"));

            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); // GH-90000

            assertThat(result.isUnhealthy()).isTrue(); // GH-90000
            assertThat(result.getMessage()).contains("s2 [GH-90000]");
        }

        @Test
        @DisplayName("empty registry → healthy (no failing checks) [GH-90000]")
        void readiness_empty_healthy() { // GH-90000
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); // GH-90000
            assertThat(result.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("check type is 'readiness' [GH-90000]")
        void readiness_checkType_correct() { // GH-90000
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.readiness()); // GH-90000
            assertThat(result.getCheckType()).isEqualTo("readiness [GH-90000]");
        }
    }

    // ─── liveness ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("liveness() [GH-90000]")
    class Liveness {

        @Test
        @DisplayName("critical internal check healthy → liveness UP [GH-90000]")
        void liveness_criticalHealthy_up() { // GH-90000
            registry.register(stubCheck("jvm-health", true, true)); // critical, healthy // GH-90000
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.liveness()); // GH-90000
            assertThat(result.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("non-critical checks are excluded from liveness [GH-90000]")
        void liveness_nonCritical_excluded() { // GH-90000
            // Only non-critical check is unhealthy — liveness should still pass
            registry.register(stubNonCriticalUnhealthyCheck("database [GH-90000]"));
            HealthCheckRegistry.OverallHealthResult result =
                    runPromise(() -> registry.liveness()); // GH-90000
            assertThat(result.isHealthy()).isTrue(); // GH-90000
        }
    }

    // ─── runAllChecks ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("runAllChecks: returns map keyed by check name [GH-90000]")
    void runAllChecks_returnsMap() { // GH-90000
        registry.register(stubCheck("alpha", false, true)); // GH-90000
        registry.register(stubCheck("beta", false, true)); // GH-90000

        java.util.Map<String, HealthCheck.HealthCheckResult> checks =
                runPromise(() -> registry.runAllChecks()); // GH-90000

        assertThat(checks).containsKeys("alpha", "beta"); // GH-90000
    }

    // ─── getLastResult ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLastResult: populated after a check runs [GH-90000]")
    void getLastResult_afterRun_present() { // GH-90000
        registry.register(stubCheck("srv", false, true)); // GH-90000
        runPromise(() -> registry.health()); // GH-90000
        assertThat(registry.getLastResult("srv [GH-90000]")).isPresent();
    }

    @Test
    @DisplayName("getLastResult: empty for unregistered name [GH-90000]")
    void getLastResult_unknown_empty() { // GH-90000
        assertThat(registry.getLastResult("nonexistent [GH-90000]")).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static HealthCheck stubCheck(String name, boolean critical, boolean healthy) { // GH-90000
        return new HealthCheck() { // GH-90000
            @Override public String getName() { return name; } // GH-90000
            @Override public boolean isCritical() { return critical; } // GH-90000
            @Override
            public Promise<HealthCheckResult> check() { // GH-90000
                return Promise.of(healthy // GH-90000
                        ? HealthCheckResult.healthy("OK [GH-90000]")
                        : HealthCheckResult.unhealthy("FAIL [GH-90000]"));
            }
        };
    }

    private static HealthCheck unhealthyCheck(String name) { // GH-90000
        return stubCheck(name, false, false); // GH-90000
    }

    private static HealthCheck stubNonCriticalUnhealthyCheck(String name) { // GH-90000
        return new HealthCheck() { // GH-90000
            @Override public String getName() { return name; } // GH-90000
            @Override public boolean isCritical() { return false; } // GH-90000
            @Override
            public Promise<HealthCheckResult> check() { // GH-90000
                return Promise.of(HealthCheckResult.unhealthy("FAIL [GH-90000]"));
            }
        };
    }
}
