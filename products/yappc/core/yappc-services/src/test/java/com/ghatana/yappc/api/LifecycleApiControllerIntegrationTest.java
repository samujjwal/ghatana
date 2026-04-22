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
import io.activej.dns.DnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
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
@DisplayName("LifecycleApiController integration [GH-90000]")
class LifecycleApiControllerIntegrationTest extends EventloopTestBase {

    private LifecycleApiController controller;

    @BeforeEach
    void setUp() { // GH-90000
        MetricsCollector metrics = MetricsCollector.create(); // GH-90000
        AuditLogger auditLogger = AuditLogger.noop(); // GH-90000

        CompletionService completionService = new DeterministicCompletionService(metrics); // GH-90000
        PolicyEngine policyEngine = new InMemoryAllowPolicyEngine(); // GH-90000

        IntentService intentService = new IntentServiceImpl(completionService, auditLogger, metrics); // GH-90000
        ShapeService shapeService = new ShapeServiceImpl(completionService, auditLogger, metrics); // GH-90000
        ValidationService validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics); // GH-90000
        GenerationService generationService = new GenerationServiceImpl(completionService, auditLogger, metrics); // GH-90000
        CiCdPort ciCdPort = new NoOpCiCdAdapter(); // GH-90000
        RunService runService = new RunServiceImpl(auditLogger, metrics, ciCdPort); // GH-90000
        ObserveService observeService = new ObserveServiceImpl(metrics, auditLogger); // GH-90000
        LearningService learningService = new LearningServiceImpl(completionService, auditLogger, metrics); // GH-90000
        EvolutionService evolutionService = new EvolutionServiceImpl(completionService, auditLogger, metrics); // GH-90000

        Eventloop testEventloop = Eventloop.builder().build(); // GH-90000
        HttpClient testHttpClient = HttpClient.create( // GH-90000
          testEventloop,
          DnsClient.builder(testEventloop, InetAddress.getLoopbackAddress()).build()); // GH-90000

        controller = new LifecycleApiController( // GH-90000
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
    @DisplayName("executes full lifecycle successfully with concrete services [GH-90000]")
    void executesFullLifecycleSuccessfullyWithConcreteServices() throws Exception { // GH-90000
        String requestJson = JsonMapper.toJson(new LifecycleRequest( // GH-90000
                IntentInput.of("Build a resilient order processing API with observability [GH-90000]"),
                "staging"));

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/lifecycle/execute [GH-90000]")
                .withBody(ByteBuf.wrapForReading(requestJson.getBytes(StandardCharsets.UTF_8))) // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.executeFullLifecycle(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000

        String body = response.getBody().asString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("\"status\" : \"SUCCESS\""); // GH-90000
        assertThat(body).contains("\"intent\""); // GH-90000
        assertThat(body).contains("\"shape\""); // GH-90000
        assertThat(body).contains("\"validation\""); // GH-90000
        assertThat(body).contains("\"artifacts\""); // GH-90000
        assertThat(body).contains("\"run\""); // GH-90000
        assertThat(body).contains("\"observation\""); // GH-90000
        assertThat(body).contains("\"insights\""); // GH-90000
        assertThat(body).contains("\"evolution\""); // GH-90000
    }

    private record LifecycleRequest(IntentInput intentInput, String environment) { // GH-90000
    }

    private static final class DeterministicCompletionService implements CompletionService {
        private final MetricsCollector metricsCollector;
        private final LLMConfiguration config;

        private DeterministicCompletionService(MetricsCollector metricsCollector) { // GH-90000
            this.metricsCollector = metricsCollector;
            this.config = LLMConfiguration.builder() // GH-90000
                    .apiKey("test-key [GH-90000]")
                    .modelName("deterministic-test-model [GH-90000]")
                    .build(); // GH-90000
        }

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) { // GH-90000
            return Promise.of(CompletionResult.of(respond(request.getPrompt()))); // GH-90000
        }

        @Override
        public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) { // GH-90000
            List<CompletionResult> results = requests.stream() // GH-90000
                    .map(req -> CompletionResult.of(respond(req.getPrompt()))) // GH-90000
                    .toList(); // GH-90000
            return Promise.of(results); // GH-90000
        }

        @Override
        public LLMConfiguration getConfig() { // GH-90000
            return config;
        }

        @Override
        public MetricsCollector getMetricsCollector() { // GH-90000
            return metricsCollector;
        }

        @Override
        public String getProviderName() { // GH-90000
            return "deterministic-integration";
        }

        private String respond(String prompt) { // GH-90000
            if (prompt == null) { // GH-90000
                return "";
            }
            if (prompt.contains("product planning expert [GH-90000]")) {
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
            if (prompt.contains("software architect [GH-90000]")) {
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
            if (prompt.contains("Analyze the following system observations [GH-90000]")) {
                return """
                    Pattern: Stable throughput under normal load
                    Anomaly: Medium latency spike during deployment windows
                    Recommendation: Prioritize deployment canary checks
                    """;
            }
            if (prompt.contains("Create an evolution plan [GH-90000]")) {
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
        public Promise<Boolean> evaluate(String policyName, Map<String, Object> context) { // GH-90000
            return Promise.of(true); // GH-90000
        }

        @Override
        public Promise<Boolean> policyExists(String policyName) { // GH-90000
            return Promise.of(true); // GH-90000
        }
    }
}
