package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.optimization.NextBestActionRecommendationService;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionType;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP servlet for next-best-action recommendation management (P3-004).
 *
 * <p>Exposes routes:
 * <ul>
 *   <li>POST  /v1/workspaces/:workspaceId/next-best-action-recommendations</li>
 *   <li>POST  /v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/approve</li>
 *   <li>POST  /v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/reject</li>
 *   <li>GET   /v1/workspaces/:workspaceId/next-best-action-recommendations</li>
 *   <li>GET   /v1/workspaces/:workspaceId/next-best-action-recommendations/:recId</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS next-best-action recommendation API servlet (P3-004)
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosNextBestActionRecommendationServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosNextBestActionRecommendationServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final NextBestActionRecommendationService service;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosNextBestActionRecommendationServlet(NextBestActionRecommendationService service, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosNextBestActionRecommendationServlet(NextBestActionRecommendationService service, Eventloop eventloop) {
        this(service, eventloop, DmosMetricsCollector.noop(), new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()), new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/next-best-action-recommendations", this::handlePublish)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/approve", this::handleApprove)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId/reject", this::handleReject)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/next-best-action-recommendations", this::handleListByWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/next-best-action-recommendations/:recId", this::handleGetById)
                .build(),
            metrics,
            "next-best-action"
        );
    }

    private Promise<HttpResponse> handlePublish(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                PublishRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), PublishRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /next-best-action-recommendations", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    NextBestActionRecommendationService.PublishRecommendationCommand command =
                        new NextBestActionRecommendationService.PublishRecommendationCommand(
                            body.campaignId(),
                            NextBestActionType.valueOf(body.actionType()),
                            body.title(),
                            body.description(),
                            body.parameters(),
                            body.confidenceScore(),
                            body.rationale(),
                            body.expiresAt()
                        );
                    return service.publish(ctx, command)
                        .map(rec -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, NextBestActionResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("publish recommendation", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to publish next-best-action recommendation", e);
                return Promise.of(errorResponse(500, "Internal error", resolveCorrelationId(request), Map.of()));
            }
        });
    }

    private Promise<HttpResponse> handleApprove(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String recId = request.getPathParameter("recId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                ApproveRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), ApproveRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /next-best-action-recommendations/:recId/approve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.approve(ctx, recId, body.executedBy())
                        .map(rec -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, NextBestActionResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("approve recommendation", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve next-best-action recommendation", e);
                return Promise.of(errorResponse(500, "Internal error", resolveCorrelationId(request), Map.of()));
            }
        });
    }

    private Promise<HttpResponse> handleReject(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String recId = request.getPathParameter("recId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                RejectRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), RejectRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /next-best-action-recommendations/:recId/reject", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.reject(ctx, recId, body.reason())
                        .map(rec -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, NextBestActionResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("reject recommendation", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to reject next-best-action recommendation", e);
                return Promise.of(errorResponse(500, "Internal error", resolveCorrelationId(request), Map.of()));
            }
        });
    }

    private Promise<HttpResponse> handleListByWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.listByWorkspace(ctx)
                .map(recs -> jsonResponse(200, recs.stream().map(NextBestActionResponse::from).collect(Collectors.toList())))
                .then(r -> Promise.of(r), e -> mapServiceError("list recommendations", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to list next-best-action recommendations", e);
            return Promise.of(errorResponse(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String recId = request.getPathParameter("recId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.findById(ctx, recId)
                .map(opt -> opt.map(rec -> jsonResponse(200, NextBestActionResponse.from(rec))).orElse(errorResponse(404, "Recommendation not found")))
                .then(r -> Promise.of(r), e -> mapServiceError("get recommendation", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage(), resolveCorrelationId(request), Map.of("request", e.getMessage())));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get next-best-action recommendation", e);
            return Promise.of(errorResponse(500, "Internal error", resolveCorrelationId(request), Map.of()));
        }
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error) {
        String correlationId = UUID.randomUUID().toString();
        if (error instanceof SecurityException) {
            return Promise.of(errorResponse(403, error.getMessage(), correlationId, Map.of()));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(errorResponse(404, error.getMessage(), correlationId, Map.of()));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(errorResponse(400, error.getMessage(), correlationId, Map.of("request", error.getMessage())));
        }
        if (error instanceof IllegalStateException) {
            return Promise.of(errorResponse(409, error.getMessage(), correlationId, Map.of()));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error", correlationId, Map.of()));
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

    private HttpResponse errorResponse(int code, String safeMessage, String correlationId, Map<String, String> details) {
        try {
            StandardErrorEnvelope envelope = StandardErrorEnvelope.withDetails(
                code,
                safeMessage,
                correlationId,
                details
            );
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                .withBody(MAPPER.writeValueAsBytes(envelope))
                .build();
        } catch (Exception e) {
            LOG.error("[DMOS] Error serialization failure", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    private static String resolveCorrelationId(HttpRequest request) {
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        if (header != null && !header.isBlank()) {
            return header;
        }
        return UUID.randomUUID().toString();
    }

    record PublishRequest(String campaignId, String actionType, String title, String description, Map<String, Object> parameters, double confidenceScore, String rationale, Instant expiresAt) {}
    record ApproveRequest(String executedBy) {}
    record RejectRequest(String reason) {}

    record NextBestActionResponse(
        String id,
        String tenantId,
        String workspaceId,
        String campaignId,
        String actionType,
        String title,
        String description,
        Map<String, Object> parameters,
        double confidenceScore,
        String rationale,
        String status,
        String rejectionReason,
        String executedBy,
        Instant createdAt,
        Instant processedAt,
        Instant expiresAt
    ) {
        static NextBestActionResponse from(NextBestActionRecommendation rec) {
            return new NextBestActionResponse(
                rec.getId(),
                rec.getTenantId(),
                rec.getWorkspaceId(),
                rec.getCampaignId(),
                rec.getActionType().name(),
                rec.getTitle(),
                rec.getDescription(),
                rec.getParameters(),
                rec.getConfidenceScore(),
                rec.getRationale(),
                rec.getStatus().name(),
                rec.getRejectionReason(),
                rec.getExecutedBy(),
                rec.getCreatedAt(),
                rec.getProcessedAt(),
                rec.getExpiresAt()
            );
        }
    }

}
