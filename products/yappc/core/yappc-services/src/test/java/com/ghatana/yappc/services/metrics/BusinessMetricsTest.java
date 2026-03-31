package com.ghatana.yappc.services.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BusinessMetrics}.
 *
 * <p>Uses a {@link SimpleMeterRegistry} so that counter and gauge values can be
 * read back without a running Prometheus scrape endpoint. No async code — no
 * {@code EventloopTestBase} required.
 */
@DisplayName("BusinessMetrics")
class BusinessMetricsTest {

    private MeterRegistry  registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics  = new BusinessMetrics(registry);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws on null MeterRegistry")
    void constructorThrowsOnNull() {
        assertThatThrownBy(() -> new BusinessMetrics(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("gauges start at zero after construction")
    void gaugesStartAtZero() {
        assertThat(gaugeValue("yappc.lifecycle.projects.active")).isEqualTo(0.0);
        assertThat(gaugeValue("yappc.lifecycle.approvals.pending")).isEqualTo(0.0);
        assertThat(gaugeValue("yappc.lifecycle.tenants.active")).isEqualTo(0.0);
    }

    // ── Gauge setters ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setActiveProjects")
    class SetActiveProjects {

        @Test
        @DisplayName("updates gauge to the given value")
        void updatesGauge() {
            metrics.setActiveProjects(42);
            assertThat(gaugeValue("yappc.lifecycle.projects.active")).isEqualTo(42.0);
        }

        @Test
        @DisplayName("clamps negative value to zero")
        void clampsNegativeToZero() {
            metrics.setActiveProjects(-5);
            assertThat(gaugeValue("yappc.lifecycle.projects.active")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("setPendingApprovals")
    class SetPendingApprovals {

        @Test
        @DisplayName("updates gauge to the given value")
        void updatesGauge() {
            metrics.setPendingApprovals(7);
            assertThat(gaugeValue("yappc.lifecycle.approvals.pending")).isEqualTo(7.0);
        }

        @Test
        @DisplayName("clamps negative value to zero")
        void clampsNegativeToZero() {
            metrics.setPendingApprovals(-1);
            assertThat(gaugeValue("yappc.lifecycle.approvals.pending")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("setActiveTenants")
    class SetActiveTenants {

        @Test
        @DisplayName("updates gauge to the given value")
        void updatesGauge() {
            metrics.setActiveTenants(15);
            assertThat(gaugeValue("yappc.lifecycle.tenants.active")).isEqualTo(15.0);
        }
    }

    // ── recordPhaseTransition ─────────────────────────────────────────────────

    @Nested
    @DisplayName("recordPhaseTransition")
    class RecordPhaseTransition {

        @Test
        @DisplayName("increments phase transition counter for the given tags")
        void incrementsCounter() {
            metrics.recordPhaseTransition("tenant-1", "INIT", "DESIGN");

            double count = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("tenant", "tenant-1")
                    .tag("from_phase", "INIT")
                    .tag("to_phase", "DESIGN")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increments independently per tag combination")
        void incrementsIndependentlyPerTags() {
            metrics.recordPhaseTransition("t1", "INIT", "DESIGN");
            metrics.recordPhaseTransition("t1", "DESIGN", "BUILD");
            metrics.recordPhaseTransition("t1", "INIT", "DESIGN");

            double initDesign = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("from_phase", "INIT").tag("to_phase", "DESIGN").counter().count();
            double designBuild = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("from_phase", "DESIGN").tag("to_phase", "BUILD").counter().count();

            assertThat(initDesign).isEqualTo(2.0);
            assertThat(designBuild).isEqualTo(1.0);
        }
    }

    // ── recordApprovalEvent ───────────────────────────────────────────────────

    @Test
    @DisplayName("recordApprovalEvent increments approvals counter")
    void recordApprovalEventIncrementsCounter() {
        metrics.recordApprovalEvent("tenant-1", "APPROVED", "human");

        double count = registry.find("yappc.lifecycle.approvals.total")
                .tag("outcome", "APPROVED")
                .tag("gate_type", "human")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    // ── recordScaffoldGeneration ──────────────────────────────────────────────

    @Test
    @DisplayName("recordScaffoldGeneration increments scaffold counter")
    void recordScaffoldGenerationIncrementsCounter() {
        metrics.recordScaffoldGeneration("tenant-1", "react-app", true);

        double count = registry.find("yappc.lifecycle.scaffold.generations.total")
                .tag("status", "success")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    // ── recordPolicyViolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("recordPolicyViolation increments policy violations counter")
    void recordPolicyViolationIncrementsCounter() {
        metrics.recordPolicyViolation("tenant-1", "NO_TESTS_POLICY", "project-abc");

        double count = registry.find("yappc.lifecycle.policy.violations.total")
                .tag("policy_id", "NO_TESTS_POLICY")
                .counter()
                .count();
        assertThat(count).isEqualTo(1.0);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double gaugeValue(String name) {
        var gauge = registry.find(name).gauge();
        assertThat(gauge).as("Gauge '%s' must be registered", name).isNotNull();
        return gauge.value();
    }
}
