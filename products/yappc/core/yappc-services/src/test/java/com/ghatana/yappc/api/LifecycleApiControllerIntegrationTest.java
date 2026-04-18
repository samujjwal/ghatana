package com.ghatana.yappc.api;

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
import io.activej.bytebuf.ByteBuf;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncHttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration tests for full lifecycle execution using concrete service implementations
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("LifecycleApiController integration")
class LifecycleApiControllerIntegrationTest extends EventloopTestBase {

    private LifecycleApiController controller;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = MetricsCollector.create();
        AuditLogger auditLogger = AuditLogger.noop();

        CompletionService completionService = new DeterministicCompletionService(metrics);
        PolicyEngine policyEngine = new InMemoryAllowPolicyEngine();

        IntentService intentService = new IntentServiceImpl(completionService, auditLogger, metrics);
        ShapeService shapeService = new ShapeServiceImpl(completionService, auditLogger, metrics);
        ValidationService validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics);
        GenerationService generationService = new GenerationServiceImpl(completionService, auditLogger, metrics);
        CiCdPort ciCdPort = new NoOpCiCdAdapter();
        RunService runService = new RunServiceImpl(auditLogger, metrics, ciCdPort);
        ObserveService observeService = new ObserveServiceImpl(metrics, auditLogger);
        LearningService learningService = new LearningServiceImpl(completionService, auditLogger, metrics);
        EvolutionService evolutionService = new EvolutionServiceImpl(completionService, auditLogger, metrics);

        Eventloop testEventloop = Eventloop.builder().build();
        AsyncHttpClient testHttpClient = AsyncHttpClient.builder(testEventloop).build();

        controller = new LifecycleApiController(
                intentService,
                shapeService,
                validationService,
                generationService,
                runService,
                observeService,
                learningService,
                evolutionService,
                testEventloop,
                testHttpClient);
    }

    @Test
    @DisplayName("executes full lifecycle successfully with concrete services")
    void executesFullLifecycleSuccessfullyWithConcreteServices() throws Exception {
        String requestJson = JsonMapper.toJson(new LifecycleRequest(
                IntentInput.of("Build a resilient order processing API with observability"),
                "staging"));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute")
                .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8)))
                .build();

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request));

        assertThat(response.getCode()).isEqualTo(200);

        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"status\" : \"SUCCESS\"");
        assertThat(body).contains("\"intent\"");
        assertThat(body).contains("\"shape\"");
        assertThat(body).contains("\"validation\"");
        assertThat(body).contains("\"artifacts\"");
        assertThat(body).contains("\"run\"");
        assertThat(body).contains("\"observation\"");
        assertThat(body).contains("\"insights\"");
        assertThat(body).contains("\"evolution\"");
    }

    private record LifecycleRequest(IntentInput intentInput, String environment) {
    }

    private static final class DeterministicCompletionService implements CompletionService {
        private final MetricsCollector metricsCollector;
        private final LLMConfiguration config;

        private DeterministicCompletionService(MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            this.config = LLMConfiguration.builder()
                    .apiKey("test-key")
                    .modelName("deterministic-test-model")
                    .build();
        }

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) {
            return Promise.of(CompletionResult.of(respond(request.getPrompt())));
        }

        @Override
        public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
            List<CompletionResult> results = requests.stream()
                    .map(req -> CompletionResult.of(respond(req.getPrompt())))
                    .toList();
            return Promise.of(results);
        }

        @Override
        public LLMConfiguration getConfig() {
            return config;
        }

        @Override
        public MetricsCollector getMetricsCollector() {
            return metricsCollector;
        }

        @Override
        public String getProviderName() {
            return "deterministic-integration";
        }

        private String respond(String prompt) {
            if (prompt == null) {
                return "";
            }
            if (prompt.contains("product planning expert")) {
                return """
                    {
                      "productName": "Order Processing API",
                      "description": "Resilient order lifecycle management service",
                      "goals": [
                        {"description": "Process orders reliably", "category": "business", "priority": 1, "successMetrics": ["p95_latency<200ms"]}
                      ],
                      "personas": [
                        {"name": "Operations", "description": "Monitors order health", "needs": ["observability"], "painPoints": ["silent failures"]}
                      ],
                      "constraints": [
                        {"type": "technical", "description": "Use Java services", "severity": "hard"}
                      ]
                    }
                    """;
            }
            if (prompt.contains("software architect")) {
                return """
                    {
                      "architecture": {"name": "microservices", "description": "Service-per-domain architecture"},
                      "domainModel": {
                        "entities": [
                          {
                            "name": "Order",
                            "description": "Order aggregate",
                            "fields": [
                              {"name": "id", "type": "UUID", "required": true, "description": "Order ID", "validation": {}},
                              {"name": "status", "type": "String", "required": true, "description": "Current state", "validation": {}}
                            ],
                            "behaviors": ["create", "updateStatus"]
                          }
                        ],
                        "relationships": [],
                        "boundedContexts": []
                      },
                      "workflows": [
                        {"id": "wf-auth", "name": "auth-login", "description": "Authentication workflow", "steps": [], "transitions": []}
                      ],
                      "integrations": []
                    }
                    """;
            }
            if (prompt.contains("Analyze the following system observations")) {
                return """
                    Pattern: Stable throughput under normal load
                    Anomaly: Medium latency spike during deployment windows
                    Recommendation: Prioritize deployment canary checks
                    """;
            }
            if (prompt.contains("Create an evolution plan")) {
                return """
                    Task: Refactor deployment orchestration to add canary policy checks
                    Task: Add telemetry guardrails for runtime latency regression
                    """;
            }
            return "Generated output";
        }
    }

    private static final class InMemoryAllowPolicyEngine implements PolicyEngine {

        @Override
        public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) {
            return Promise.of(true);
        }

        @Override
        public Promise<Boolean> policyExists(String policyName) {
            return Promise.of(true);
        }
    }
}
