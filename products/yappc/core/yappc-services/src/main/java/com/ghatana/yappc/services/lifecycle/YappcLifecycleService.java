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
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsProvider;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import com.ghatana.yappc.api.GenerationApiController;
import com.ghatana.yappc.api.IntentApiController;
import com.ghatana.yappc.api.ShapeApiController;
import com.ghatana.yappc.api.ValidationApiController;
import com.ghatana.yappc.agent.AepEventPublisher;
import com.ghatana.yappc.services.security.YappcApiSecurity;
import com.ghatana.yappc.services.lifecycle.workflow.LifecycleWorkflowService;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpResponse;
import io.activej.http.HttpServer;
import io.activej.http.RoutingServlet;
import io.activej.inject.Injector;
import io.activej.inject.module.ModuleBuilder;
import io.activej.promise.Promise;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.activej.http.HttpMethod.GET;
import static io.activej.http.HttpMethod.POST;

/**
 * YAPPC Lifecycle Service — SDLC phase management and workflow orchestration.
 *
 * <p>Manages the complete software development lifecycle:
 * Intent → Shape → Generate → Run → Observe → Evolve → Learn → Validate</p>
 *
 * <p>Security: All {@code /api/*} routes are protected by authentication.
 * The {@code /health} endpoint is public. API keys are loaded from the
 * {@code YAPPC_API_KEYS} environment variable (comma-separated list).</p>
 *
 * <p>Components wired via {@link LifecycleServiceModule}:
 * <ul>
 *   <li>{@code IntentService} — AI-assisted intent capture</li>
 *   <li>{@code ShapeService} — Architecture and domain modeling</li>
 *   <li>{@code GenerationService} — Code and artifact generation</li>
 *   <li>{@code RunService} — Build execution and orchestration</li>
 *   <li>{@code ObserveService} — Runtime monitoring</li>
 *   <li>{@code EvolutionService} — Progressive evolution planning</li>
 *   <li>{@code LearningService} — Pattern extraction and learning</li>
 *   <li>{@code ValidationService} — Security and policy compliance</li>
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
        // Fail-fast environment validation before any DI wiring
        com.ghatana.yappc.services.security.YappcEnvironmentConfig.validate();

        // Eventloop (NioReactor) for HTTP server and routing
        builder.bind(io.activej.eventloop.Eventloop.class)
                .toInstance(io.activej.eventloop.Eventloop.create());

        // Prometheus-backed metrics collector — use the canonical shared platform registry
        // so platform-level metrics are included in the /metrics scrape endpoint.
        PrometheusMeterRegistry prometheusRegistry = MetricsProvider.getRegistry();
        MetricsCollector metrics = new SimpleMetricsCollector(prometheusRegistry);
        builder.bind(MetricsCollector.class).toInstance(metrics);
        builder.bind(PrometheusMeterRegistry.class).toInstance(prometheusRegistry);

        // Durable JDBC audit logger — persists all lifecycle phase events.
        // DataSource is resolved later by LifecycleServiceModule; we bind the
        // AuditLogger factory so the injector can create the JdbcAuditLogger at
        // startup once the DataSource is available.  Falls back to SLF4J-only if
        // the DB connection fails at startup-time (prevents hard crash on misconfiguration).
        // NOTE: LifecycleServiceModule provides a @Provides AuditLogger method that
        // supersedes this binding when the DataSource is successfully initialised.
        builder.bind(AuditLogger.class).toInstance(event -> {
            logger.info("[AUDIT-FALLBACK] {}", event);
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
        com.ghatana.yappc.services.security.JwtAuthController jwtAuthController =
                injector.getInstance(com.ghatana.yappc.services.security.JwtAuthController.class);
        com.ghatana.yappc.services.security.LifecycleLoginController loginController =
                injector.getInstance(com.ghatana.yappc.services.security.LifecycleLoginController.class);
        com.ghatana.yappc.services.lifecycle.gdpr.GdprController gdprController =
                injector.getInstance(com.ghatana.yappc.services.lifecycle.gdpr.GdprController.class);

        // ── Distributed Tracing (Dimension 7.1) ──────────────────────────
        // Eagerly instantiate LifecycleTracingConfig so the TracingManager is ready
        // before any spans are emitted. Assignment intentionally omitted to avoid DLS.
        injector.getInstance(com.ghatana.yappc.services.lifecycle.config.LifecycleTracingConfig.class);

        // ── Config hot-reload watcher (Dimension 8.3) ─────────────────────
        // Eagerly instantiating ConfigWatchService starts its background thread.
        // The service is registered for shutdown via the Runtime shutdown hook in main().
        com.ghatana.yappc.services.lifecycle.config.ConfigWatchService configWatcher =
                injector.getInstance(com.ghatana.yappc.services.lifecycle.config.ConfigWatchService.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { configWatcher.close(); } catch (Exception e) {
                logger.warn("ConfigWatchService failed to close cleanly during JVM shutdown", e);
            }
        }, "config-watch-shutdown"));

        // ── Build secured API servlet with auth + RBAC + rate limiting ───
        AsyncServlet apiServlet = buildApiServlet(eventloop,
                intentController, shapeController, generationController,
                validationController, advancePhaseUseCase, aepPublisher,
                humanApprovalService, workflowService);
        com.ghatana.platform.security.rbac.PolicyRepository policyRepository =
                injector.getInstance(com.ghatana.platform.security.rbac.PolicyRepository.class);
        YappcApiSecurity.SecurityRoutes securedApi =
                YappcApiSecurity.secureApi(apiServlet, "yappc:lifecycle-api", policyRepository);
        AsyncServlet securedMetrics = YappcApiSecurity.secureReadEndpoint(
                request -> HttpResponse.ok200()
                        .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE,
                                io.activej.http.HttpHeaderValue.of(
                                        "text/plain; version=0.0.4; charset=utf-8"))
                        .withBody(prometheusRegistry.scrape().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .toPromise(),
                "yappc:lifecycle-metrics",
                policyRepository);

        // ── Outer router: /health public, everything else auth-gated ─────
        var router = RoutingServlet.builder(eventloop)
                .with(GET, "/health",
                        request -> HttpResponse.ok200().withPlainText("OK").toPromise())
                .with(GET, "/ready",
                        request -> HttpResponse.ok200().withPlainText("READY").toPromise())
                // Public bearer-token auth helpers used by frontend session checks.
                .with(GET, "/api/auth/me", jwtAuthController::currentUser)
                .with(GET, "/api/auth/validate", jwtAuthController::validate)
                // Login/logout (credential issuance — public, no prior JWT required)
                .with(POST, "/api/auth/login", loginController::login)
                .with(POST, "/api/auth/logout", loginController::logout)
                // Prometheus scrape endpoint (Observability 6.1)
                .with(GET, "/metrics", securedMetrics)
                // GDPR compliance endpoints (Dimension 5: Data Cloud)
                .with(io.activej.http.HttpMethod.DELETE, "/api/v1/gdpr/tenant/:tenantId", gdprController::deleteTenantData)
                .with(GET,  "/api/v1/gdpr/tenant/:tenantId/export", gdprController::exportTenantData)
                .with(io.activej.http.HttpMethod.DELETE, "/api/v1/gdpr/user/:userId", gdprController::deleteUserData)
                // Secured API routes (read/write permissions)
                .with(GET,  "/api/*", securedApi.readApi())
                .with(POST, "/api/*", securedApi.writeApi())
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

        return HttpServer.builder(eventloop,
                        new com.ghatana.yappc.services.security.SecurityHeadersServlet(correlationWrapper))
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
                ApprovalHttpHandlers approvalHttpHandlers = new ApprovalHttpHandlers(humanApprovalService, objectMapper);

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
                                        (String) json.get("tenantId"),
                                        (String) json.get("requestedBy"));
                                if (tr.tenantId() == null || tr.tenantId().isBlank()) {
                                    return Promise.of(HttpResponse.ofCode(400)
                                            .withJson("{\"error\":\"Missing required tenantId field\"}")
                                            .build());
                                }
                                return advancePhaseUseCase.execute(tr)
                                        .map(result -> {
                                            // Fire-and-forget AEP event (3.4/Ph1c)
                                            String eventType = result.isSuccess()
                                                    ? "lifecycle.phase.advanced"
                                                    : "lifecycle.phase.blocked";
                                            String tid = tr.tenantId();
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
                                .with(GET, "/api/v1/approvals/pending", approvalHttpHandlers::listPending)
                                .with(POST, "/api/v1/approvals",
                                                approvalHttpHandlers::create)
                                .with(GET, "/api/v1/approvals/:id",
                                                request -> approvalHttpHandlers.getById(request, request.getPathParameter("id")))
                                .with(POST, "/api/v1/approvals/:id/approve",
                                                request -> approvalHttpHandlers.approve(request, request.getPathParameter("id")))
                                .with(POST, "/api/v1/approvals/:id/reject",
                                                request -> approvalHttpHandlers.reject(request, request.getPathParameter("id")))

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
                    if (rawTenantId == null || rawTenantId.isBlank()) {
                        return Promise.of(HttpResponse.ofCode(400)
                                .withJson("{\"error\":\"Missing required X-Tenant-Id header\"}")
                                .build());
                    }
                    final String tenantId = rawTenantId;
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
