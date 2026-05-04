package com.ghatana.yappc.services.performance;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Performance baseline regression tests for all six AI-heavy YAPPC lifecycle phases:
 * Intent (capture/analyze), Shape (derive/generateModel), Validate, Observe (collect),
 * Learn (analyze), and Evolve (propose).
 *
 * <p>Thresholds are conservative wall-clock limits meant to catch catastrophic regressions
 * and accidental O(n²) loops. These tests use a synchronous in-process mock LLM so the
 * measured time is pure service logic overhead.
 *
 * <p>Covered phases:
 * <ul>
 *   <li>Phase 0: Intent — capture + analyze</li>
 *   <li>Phase 1: Shape — derive + generateModel</li>
 *   <li>Phase 2: Validate — validate + validateWithPolicy</li>
 *   <li>Phase 5: Observe — collect</li>
 *   <li>Phase 6: Learn — analyze</li>
 *   <li>Phase 7: Evolve — propose</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Performance baselines for YAPPC AI-heavy lifecycle phases (0-2, 5-7)
 * @doc.layer test
 * @doc.pattern Test
 */
@Tag("performance")
@DisplayName("YAPPC AI-Phase Performance Baselines")
class YappcAiPhasePerformanceBaselineTest extends EventloopTestBase {

    private static final long SINGLE_BUDGET_MS = 250;
    private static final long BATCH_30_BUDGET_MS = 5_000;

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

        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Product: Perf Test Service\nDescription: A service under performance testing")
                        .modelUsed("perf-test-model")
                        .build()));
        when(aiService.getConfig()).thenReturn(LLMConfiguration.builder()
                .apiKey("test-key")
                .modelName("perf-test-model")
                .build());
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        when(policyEngine.evaluate(any(), any())).thenReturn(Promise.of(true));

        intentService   = new IntentServiceImpl(aiService, auditLogger, metrics);
        shapeService    = new ShapeServiceImpl(aiService, auditLogger, metrics);
        validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics);
        observeService  = new ObserveServiceImpl(metrics, auditLogger);
        learningService = new LearningServiceImpl(aiService, auditLogger, metrics);
        evolutionService = new EvolutionServiceImpl(aiService, auditLogger, metrics);
    }

    // =========================================================================
    // Phase 0: Intent Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 0 — Intent")
    class IntentPhase {

        @Test
        @DisplayName("single intent capture completes within " + SINGLE_BUDGET_MS + "ms")
        void singleIntentCaptureWithinBudget() {
            IntentInput input = IntentInput.of("Build a payment processing service", "perf-tenant");

            long start = System.currentTimeMillis();
            IntentSpec result = runPromise(() -> intentService.capture(input));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(result.tenantId()).isEqualTo("perf-tenant");
            assertThat(elapsed)
                    .as("intent capture must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 30 intent captures completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchIntentCaptureWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 30; i++) {
                final int idx = i;
                IntentInput input = IntentInput.of("Build service-" + idx, "tenant-" + idx);
                IntentSpec result = runPromise(() -> intentService.capture(input));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("30 intent captures must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }

        @Test
        @DisplayName("single intent analyze completes within " + SINGLE_BUDGET_MS + "ms")
        void singleIntentAnalyzeWithinBudget() {
            IntentInput input = IntentInput.of("Build auth service", "perf-tenant");
            IntentSpec spec = runPromise(() -> intentService.capture(input));

            long start = System.currentTimeMillis();
            var analysis = runPromise(() -> intentService.analyze(spec));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(analysis).isNotNull();
            assertThat(elapsed)
                    .as("intent analyze must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }
    }

    // =========================================================================
    // Phase 1: Shape Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 1 — Shape")
    class ShapePhase {

        private IntentSpec intentSpec;

        @BeforeEach
        void buildIntentSpec() {
            intentSpec = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics platform", "shape-perf-tenant")));
        }

        @Test
        @DisplayName("single shape derivation completes within " + SINGLE_BUDGET_MS + "ms")
        void singleShapeDeriveWithinBudget() {
            long start = System.currentTimeMillis();
            ShapeSpec result = runPromise(() -> shapeService.derive(intentSpec));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(elapsed)
                    .as("shape derive must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 20 shape derivations completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchShapeDeriveWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                ShapeSpec result = runPromise(() -> shapeService.derive(intentSpec));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("20 shape derivations must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }

        @Test
        @DisplayName("single system model generation completes within " + SINGLE_BUDGET_MS + "ms")
        void singleSystemModelGenerationWithinBudget() {
            ShapeSpec shapeSpec = runPromise(() -> shapeService.derive(intentSpec));

            long start = System.currentTimeMillis();
            var model = runPromise(() -> shapeService.generateModel(shapeSpec));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(model).isNotNull();
            assertThat(elapsed)
                    .as("system model generation must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }
    }

    // =========================================================================
    // Phase 2: Validation Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 2 — Validate")
    class ValidatePhase {

        private ShapeSpec shapeSpec;

        @BeforeEach
        void buildShapeSpec() {
            IntentSpec intentSpec = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "validate-perf-tenant")));
            shapeSpec = runPromise(() -> shapeService.derive(intentSpec));
        }

        @Test
        @DisplayName("single validation completes within " + SINGLE_BUDGET_MS + "ms")
        void singleValidationWithinBudget() {
            long start = System.currentTimeMillis();
            LifecycleValidationResult result = runPromise(() -> validationService.validate(shapeSpec));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(elapsed)
                    .as("validation must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 30 validations completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchValidationWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 30; i++) {
                LifecycleValidationResult result = runPromise(() -> validationService.validate(shapeSpec));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("30 validations must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }
    }

    // =========================================================================
    // Phase 5: Observe Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 5 — Observe")
    class ObservePhase {

        private RunResult stubRunResult;

        @BeforeEach
        void buildRunResult() {
            stubRunResult = RunResult.builder()
                    .id("run-perf-001")
                    .runSpecRef("spec-perf-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of("env", "ci"))
                    .build();
        }

        @Test
        @DisplayName("single observation collection completes within " + SINGLE_BUDGET_MS + "ms")
        void singleObserveCollectWithinBudget() {
            long start = System.currentTimeMillis();
            Observation result = runPromise(() -> observeService.collect(stubRunResult));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
                assertThat(result.runRef()).isEqualTo("spec-perf-001");
            assertThat(elapsed)
                    .as("observation collect must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 30 observation collections completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchObserveCollectWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 30; i++) {
                Observation result = runPromise(() -> observeService.collect(stubRunResult));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("30 observation collections must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }
    }

    // =========================================================================
    // Phase 6: Learn Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 6 — Learn")
    class LearnPhase {

        private Observation stubObservation;

        @BeforeEach
        void buildObservation() {
            RunResult runResult = RunResult.builder()
                    .id("run-learn-perf-001")
                    .runSpecRef("spec-learn-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(10))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            stubObservation = runPromise(() -> observeService.collect(runResult));
        }

        @Test
        @DisplayName("single learning analysis completes within " + SINGLE_BUDGET_MS + "ms")
        void singleLearningAnalysisWithinBudget() {
            long start = System.currentTimeMillis();
            Insights result = runPromise(() -> learningService.analyze(stubObservation));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(result.observationRef()).isEqualTo(stubObservation.id());
            assertThat(elapsed)
                    .as("learning analysis must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 20 learning analyses completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchLearningAnalysisWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                Insights result = runPromise(() -> learningService.analyze(stubObservation));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("20 learning analyses must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }
    }

    // =========================================================================
    // Phase 7: Evolve Service
    // =========================================================================

    @Nested
    @DisplayName("Phase 7 — Evolve")
    class EvolvePhase {

        private Insights stubInsights;

        @BeforeEach
        void buildInsights() {
            RunResult runResult = RunResult.builder()
                    .id("run-evolve-perf-001")
                    .runSpecRef("spec-evolve-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(15))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            Observation obs = runPromise(() -> observeService.collect(runResult));
            stubInsights = runPromise(() -> learningService.analyze(obs));
        }

        @Test
        @DisplayName("single evolution proposal completes within " + SINGLE_BUDGET_MS + "ms")
        void singleEvolutionProposeWithinBudget() {
            long start = System.currentTimeMillis();
            EvolutionPlan result = runPromise(() -> evolutionService.propose(stubInsights));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(result).isNotNull();
            assertThat(elapsed)
                    .as("evolution propose must complete within %dms — actual: %dms", SINGLE_BUDGET_MS, elapsed)
                    .isLessThan(SINGLE_BUDGET_MS);
        }

        @Test
        @DisplayName("batch of 20 evolution proposals completes within " + BATCH_30_BUDGET_MS + "ms")
        void batchEvolutionProposeWithinBudget() {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                EvolutionPlan result = runPromise(() -> evolutionService.propose(stubInsights));
                assertThat(result).isNotNull();
            }
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed)
                    .as("20 evolution proposals must complete within %dms — actual: %dms", BATCH_30_BUDGET_MS, elapsed)
                    .isLessThan(BATCH_30_BUDGET_MS);
        }
    }

    // =========================================================================
    // Cross-phase pipeline throughput
    // =========================================================================

    @Nested
    @DisplayName("Full pipeline throughput")
    class PipelineThroughput {

        @Test
        @DisplayName("10 sequential full intent→evolve pipelines complete within 30s")
        void tenPipelinesCompleteWithin30Seconds() {
            long start = System.currentTimeMillis();

            for (int i = 0; i < 10; i++) {
                // Phase 0: Intent
                IntentInput input = IntentInput.of("Build service-" + i, "perf-pipeline-tenant");
                IntentSpec intentSpec = runPromise(() -> intentService.capture(input));

                // Phase 1: Shape
                ShapeSpec shapeSpec = runPromise(() -> shapeService.derive(intentSpec));

                // Phase 2: Validate
                LifecycleValidationResult validation = runPromise(() -> validationService.validate(shapeSpec));
                assertThat(validation).isNotNull();

                // Phase 5: Observe (using synthetic run result)
                RunResult runResult = RunResult.builder()
                        .id("run-pipeline-" + i)
                        .runSpecRef("spec-" + i)
                        .status(RunStatus.SUCCESS)
                        .taskResults(List.of())
                        .startedAt(Instant.now().minusSeconds(5))
                        .completedAt(Instant.now())
                        .metadata(Map.of())
                        .build();
                Observation observation = runPromise(() -> observeService.collect(runResult));

                // Phase 6: Learn
                Insights insights = runPromise(() -> learningService.analyze(observation));

                // Phase 7: Evolve
                EvolutionPlan plan = runPromise(() -> evolutionService.propose(insights));
                assertThat(plan).isNotNull();
            }

            long elapsed = System.currentTimeMillis() - start;
            assertThat(elapsed)
                    .as("10 full intent→evolve pipelines must complete within 30s — actual: %dms", elapsed)
                    .isLessThan(30_000L);
        }
    }
}
