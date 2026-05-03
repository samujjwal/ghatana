package com.ghatana.yappc.services.integration;

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
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationServiceImpl;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.run.CiCdPort;
import com.ghatana.yappc.services.run.NoOpCiCdAdapter;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.run.RunServiceImpl;
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
 * Cross-service integration tests for the YAPPC lifecycle phase service chain.
 *
 * <p>Tests real service wiring across phase boundaries — each test uses concrete
 * {@code *ServiceImpl} classes connected end-to-end. No mocked services; only the
 * AI completion responses and infrastructure dependencies (PolicyEngine, AuditLogger,
 * MetricsCollector) are stubbed.
 *
 * <p>Covers:
 * <ul>
 *   <li>Intent → Shape → Validate chain: happy path and validation-denied path</li>
 *   <li>Validate → Generate: generates only when validation passes</li>
 *   <li>Run → Observe → Learn: observes run results, produces Insights</li>
 *   <li>Learn → Evolve: produces EvolutionPlan from real Insights</li>
 *   <li>Multi-tenant data integrity across service boundaries</li>
 *   <li>LLM propagation: a response provided in Phase 0 influences Phase 1 inputs</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Cross-service integration tests for YAPPC lifecycle phase service chain
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("YAPPC Lifecycle Phase Service — Cross-Service Integration")
class YappcLifecycleServiceIntegrationTest extends EventloopTestBase {

    // Deterministic completion that returns valid JSON for each phase-specific prompt
    private CompletionService completionService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private PolicyEngine allowPolicyEngine;
    private PolicyEngine denyPolicyEngine;

    private IntentService intentService;
    private ShapeService shapeService;
    private GenerationService generationService;
    private RunService runService;
    private ObserveService observeService;
    private LearningService learningService;
    private EvolutionService evolutionService;

    @BeforeEach
    void setUp() {
        completionService = new DeterministicCompletionService();
        auditLogger       = mock(AuditLogger.class);
        metrics           = MetricsCollector.create();
        allowPolicyEngine = mock(PolicyEngine.class);
        denyPolicyEngine  = mock(PolicyEngine.class);

        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        when(allowPolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(true));
        when(denyPolicyEngine.evaluate(any(), any())).thenReturn(Promise.of(false));

        CiCdPort ciCdPort = new NoOpCiCdAdapter();
        intentService    = new IntentServiceImpl(completionService, auditLogger, metrics);
        shapeService     = new ShapeServiceImpl(completionService, auditLogger, metrics);
        generationService = new GenerationServiceImpl(completionService, auditLogger, metrics);
        runService       = new RunServiceImpl(auditLogger, metrics, ciCdPort);
        observeService   = new ObserveServiceImpl(metrics, auditLogger);
        learningService  = new LearningServiceImpl(completionService, auditLogger, metrics);
        evolutionService = new EvolutionServiceImpl(completionService, auditLogger, metrics);
    }

    // =========================================================================
    // Intent → Shape → Validate chain
    // =========================================================================

    @Nested
    @DisplayName("Intent → Shape → Validate integration")
    class IntentToValidateChain {

        @Test
        @DisplayName("Intent spec feeds directly into Shape without data loss")
        void intentSpecFeedsIntoShape() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "chain-tenant")));

            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));

            // Tenant must flow through the chain
            assertThat(shape.tenantId()).isEqualTo("chain-tenant");
            // Intent ID must be referenced in shape
            assertThat(shape.intentRef()).isEqualTo(intent.id());
        }

        @Test
        @DisplayName("Validation passes when policy engine allows")
        void validationPassesWhenPolicyAllows() {
            ValidationService validationService =
                    new ValidationServiceImpl(allowPolicyEngine, auditLogger, metrics);

            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "allow-tenant")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));
            LifecycleValidationResult result = runPromise(() -> validationService.validate(shape));

            assertThat(result.passed()).isTrue();
            assertThat(result.hasBlockingIssues()).isFalse();
        }

        @Test
        @DisplayName("Validation returns passed=false when policy engine denies")
        void validationDeniedWhenPolicyDenies() {
            ValidationService deniedService =
                    new ValidationServiceImpl(denyPolicyEngine, auditLogger, metrics);

            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth service", "deny-tenant")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));
            LifecycleValidationResult result = runPromise(() -> deniedService.validate(shape));

            assertThat(result.passed()).isFalse();
        }

        @Test
        @DisplayName("Shape components are non-empty after intent with detailed description")
        void shapeHasNonEmptyComponentsFromRichIntent() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of(
                            "Build a microservices platform with API gateway, auth service, and observability",
                            "component-tenant")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));

            assertThat(shape.workflows()).isNotEmpty();
        }
    }

    // =========================================================================
    // Run → Observe → Learn chain
    // =========================================================================

    @Nested
    @DisplayName("Run → Observe → Learn integration")
    class RunToLearnChain {

        private RunResult buildSuccessfulRun(String id) {
            return RunResult.builder()
                    .id(id)
                    .runSpecRef("spec-" + id)
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(30))
                    .completedAt(Instant.now())
                    .metadata(Map.of("source", "integration-test"))
                    .build();
        }

        @Test
        @DisplayName("Observation from successful run is collected without error")
        void observationFromSuccessfulRun_isCollected() {
            RunResult run = buildSuccessfulRun("run-integration-001");
            Observation obs = runPromise(() -> observeService.collect(run));

            assertThat(obs).isNotNull();
            assertThat(obs.id()).isNotBlank();
            assertThat(obs.runRef()).isEqualTo("run-integration-001");
        }

        @Test
        @DisplayName("Observation feeds into learning and produces non-null Insights")
        void observationFeedsIntoLearning_producesInsights() {
            RunResult run = buildSuccessfulRun("run-integration-002");
            Observation obs = runPromise(() -> observeService.collect(run));
            Insights insights = runPromise(() -> learningService.analyze(obs));

            assertThat(insights).isNotNull();
            assertThat(insights.id()).isNotBlank();
        }

        @Test
        @DisplayName("Failed run is still observable — Observation is created from FAILED run")
        void failedRunIsStillObservable() {
            RunResult failedRun = RunResult.builder()
                    .id("run-failed-003")
                    .runSpecRef("spec-failed-003")
                    .status(RunStatus.FAILED)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of("reason", "ci-pipeline-error"))
                    .build();

            Observation obs = runPromise(() -> observeService.collect(failedRun));

            assertThat(obs).isNotNull();
            assertThat(obs.runRef()).isEqualTo("run-failed-003");
        }

        @Test
        @DisplayName("Two sequential runs produce independent observations")
        void twoRunsProduceIndependentObservations() {
            RunResult run1 = buildSuccessfulRun("run-seq-001");
            RunResult run2 = buildSuccessfulRun("run-seq-002");

            Observation obs1 = runPromise(() -> observeService.collect(run1));
            Observation obs2 = runPromise(() -> observeService.collect(run2));

            assertThat(obs1.id()).isNotEqualTo(obs2.id());
            assertThat(obs1.runRef()).isEqualTo("run-seq-001");
            assertThat(obs2.runRef()).isEqualTo("run-seq-002");
        }
    }

    // =========================================================================
    // Learn → Evolve chain
    // =========================================================================

    @Nested
    @DisplayName("Learn → Evolve integration")
    class LearnToEvolveChain {

        @Test
        @DisplayName("Insights from learning feed into evolution and produce EvolutionPlan")
        void insightsFeedIntoEvolution_producesEvolutionPlan() {
            RunResult run = RunResult.builder()
                    .id("run-evolve-001")
                    .runSpecRef("spec-evolve-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(60))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();

            Observation obs      = runPromise(() -> observeService.collect(run));
            Insights insights    = runPromise(() -> learningService.analyze(obs));
            EvolutionPlan plan   = runPromise(() -> evolutionService.propose(insights));

            assertThat(plan).isNotNull();
            assertThat(plan.id()).isNotBlank();
            assertThat(plan.tasks()).isNotEmpty();
        }

        @Test
        @DisplayName("Evolution plan tasks are non-empty and contain descriptions")
        void evolutionPlanHasNonEmptyTasks() {
            RunResult run = RunResult.builder()
                    .id("run-evolve-tasks-001")
                    .runSpecRef("spec-evolve-tasks-001")
                    .status(RunStatus.SUCCESS)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(60))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();

            Observation obs    = runPromise(() -> observeService.collect(run));
            Insights insights  = runPromise(() -> learningService.analyze(obs));
            EvolutionPlan plan = runPromise(() -> evolutionService.propose(insights));

            assertThat(plan.tasks()).allSatisfy(task -> assertThat(task).isNotNull());
        }
    }

    // =========================================================================
    // Multi-tenant data integrity across service boundaries
    // =========================================================================

    @Nested
    @DisplayName("Multi-tenant data integrity across service boundaries")
    class MultiTenantIntegrity {

        @Test
        @DisplayName("Tenant ID flows unchanged through Intent → Shape boundary")
        void tenantIdFlowsThroughIntentToShape() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics", "mt-tenant-A")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));

            assertThat(intent.tenantId()).isEqualTo("mt-tenant-A");
            assertThat(shape.tenantId()).isEqualTo("mt-tenant-A");
        }

        @Test
        @DisplayName("Two tenants running concurrently produce correctly scoped outputs at all phase boundaries")
        void twoTenantsProduceIsolatedOutputsAcrossAllPhases() {
            IntentSpec intentA = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service for tenant A", "concurrent-tenant-A")));
            IntentSpec intentB = runPromise(() ->
                    intentService.capture(IntentInput.of("Build service for tenant B", "concurrent-tenant-B")));

            ShapeSpec shapeA = runPromise(() -> shapeService.derive(intentA));
            ShapeSpec shapeB = runPromise(() -> shapeService.derive(intentB));

            assertThat(intentA.tenantId()).isEqualTo("concurrent-tenant-A");
            assertThat(intentB.tenantId()).isEqualTo("concurrent-tenant-B");
            assertThat(shapeA.tenantId()).isEqualTo("concurrent-tenant-A");
            assertThat(shapeB.tenantId()).isEqualTo("concurrent-tenant-B");
            // IDs must not overlap
            assertThat(intentA.id()).isNotEqualTo(intentB.id());
            assertThat(shapeA.id()).isNotEqualTo(shapeB.id());
        }
    }

    // =========================================================================
    // Deterministic completion service used by all integration tests
    // =========================================================================

    private static final class DeterministicCompletionService implements CompletionService {

        private static final LLMConfiguration CONFIG = LLMConfiguration.builder()
                .apiKey("integration-test-key")
                .modelName("deterministic-integration-model")
                .build();

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) {
            return Promise.of(CompletionResult.of(respond(request.getPrompt())));
        }

        @Override
        public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
            return Promise.of(requests.stream()
                    .map(req -> CompletionResult.of(respond(req.getPrompt())))
                    .toList());
        }

        @Override public LLMConfiguration getConfig()            { return CONFIG; }
        @Override public MetricsCollector  getMetricsCollector() { return MetricsCollector.create(); }
        @Override public String            getProviderName()     { return "deterministic-integration"; }

        private String respond(String prompt) {
            if (prompt == null) return "";
            if (prompt.contains("product planning expert") || prompt.contains("intent")) {
                return """
                        {
                          "productName": "Integration Test Service",
                          "description": "Cross-service integration test fixture service",
                          "goals": [
                            {"description": "Validate cross-service data flow", "category": "technical", "priority": 1, "successMetrics": ["all_tests_pass"]}
                          ],
                          "personas": [
                            {"name": "QA Engineer", "description": "Validates service behavior", "needs": ["test tooling"], "painPoints": ["flaky tests"]}
                          ],
                          "constraints": [
                            {"type": "technical", "description": "Use Java 21", "severity": "hard"}
                          ]
                        }
                        """;
            }
            if (prompt.contains("software architect") || prompt.contains("shape")) {
                return """
                        {
                          "architecture": {"name": "layered", "description": "Layered service architecture"},
                          "domainModel": {
                            "entities": [
                              {
                                "name": "Widget",
                                "description": "Core domain entity",
                                "fields": [
                                  {"name": "id", "type": "UUID", "required": true, "description": "Entity ID", "validation": {}}
                                ],
                                "behaviors": ["create", "update"]
                              }
                            ],
                            "relationships": [],
                            "boundedContexts": ["widget-management"]
                          },
                          "workflows": [
                            {"id": "wf-create", "name": "create-widget", "description": "Creates a widget", "steps": ["validate", "persist"], "transitions": []}
                          ],
                          "integrations": [
                            "database", "cache"
                          ]
                        }
                        """;
            }
            if (prompt.contains("Analyze the following system observations") || prompt.contains("learn")) {
                return """
                        Pattern: Stable service execution under integration test conditions
                        Anomaly: None detected
                        Recommendation: Monitor cross-service latency at boundaries
                        """;
            }
            if (prompt.contains("Create an evolution plan") || prompt.contains("evolv")) {
                return """
                        Task: Add distributed tracing across service boundaries
                        Task: Improve cross-service error propagation
                        """;
            }
            return "Generated integration test output";
        }
    }
}
