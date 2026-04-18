package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.services.run.RunService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.error500;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @doc.type class
 * @doc.purpose HTTP API controller for Run phase
 * @doc.layer api
 * @doc.pattern Controller
 */
public class RunApiController {

    private static final Logger log = LoggerFactory.getLogger(RunApiController.class);

    private final RunService runService;

    public RunApiController(RunService runService) {
        this.runService = runService;
    }

    public Promise<HttpResponse> executeRun(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    RunSpec spec = JsonMapper.fromJson(json, RunSpec.class);
                    if (spec == null || spec.id() == null || spec.id().isBlank()) {
                        return Promise.of(badRequest400("runSpec.id is required"));
                    }

                    return runService.execute(spec)
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid run execute request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Run execution request failed", e));
    }

    public Promise<HttpResponse> executeRunWithObservation(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    RunWithObservationRequest payload = JsonMapper.fromJson(json, RunWithObservationRequest.class);
                    if (payload == null || payload.runSpec() == null) {
                        return Promise.of(badRequest400("runSpec is required"));
                    }
                    if (payload.runSpec().id() == null || payload.runSpec().id().isBlank()) {
                        return Promise.of(badRequest400("runSpec.id is required"));
                    }

                    ObservationConfig config = payload.observationConfig() == null
                        ? ObservationConfig.defaultConfig()
                        : payload.observationConfig();

                    return runService.executeWithObservation(payload.runSpec(), config)
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid run execute-with-observation request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Run execute-with-observation request failed", e));
    }

    public Promise<HttpResponse> rollback(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    RollbackRequest payload = JsonMapper.fromJson(json, RollbackRequest.class);
                    if (payload == null || isBlank(payload.deploymentId()) || isBlank(payload.targetVersion())) {
                        return Promise.of(badRequest400("deploymentId and targetVersion are required"));
                    }

                    return runService.rollback(payload.deploymentId(), payload.targetVersion())
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid rollback request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Rollback request failed", e));
    }

    public Promise<HttpResponse> promote(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    PromoteRequest payload = JsonMapper.fromJson(json, PromoteRequest.class);
                    if (payload == null || isBlank(payload.deploymentId()) || isBlank(payload.targetEnvironment())) {
                        return Promise.of(badRequest400("deploymentId and targetEnvironment are required"));
                    }

                    return runService.promote(payload.deploymentId(), payload.targetEnvironment())
                        .map(this::toJsonResponse);
                } catch (JsonProcessingException e) {
                    log.error("Invalid promote request", e);
                    return Promise.of(badRequest400("Invalid JSON format"));
                }
            })
            .whenException(e -> log.error("Promote request failed", e));
    }

    private HttpResponse toJsonResponse(RunResult result) {
        try {
            return ok200Json(JsonMapper.toJson(result));
        } catch (JsonProcessingException e) {
            log.error("Error serializing run response", e);
            return error500("Internal server error");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RollbackRequest(String deploymentId, String targetVersion) {
    }

    private record PromoteRequest(String deploymentId, String targetEnvironment) {
    }

    private record RunWithObservationRequest(RunSpec runSpec, ObservationConfig observationConfig) {
    }
}