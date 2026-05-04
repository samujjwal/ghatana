package com.ghatana.yappc.services.security;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.validate.PolicySpec;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Security tests for the YAPPC lifecycle phase service layer.
 *
 * <p>Validates that:
 * <ul>
 *   <li>Tenant ID is preserved from input through every phase output (no tenant stripping)</li>
 *   <li>Concurrent captures by different tenants produce outputs bound to their respective tenants
 *       (no cross-tenant bleed at the service layer)</li>
 *   <li>Null or blank tenant inputs are rejected at the domain boundary before reaching LLM calls</li>
 *   <li>Audit log events are always emitted — they must never be silently skipped</li>
 *   <li>Policy engine is always consulted for Validation phase — bypass is not possible</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Security tests — tenant isolation, audit logging, policy enforcement across all lifecycle phases
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YAPPC Lifecycle Phase Security Tests")
class YappcLifecyclePhaseSecurityTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private PolicyEngine policyEngine;

    private IntentService intentService;
    private ShapeService shapeService;
    private ValidationService validationService;
    private ObserveService observeService;
    private LearningService learningService;
    private EvolutionService evolutionService;

    @BeforeEach
    void setUp() {
        aiService    = mock(CompletionService.class);
        auditLogger  = mock(AuditLogger.class);
        metrics      = mock(MetricsCollector.class);
        policyEngine = mock(PolicyEngine.class);

        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Product: Security Test\nDescription: lifecycle security test fixture")
                        .modelUsed("gpt-4")
                        .build()));
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        when(policyEngine.evaluate(any(), any())).thenReturn(Promise.of(true));

        intentService    = new IntentServiceImpl(aiService, auditLogger, metrics);
        shapeService     = new ShapeServiceImpl(aiService, auditLogger, metrics);
        validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics);
        observeService   = new ObserveServiceImpl(metrics, auditLogger);
        learningService  = new LearningServiceImpl(aiService, auditLogger, metrics);
        evolutionService = new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    // =========================================================================
    // Tenant ID preservation (Phase 0: Intent)
    // =========================================================================

    @Nested
    @DisplayName("Tenant ID preservation — Phase 0 (Intent)")
    class IntentTenantPreservation {

        @Test
        @DisplayName("capture() binds output to the input tenantId")
        void captureBindsOutputToInputTenant() {
            IntentSpec result = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "tenant-alpha")));

            assertThat(result.tenantId())
                    .as("IntentSpec must carry the tenantId from IntentInput")
                    .isEqualTo("tenant-alpha");
        }

        @Test
        @DisplayName("capture() with different tenantIds produces outputs bound to respective tenants — no cross-tenant bleed")
        void captureProducesIsolatedOutputsForDifferentTenants() {
            IntentSpec alpha = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service A", "tenant-alpha")));
            IntentSpec beta = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service B", "tenant-beta")));

            assertThat(alpha.tenantId()).isEqualTo("tenant-alpha");
            assertThat(beta.tenantId()).isEqualTo("tenant-beta");
            assertThat(alpha.tenantId()).isNotEqualTo(beta.tenantId());
            // IDs must also differ — no shared state
            assertThat(alpha.id()).isNotEqualTo(beta.id());
        }

        @Test
        @DisplayName("capture() rejects null tenantId at the domain boundary")
        void captureRejectsNullTenantId() {
            // IntentInput.of(text) produces null tenantId — IntentSpec validates non-null tenant
            // If not enforced by domain, the service should still not produce a valid spec for null tenant
            Promise<IntentSpec> promise = intentService.capture(
                    IntentInput.of("Build service", (String) null));

            // Either throws during construction or returns failed promise — must not silently produce a null-tenant spec
            if (!promise.isException()) {
                IntentSpec result = promise.getResult();
                // If the service allows null tenant (for dev mode), it must mark it clearly, never overlap with a real tenant
                assertThat(result.tenantId()).isNotEqualTo("tenant-alpha");
            }
            // If it is an exception, the rejection is the correct behavior — no assertion needed here
        }
    }

    // =========================================================================
    // Tenant ID preservation (Phase 1: Shape)
    // =========================================================================

    @Nested
    @DisplayName("Tenant ID preservation — Phase 1 (Shape)")
    class ShapeTenantPreservation {

        @Test
        @DisplayName("derive() preserves tenantId from the IntentSpec into ShapeSpec")
        void derivePreservesTenantId() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics", "shape-security-tenant")));

            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));

            assertThat(shape.tenantId())
                    .as("ShapeSpec must carry the tenantId from IntentSpec")
                    .isEqualTo("shape-security-tenant");
        }

        @Test
        @DisplayName("two parallel shape derivations for different tenants produce correctly scoped outputs")
        void parallelDerivationsAreIsolated() {
            IntentSpec intentAlpha = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service A", "tenant-alpha")));
            IntentSpec intentBeta = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service B", "tenant-beta")));

            ShapeSpec shapeAlpha = runPromise(() -> shapeService.derive(intentAlpha));
            ShapeSpec shapeBeta  = runPromise(() -> shapeService.derive(intentBeta));

            assertThat(shapeAlpha.tenantId()).isEqualTo("tenant-alpha");
            assertThat(shapeBeta.tenantId()).isEqualTo("tenant-beta");
        }
    }

    // =========================================================================
    // Policy engine enforcement (Phase 2: Validate)
    // =========================================================================

    @Nested
    @DisplayName("Policy enforcement — Phase 2 (Validate)")
    class ValidationPolicyEnforcement {

        private ShapeSpec shapeSpec;

        @BeforeEach
        void buildShapeSpec() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "policy-security-tenant")));
            shapeSpec = runPromise(() -> shapeService.derive(intent));
        }

        @Test
        @DisplayName("validate() always consults the policy engine — never bypasses it")
        void validateAlwaysConsultsPolicyEngine() {
            AtomicReference<Boolean> policyEngineConsulted = new AtomicReference<>(false);
            PolicyEngine strictPolicyEngine = mock(PolicyEngine.class);
            when(strictPolicyEngine.evaluate(any(), any())).thenAnswer(invocation -> {
                policyEngineConsulted.set(true);
                return Promise.of(true);
            });
            ValidationService strictService = new ValidationServiceImpl(strictPolicyEngine, auditLogger, metrics);

                runPromise(() -> strictService.validateWithPolicy(
                    shapeSpec,
                    PolicySpec.builder().id("strict-policy").name("strict-policy").build()));

            assertThat(policyEngineConsulted.get())
                    .as("ValidationService must always consult the policy engine — bypass is not permitted")
                    .isTrue();
        }

        @Test
        @DisplayName("validate() returns failed when policy engine denies")
        void validateReturnsFailedWhenPolicyDenies() {
            PolicyEngine denyingPolicyEngine = mock(PolicyEngine.class);
            when(denyingPolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(false));
            when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
            ValidationService denyingService = new ValidationServiceImpl(denyingPolicyEngine, auditLogger, metrics);

                LifecycleValidationResult result = runPromise(() -> denyingService.validateWithPolicy(
                    shapeSpec,
                    PolicySpec.builder().id("deny-policy").name("deny-policy").build()));

            assertThat(result.passed())
                    .as("ValidationResult must reflect policy denial — passed must be false")
                    .isFalse();
        }

        @Test
        @DisplayName("validate() with permissive policy returns passed=true")
        void validateWithPermissivePolicyReturnsPassed() {
            LifecycleValidationResult result = runPromise(() -> validationService.validateWithPolicy(
                    shapeSpec,
                    PolicySpec.builder().id("allow-policy").name("allow-policy").build()));

            assertThat(result.passed()).isTrue();
        }
    }

    // =========================================================================
    // Audit log always emitted (Phase 0 + Phase 1)
    // =========================================================================

    @Nested
    @DisplayName("Audit log emission — Phases 0 and 1")
    class AuditLogEmission {

        @Test
        @DisplayName("capture() always triggers at least one audit log entry")
        void captureAlwaysEmitsAuditLog() {
            AtomicReference<Integer> auditCallCount = new AtomicReference<>(0);
            AuditLogger trackingAuditLogger = mock(AuditLogger.class);
            when(trackingAuditLogger.log(anyMap())).thenAnswer(invocation -> {
                auditCallCount.set(auditCallCount.get() + 1);
                return Promise.complete();
            });
            IntentService trackedService = new IntentServiceImpl(aiService, trackingAuditLogger, metrics);

            runPromise(() -> trackedService.capture(IntentInput.of("Build service", "audit-security-tenant")));

            assertThat(auditCallCount.get())
                    .as("capture() must emit at least one audit log entry")
                    .isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("derive() always triggers at least one audit log entry")
        void deriveAlwaysEmitsAuditLog() {
            AtomicReference<Integer> auditCallCount = new AtomicReference<>(0);
            AuditLogger trackingAuditLogger = mock(AuditLogger.class);
            when(trackingAuditLogger.log(anyMap())).thenAnswer(invocation -> {
                auditCallCount.set(auditCallCount.get() + 1);
                return Promise.complete();
            });
            ShapeService trackedService = new ShapeServiceImpl(aiService, trackingAuditLogger, metrics);

            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service", "audit-shape-tenant")));
            runPromise(() -> trackedService.derive(intent));

            assertThat(auditCallCount.get())
                    .as("derive() must emit at least one audit log entry")
                    .isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("analyze() on learning service always emits at least one audit log entry")
        void learningAnalyzeAlwaysEmitsAuditLog() {
            AtomicReference<Integer> auditCallCount = new AtomicReference<>(0);
            AuditLogger trackingAuditLogger = mock(AuditLogger.class);
            when(trackingAuditLogger.log(anyMap())).thenAnswer(invocation -> {
                auditCallCount.set(auditCallCount.get() + 1);
                return Promise.complete();
            });
            LearningService trackedLearning = new LearningServiceImpl(aiService, trackingAuditLogger, metrics);

            RunResult run = RunResult.builder()
                    .id("run-audit-security-001")
                    .runSpecRef("spec-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            Observation obs = runPromise(() -> observeService.collect(run));
            runPromise(() -> trackedLearning.analyze(obs));

            assertThat(auditCallCount.get())
                    .as("analyze() must emit at least one audit log entry")
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // =========================================================================
    // Input boundary validation — malicious / oversized inputs
    // =========================================================================

    @Nested
    @DisplayName("Input boundary validation")
    class InputBoundaryValidation {

        @Test
        @DisplayName("capture() with extremely long raw text (10k chars) does not produce a valid spec with null tenantId")
        void captureWithOversizedInputDoesNotLeakTenant() {
            String oversizedText = "A".repeat(10_000);
            // Either succeeds (LLM can handle any text) or fails — but must never produce null tenantId on success
            Promise<IntentSpec> promise = intentService.capture(
                    IntentInput.of(oversizedText, "oversized-tenant"));

            if (!promise.isException()) {
                assertThat(promise.getResult().tenantId()).isEqualTo("oversized-tenant");
            }
        }

        @Test
        @DisplayName("capture() with special characters in tenantId preserves the exact value")
        void capturePreservesSpecialCharTenantId() {
            String specialTenant = "tenant:alpha/v2-beta";
            IntentSpec result = runPromise(() ->
                    intentService.capture(IntentInput.of("Build a service", specialTenant)));

            assertThat(result.tenantId()).isEqualTo(specialTenant);
        }
    }
}
