package com.ghatana.yappc.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.common.JsonMapper;
import com.ghatana.yappc.domain.run.ObservationConfig;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunSpec;
import com.ghatana.yappc.services.run.RunService;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
    private final AuditLogger auditLogger;

    public RunApiController(RunService runService) {
        this(runService, AuditLogger.noop());
    }

    public RunApiController(RunService runService, AuditLogger auditLogger) {
        this.runService = runService;
        this.auditLogger = auditLogger;
    }

    public Promise<HttpResponse> executeRun(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                String json = body.asString(UTF_8);
                try {
                    RunSpec spec = JsonMapper.fromJson(json, RunSpec.class);
                    if (spec == null || spec.id() == null || spec.id().isBlank()) {
                        return auditThenRespond(
                            request,
                            "run.execute.request",
                            "rejected",
                            null,
                            null,
                            Map.of("route", "run", "reason", "runSpec.id is required"),
                            badRequest400("runSpec.id is required")
                        );
                    }

                    return runService.execute(spec)
                        .then(result -> auditThenRespond(
                            request,
                            "run.execute.request",
                            "succeeded",
                            spec.id(),
                            spec.environment(),
                            Map.of(
                                "route", "run",
                                "runSpecId", spec.id(),
                                "environment", safeValue(spec.environment()),
                                "status", result.status().name()
                            ),
                            toJsonResponse(result)
                        ));
                } catch (JsonProcessingException e) {
                    log.error("Invalid run execute request", e);
                    return auditThenRespond(
                        request,
                        "run.execute.request",
                        "rejected",
                        null,
                        null,
                        Map.of("route", "run", "reason", "Invalid JSON format"),
                        badRequest400("Invalid JSON format")
                    );
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
                        return auditThenRespond(
                            request,
                            "run.observation.request",
                            "rejected",
                            null,
                            null,
                            Map.of("route", "run-with-observation", "reason", "runSpec is required"),
                            badRequest400("runSpec is required")
                        );
                    }
                    if (payload.runSpec().id() == null || payload.runSpec().id().isBlank()) {
                        return auditThenRespond(
                            request,
                            "run.observation.request",
                            "rejected",
                            null,
                            safeValue(payload.runSpec().environment()),
                            Map.of("route", "run-with-observation", "reason", "runSpec.id is required"),
                            badRequest400("runSpec.id is required")
                        );
                    }

                    ObservationConfig config = payload.observationConfig() == null
                        ? ObservationConfig.defaultConfig()
                        : payload.observationConfig();

                    return runService.executeWithObservation(payload.runSpec(), config)
                        .then(result -> auditThenRespond(
                            request,
                            "run.observation.request",
                            "succeeded",
                            payload.runSpec().id(),
                            payload.runSpec().environment(),
                            Map.of(
                                "route", "run-with-observation",
                                "runSpecId", payload.runSpec().id(),
                                "environment", safeValue(payload.runSpec().environment()),
                                "status", result.status().name(),
                                "observationConfig", String.valueOf(config)
                            ),
                            toJsonResponse(result)
                        ));
                } catch (JsonProcessingException e) {
                    log.error("Invalid run execute-with-observation request", e);
                    return auditThenRespond(
                        request,
                        "run.observation.request",
                        "rejected",
                        null,
                        null,
                        Map.of("route", "run-with-observation", "reason", "Invalid JSON format"),
                        badRequest400("Invalid JSON format")
                    );
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
                        return auditThenRespond(
                            request,
                            "run.rollback.request",
                            "rejected",
                            payload == null ? null : payload.deploymentId(),
                            null,
                            Map.of("route", "run-rollback", "reason", "deploymentId and targetVersion are required"),
                            badRequest400("deploymentId and targetVersion are required")
                        );
                    }

                    return runService.rollback(payload.deploymentId(), payload.targetVersion())
                        .then(result -> auditThenRespond(
                            request,
                            "run.rollback.request",
                            "succeeded",
                            payload.deploymentId(),
                            null,
                            Map.of(
                                "route", "run-rollback",
                                "deploymentId", payload.deploymentId(),
                                "targetVersion", payload.targetVersion(),
                                "status", result.status().name()
                            ),
                            toJsonResponse(result)
                        ));
                } catch (JsonProcessingException e) {
                    log.error("Invalid rollback request", e);
                    return auditThenRespond(
                        request,
                        "run.rollback.request",
                        "rejected",
                        null,
                        null,
                        Map.of("route", "run-rollback", "reason", "Invalid JSON format"),
                        badRequest400("Invalid JSON format")
                    );
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
                        return auditThenRespond(
                            request,
                            "run.promote.request",
                            "rejected",
                            payload == null ? null : payload.deploymentId(),
                            payload == null ? null : payload.targetEnvironment(),
                            Map.of("route", "run-promote", "reason", "deploymentId and targetEnvironment are required"),
                            badRequest400("deploymentId and targetEnvironment are required")
                        );
                    }

                    return runService.promote(payload.deploymentId(), payload.targetEnvironment())
                        .then(result -> auditThenRespond(
                            request,
                            "run.promote.request",
                            "succeeded",
                            payload.deploymentId(),
                            payload.targetEnvironment(),
                            Map.of(
                                "route", "run-promote",
                                "deploymentId", payload.deploymentId(),
                                "targetEnvironment", payload.targetEnvironment(),
                                "status", result.status().name()
                            ),
                            toJsonResponse(result)
                        ));
                } catch (JsonProcessingException e) {
                    log.error("Invalid promote request", e);
                    return auditThenRespond(
                        request,
                        "run.promote.request",
                        "rejected",
                        null,
                        null,
                        Map.of("route", "run-promote", "reason", "Invalid JSON format"),
                        badRequest400("Invalid JSON format")
                    );
                }
            })
            .whenException(e -> log.error("Promote request failed", e));
    }

    private Promise<HttpResponse> auditThenRespond(
            HttpRequest request,
            String eventType,
            String outcome,
            String runId,
            String environment,
            Map<String, Object> metadata,
            HttpResponse response) {
        return auditRunEvent(request, eventType, outcome, runId, environment, metadata)
            .then(($, e) -> {
                if (e != null) {
                    log.warn("Run controller audit failed for eventType={} outcome={}: {}",
                        eventType, outcome, e.getMessage());
                }
                return Promise.of(response);
            });
    }

    private Promise<Void> auditRunEvent(
            HttpRequest request,
            String eventType,
            String outcome,
            String runId,
            String environment,
            Map<String, Object> metadata) {
        Principal principal = request.getAttachment(Principal.class);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("outcome", outcome);
        event.put("actor", principal.getName());
        event.put("tenantId", principal.getTenantId());
        event.put("workspaceId", firstNonBlank(
            request.getHeader(HttpHeaders.of("X-Workspace-Id")),
            request.getHeader(HttpHeaders.of("X-Workspace-ID")),
            "unknown"
        ));
        event.put("projectId", firstNonBlank(request.getHeader(HttpHeaders.of("X-Project-Id")), "unknown"));
        event.put("runId", firstNonBlank(runId, "unknown"));
        event.put("environment", firstNonBlank(environment, "unknown"));
        event.put("correlationId", firstNonBlank(
            request.getHeader(HttpHeaders.of("X-Correlation-ID")),
            request.getHeader(HttpHeaders.of("X-Request-ID")),
            UUID.randomUUID().toString()
        ));
        event.put("timestamp", Instant.now().toString());
        event.put("metadata", metadata == null ? Map.of() : metadata);
        return auditLogger.log(event);
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private record RollbackRequest(String deploymentId, String targetVersion) {
    }

    private record PromoteRequest(String deploymentId, String targetEnvironment) {
    }

    private record RunWithObservationRequest(RunSpec runSpec, ObservationConfig observationConfig) {
    }
}
