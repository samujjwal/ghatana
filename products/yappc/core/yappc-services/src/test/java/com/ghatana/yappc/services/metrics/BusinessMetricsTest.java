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

    // ── recordPhaseGateValidation ─────────────────────────────────────────────

    @Test
    @DisplayName("recordPhaseGateValidation increments PASS counter with correct tags")
    void recordPhaseGateValidationPassIncrementsCounter() { 
        metrics.recordPhaseGateValidation("tenant-1", "Build", "PASS", 42L); 

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("tenant", "tenant-1") 
                .tag("phase", "Build") 
                .tag("outcome", "PASS") 
                .counter() 
                .count(); 
        assertThat(count).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("recordPhaseGateValidation increments BLOCK counter with correct tags")
    void recordPhaseGateValidationBlockIncrementsCounter() { 
        metrics.recordPhaseGateValidation("tenant-2", "Test", "BLOCK", 10L); 

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("outcome", "BLOCK") 
                .counter() 
                .count(); 
        assertThat(count).isEqualTo(1.0); 
    }

    @Test
    @DisplayName("recordPhaseGateValidation records duration in distribution summary")
    void recordPhaseGateValidationRecordsDuration() { 
        metrics.recordPhaseGateValidation("tenant-1", "Design", "PASS", 75L); 

        var summary = registry.find("yappc.lifecycle.phase.gate.duration.ms")
                .tag("tenant", "tenant-1") 
                .tag("phase", "Design") 
                .summary(); 
        assertThat(summary).isNotNull(); 
        assertThat(summary.count()).isEqualTo(1); 
        assertThat(summary.totalAmount()).isEqualTo(75.0); 
    }

    @Test
    @DisplayName("recordPhaseGateValidation handles null tenant and phase safely")
    void recordPhaseGateValidationHandlesNulls() { 
        metrics.recordPhaseGateValidation(null, null, "PASS", 0L); 

        double count = registry.find("yappc.lifecycle.phase.gate.validations.total")
                .tag("tenant", "unknown") 
                .tag("phase", "unknown") 
                .counter() 
                .count(); 
        assertThat(count).isEqualTo(1.0); 
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    // ── Critical-flow canonical metrics ──────────────────────────────────────

    @Nested
    @DisplayName("recordCriticalFlow")
    class RecordCriticalFlow {

        @Test
        @DisplayName("emits counter with all nine canonical tags")
        void emitsAllNineCanonicalTags() {
            metrics.recordCriticalFlow(
                    BusinessMetrics.METRIC_GENERATION_RUN,
                    "tenant-1", "ws-1", "proj-1",
                    "BUILD", "generate", "SUCCESS",
                    false, null, "corr-abc");

            double count = registry.find(BusinessMetrics.METRIC_GENERATION_RUN)
                    .tag("tenantId",      "tenant-1")
                    .tag("workspaceId",   "ws-1")
                    .tag("projectId",     "proj-1")
                    .tag("phase",         "BUILD")
                    .tag("operation",     "generate")
                    .tag("outcome",       "SUCCESS")
                    .tag("degraded",      "false")
                    .tag("errorClass",    "none")
                    .tag("correlationId", "corr-abc")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("normalises null values to sentinel strings")
        void normalisesNullsToSentinels() {
            metrics.recordCriticalFlow(
                    BusinessMetrics.METRIC_DASHBOARD_ACTION,
                    null, null, null, null, null, null, true, null, null);

            double count = registry.find(BusinessMetrics.METRIC_DASHBOARD_ACTION)
                    .tag("tenantId",      "unknown")
                    .tag("workspaceId",   "unknown")
                    .tag("errorClass",    "none")
                    .tag("correlationId", "none")
                    .tag("degraded",      "true")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        @DisplayName("phase packet build delegates to canonical counter")
        void phasePacketBuildDelegatesToCanonical() {
            metrics.recordPhasePacketBuild("t1", "ws1", "proj1",
                    "DESIGN", "build", "ERROR", true, "TimeoutException", "c1");
            assertThat(registry.find(BusinessMetrics.METRIC_PHASE_PACKET_BUILD)
                    .tag("outcome", "ERROR")
                    .tag("degraded", "true")
                    .tag("errorClass", "TimeoutException")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("generation review with operation=rollback is recorded")
        void generationReviewRollbackIsRecorded() {
            metrics.recordGenerationReview("t1", "ws1", "proj1",
                    "BUILD", "rollback", "SUCCESS", false, null, null);
            assertThat(registry.find(BusinessMetrics.METRIC_GENERATION_REVIEW)
                    .tag("operation", "rollback").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("preview session create and validate are independent counters")
        void previewSessionCreateAndValidateAreIndependent() {
            metrics.recordPreviewSession("t1", "ws1", "p1", "TEST", "create", "SUCCESS", false, null, null);
            metrics.recordPreviewSession("t1", "ws1", "p1", "TEST", "validate", "BLOCKED", false, null, null);

            assertThat(registry.find(BusinessMetrics.METRIC_PREVIEW_SESSION)
                    .tag("operation", "create").counter().count()).isEqualTo(1.0);
            assertThat(registry.find(BusinessMetrics.METRIC_PREVIEW_SESSION)
                    .tag("operation", "validate").counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("degraded source import has degraded=true tag")
        void degradedSourceImportTagged() {
            metrics.recordSourceImport("t1", "ws1", "p1", "INIT", "import", "ERROR", true, "IOException", "c9");
            assertThat(registry.find(BusinessMetrics.METRIC_SOURCE_IMPORT)
                    .tag("degraded",   "true")
                    .tag("errorClass", "IOException")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("all ten flow metrics have independent counters")
        void allFlowMetricsAreIndependent() {
            String[] metricNames = {
                BusinessMetrics.METRIC_PHASE_PACKET_BUILD,
                BusinessMetrics.METRIC_DASHBOARD_ACTION,
                BusinessMetrics.METRIC_GENERATION_RUN,
                BusinessMetrics.METRIC_GENERATION_REVIEW,
                BusinessMetrics.METRIC_PREVIEW_SESSION,
                BusinessMetrics.METRIC_SOURCE_IMPORT,
                BusinessMetrics.METRIC_RESIDUAL_REVIEW,
                BusinessMetrics.METRIC_EVIDENCE_SEARCH,
                BusinessMetrics.METRIC_POLICY_EVALUATION,
                BusinessMetrics.METRIC_LEARNING_PROMOTION,
                BusinessMetrics.METRIC_KERNEL_TRUTH_SOURCE,
                BusinessMetrics.METRIC_GENERATION_ASSURANCE,
            };
            for (String metricName : metricNames) {
                metrics.recordCriticalFlow(metricName, "t", "w", "p", "ph", "op", "OK", false, null, null);
            }
            for (String metricName : metricNames) {
                assertThat(registry.find(metricName).counter()).as("counter for %s", metricName).isNotNull();
            }
        }

        @Test
        @DisplayName("policy deny and evidence miss outcomes are labelled")
        void policyDenyAndEvidenceMissAreLabelled() {
            metrics.recordPolicyEvaluation("tenant-1", "ws-1", "project-1",
                    "VALIDATE", "phase-advance", "DENIED", false, null, "corr-policy-1");
            metrics.recordPlatformEvidenceSearch("tenant-1", "ws-1", "project-1",
                    "VALIDATE", "phase-evidence", "MISS", true, "EvidenceUnavailable", "corr-evidence-1");

            assertThat(registry.find(BusinessMetrics.METRIC_POLICY_EVALUATION)
                    .tag("outcome", "DENIED")
                    .tag("operation", "phase-advance")
                    .tag("correlationId", "corr-policy-1")
                    .counter().count()).isEqualTo(1.0);
            assertThat(registry.find(BusinessMetrics.METRIC_EVIDENCE_SEARCH)
                    .tag("outcome", "MISS")
                    .tag("degraded", "true")
                    .tag("errorClass", "EvidenceUnavailable")
                    .counter().count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("kernel truth source and generation assurance outcomes are labelled")
        void kernelTruthAndGenerationAssuranceAreLabelled() {
            metrics.recordKernelTruthSource("tenant-1", "ws-1", "project-1",
                    "OBSERVE", "data-cloud", "DEGRADED", true, "MalformedTruthRecord", "corr-kernel-1");
            metrics.recordGenerationAssurance("tenant-1", "ws-1", "project-1",
                    "GENERATE", "security", "FAILED", false, "SecurityScanFailure", "corr-assurance-1");

            assertThat(registry.find(BusinessMetrics.METRIC_KERNEL_TRUTH_SOURCE)
                    .tag("operation", "data-cloud")
                    .tag("outcome", "DEGRADED")
                    .tag("errorClass", "MalformedTruthRecord")
                    .counter().count()).isEqualTo(1.0);
            assertThat(registry.find(BusinessMetrics.METRIC_GENERATION_ASSURANCE)
                    .tag("operation", "security")
                    .tag("outcome", "FAILED")
                    .tag("correlationId", "corr-assurance-1")
                    .counter().count()).isEqualTo(1.0);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private double gaugeValue(String name) { 
        var gauge = registry.find(name).gauge(); 
        assertThat(gauge).as("Gauge '%s' must be registered", name).isNotNull(); 
        return gauge.value(); 
    }
}
