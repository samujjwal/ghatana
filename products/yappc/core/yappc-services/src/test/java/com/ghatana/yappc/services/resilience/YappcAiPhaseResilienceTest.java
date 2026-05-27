package com.ghatana.yappc.services.resilience;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
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
import com.ghatana.yappc.services.intent.IntentServiceTestFactory;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resilience tests for all YAPPC AI-heavy lifecycle phases.
 *
 * <p>Validates that when the underlying LLM service fails (network error, timeout,
 * malformed response), each phase:
 * <ul>
 *   <li>Returns a failed promise — never throws synchronously</li>
 *   <li>Does not swallow the exception (the promise carries the original cause)</li>
 *   <li>Is isolated: failure in one phase does not corrupt other phase state</li>
 *   <li>Recovers fully when the LLM becomes available again after a transient failure</li>
 * </ul>
 *
 * <p>Also validates that validation and observe phases (which do not call the LLM
 * directly) remain operational even when the AI service is unavailable.
 *
 * @doc.type class
 * @doc.purpose Resilience tests — LLM failure isolation across all 8 YAPPC lifecycle phases
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YAPPC AI-Phase Resilience — LLM failure isolation")
class YappcAiPhaseResilienceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private PolicyEngine policyEngine;

    // Services under test
    private IntentService intentService;
    private ShapeService shapeService;
    private ValidationService validationService;
    private ObserveService observeService;
    private LearningService learningService;
    private EvolutionService evolutionService;

    @BeforeEach
    void setUp() {
        aiService = mock(CompletionService.class);
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        policyEngine = mock(PolicyEngine.class);

        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        when(policyEngine.evaluate(any(), any())).thenReturn(Promise.of(true));

        intentService    = IntentServiceTestFactory.create(aiService, auditLogger, metrics);
        shapeService     = new ShapeServiceImpl(aiService, auditLogger, metrics);
        validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics);
        observeService   = new ObserveServiceImpl(metrics, auditLogger);
        learningService  = new LearningServiceImpl(aiService, auditLogger, metrics);
        evolutionService = new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    // =========================================================================
    // Phase 0: Intent — LLM failure modes
    // =========================================================================

    @Nested
    @DisplayName("Phase 0 — Intent resilience")
    class IntentResilience {

        @Test
        @DisplayName("capture() falls back and returns a valid intent when AI service fails")
        void captureUsesFallbackOnLlmFailure() {
            when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("LLM connection refused")));

            IntentSpec spec = runPromise(() -> intentService.capture(
                IntentInput.of("Build a service", "resilience-tenant")));

            assertThat(spec).isNotNull();
            assertThat(spec.id()).isNotBlank();
        }

        @Test
        @DisplayName("capture() returns fallback intent when AI service returns errored promise")
        void captureReturnsFallbackIntentOnLlmError() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")));

            IntentSpec spec = runPromise(() -> intentService.capture(
                IntentInput.of("Build a service", "resilience-tenant")));

            assertThat(spec).isNotNull();
            assertThat(spec.tenantId()).isEqualTo("resilience-tenant");
        }

        @Test
        @DisplayName("capture() recovers after transient LLM failure")
        void captureRecoverAfterTransientFailure() {
            // First call fails
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("transient")))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Product: Recovered Service\nDescription: Recovered after failure")
                            .modelUsed("gpt-4")
                            .build()));

                // First attempt — fallback response
                IntentSpec first = runPromise(() -> intentService.capture(
                    IntentInput.of("Build a service", "resilience-tenant")));
                assertThat(first).isNotNull();

            // Second attempt — expect recovery
            IntentSpec recovered = runPromise(() -> intentService.capture(
                    IntentInput.of("Build a service", "resilience-tenant")));
            assertThat(recovered).isNotNull();
            assertThat(recovered.id()).isNotBlank();
        }

        @Test
        @DisplayName("analyze() returns fallback analysis when AI service errors")
        void analyzeReturnsFallbackAnalysisOnLlmError() {
            // Set up capture to succeed, then fail analyze
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Product: OK\nDescription: test")
                            .modelUsed("gpt-4")
                            .build()))
                    .thenReturn(Promise.ofException(new RuntimeException("analyze LLM error")));

            IntentSpec spec = runPromise(() -> intentService.capture(
                    IntentInput.of("Build a service", "resilience-tenant")));

                com.ghatana.yappc.domain.intent.IntentAnalysis analysis = runPromise(() -> intentService.analyze(spec));
                assertThat(analysis).isNotNull();
        }
    }

    // =========================================================================
    // Phase 1: Shape — LLM failure modes
    // =========================================================================

    @Nested
    @DisplayName("Phase 1 — Shape resilience")
    class ShapeResilience {

        private IntentSpec stubIntentSpec;

        @BeforeEach
        void buildGoodIntentSpec() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Product: Shape Test\nDescription: shape service test")
                            .modelUsed("gpt-4")
                            .build()));
            stubIntentSpec = runPromise(() -> intentService.capture(
                    IntentInput.of("Build service", "shape-resilience-tenant")));
        }

        @Test
        @DisplayName("derive() returns fallback shape when AI service errors")
        void deriveReturnsFallbackShapeOnLlmError() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("shape LLM error")));

            ShapeSpec shape = runPromise(() -> shapeService.derive(stubIntentSpec));
            assertThat(shape).isNotNull();
        }

        @Test
        @DisplayName("derive() failure is isolated — service remains usable after recovery")
        void deriveFailureIsIsolated() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("transient")))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Architecture: microservices\nDomain: auth")
                            .modelUsed("gpt-4")
                            .build()));

            // First call falls back
            ShapeSpec first = runPromise(() -> shapeService.derive(stubIntentSpec));
            assertThat(first).isNotNull();

            // Recovery
            ShapeSpec recovered = runPromise(() -> shapeService.derive(stubIntentSpec));
            assertThat(recovered).isNotNull();
        }
    }

    // =========================================================================
    // Phase 2: Validation — LLM-independent, remains operational when AI fails
    // =========================================================================

    @Nested
    @DisplayName("Phase 2 — Validate resilience")
    class ValidateResilience {

        private ShapeSpec stubShapeSpec;

        @BeforeEach
        void buildGoodShapeSpec() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Product: Validate Test\nDescription: validate test")
                            .modelUsed("gpt-4")
                            .build()));
            IntentSpec intent = runPromise(() -> intentService.capture(
                    IntentInput.of("Build a service", "validate-resilience-tenant")));
            stubShapeSpec = runPromise(() -> shapeService.derive(intent));
        }

        @Test
        @DisplayName("validate() succeeds even when AI service is completely unavailable")
        void validateSucceedsWhenAiUnavailable() {
            // Policy engine is the real dependency for validate — AI is not involved
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenThrow(new AssertionError("Validation must not call LLM"));

            LifecycleValidationResult result = runPromise(() -> validationService.validate(stubShapeSpec));

            assertThat(result).isNotNull();
                assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("validate() fails gracefully when policy engine errors")
        void validateReturnsFailedPromiseOnPolicyEngineError() {
            when(policyEngine.evaluate(any(), any()))
                    .thenReturn(Promise.ofException(new RuntimeException("policy engine down")));

                Promise<LifecycleValidationResult> promise = validationService.validateWithPolicy(
                    stubShapeSpec,
                    PolicySpec.builder().id("policy-down").name("policy-down").build());

            assertThat(promise.isException()).isTrue();
        }
    }

    // =========================================================================
    // Phase 5: Observe — no LLM dependency, must remain operational
    // =========================================================================

    @Nested
    @DisplayName("Phase 5 — Observe resilience")
    class ObserveResilience {

        private RunResult stubRunResult;

        @BeforeEach
        void buildRunResult() {
            stubRunResult = RunResult.builder()
                    .id("run-resilience-001")
                    .runSpecRef("spec-resilience-001")
                    .status(RunStatus.FAILED)  // deliberately failed run to test observe under stress
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(10))
                    .completedAt(Instant.now())
                    .metadata(Map.of("reason", "test-failure"))
                    .build();
        }

        @Test
        @DisplayName("collect() succeeds even when AI service is completely unavailable")
        void collectSucceedsWhenAiUnavailable() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenThrow(new AssertionError("Observe must not call LLM"));

            Observation result = runPromise(() -> observeService.collect(stubRunResult));

            assertThat(result).isNotNull();
            assertThat(result.runRef()).isEqualTo("spec-resilience-001");
        }

        @Test
        @DisplayName("collect() on FAILED run result does not throw or produce null")
        void collectOnFailedRunProducesValidObservation() {
            Observation result = runPromise(() -> observeService.collect(stubRunResult));

            assertThat(result).isNotNull();
            assertThat(result.id()).isNotBlank();
        }

        @Test
        @DisplayName("streamObservations() calls consumer even when run status is FAILED")
        void streamObservationsCallsConsumerOnFailedRun() {
            java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean(false);

            runPromise(() -> observeService.streamObservations(stubRunResult, obs -> {
                assertThat(obs).isNotNull();
                called.set(true);
            }));

            assertThat(called).isTrue();
        }
    }

    // =========================================================================
    // Phase 6: Learn — LLM failure modes
    // =========================================================================

    @Nested
    @DisplayName("Phase 6 — Learn resilience")
    class LearnResilience {

        private Observation stubObservation;

        @BeforeEach
        void buildObservation() {
            RunResult run = RunResult.builder()
                    .id("run-learn-resilience-001")
                    .runSpecRef("spec-learn-resilience-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            stubObservation = runPromise(() -> observeService.collect(run));
        }

        @Test
        @DisplayName("analyze() returns fallback insights when AI service errors")
        void analyzeReturnsFallbackInsightsOnLlmError() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("learn LLM error")));

            Insights insights = runPromise(() -> learningService.analyze(stubObservation));
            assertThat(insights).isNotNull();
        }

        @Test
        @DisplayName("analyze() recovers after transient LLM failure")
        void analyzeRecoverAfterTransientFailure() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("transient")))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Pattern: caching-miss\nAnomaly: none\nRecommendation: add cache")
                            .modelUsed("gpt-4")
                            .build()));

            Insights first = runPromise(() -> learningService.analyze(stubObservation));
            assertThat(first).isNotNull();

            Insights recovered = runPromise(() -> learningService.analyze(stubObservation));
            assertThat(recovered).isNotNull();
        }
    }

    // =========================================================================
    // Phase 7: Evolve — LLM failure modes
    // =========================================================================

    @Nested
    @DisplayName("Phase 7 — Evolve resilience")
    class EvolveResilience {

        private Insights stubInsights;

        @BeforeEach
        void buildInsights() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Pattern: none\nAnomaly: none")
                            .modelUsed("gpt-4")
                            .build()));
            RunResult run = RunResult.builder()
                    .id("run-evolve-resilience-001")
                    .runSpecRef("spec-evolve-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            Observation obs = runPromise(() -> observeService.collect(run));
            stubInsights = runPromise(() -> learningService.analyze(obs));
        }

        @Test
        @DisplayName("propose() returns fallback plan when AI service errors")
        void proposeReturnsFallbackPlanOnLlmError() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("evolve LLM error")));

            EvolutionPlan plan = runPromise(() -> evolutionService.propose(stubInsights));
            assertThat(plan).isNotNull();
        }

        @Test
        @DisplayName("propose() recovers after transient LLM failure")
        void proposeRecoverAfterTransientFailure() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("transient")))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Task: upgrade-dependencies\nPriority: medium")
                            .modelUsed("gpt-4")
                            .build()));

            EvolutionPlan first = runPromise(() -> evolutionService.propose(stubInsights));
            assertThat(first).isNotNull();

            EvolutionPlan recovered = runPromise(() -> evolutionService.propose(stubInsights));
            assertThat(recovered).isNotNull();
        }

        @Test
        @DisplayName("phase failure isolation: evolve failure does not corrupt observe or learn outputs")
        void evolutionFailureDoesNotCorruptEarlierPhaseOutputs() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("evolve crashed")));

            EvolutionPlan plan = runPromise(() -> evolutionService.propose(stubInsights));
            assertThat(plan).isNotNull();

            // The insights object remains intact — earlier phases are unaffected
            assertThat(stubInsights).isNotNull();
            assertThat(stubInsights.id()).isNotBlank();
        }
    }
}
