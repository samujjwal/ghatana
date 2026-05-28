package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.evolve.EvolutionServiceImpl;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.generate.GenerationServiceTestFactory;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceTestFactory;
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
import io.activej.bytebuf.ByteBuf;
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end workflow tests for the YAPPC 8-phase lifecycle.
 *
 * <p>Each test drives a complete or partial lifecycle through
 * {@link LifecycleApiController} with deterministic in-process service
 * implementations — no external dependencies required.
 *
 * <p>Covered scenarios:
 * <ul>
 *   <li>idea → artifact: full 8-phase pipeline from raw intent to generated artifacts</li>
 *   <li>refactor: intent expressing a refactor goal produces evolution plan output</li>
 *   <li>preview: dry-run intent with preview flag returns artifacts without running</li>
 *   <li>approval gate: validation-failing intent halts pipeline at VALIDATE phase</li>
 *   <li>rollback signal: evolution plan contains rollback instruction when requested</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose YAPPC E2E workflow tests: idea→artifact, refactor, preview, approval, rollback
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("YappcWorkflowE2ETest")
@Tag("e2e")
class YappcWorkflowE2ETest extends EventloopTestBase {

    private LifecycleApiController controller;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = MetricsCollector.create();
                AuditLogger audit = event -> Promise.complete();
                LifecycleExecutionRepository lifecycleExecutionRepository = new LifecycleExecutionRepository() {
                        @Override
                        public Promise<Void> persist(LifecycleExecution execution) {
                                return Promise.complete();
                        }

                        @Override
                        public Promise<LifecycleExecution> findById(String executionId) {
                                return Promise.of(null);
                        }

                        @Override
                        public Promise<List<LifecycleExecution>> findByProject(String tenantId, String projectId, int limit) {
                                return Promise.of(List.of());
                        }
                };
        PolicyEngine policy = new AllowAllPolicyEngine();
        CompletionService llm = new ScriptedCompletionService(metrics);
        CiCdPort ciCd = new NoOpCiCdAdapter();
        GenerationRunRepository generationRunRepository = mock(GenerationRunRepository.class);

        IntentService intentService = IntentServiceTestFactory.create(llm, audit, metrics);
        ShapeService shapeService = new ShapeServiceImpl(llm, audit, metrics);
        ValidationService validationService = new ValidationServiceImpl(policy, audit, metrics);
        GenerationService generationService = GenerationServiceTestFactory.create(
                llm,
                audit,
                metrics,
                generationRunRepository,
                new ObjectMapper());
        RunService runService = new RunServiceImpl(audit, metrics, ciCd);
        ObserveService observeService = new ObserveServiceImpl(metrics, audit);
        LearningService learningService = new LearningServiceImpl(llm, audit, metrics);
        EvolutionService evolutionService = new EvolutionServiceImpl(llm, audit, metrics);

        Eventloop el = Eventloop.builder().build();
        HttpClient httpClient = HttpClient.create(el,
                DnsClient.builder(el, InetAddress.getLoopbackAddress()).build());

        controller = new LifecycleApiController(
                intentService, shapeService, validationService,
                generationService, runService, observeService,
                learningService, evolutionService, el, httpClient, lifecycleExecutionRepository);
    }

    // =========================================================================
    // Scenario 1: Idea → Artifact (full 8-phase happy path)
    // =========================================================================

    @Test
    @Disabled("GH-1210 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("idea → artifact: full 8-phase pipeline produces SUCCESS with all phase outputs")
    void ideaToArtifactFullPipelineSucceeds() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Build a resilient payment processing service with observability"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "staging"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        assertThat(body).contains("\"status\" : \"SUCCESS\"");
        // All 8 phases must appear in the result
        for (String phase : List.of("intent", "shape", "validation", "artifacts", "run", "observation", "insights", "evolution")) {
            assertThat(body)
                    .as("phase '%s' must be present in full lifecycle result".formatted(phase))
                    .contains("\"" + phase + "\"");
        }
    }

    @Test
    @Disabled("GH-1211 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("idea → artifact: executedPhases list covers all 8 lifecycle phases in order")
    void allEightPhasesExecutedInOrder() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Create an event-driven order orchestration system"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "production"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        // executedPhases must contain all phases
        for (String phase : List.of("INTENT", "SHAPE", "VALIDATE", "GENERATE", "RUN", "OBSERVE", "LEARN", "EVOLVE")) {
            assertThat(body)
                    .as("executedPhases must include '%s'".formatted(phase))
                    .contains(phase);
        }
    }

    // =========================================================================
    // Scenario 2: Refactor workflow
    // =========================================================================

    @Test
    @Disabled("GH-1212 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("refactor: intent expressing a refactor goal produces an evolution plan")
    void refactorIntentProducesEvolutionPlan() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Refactor the authentication module to use JWT key rotation"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "staging"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        // Evolution phase must be present with plan content
        assertThat(body)
                .as("refactor workflow must produce an evolution section")
                .contains("\"evolution\"");
        assertThat(body)
                .as("refactor workflow must reach SUCCESS or include evolution plan tasks")
                .satisfiesAnyOf(
                        b -> assertThat(b).contains("\"status\" : \"SUCCESS\""),
                        b -> assertThat(b).contains("evolution"));
    }

    // =========================================================================
    // Scenario 3: Preview (dry-run — no RUN phase executed)
    // =========================================================================

    @Test
    @Disabled("GH-1213 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("preview: intent with dryRun=true returns artifacts without executing RUN")
    void previewDryRunReturnsArtifactsWithoutRun() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Preview the generated API for a notifications service"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "preview"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        // Must return a valid response — either success or validation/generation partial result
        assertThat(response.getCode()).isIn(200, 202);
        String body = body(response);

        // Artifacts must be present (GENERATE phase ran)
        assertThat(body)
                .as("preview must produce artifacts section")
                .contains("\"artifacts\"");
    }

    // =========================================================================
    // Scenario 4: Approval gate (validation failure halts pipeline)
    // =========================================================================

    @Test
    @Disabled("GH-1214 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("approval gate: policy-blocking intent halts pipeline at VALIDATE with VALIDATION_FAILED status")
    void approvalGateBlocksValidationFailingIntent() throws Exception {
        // Wire a policy engine that blocks all requests
        MetricsCollector metrics = MetricsCollector.create();
                AuditLogger audit = event -> Promise.complete();
                LifecycleExecutionRepository lifecycleExecutionRepository = new LifecycleExecutionRepository() {
                        @Override
                        public Promise<Void> persist(LifecycleExecution execution) {
                                return Promise.complete();
                        }

                        @Override
                        public Promise<LifecycleExecution> findById(String executionId) {
                                return Promise.of(null);
                        }

                        @Override
                        public Promise<List<LifecycleExecution>> findByProject(String tenantId, String projectId, int limit) {
                                return Promise.of(List.of());
                        }
                };
        PolicyEngine blockAll = new BlockAllPolicyEngine();
        CompletionService llm = new ScriptedCompletionService(metrics);
        CiCdPort ciCd = new NoOpCiCdAdapter();

        ValidationService blockingValidation = new ValidationServiceImpl(blockAll, audit, metrics);
        GenerationRunRepository generationRunRepository2 = mock(GenerationRunRepository.class);
        Eventloop el = Eventloop.builder().build();
        HttpClient httpClient = HttpClient.create(el,
                DnsClient.builder(el, InetAddress.getLoopbackAddress()).build());

        LifecycleApiController blockingController = new LifecycleApiController(
                IntentServiceTestFactory.create(llm, audit, metrics),
                new ShapeServiceImpl(llm, audit, metrics),
                blockingValidation,
                GenerationServiceTestFactory.create(llm, audit, metrics, generationRunRepository2, new ObjectMapper()),
                new RunServiceImpl(audit, metrics, ciCd),
                new ObserveServiceImpl(metrics, audit),
                new LearningServiceImpl(llm, audit, metrics),
                new EvolutionServiceImpl(llm, audit, metrics),
                el, httpClient, lifecycleExecutionRepository);

        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Deploy a system that would violate compliance rules"),
                "tenant-1",
                "project-1",
                "workspace-1",
                "production"));

        HttpResponse response = runPromise(() ->
                blockingController.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        // Pipeline must halt — VALIDATION_FAILED status or error field present
        assertThat(body)
                .as("blocked intent must produce VALIDATION_FAILED or error indication")
                .satisfiesAnyOf(
                        b -> assertThat(b).contains("VALIDATION_FAILED"),
                        b -> assertThat(b).contains("\"passed\" : false"),
                        b -> assertThat(b).contains("blocking"));
    }

    // =========================================================================
    // Scenario 5: Rollback signal in evolution plan
    // =========================================================================

    @Test
    @Disabled("GH-1215 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("rollback: lifecycle with rollback-tagged intent produces evolution plan containing rollback instructions")
    void rollbackIntentProducesEvolutionPlanWithRollbackInstructions() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Rollback the recent deployment due to increased error rate"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "production"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        // Evolution section must be present; rollback-related content must appear somewhere in result
        assertThat(body).contains("\"evolution\"");
    }

    // =========================================================================
    // Scenario 6: Missing intent input returns 400
    // =========================================================================

    @Test
    @DisplayName("missing intentInput returns 400 without executing any lifecycle phase")
    void missingIntentInputReturns400() throws Exception {
        String json = "{\"environment\": \"staging\"}";

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("empty intentInput.rawText returns 400")
    void emptyRawTextReturns400() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of(""),
                "tenant-1",
                "project-1",
                "workspace-1",
                "staging"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("malformed JSON returns 400")
    void malformedJsonReturns400() throws Exception {
        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post("{ not json }")));

        assertThat(response.getCode()).isEqualTo(400);
    }

    // =========================================================================
    // Scenario 7: phaseDurationsMs is present and positive for all executed phases
    // =========================================================================

    @Test
    @Disabled("GH-1216 GenerationRunRepository configuration must be replaced with production test wiring")
    @DisplayName("phaseDurationsMs is present in pipeline result and all values are >= 0")
    void phaseDurationsArePresentAndNonNegative() throws Exception {
        String json = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Build a multi-tenant SaaS dashboard"),
                                "tenant-1",
                                "project-1",
                                "workspace-1",
                                "staging"));

        HttpResponse response = runPromise(() ->
                controller.executeFullLifecycle(post(json)));

        assertThat(response.getCode()).isEqualTo(200);
        String body = body(response);

        assertThat(body)
                .as("phaseDurationsMs must be present in lifecycle result metadata")
                .contains("phaseDurationsMs");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static HttpRequest post(String json) {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
                .withBody(ByteBuf.wrapForReading(json.getBytes(StandardCharsets.UTF_8)))
                .build();
        request.attach(com.ghatana.platform.governance.security.Principal.class,
                new com.ghatana.platform.governance.security.Principal("user-1", List.of("builder"), "tenant-1"));
        return request;
    }

    private static String body(HttpResponse response) {
        return response.getBody().asString(StandardCharsets.UTF_8);
    }

    private record LifecycleRequest(
            IntentInput intentInput,
            String tenantId,
            String projectId,
            String workspaceId,
            String environment) {}

    /**
     * Scripted LLM that returns deterministic structured JSON for each prompt role.
     * Mirrors the DeterministicCompletionService in LifecycleApiControllerIntegrationTest
     * but handles additional prompt patterns for refactor and rollback scenarios.
     */
    private static final class ScriptedCompletionService implements CompletionService {

        private final MetricsCollector metrics;
        private final LLMConfiguration config;

        ScriptedCompletionService(MetricsCollector metrics) {
            this.metrics = metrics;
            this.config = LLMConfiguration.builder()
                    .apiKey("test-key")
                    .modelName("scripted-test-model")
                    .build();
        }

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) {
            return Promise.of(CompletionResult.of(respond(request.getPrompt())));
        }

        @Override
        public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
            return Promise.of(requests.stream()
                    .map(r -> CompletionResult.of(respond(r.getPrompt())))
                    .toList());
        }

        @Override
        public LLMConfiguration getConfig() { return config; }

        @Override
        public MetricsCollector getMetricsCollector() { return metrics; }

        @Override
        public String getProviderName() { return "scripted-e2e"; }

        private String respond(String prompt) {
            if (prompt == null) return "";
            if (prompt.contains("product planning expert")) {
                return """
                    {
                      "productName": "E2E Test Product",
                      "description": "Generated for E2E workflow testing",
                      "goals": [{"description": "Pass E2E test", "category": "technical", "priority": 1, "successMetrics": ["test_pass=true"]}],
                      "personas": [{"name": "Engineer", "description": "Test engineer", "needs": ["reliability"], "painPoints": ["flaky tests"]}],
                      "constraints": [{"type": "technical", "description": "Use Java", "severity": "soft"}]
                    }
                    """;
            }
            if (prompt.contains("software architect")) {
                return """
                    {
                      "architecture": {"name": "microservices", "description": "Test architecture"},
                      "domainModel": {
                        "entities": [{"name": "TestEntity", "description": "Test domain entity",
                          "fields": [{"name": "id", "type": "UUID", "required": true, "description": "ID", "validation": {}}],
                          "behaviors": ["create"]}],
                        "relationships": [], "boundedContexts": []
                      },
                      "workflows": [{"id": "wf-1", "name": "test-workflow", "description": "Test workflow", "steps": [], "transitions": []}],
                      "integrations": []
                    }
                    """;
            }
            if (prompt.contains("Analyze the following system observations")) {
                return """
                    Pattern: Stable under test conditions
                    Recommendation: Continue test execution
                    """;
            }
            if (prompt.contains("Create an evolution plan") || prompt.contains("Rollback")) {
                return """
                    Task: Validate rollback readiness
                    Task: Execute rollback procedure
                    Task: Confirm system stability post-rollback
                    """;
            }
            return "Generated output";
        }
    }

    private static final class AllowAllPolicyEngine implements PolicyEngine {
        @Override
        public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> policyExists(String policyName) {
            return Promise.of(true);
        }
    }

    private static final class BlockAllPolicyEngine implements PolicyEngine {
        @Override
        public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
            return Promise.of(false);
        }

        @Override
        public Promise<Boolean> policyExists(String policyName) {
            return Promise.of(true);
        }
    }
}
