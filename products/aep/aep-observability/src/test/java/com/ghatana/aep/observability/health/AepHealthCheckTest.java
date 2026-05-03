/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.observability.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AepHealthCheck}.
 *
 * <p>Verifies probe registration, aggregate status computation,
 * exception-safe probe evaluation, and the {@link AepHealthCheck#isHealthy()} helper.
 */
@DisplayName("AepHealthCheck")
class AepHealthCheckTest {

    private AepHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new AepHealthCheck();
    }

    // ─── Empty / no-probe baseline ────────────────────────────────────────────

    @Nested
    @DisplayName("No probes registered")
    class NoProbes {

        @Test
        @DisplayName("returns UP when no probes are registered")
        void returnsUpWithNoProbes() {
            AepHealthCheck.HealthResult result = healthCheck.check();
            assertThat(result.status()).isEqualTo(AepHealthCheck.Status.UP);
        }

        @Test
        @DisplayName("returns empty details when no probes are registered")
        void returnsEmptyDetailsWithNoProbes() {
            assertThat(healthCheck.check().details()).isEmpty();
        }

        @Test
        @DisplayName("probeCount returns 0 when no probes registered")
        void probeCountZero() {
            assertThat(healthCheck.probeCount()).isEqualTo(0);
        }
    }

    // ─── Single-probe scenarios ───────────────────────────────────────────────

    @Nested
    @DisplayName("Single probe")
    class SingleProbe {

        @Test
        @DisplayName("UP probe yields UP aggregate")
        void upProbeYieldsUp() {
            healthCheck.registerProbe("state-store", () -> true);

            AepHealthCheck.HealthResult result = healthCheck.check();

            assertThat(result.status()).isEqualTo(AepHealthCheck.Status.UP);
            assertThat(result.details()).containsEntry("state-store", "UP");
        }

        @Test
        @DisplayName("DOWN probe yields DOWN aggregate")
        void downProbeYieldsDown() {
            healthCheck.registerProbe("registry", () -> false);

            AepHealthCheck.HealthResult result = healthCheck.check();

            assertThat(result.status()).isEqualTo(AepHealthCheck.Status.DOWN);
            assertThat(result.details()).containsEntry("registry", "DOWN");
        }

        @Test
        @DisplayName("throwing probe yields DOWN (not exception propagation)")
        void throwingProbeYieldsDown() {
            healthCheck.registerProbe("broken-probe", () -> {
                throw new RuntimeException("simulated failure");
            });

            AepHealthCheck.HealthResult result = healthCheck.check();

            assertThat(result.status()).isEqualTo(AepHealthCheck.Status.DOWN);
            assertThat(result.details()).containsEntry("broken-probe", "DOWN");
        }
    }

    // ─── Multi-probe aggregation ──────────────────────────────────────────────

    @Nested
    @DisplayName("Multiple probes")
    class MultipleProbes {

        @Test
        @DisplayName("all UP probes yield UP aggregate")
        void allUpYieldsUp() {
            healthCheck.registerProbe("state-store", () -> true);
            healthCheck.registerProbe("pipeline",    () -> true);
            healthCheck.registerProbe("registry",    () -> true);

            assertThat(healthCheck.check().status()).isEqualTo(AepHealthCheck.Status.UP);
        }

        @Test
        @DisplayName("one DOWN probe among UP probes yields DOWN aggregate")
        void oneDownYieldsDown() {
            healthCheck.registerProbe("state-store", () -> true);
            healthCheck.registerProbe("pipeline",    () -> false);
            healthCheck.registerProbe("registry",    () -> true);

            AepHealthCheck.HealthResult result = healthCheck.check();

            assertThat(result.status()).isEqualTo(AepHealthCheck.Status.DOWN);
            assertThat(result.details()).containsEntry("pipeline", "DOWN");
            assertThat(result.details()).containsEntry("state-store", "UP");
        }

        @Test
        @DisplayName("details contain all registered probe names")
        void detailsContainAllProbeNames() {
            healthCheck.registerProbe("probe-a", () -> true);
            healthCheck.registerProbe("probe-b", () -> false);

            assertThat(healthCheck.check().details()).containsKeys("probe-a", "probe-b");
        }
    }

    // ─── Probe registration / deregistration ─────────────────────────────────

    @Nested
    @DisplayName("Probe registration")
    class ProbeRegistration {

        @Test
        @DisplayName("re-registering a probe replaces the previous one")
        void reRegistrationReplacesProbe() {
            healthCheck.registerProbe("dynamic", () -> false);
            healthCheck.registerProbe("dynamic", () -> true);  // replace

            assertThat(healthCheck.check().status()).isEqualTo(AepHealthCheck.Status.UP);
        }

        @Test
        @DisplayName("deregistering a probe removes it from future checks")
        void deregistrationRemovesProbe() {
            healthCheck.registerProbe("transient", () -> false);
            healthCheck.deregisterProbe("transient");

            assertThat(healthCheck.check().status()).isEqualTo(AepHealthCheck.Status.UP);
            assertThat(healthCheck.check().details()).doesNotContainKey("transient");
        }

        @Test
        @DisplayName("probeCount reflects current probe count")
        void probeCountReflectsCount() {
            healthCheck.registerProbe("a", () -> true);
            healthCheck.registerProbe("b", () -> true);
            assertThat(healthCheck.probeCount()).isEqualTo(2);

            healthCheck.deregisterProbe("a");
            assertThat(healthCheck.probeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("registerProbe with null name throws NullPointerException")
        void registerNullNameThrows() {
            assertThatThrownBy(() -> healthCheck.registerProbe(null, () -> true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("registerProbe with null probe throws NullPointerException")
        void registerNullProbeThrows() {
            assertThatThrownBy(() -> healthCheck.registerProbe("probe", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─── isHealthy helper ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("isHealthy()")
    class IsHealthy {

        @Test
        @DisplayName("returns true when all probes are UP")
        void trueWhenAllUp() {
            healthCheck.registerProbe("p1", () -> true);
            assertThat(healthCheck.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("returns false when any probe is DOWN")
        void falseWhenAnyDown() {
            healthCheck.registerProbe("p1", () -> false);
            assertThat(healthCheck.isHealthy()).isFalse();
        }
    }

    // ─── HealthResult immutability ────────────────────────────────────────────

    @Test
    @DisplayName("HealthResult.details() is unmodifiable")
    void healthResultDetailsIsUnmodifiable() {
        healthCheck.registerProbe("probe", () -> true);
        AepHealthCheck.HealthResult result = healthCheck.check();

        assertThatThrownBy(() -> result.details().put("injected", "UP"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("HealthResult.checkedAt() is not null")
    void healthResultCheckedAtNotNull() {
        assertThat(healthCheck.check().checkedAt()).isNotNull();
    }
}
