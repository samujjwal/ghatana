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
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        metrics  = new BusinessMetrics(registry); // GH-90000
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor throws on null MeterRegistry")
    void constructorThrowsOnNull() { // GH-90000
        assertThatThrownBy(() -> new BusinessMetrics(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("gauges start at zero after construction")
    void gaugesStartAtZero() { // GH-90000
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
        void updatesGauge() { // GH-90000
            metrics.setActiveProjects(42); // GH-90000
            assertThat(gaugeValue("yappc.lifecycle.projects.active")).isEqualTo(42.0);
        }

        @Test
        @DisplayName("clamps negative value to zero")
        void clampsNegativeToZero() { // GH-90000
            metrics.setActiveProjects(-5); // GH-90000
            assertThat(gaugeValue("yappc.lifecycle.projects.active")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("setPendingApprovals")
    class SetPendingApprovals {

        @Test
        @DisplayName("updates gauge to the given value")
        void updatesGauge() { // GH-90000
            metrics.setPendingApprovals(7); // GH-90000
            assertThat(gaugeValue("yappc.lifecycle.approvals.pending")).isEqualTo(7.0);
        }

        @Test
        @DisplayName("clamps negative value to zero")
        void clampsNegativeToZero() { // GH-90000
            metrics.setPendingApprovals(-1); // GH-90000
            assertThat(gaugeValue("yappc.lifecycle.approvals.pending")).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("setActiveTenants")
    class SetActiveTenants {

        @Test
        @DisplayName("updates gauge to the given value")
        void updatesGauge() { // GH-90000
            metrics.setActiveTenants(15); // GH-90000
            assertThat(gaugeValue("yappc.lifecycle.tenants.active")).isEqualTo(15.0);
        }
    }

    // ── recordPhaseTransition ─────────────────────────────────────────────────

    @Nested
    @DisplayName("recordPhaseTransition")
    class RecordPhaseTransition {

        @Test
        @DisplayName("increments phase transition counter for the given tags")
        void incrementsCounter() { // GH-90000
            metrics.recordPhaseTransition("tenant-1", "INIT", "DESIGN"); // GH-90000

            double count = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("tenant", "tenant-1") // GH-90000
                    .tag("from_phase", "INIT") // GH-90000
                    .tag("to_phase", "DESIGN") // GH-90000
                    .counter() // GH-90000
                    .count(); // GH-90000
            assertThat(count).isEqualTo(1.0); // GH-90000
        }

        @Test
        @DisplayName("increments independently per tag combination")
        void incrementsIndependentlyPerTags() { // GH-90000
            metrics.recordPhaseTransition("t1", "INIT", "DESIGN"); // GH-90000
            metrics.recordPhaseTransition("t1", "DESIGN", "BUILD"); // GH-90000
            metrics.recordPhaseTransition("t1", "INIT", "DESIGN"); // GH-90000

            double initDesign = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("from_phase", "INIT").tag("to_phase", "DESIGN").counter().count(); // GH-90000
            double designBuild = registry.find("yappc.lifecycle.phase.transitions.total")
                    .tag("from_phase", "DESIGN").tag("to_phase", "BUILD").counter().count(); // GH-90000

            assertThat(initDesign).isEqualTo(2.0); // GH-90000
            assertThat(designBuild).isEqualTo(1.0); // GH-90000
        }
    }

    // ── recordApprovalEvent ───────────────────────────────────────────────────

    @Test
    @DisplayName("recordApprovalEvent increments approvals counter")
    void recordApprovalEventIncrementsCounter() { // GH-90000
        metrics.recordApprovalEvent("tenant-1", "APPROVED", "human"); // GH-90000

        double count = registry.find("yappc.lifecycle.approvals.total")
                .tag("outcome", "APPROVED") // GH-90000
                .tag("gate_type", "human") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    // ── recordScaffoldGeneration ──────────────────────────────────────────────

    @Test
    @DisplayName("recordScaffoldGeneration increments scaffold counter")
    void recordScaffoldGenerationIncrementsCounter() { // GH-90000
        metrics.recordScaffoldGeneration("tenant-1", "react-app", true); // GH-90000

        double count = registry.find("yappc.lifecycle.scaffold.generations.total")
                .tag("status", "success") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    // ── recordPolicyViolation ─────────────────────────────────────────────────

    @Test
    @DisplayName("recordPolicyViolation increments policy violations counter")
    void recordPolicyViolationIncrementsCounter() { // GH-90000
        metrics.recordPolicyViolation("tenant-1", "NO_TESTS_POLICY", "project-abc"); // GH-90000

        double count = registry.find("yappc.lifecycle.policy.violations.total")
                .tag("policy_id", "NO_TESTS_POLICY") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    // ── recordPhaseGateValidation ─────────────────────────────────────────────

    @Test
    @DisplayName("recordPhaseGateValidation increments PASS counter with correct tags")
    void recordPhaseGateValidationPassIncrementsCounter() { // GH-90000
        metrics.recordPhaseGateValidation("tenant-1", "Build", "PASS", 42L); // GH-90000

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("tenant", "tenant-1") // GH-90000
                .tag("phase", "Build") // GH-90000
                .tag("outcome", "PASS") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("recordPhaseGateValidation increments BLOCK counter with correct tags")
    void recordPhaseGateValidationBlockIncrementsCounter() { // GH-90000
        metrics.recordPhaseGateValidation("tenant-2", "Test", "BLOCK", 10L); // GH-90000

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("outcome", "BLOCK") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("recordPhaseGateValidation records duration in distribution summary")
    void recordPhaseGateValidationRecordsDuration() { // GH-90000
        metrics.recordPhaseGateValidation("tenant-1", "Design", "PASS", 75L); // GH-90000

        var summary = registry.find("yappc.lifecycle.phase.gate.duration.ms")
                .tag("tenant", "tenant-1") // GH-90000
                .tag("phase", "Design") // GH-90000
                .summary(); // GH-90000
        assertThat(summary).isNotNull(); // GH-90000
        assertThat(summary.count()).isEqualTo(1); // GH-90000
        assertThat(summary.totalAmount()).isEqualTo(75.0); // GH-90000
    }

    @Test
    @DisplayName("recordPhaseGateValidation handles null tenant and phase safely")
    void recordPhaseGateValidationHandlesNulls() { // GH-90000
        metrics.recordPhaseGateValidation(null, null, "PASS", 0L); // GH-90000

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("tenant", "unknown") // GH-90000
                .tag("phase", "unknown") // GH-90000
                .counter() // GH-90000
                .count(); // GH-90000
        assertThat(count).isEqualTo(1.0); // GH-90000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double gaugeValue(String name) { // GH-90000
        var gauge = registry.find(name).gauge(); // GH-90000
        assertThat(gauge).as("Gauge '%s' must be registered", name).isNotNull(); // GH-90000
        return gauge.value(); // GH-90000
    }
}
