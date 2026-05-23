package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.intent.ConstraintSpec;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.learn.HistoricalContext;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.domain.run.RunTask;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.services.evolve.EvolutionService;
import com.ghatana.yappc.services.generate.GenerationService;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.run.RunService;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.validate.ValidationService;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpMethod;
import io.activej.http.HttpClient;
import io.activej.http.HttpHeaders;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.error500;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for end-to-end YAPPC lifecycle orchestration
 * @doc.layer api
 * @doc.pattern Controller
 */
public class LifecycleApiController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleApiController.class);

    private final IntentService intentService;
    private final ShapeService shapeService;
    private final ValidationService validationService;
    private final GenerationService generationService;
    private final RunService runService;
    private final ObserveService observeService;
    private final LearningService learningService;
    private final EvolutionService evolutionService;
    private final Eventloop eventloop;
    private final HttpClient httpClient;
    private final PhaseActionContract phaseActionContract;
    private final LifecycleExecutionRepository executionRepository;
    private static final List<String> PIPELINE_DAG_ORDER = List.of(
        "INTENT", "SHAPE", "VALIDATE", "GENERATE", "RUN", "OBSERVE", "LEARN", "EVOLVE"
    );

    public LifecycleApiController(
        IntentService intentService,
        ShapeService shapeService,
        ValidationService validationService,
        GenerationService generationService,
        RunService runService,
        ObserveService observeService,
        LearningService learningService,
        EvolutionService evolutionService,
        Eventloop eventloop,
        HttpClient httpClient,
        LifecycleExecutionRepository executionRepository
    ) {
        this(
            intentService,
            shapeService,
            validationService,
            generationService,
            runService,
            observeService,
            learningService,
            evolutionService,
            eventloop,
            httpClient,
            executionRepository,
            PhaseActionContract.defaults()
        );
    }

    public LifecycleApiController(
        IntentService intentService,
        ShapeService shapeService,
        ValidationService validationService,
        GenerationService generationService,
        RunService runService,
        ObserveService observeService,
        LearningService learningService,
        EvolutionService evolutionService,
        Eventloop eventloop,
        HttpClient httpClient,
        LifecycleExecutionRepository executionRepository,
        PhaseActionContract phaseActionContract
    ) {
        this.intentService = intentService;
        this.shapeService = shapeService;
        this.validationService = validationService;
        this.generationService = generationService;
        this.runService = runService;
        this.observeService = observeService;
        this.learningService = learningService;
        this.evolutionService = evolutionService;
        this.eventloop = eventloop;
        this.httpClient = httpClient;
        this.phaseActionContract = phaseActionContract;
        this.executionRepository = executionRepository;
    }

    public Promise<HttpResponse> executeFullLifecycle(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    LifecycleExecuteRequest payload = JsonMapper.fromJson(json, LifecycleExecuteRequest.class);
                    if (payload == null || payload.intentInput() == null || isBlank(payload.intentInput().rawText())) {
                        return Promise.of(badRequest400("intentInput.rawText is required"));
                    }

                    // Validate required traceability fields
                    if (payload.projectId() == null || payload.projectId().isBlank()) {
                        return Promise.of(badRequest400("projectId is required"));
                    }
                    if (payload.workspaceId() == null || payload.workspaceId().isBlank()) {
                        return Promise.of(badRequest400("workspaceId is required"));
                    }

                    // Extract actor and correlation ID from authenticated principal
                    Principal principal = request.getAttachment(Principal.class);
                    if (principal == null) {
                        return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Unauthenticated\"}")
                            .build());
                    }

                    String actorId = principal.getName();
                    String tenantId = principal.getTenantId();
                    String correlationId = extractCorrelationId(request);

                    // Validate tenant scope - principal's tenant must match request tenant
                    if (payload.tenantId() != null && !payload.tenantId().equals(tenantId)) {
                        log.warn("Tenant scope mismatch: principalTenant={}, requestTenant={}",
                            tenantId, payload.tenantId());
                        return Promise.of(HttpResponse.ofCode(403)
                            .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                            .build());
                    }

                    LifecycleExecutionContext context = new LifecycleExecutionContext(
                        tenantId,
                        payload.workspaceId(),
                        payload.projectId(),
                        actorId,
                        correlationId
                    );

                    return executeLifecyclePipeline(payload, context)
                        .map(this::toJsonResponse)
                        .then((response, error) -> {
                            if (error != null) {
                                log.error("Lifecycle DAG execution failed", error);
                                return Promise.of(toJsonResponse(buildPipelineFailureResult(error, context)));
                            }
                            return Promise.of(response);
                        });
                } catch (JsonProcessingException e) {
                    log.error("Invalid full lifecycle request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Full lifecycle execution failed", e));
    }

    private Promise<LifecycleExecutionResult> executeLifecyclePipeline(LifecycleExecuteRequest payload, LifecycleExecutionContext context) {
        Map<String, Long> phaseDurationsMs = new LinkedHashMap<>();
        List<String> executedPhases = new ArrayList<>();
        List<String> executionPlan = resolveDagExecutionPlan();
        String executionId = UUID.randomUUID().toString();

        long pipelineStartMs = System.currentTimeMillis();
        return intentService.capture(payload.intentInput())
            .then(intentSpec -> {
                recordPhaseTiming("INTENT", pipelineStartMs, phaseDurationsMs, executedPhases);

                long shapeStart = System.currentTimeMillis();
                return shapeService.derive(intentSpec)
                    .then(shapeSpec -> {
                        recordPhaseTiming("SHAPE", shapeStart, phaseDurationsMs, executedPhases);

                        long validateStart = System.currentTimeMillis();
                        return validationService.validate(shapeSpec)
                            .then(validationResult -> {
                                recordPhaseTiming("VALIDATE", validateStart, phaseDurationsMs, executedPhases);

                                if (!validationResult.passed() || validationResult.hasBlockingIssues()) {
                                    Map<String, String> metadata = buildPipelineMetadata(
                                        "VALIDATION_FAILED",
                                        executionId,
                                        executionPlan,
                                        executedPhases,
                                        phaseDurationsMs
                                    );
                                    return Promise.of(buildFailedLifecycleResult(intentSpec, shapeSpec, validationResult, metadata));
                                }

                                ValidatedSpec validatedSpec = ValidatedSpec.of(shapeSpec, validationResult);
                                long generateStart = System.currentTimeMillis();

                                com.ghatana.yappc.domain.generate.GenerationContext genContext =
                                    com.ghatana.yappc.domain.generate.GenerationContext.builder()
                                        .tenantId(context.tenantId())
                                        .workspaceId(context.workspaceId())
                                        .projectId(context.projectId())
                                        .actorId(context.actorId())
                                        .phase("GENERATE")
                                        .sourceArtifactIds(java.util.List.of())
                                        .canvasNodeIds(java.util.List.of())
                                        .intentId(intentSpec.id())
                                        .shapeId(shapeSpec.id())
                                        .correlationId(context.correlationId())
                                        .build();

                                return generationService.generate(validatedSpec, genContext)
                                    .then(artifacts -> {
                                        recordPhaseTiming("GENERATE", generateStart, phaseDurationsMs, executedPhases);

                                        RunSpec runSpec = payload.runSpec() != null
                                            ? payload.runSpec()
                                            : defaultRunSpec(artifacts, payload.environment());

                                        long runStart = System.currentTimeMillis();
                                        return runService.execute(runSpec)
                                            .then(runResult -> {
                                                recordPhaseTiming("RUN", runStart, phaseDurationsMs, executedPhases);

                                                long observeStart = System.currentTimeMillis();
                                                return observeService.collect(runResult)
                                                    .then(observation -> {
                                                        recordPhaseTiming("OBSERVE", observeStart, phaseDurationsMs, executedPhases);

                                                        long learnEvolveStart = System.currentTimeMillis();
                                                        return analyzeAndEvolve(payload, context, executionId, intentSpec, shapeSpec,
                                                            validationResult, artifacts, runResult, observation,
                                                            phaseDurationsMs, executedPhases, pipelineStartMs)
                                                            .map(result -> {
                                                                recordPhaseTiming("LEARN_EVOLVE", learnEvolveStart, phaseDurationsMs, executedPhases);
                                                                Map<String, String> metadata = buildPipelineMetadata(
                                                                    "SUCCESS",
                                                                    executionId,
                                                                    executionPlan,
                                                                    executedPhases,
                                                                    phaseDurationsMs
                                                                );
                                                                return result.withMetadata(metadata);
                                                            });
                                                    });
                                            });
                                    });
                            });
                    });
            });
    }

    private Promise<LifecycleExecutionResult> analyzeAndEvolve(
        LifecycleExecuteRequest payload,
        LifecycleExecutionContext context,
        String executionId,
        IntentSpec intentSpec,
        ShapeSpec shapeSpec,
        LifecycleValidationResult validationResult,
        GeneratedArtifacts artifacts,
        RunResult runResult,
        Observation observation,
        Map<String, Long> phaseDurationsMs,
        List<String> executedPhases,
        long pipelineStartMs
    ) {
        Promise<Insights> insightsPromise = payload.historicalContext() == null
            ? learningService.analyze(observation)
            : learningService.analyzeWithContext(observation, payload.historicalContext());

        return insightsPromise.then(insights -> {
            Promise<EvolutionPlan> evolvePromise = payload.constraints() == null
                ? evolutionService.propose(insights)
                : evolutionService.proposeWithConstraints(insights, payload.constraints());

            return evolvePromise.map(evolutionPlan -> {
                LifecycleExecutionResult result = new LifecycleExecutionResult(
                    intentSpec,
                    shapeSpec,
                    validationResult,
                    artifacts,
                    runResult,
                    observation,
                    insights,
                    evolutionPlan,
                    Map.of("status", "SUCCESS")
                );

                long totalDurationMs = System.currentTimeMillis() - pipelineStartMs;
                persistExecutionResult(executionId, context, result, phaseDurationsMs, executedPhases, totalDurationMs);

                return result;
            });
        });
    }

    private void persistExecutionResult(
        String executionId,
        LifecycleExecutionContext context,
        LifecycleExecutionResult result,
        Map<String, Long> phaseDurationsMs,
        List<String> executedPhases,
        long totalDurationMs
    ) {
        try {
            Instant startedAt = Instant.now().minusMillis(totalDurationMs);
            Instant completedAt = Instant.now();
            String idempotencyKey = executionId + ":" + context.correlationId();

            LifecycleExecutionRepository.LifecycleExecution execution = new LifecycleExecutionRepository.LifecycleExecution(
                executionId,
                context.tenantId(),
                context.workspaceId(),
                context.projectId(),
                context.actorId(),
                context.correlationId(),
                idempotencyKey,
                startedAt,
                completedAt,
                totalDurationMs,
                executedPhases,
                phaseDurationsMs,
                result.metadata().getOrDefault("status", "SUCCESS"),
                result.intent() != null ? mapOfNonNull(
                    "id", result.intent().id(),
                    "title", result.intent().productName(),
                    "description", result.intent().description()
                ) : null,
                result.shape() != null ? mapOfNonNull(
                    "architecture", result.shape().architecture(),
                    "domainModel", result.shape().domainModel(),
                    "workflows", result.shape().workflows(),
                    "integrations", result.shape().integrations()
                ) : null,
                result.validation() != null ? mapOfNonNull(
                    "passed", result.validation().passed(),
                    "hasBlockingIssues", result.validation().hasBlockingIssues(),
                    "issues", result.validation().issues()
                ) : null,
                result.artifacts() != null ? mapOfNonNull(
                    "id", result.artifacts().id(),
                    "specRef", result.artifacts().specRef(),
                    "generatorVersion", result.artifacts().generatorVersion(),
                    "artifacts", result.artifacts().artifacts()
                ) : null,
                result.run() != null ? mapOfNonNull(
                    "status", result.run().status().toString(),
                    "taskResults", result.run().taskResults()
                ) : null,
                result.observation() != null ? mapOfNonNull(
                    "metrics", result.observation().metrics(),
                    "logs", result.observation().logs(),
                    "traces", result.observation().traces()
                ) : null,
                result.insights() != null ? mapOfNonNull(
                    "patterns", result.insights().patterns(),
                    "anomalies", result.insights().anomalies(),
                    "recommendations", result.insights().recommendations()
                ) : null,
                result.evolution() != null ? mapOfNonNull(
                    "tasks", result.evolution().tasks(),
                    "newIntentRef", result.evolution().newIntentRef(),
                    "metadata", result.evolution().metadata()
                ) : null,
                result.metadata()
            );

            // Persist using durable repository
            executionRepository.persist(execution)
                .whenResult(ignored -> log.info("Successfully persisted lifecycle execution: executionId={}, tenantId={}, projectId={}",
                    executionId, context.tenantId(), context.projectId()))
                .whenException(ex -> log.error("Failed to persist lifecycle execution: executionId={}, tenantId={}, projectId={}",
                    executionId, context.tenantId(), context.projectId(), ex));

        } catch (Exception ex) {
            log.error("Error preparing lifecycle execution result for persistence", ex);
        }
    }

    private Map<String, Object> mapOfNonNull(Object... keyValues) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (!(key instanceof String keyString)) {
                continue;
            }
            if (value != null) {
                values.put(keyString, value);
            }
        }
        return values;
    }

    private List<String> resolveDagExecutionPlan() {
        // Current lifecycle is a linearized DAG. Keeping this explicit allows non-linear expansion later.
        return List.copyOf(PIPELINE_DAG_ORDER);
    }

    private void recordPhaseTiming(
        String phase,
        long phaseStartMillis,
        Map<String, Long> phaseDurationsMs,
        List<String> executedPhases
    ) {
        phaseDurationsMs.put(phase, Math.max(0L, System.currentTimeMillis() - phaseStartMillis));
        executedPhases.add(phase);
    }

    private Map<String, String> buildPipelineMetadata(
        String status,
        String executionId,
        List<String> executionPlan,
        List<String> executedPhases,
        Map<String, Long> phaseDurationsMs
    ) {
        return Map.of(
            "status", status,
            "executionId", executionId,
            "pipelineMode", "DAG",
            "pipelineGraphVersion", "2026-04-17",
            "executionPlan", String.join("->", executionPlan),
            "executedPhases", String.join("->", executedPhases),
            "phaseDurationsMs", phaseDurationsMs.toString()
        );
    }

    private LifecycleExecutionResult buildPipelineFailureResult(Throwable error, LifecycleExecutionContext context) {
        // Even failed executions should be persisted for traceability
        String executionId = UUID.randomUUID().toString();
        LifecycleExecutionResult result = new LifecycleExecutionResult(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of(
                "status", "FAILED",
                "pipelineMode", "DAG",
                "error", error.getClass().getSimpleName(),
                "errorMessage", error.getMessage()
            )
        );

        // Persist failure for traceability
        try {
            Instant startedAt = Instant.now();
            Instant completedAt = Instant.now();
            String idempotencyKey = executionId + ":" + context.correlationId();

            LifecycleExecutionRepository.LifecycleExecution execution = new LifecycleExecutionRepository.LifecycleExecution(
                executionId,
                context.tenantId(),
                context.workspaceId(),
                context.projectId(),
                context.actorId(),
                context.correlationId(),
                idempotencyKey,
                startedAt,
                completedAt,
                0,
                List.of(),
                Map.of(),
                "FAILED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                result.metadata()
            );

            executionRepository.persist(execution)
                .whenResult(ignored -> log.info("Persisted failed lifecycle execution: executionId={}", executionId))
                .whenException(ex -> log.error("Failed to persist failed lifecycle execution: executionId={}", executionId, ex));
        } catch (Exception ex) {
            log.error("Error preparing failed lifecycle execution for persistence", ex);
        }

        return result;
    }

    private LifecycleExecutionResult buildFailedLifecycleResult(
        IntentSpec intentSpec,
        ShapeSpec shapeSpec,
        LifecycleValidationResult validationResult,
        Map<String, String> metadata
    ) {
        return new LifecycleExecutionResult(
            intentSpec,
            shapeSpec,
            validationResult,
            null,
            null,
            null,
            null,
            null,
            metadata
        );
    }

    private RunSpec defaultRunSpec(GeneratedArtifacts artifacts, String requestedEnvironment) {
        String environment = (requestedEnvironment == null || requestedEnvironment.isBlank())
            ? phaseActionContract.defaultRunEnvironment()
            : requestedEnvironment;
        return RunSpec.builder()
            .id("run-" + artifacts.id())
            .artifactsRef(artifacts.id())
            .environment(environment)
            .tasks(List.of(
                RunTask.builder().id("build").type("build").name("Build artifacts").config(Map.of("specRef", artifacts.specRef())).build(),
                RunTask.builder().id("test").type("test").name("Run tests").config(Map.of("specRef", artifacts.specRef())).build(),
                RunTask.builder().id("deploy").type("deploy").name("Deploy generated artifacts").config(Map.of("specRef", artifacts.specRef())).build()
            ))
            .config(Map.of("generatedArtifactsRef", artifacts.id(), "generatorVersion", artifacts.generatorVersion()))
            .build();
    }

    private HttpResponse toJsonResponse(LifecycleExecutionResult result) {
        try {
            return ok200Json(JsonMapper.toJson(result));
        } catch (JsonProcessingException e) {
            log.error("Error serializing lifecycle response", e);
            return error500("Internal server error");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String extractCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader(HttpHeaders.of("X-Correlation-Id"));
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private record LifecycleExecuteRequest(
        IntentInput intentInput,
        String tenantId,
        String projectId,
        String workspaceId,
        String environment,
        RunSpec runSpec,
        HistoricalContext historicalContext,
        ConstraintSpec constraints
    ) {
    }

    private record LifecycleExecutionContext(
        String tenantId,
        String workspaceId,
        String projectId,
        String actorId,
        String correlationId
    ) {
    }

    private record LifecycleExecutionResult(
        IntentSpec intent,
        ShapeSpec shape,
        LifecycleValidationResult validation,
        GeneratedArtifacts artifacts,
        RunResult run,
        Observation observation,
        Insights insights,
        EvolutionPlan evolution,
        Map<String, String> metadata
    ) {
        private LifecycleExecutionResult withMetadata(Map<String, String> newMetadata) {
            return new LifecycleExecutionResult(
                intent,
                shape,
                validation,
                artifacts,
                run,
                observation,
                insights,
                evolution,
                newMetadata
            );
        }
    }
}
