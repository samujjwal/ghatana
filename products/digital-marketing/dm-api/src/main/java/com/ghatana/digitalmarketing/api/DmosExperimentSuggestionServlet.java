package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.optimization.ExperimentSuggestionService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestionStatus;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentType;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HTTP servlet for experiment suggestion management (P3-004).
 *
 * @doc.type class
 * @doc.purpose DMOS experiment suggestion API servlet (P3-004)
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosExperimentSuggestionServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosExperimentSuggestionServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ExperimentSuggestionService service;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosExperimentSuggestionServlet(ExperimentSuggestionService service, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosExperimentSuggestionServlet(ExperimentSuggestionService service, Eventloop eventloop) {
        this(service, eventloop, DmosMetricsCollector.noop(), new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()), new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/experiment-suggestions", this::handlePublish)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/experiment-suggestions/:suggestionId/approve", this::handleApprove)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/experiment-suggestions/:suggestionId/reject", this::handleReject)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/experiment-suggestions", this::handleListByWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/experiment-suggestions/:suggestionId", this::handleGetById)
                .build(),
            metrics,
            "experiment-suggestions"
        );
    }

    private Promise<HttpResponse> handlePublish(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                PublishRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), PublishRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /experiment-suggestions", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    ExperimentSuggestionService.PublishSuggestionCommand command =
                        new ExperimentSuggestionService.PublishSuggestionCommand(
                            body.campaignId(),
                            ExperimentType.valueOf(body.experimentType()),
                            body.title(),
                            body.description(),
                            body.controlVariant(),
                            body.treatmentVariant(),
                            body.hypothesis(),
                            body.successMetric(),
                            body.minimumDetectableEffect(),
                            body.rationale(),
                            body.expiresAt()
                        );
                    return service.publish(ctx, command)
                        .map(suggestion -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, ExperimentSuggestionResponse.from(suggestion));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("publish suggestion", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to publish experiment suggestion", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleApprove(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String suggestionId = request.getPathParameter("suggestionId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                ApproveRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), ApproveRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /experiment-suggestions/:suggestionId/approve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.approve(ctx, suggestionId, body.experimentId(), body.approvedBy())
                        .map(suggestion -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, ExperimentSuggestionResponse.from(suggestion));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("approve suggestion", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve experiment suggestion", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleReject(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String suggestionId = request.getPathParameter("suggestionId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                RejectRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), RejectRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /experiment-suggestions/:suggestionId/reject", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.reject(ctx, suggestionId, body.reason())
                        .map(suggestion -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, ExperimentSuggestionResponse.from(suggestion));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("reject suggestion", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to reject experiment suggestion", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleListByWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.listByWorkspace(ctx)
                .map(suggestions -> jsonResponse(200, suggestions.stream().map(ExperimentSuggestionResponse::from).collect(Collectors.toList())))
                .then(r -> Promise.of(r), e -> mapServiceError("list suggestions", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to list experiment suggestions", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String suggestionId = request.getPathParameter("suggestionId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.findById(ctx, suggestionId)
                .map(opt -> opt.map(suggestion -> jsonResponse(200, ExperimentSuggestionResponse.from(suggestion))).orElse(errorResponse(404, "Suggestion not found")))
                .then(r -> Promise.of(r), e -> mapServiceError("get suggestion", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get experiment suggestion", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error) {
        if (error instanceof SecurityException) {
            return Promise.of(errorResponse(403, error.getMessage()));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(errorResponse(404, error.getMessage()));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(errorResponse(400, error.getMessage()));
        }
        if (error instanceof IllegalStateException) {
            return Promise.of(errorResponse(409, error.getMessage()));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error"));
    }

    private HttpResponse jsonResponse(int code, Object body) {
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build();
        } catch (Exception e) {
            LOG.error("[DMOS] Serialization failure", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    private HttpResponse errorResponse(int code, String message) {
        return DmosApiErrorResponses.error(code, message, DmCorrelationId.generate().getValue());
    }

    record PublishRequest(String campaignId, String experimentType, String title, String description, Map<String, Object> controlVariant, Map<String, Object> treatmentVariant, String hypothesis, String successMetric, double minimumDetectableEffect, String rationale, Instant expiresAt) {}
    record ApproveRequest(String experimentId, String approvedBy) {}
    record RejectRequest(String reason) {}

    record ExperimentSuggestionResponse(
        String id,
        String tenantId,
        String workspaceId,
        String campaignId,
        String experimentType,
        String title,
        String description,
        Map<String, Object> controlVariant,
        Map<String, Object> treatmentVariant,
        String hypothesis,
        String successMetric,
        double minimumDetectableEffect,
        String rationale,
        String status,
        String rejectionReason,
        String experimentId,
        String approvedBy,
        Instant createdAt,
        Instant approvedAt,
        Instant expiresAt
    ) {
        static ExperimentSuggestionResponse from(ExperimentSuggestion suggestion) {
            return new ExperimentSuggestionResponse(
                suggestion.getId(),
                suggestion.getTenantId(),
                suggestion.getWorkspaceId(),
                suggestion.getCampaignId(),
                suggestion.getExperimentType().name(),
                suggestion.getTitle(),
                suggestion.getDescription(),
                suggestion.getControlVariant(),
                suggestion.getTreatmentVariant(),
                suggestion.getHypothesis(),
                suggestion.getSuccessMetric(),
                suggestion.getMinimumDetectableEffect(),
                suggestion.getRationale(),
                suggestion.getStatus().name(),
                suggestion.getRejectionReason(),
                suggestion.getExperimentId(),
                suggestion.getApprovedBy(),
                suggestion.getCreatedAt(),
                suggestion.getApprovedAt(),
                suggestion.getExpiresAt()
            );
        }
    }

}


