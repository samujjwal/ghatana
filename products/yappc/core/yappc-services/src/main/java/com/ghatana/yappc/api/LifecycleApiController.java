package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        EvolutionService evolutionService
    ) {
        this.intentService = intentService;
        this.shapeService = shapeService;
        this.validationService = validationService;
        this.generationService = generationService;
        this.runService = runService;
        this.observeService = observeService;
        this.learningService = learningService;
        this.evolutionService = evolutionService;
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

                    return executeLifecyclePipeline(payload)
                        .map(this::toJsonResponse)
                        .then((response, error) -> {
                            if (error != null) {
                                log.error("Lifecycle DAG execution failed", error);
                                return Promise.of(toJsonResponse(buildPipelineFailureResult(error)));
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

    private Promise<LifecycleExecutionResult> executeLifecyclePipeline(LifecycleExecuteRequest payload) {
        Map<String, Long> phaseDurationsMs = new LinkedHashMap<>();
        List<String> executedPhases = new ArrayList<>();
        List<String> executionPlan = resolveDagExecutionPlan();

        long intentStart = System.currentTimeMillis();
        return intentService.capture(payload.intentInput())
            .then(intentSpec -> {
                recordPhaseTiming("INTENT", intentStart, phaseDurationsMs, executedPhases);

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
                                        executionPlan,
                                        executedPhases,
                                        phaseDurationsMs
                                    );
                                    return Promise.of(buildFailedLifecycleResult(intentSpec, shapeSpec, validationResult, metadata));
                                }

                                ValidatedSpec validatedSpec = ValidatedSpec.of(shapeSpec, validationResult);
                                long generateStart = System.currentTimeMillis();
                                return generationService.generate(validatedSpec)
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
                                                        return analyzeAndEvolve(payload, intentSpec, shapeSpec,
                                                            validationResult, artifacts, runResult, observation)
                                                            .map(result -> {
                                                                recordPhaseTiming("LEARN_EVOLVE", learnEvolveStart, phaseDurationsMs, executedPhases);
                                                                Map<String, String> metadata = buildPipelineMetadata(
                                                                    "SUCCESS",
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
        IntentSpec intentSpec,
        ShapeSpec shapeSpec,
        LifecycleValidationResult validationResult,
        GeneratedArtifacts artifacts,
        RunResult runResult,
        Observation observation
    ) {
        Promise<Insights> insightsPromise = payload.historicalContext() == null
            ? learningService.analyze(observation)
            : learningService.analyzeWithContext(observation, payload.historicalContext());

        return insightsPromise.then(insights -> {
            Promise<EvolutionPlan> evolvePromise = payload.constraints() == null
                ? evolutionService.propose(insights)
                : evolutionService.proposeWithConstraints(insights, payload.constraints());

            return evolvePromise.map(evolutionPlan -> new LifecycleExecutionResult(
                intentSpec,
                shapeSpec,
                validationResult,
                artifacts,
                runResult,
                observation,
                insights,
                evolutionPlan,
                Map.of("status", "SUCCESS")
            ));
        });
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
        List<String> executionPlan,
        List<String> executedPhases,
        Map<String, Long> phaseDurationsMs
    ) {
        return Map.of(
            "status", status,
            "pipelineMode", "DAG",
            "pipelineGraphVersion", "2026-04-17",
            "executionPlan", String.join("->", executionPlan),
            "executedPhases", String.join("->", executedPhases),
            "phaseDurationsMs", phaseDurationsMs.toString()
        );
    }

    private LifecycleExecutionResult buildPipelineFailureResult(Throwable error) {
        return new LifecycleExecutionResult(
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
                "error", error.getClass().getSimpleName()
            )
        );
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
            ? "staging"
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

    private record LifecycleExecuteRequest(
        IntentInput intentInput,
        String environment,
        RunSpec runSpec,
        HistoricalContext historicalContext,
        ConstraintSpec constraints
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
