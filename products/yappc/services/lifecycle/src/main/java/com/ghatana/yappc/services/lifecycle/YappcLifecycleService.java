package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.core.activej.launcher.UnifiedApplicationLauncher;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.governance.security.ApiKeyAuthFilter;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import com.ghatana.yappc.api.GenerationApiController;
import com.ghatana.yappc.api.IntentApiController;
import com.ghatana.yappc.api.ShapeApiController;
import com.ghatana.yappc.api.ValidationApiController;
import com.ghatana.yappc.agent.AepEventPublisher;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.lifecycle.workflow.LifecycleWorkflowService;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC Lifecycle Service — SDLC phase management and workflow orchestration.
 *
 * <p>Manages the complete software development lifecycle:
 * Intent → Shape → Generate → Run → Observe → Evolve → Learn → Validate</p>
 *
 * <p>Security: All {@code /api/*} routes are protected by {@link ApiKeyAuthFilter}.
 * The {@code /health} endpoint is public. API keys are loaded from the
 * {@code YAPPC_API_KEYS} environment variable (comma-separated list).</p>
 *
 * <p>Components wired via {@link LifecycleServiceModule}:
 * <ul>
 *   <li>{@link IntentService} — AI-assisted intent capture</li>
 *   <li>{@link ShapeService} — Architecture and domain modeling</li>
 *   <li>{@link GenerationService} — Code and artifact generation</li>
 *   <li>{@link RunService} — Build execution and orchestration</li>
 *   <li>{@link ObserveService} — Runtime monitoring</li>
 *   <li>{@link EvolutionService} — Progressive evolution planning</li>
 *   <li>{@link LearningService} — Pattern extraction and learning</li>
 *   <li>{@link ValidationService} — Security and policy compliance</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose SDLC lifecycle management service entry point with security and governance
 * @doc.layer product
 * @doc.pattern Launcher
 */
public class YappcLifecycleService extends UnifiedApplicationLauncher {

    private static final Logger logger = LoggerFactory.getLogger(YappcLifecycleService.class);
    private static final int DEFAULT_PORT = 8082;

    /**
     * Environment variable containing comma-separated allowed API keys.
     * In production, inject via secrets manager / Kubernetes secret.
     * Falls back to {@code "dev-key"} when not set (development only).
     */
    private static final String API_KEYS_ENV = "YAPPC_API_KEYS";

    @Override
    protected String getServiceName() {
        return "yappc-lifecycle";
    }

    @Override
    protected String getServiceVersion() {
        return "2.0.0";
    }

    @Override
    protected void setupService(ModuleBuilder builder) {
        // Eventloop (NioReactor) for HTTP server and routing
        builder.bind(io.activej.eventloop.Eventloop.class)
                .toInstance(io.activej.eventloop.Eventloop.create());

        // Prometheus-backed metrics collector (scrape via GET /metrics)
        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        MetricsCollector metrics = new SimpleMetricsCollector(prometheusRegistry);
        builder.bind(MetricsCollector.class).toInstance(metrics);
        builder.bind(PrometheusMeterRegistry.class).toInstance(prometheusRegistry);

        // Structured audit logger — real implementation logs async to SLF4J;
        // swap for JdbcAuditLogger in a full persistence setup.
        builder.bind(AuditLogger.class).toInstance(event -> {
            logger.info("[AUDIT] {}", event);
            return io.activej.promise.Promise.complete();
        });

        // CompletionService — resolved from env-driven provider; fail-fast if none configured.
        String anthropicKey  = System.getenv("ANTHROPIC_API_KEY");
        String openAiKey     = System.getenv("OPENAI_API_KEY");
        String ollamaHost    = System.getenv("OLLAMA_HOST");
        CompletionService completionService;
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            String model = System.getenv("ANTHROPIC_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "ANTHROPIC_API_KEY is set but ANTHROPIC_MODEL env var is missing.");
            }
            LLMConfiguration llmConfig = LLMConfiguration.builder()
                    .apiKey(anthropicKey).modelName(model)
                    .temperature(0.7).maxTokens(2000).timeoutSeconds(30).maxRetries(3).build();
            completionService = new ToolAwareAnthropicCompletionService(llmConfig, null, metrics);
            logger.info("Lifecycle service CompletionService configured with Anthropic provider");
        } else if (openAiKey != null && !openAiKey.isBlank()) {
            String model = System.getenv("OPENAI_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "OPENAI_API_KEY is set but OPENAI_MODEL env var is missing.");
            }
            LLMConfiguration llmConfig = LLMConfiguration.builder()
                    .apiKey(openAiKey).modelName(model)
                    .temperature(0.7).maxTokens(2000).timeoutSeconds(30).maxRetries(3).build();
            completionService = new ToolAwareOpenAICompletionService(llmConfig, null, metrics);
            logger.info("Lifecycle service CompletionService configured with OpenAI provider");
        } else if (ollamaHost != null && !ollamaHost.isBlank()) {
            String model = System.getenv("OLLAMA_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "OLLAMA_HOST is set but OLLAMA_MODEL env var is missing.");
            }
            LLMConfiguration llmConfig = LLMConfiguration.builder()
                    .apiKey("ollama").baseUrl(ollamaHost).modelName(model)
                    .temperature(0.7).maxTokens(2000).timeoutSeconds(60).maxRetries(2).build();
            completionService = new OllamaCompletionService(llmConfig, null, metrics);
            logger.info("Lifecycle service CompletionService configured with Ollama provider");
        } else {
            throw new IllegalStateException(
                    "No LLM provider configured for YAPPC Lifecycle service. "
                    + "Set ANTHROPIC_API_KEY (+ ANTHROPIC_MODEL), OPENAI_API_KEY (+ OPENAI_MODEL), "
                    + "or OLLAMA_HOST (+ OLLAMA_MODEL).");
        }
        builder.bind(CompletionService.class).toInstance(completionService);

        // PolicyEngine — real YAML-backed implementation (replaces always-true stub)
        builder.bind(PolicyEngine.class).toInstance(new YappcPolicyEngine());

        // Install the lifecycle DI module (all 8 phase services + API controllers)
        builder.install(new LifecycleServiceModule());

        logger.info("YAPPC Lifecycle service bindings configured (real PolicyEngine + MetricsCollector)");
    }

    @Override
    protected HttpServer createHttpServer(Injector injector) {
        int port = Integer.parseInt(System.getProperty("yappc.lifecycle.port",
                String.valueOf(DEFAULT_PORT)));

        io.activej.eventloop.Eventloop eventloop =
                injector.getInstance(io.activej.eventloop.Eventloop.class);

        // ── Resolve API controllers from DI ──────────────────────────────
        IntentApiController intentController =
                injector.getInstance(IntentApiController.class);
        ShapeApiController shapeController =
                injector.getInstance(ShapeApiController.class);
        GenerationApiController generationController =
                injector.getInstance(GenerationApiController.class);
        ValidationApiController validationController =
                injector.getInstance(ValidationApiController.class);
        AdvancePhaseUseCase advancePhaseUseCase =
                injector.getInstance(AdvancePhaseUseCase.class);
        AepEventPublisher aepPublisher =
                injector.getInstance(AepEventPublisher.class);
        PrometheusMeterRegistry prometheusRegistry =
                injector.getInstance(PrometheusMeterRegistry.class);
        HumanApprovalService humanApprovalService =
                injector.getInstance(HumanApprovalService.class);
        LifecycleWorkflowService workflowService =
                injector.getInstance(LifecycleWorkflowService.class);

        // ── Auth filter (Security 4.3) ────────────────────────────────────
        // API keys are comma-separated in YAPPC_API_KEYS env var.
        // Production deployments should bind an ApiKeyResolver to a database/secrets store.
        String apiKeyEnv = System.getenv().getOrDefault(API_KEYS_ENV, "dev-key");
        Set<String> allowedKeys = new HashSet<>(Arrays.asList(apiKeyEnv.split(",")));
        ApiKeyAuthFilter authFilter = new ApiKeyAuthFilter(allowedKeys);

        // ── Build authenticated API servlet with all lifecycle routes ─────
        AsyncServlet apiServlet = buildApiServlet(eventloop,
                intentController, shapeController, generationController,
                validationController, advancePhaseUseCase, aepPublisher,
                humanApprovalService, workflowService);
        AsyncServlet securedApiServlet = authFilter.secure(apiServlet);

        // ── Outer router: /health public, everything else auth-gated ─────
        var router = RoutingServlet.builder(eventloop)
                .with(GET, "/health",
                        request -> HttpResponse.ok200().withPlainText("OK").toPromise())
                // Prometheus scrape endpoint (Observability 6.1)
                .with(GET, "/metrics",
                        request -> HttpResponse.ok200()
                                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE,
                                        io.activej.http.HttpHeaderValue.of(
                                                "text/plain; version=0.0.4; charset=utf-8"))
                                .withBody(prometheusRegistry.scrape().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                                .toPromise())
                // Secured API routes
                .with(GET,  "/api/*", securedApiServlet)
                .with(POST, "/api/*", securedApiServlet)
                .build();

        // Wrap outer router with Correlation ID propagation (Observability 6.5)
        io.activej.http.AsyncServlet correlationWrapper = new io.activej.http.AsyncServlet() {
            private final io.activej.http.HttpHeader X_CORRELATION_ID =
                    io.activej.http.HttpHeaders.register("X-Correlation-ID");

            @Override
            public io.activej.promise.Promise<io.activej.http.HttpResponse> serve(
                    io.activej.http.HttpRequest request) throws Exception {
                String id = request.getHeader(X_CORRELATION_ID);
                if (id == null || id.isBlank()) {
                    id = java.util.UUID.randomUUID().toString();
                }
                final String correlationId = id;
                org.slf4j.MDC.put("correlationId", correlationId);
                return router.serve(request)
                        .then(
                            response -> {
                                org.slf4j.MDC.remove("correlationId");
                                io.activej.http.HttpResponse.Builder respBuilder =
                                        io.activej.http.HttpResponse.ofCode(response.getCode());
                                for (var entry : response.getHeaders()) {
                                    respBuilder.withHeader(entry.getKey(), entry.getValue());
                                }
                                respBuilder.withHeader(X_CORRELATION_ID, correlationId);
                                respBuilder.withBody(response.getBody());
                                return io.activej.promise.Promise.of(respBuilder.build());
                            },
                            e -> {
                                org.slf4j.MDC.remove("correlationId");
                                return io.activej.promise.Promise.ofException(e);
                            }
                        );
            }
        };

        logger.info("Creating YAPPC Lifecycle HTTP server on port {}", port);

        return HttpServer.builder(eventloop, correlationWrapper)
                .withListenPort(port)
                .build();
    }

    /**
     * Builds the inner API RoutingServlet with all lifecycle endpoint routes.
     * This servlet is passed through the auth filter before being mounted on the outer router.
     */
    private AsyncServlet buildApiServlet(
            io.activej.eventloop.Eventloop eventloop,
            IntentApiController intentController,
            ShapeApiController shapeController,
            GenerationApiController generationController,
            ValidationApiController validationController,
            AdvancePhaseUseCase advancePhaseUseCase,
            AepEventPublisher aepPublisher,
            HumanApprovalService humanApprovalService,
            LifecycleWorkflowService workflowService) {

        ObjectMapper objectMapper = new ObjectMapper();

        return RoutingServlet.builder(eventloop)

                // ── Lifecycle meta ──────────────────────────────────────────
                .with(GET, "/api/v1/lifecycle/phases",
                        request -> HttpResponse.ok200()
                                .withJson("{\"phases\":[\"intent\",\"shape\",\"generate\",\"run\","
                                        + "\"observe\",\"evolve\",\"learn\",\"validate\"]}")
                                .toPromise())

                // ── Phase transition (Lifecycle 3.2) ────────────────────────
                .with(POST, "/api/v1/lifecycle/advance", request ->
                        request.loadBody().then(body -> {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> json = objectMapper.readValue(
                                        body.getString(StandardCharsets.UTF_8), Map.class);
                                TransitionRequest tr = new TransitionRequest(
                                        (String) json.get("projectId"),
                                        (String) json.get("fromPhase"),
                                        (String) json.get("toPhase"),
                                        (String) json.getOrDefault("tenantId", "default"),
                                        (String) json.get("requestedBy"));
                                return advancePhaseUseCase.execute(tr)
                                        .map(result -> {
                                            // Fire-and-forget AEP event (3.4/Ph1c)
                                            String eventType = result.isSuccess()
                                                    ? "lifecycle.phase.advanced"
                                                    : "lifecycle.phase.blocked";
                                            String tid = tr.tenantId() != null
                                                    ? tr.tenantId() : "default";
                                            aepPublisher.publish(eventType, tid,
                                                    buildTransitionPayload(tr, result))
                                                    .whenComplete((v, e) -> {
                                                        if (e != null) {
                                                            logger.warn("AEP event publish failed: {}", e.getMessage());
                                                        }
                                                    });
                                            try {
                                                String resultJson = objectMapper.writeValueAsString(result);
                                                return result.isSuccess()
                                                        ? HttpResponse.ok200().withJson(resultJson).build()
                                                        : HttpResponse.ofCode(409).withJson(resultJson).build();
                                            } catch (Exception ex) {
                                                logger.error("Failed to serialize transition result", ex);
                                                return HttpResponse.ofCode(500)
                                                        .withPlainText("Internal error").build();
                                            }
                                        });
                            } catch (Exception e) {
                                logger.warn("Invalid /lifecycle/advance request: {}", e.getMessage());
                                return Promise.of(HttpResponse.ofCode(400)
                                        .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                            }
                        }))

                // ── Intent phase (Phase 1) ──────────────────────────────────
                .with(POST, "/api/v1/yappc/intent/capture", intentController::captureIntent)
                .with(POST, "/api/v1/yappc/intent/analyze", intentController::analyzeIntent)
                .with(GET,  "/api/v1/yappc/intent/:id",     intentController::getIntent)

                // ── Shape phase (Phase 2) ───────────────────────────────────
                .with(POST, "/api/v1/yappc/shape/derive",   shapeController::deriveShape)
                .with(POST, "/api/v1/yappc/shape/model",    shapeController::generateSystemModel)
                .with(GET,  "/api/v1/yappc/shape/:id",      shapeController::getShape)

                // ── Generation phase (Phase 3) ──────────────────────────────
                .with(POST, "/api/v1/yappc/generate",               generationController::generateArtifacts)
                .with(POST, "/api/v1/yappc/generate/diff",          generationController::regenerateWithDiff)
                .with(GET,  "/api/v1/yappc/generate/artifacts/:id", generationController::getArtifacts)

                // ── Validation phase (Phase 8) ──────────────────────────────
                .with(POST, "/api/v1/yappc/validate",               validationController::validate)
                .with(POST, "/api/v1/yappc/validate/with-config",   validationController::validateWithConfig)
                .with(POST, "/api/v1/yappc/validate/with-policy",   validationController::validateWithPolicy)

                // ── Human Approval Gate (Lifecycle 3.5) ─────────────────────
                .with(GET, "/api/v1/approvals/pending", request -> {
                    String tenantId = request.getHeader(
                            io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                    if (tenantId == null) tenantId = "default";
                    try {
                        String json = objectMapper.writeValueAsString(
                                humanApprovalService.allPending(tenantId));
                        return HttpResponse.ok200().withJson(json).toPromise();
                    } catch (Exception e) {
                        return Promise.of(HttpResponse.ofCode(500).withPlainText("Serialization error").build());
                    }
                })
                .with(POST, "/api/v1/approvals/:id/approve", request -> {
                    String requestId = request.getPathParameter("id");
                    String rawTenantId  = request.getHeader(
                            io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                    final String tenantId = rawTenantId != null ? rawTenantId : "default";
                    return request.loadBody().then(body -> {
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> payload = objectMapper.readValue(
                                    body.getString(StandardCharsets.UTF_8), java.util.Map.class);
                            String decidedBy = (String) payload.getOrDefault("decidedBy", "unknown");
                            return humanApprovalService.approve(tenantId, requestId, decidedBy)
                                    .map(r -> {
                                        try {
                                            return HttpResponse.ok200()
                                                    .withJson(objectMapper.writeValueAsString(r))
                                                    .build();
                                        } catch (Exception ex) {
                                            return HttpResponse.ofCode(500).withPlainText("Serialization error")
                                                    .build();
                                        }
                                    })
                                    .then(
                                        r -> Promise.of(r),
                                        e -> Promise.of(HttpResponse.ofCode(409)
                                                .withJson("{\"error\":\"" + e.getMessage() + "\"}")
                                                .build())
                                    );
                        } catch (Exception e) {
                            return Promise.of(HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                        }
                    });
                })
                .with(POST, "/api/v1/approvals/:id/reject", request -> {
                    String requestId = request.getPathParameter("id");
                    String rawTenantId  = request.getHeader(
                            io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                    final String tenantId = rawTenantId != null ? rawTenantId : "default";
                    return request.loadBody().then(body -> {
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> payload = objectMapper.readValue(
                                    body.getString(StandardCharsets.UTF_8), java.util.Map.class);
                            String decidedBy = (String) payload.getOrDefault("decidedBy", "unknown");
                            return humanApprovalService.reject(tenantId, requestId, decidedBy)
                                    .map(r -> {
                                        try {
                                            return HttpResponse.ok200()
                                                    .withJson(objectMapper.writeValueAsString(r))
                                                    .build();
                                        } catch (Exception ex) {
                                            return HttpResponse.ofCode(500).withPlainText("Serialization error")
                                                    .build();
                                        }
                                    })
                                    .then(
                                        r -> Promise.of(r),
                                        e -> Promise.of(HttpResponse.ofCode(409)
                                                .withJson("{\"error\":\"" + e.getMessage() + "\"}")
                                                .build())
                                    );
                        } catch (Exception e) {
                            return Promise.of(HttpResponse.ofCode(400)
                                    .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                        }
                    });
                })

                // ── API info ────────────────────────────────────────────────
                .with(GET, "/api/v1/yappc/info",
                        request -> HttpResponse.ok200()
                                .withJson("{\"name\":\"YAPPC Lifecycle API\",\"version\":\"2.0.0\","
                                        + "\"phases\":[\"intent\",\"shape\",\"generate\",\"run\","
                                        + "\"observe\",\"evolve\",\"learn\",\"validate\"]}")
                                .toPromise())

                // ── Canonical Workflow Routes (YAPPC-Ph9) ────────────────────
                // GET  /api/v1/workflows/templates        — list registered template IDs
                // POST /api/v1/workflows/:templateId/start — start a new workflow run
                // GET  /api/v1/workflows/:runId/status    — get run status
                .with(GET, "/api/v1/workflows/templates", request -> {
                    try {
                        java.util.Set<String> templates = workflowService.registeredTemplates();
                        String json = objectMapper.writeValueAsString(
                                java.util.Map.of("templates", templates, "count", templates.size()));
                        return HttpResponse.ok200().withJson(json).toPromise();
                    } catch (Exception e) {
                        logger.warn("Failed to list workflow templates: {}", e.getMessage());
                        return Promise.of(HttpResponse.ofCode(500)
                                .withJson("{\"error\":\"Failed to list templates\"}").build());
                    }
                })
                .with(POST, "/api/v1/workflows/:templateId/start", request -> {
                    String templateId = request.getPathParameter("templateId");
                    String rawTenantId = request.getHeader(
                            io.activej.http.HttpHeaders.of("X-Tenant-Id"));
                    final String tenantId = rawTenantId != null ? rawTenantId : "default";
                    return request.loadBody().then(body -> {
                        try {
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> inputVars = body.array().length > 0
                                    ? objectMapper.readValue(
                                            body.getString(StandardCharsets.UTF_8),
                                            java.util.Map.class)
                                    : java.util.Map.of();
                            if (!workflowService.isRegistered(templateId)) {
                                return Promise.of(HttpResponse.ofCode(404)
                                        .withJson("{\"error\":\"Unknown workflow template: "
                                                + templateId + "\"}").build());
                            }
                            DurableWorkflowEngine.WorkflowExecution exec =
                                    workflowService.startWorkflow(templateId, tenantId, inputVars);
                            String runId = exec.workflowId();
                            String resp = objectMapper.writeValueAsString(java.util.Map.of(
                                    "runId",      runId,
                                    "templateId", templateId,
                                    "tenantId",   tenantId,
                                    "status",     "STARTED"));
                            return Promise.of(HttpResponse.ofCode(202).withJson(resp).build());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(HttpResponse.ofCode(404)
                                    .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                        } catch (Exception e) {
                            logger.warn("Failed to start workflow '{}': {}", templateId, e.getMessage());
                            return Promise.of(HttpResponse.ofCode(500)
                                    .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                        }
                    });
                })
                .with(GET, "/api/v1/workflows/:runId/status", request -> {
                    String runId = request.getPathParameter("runId");
                    try {
                        java.util.Optional<DurableWorkflowEngine.WorkflowRun> run =
                                workflowService.getRunStatus(runId);
                        if (run.isEmpty()) {
                            return Promise.of(HttpResponse.ofCode(404)
                                    .withJson("{\"error\":\"Run not found: " + runId + "\"}").build());
                        }
                        DurableWorkflowEngine.WorkflowRun wr = run.get();
                        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
                        status.put("runId",          wr.workflowId());
                        status.put("status",         wr.status().name());
                        status.put("stepStatuses",   stepStatusesToMap(wr.stepStatuses()));
                        if (wr.failureReason() != null) {
                            status.put("failureReason", wr.failureReason());
                        }
                        return Promise.of(HttpResponse.ok200()
                                .withJson(objectMapper.writeValueAsString(status)).build());
                    } catch (Exception e) {
                        logger.warn("Failed to get run status '{}': {}", runId, e.getMessage());
                        return Promise.of(HttpResponse.ofCode(500)
                                .withJson("{\"error\":\"" + e.getMessage() + "\"}").build());
                    }
                })

                .build();
    }

    /** Converts a step-status array to a list of name strings for JSON serialization. */
    private static java.util.List<String> stepStatusesToMap(
            DurableWorkflowEngine.StepStatus[] statuses) {
        java.util.List<String> result = new java.util.ArrayList<>(statuses.length);
        for (DurableWorkflowEngine.StepStatus s : statuses) {
            result.add(s.name());
        }
        return result;
    }

    @Override
    protected void onApplicationStarted() {
        logger.info("=== YAPPC Lifecycle Service v{} started on port {} ===",
                getServiceVersion(),
                System.getProperty("yappc.lifecycle.port", String.valueOf(DEFAULT_PORT)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the AEP event payload for a lifecycle phase transition,
     * previously handled by AepEventBridge.buildPayload() (deleted in Ph1c).
     */
    private static Map<String, Object> buildTransitionPayload(
            TransitionRequest request, TransitionResult result) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("projectId",  request.projectId());
        payload.put("tenantId",   request.tenantId());
        payload.put("fromPhase",  request.fromPhase());
        payload.put("toPhase",    result.isSuccess() ? result.toPhase() : request.toPhase());
        payload.put("timestamp",  java.time.Instant.now().toString());
        payload.put("status",     result.status());
        if (!result.isSuccess()) {
            payload.put("blockCode",        result.blockCode());
            payload.put("blockReason",      result.blockReason());
            payload.put("missingArtifacts", result.missingArtifacts());
        }
        return payload;
    }

    public static void main(String[] args) throws Exception {
        new YappcLifecycleService().launch(args);
    }
}

