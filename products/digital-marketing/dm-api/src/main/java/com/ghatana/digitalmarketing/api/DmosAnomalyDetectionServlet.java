package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.optimization.AnomalyDetectionService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyDetectionResult;
import com.ghatana.digitalmarketing.domain.optimization.AnomalySeverity;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyStatus;
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
 * HTTP servlet for anomaly detection result management (P3-004).
 *
 * @doc.type class
 * @doc.purpose DMOS anomaly detection API servlet (P3-004)
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosAnomalyDetectionServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAnomalyDetectionServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final AnomalyDetectionService service;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosAnomalyDetectionServlet(AnomalyDetectionService service, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosAnomalyDetectionServlet(AnomalyDetectionService service, Eventloop eventloop) {
        this(service, eventloop, DmosMetricsCollector.noop(), new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()), new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/anomalies", this::handlePublish)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/anomalies/:anomalyId/acknowledge", this::handleAcknowledge)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/anomalies/:anomalyId/resolve", this::handleResolve)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/anomalies/:anomalyId/dismiss", this::handleDismiss)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/anomalies", this::handleListByWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/anomalies/:anomalyId", this::handleGetById)
                .build(),
            metrics,
            "anomaly-detection"
        );
    }

    private Promise<HttpResponse> handlePublish(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                PublishRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), PublishRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /anomalies", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    AnomalyDetectionService.PublishAnomalyCommand command =
                        new AnomalyDetectionService.PublishAnomalyCommand(
                            body.campaignId(),
                            AnomalySeverity.valueOf(body.severity()),
                            body.metricName(),
                            body.anomalyType(),
                            body.expectedValue(),
                            body.actualValue(),
                            body.deviationPercentage(),
                            body.description(),
                            body.context(),
                            body.rationale()
                        );
                    return service.publish(ctx, command)
                        .map(anomaly -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, AnomalyResponse.from(anomaly));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("publish anomaly", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to publish anomaly", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleAcknowledge(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String anomalyId = request.getPathParameter("anomalyId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                AcknowledgeRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), AcknowledgeRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /anomalies/:anomalyId/acknowledge", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.acknowledge(ctx, anomalyId, body.acknowledgedBy())
                        .map(anomaly -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, AnomalyResponse.from(anomaly));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("acknowledge anomaly", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to acknowledge anomaly", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleResolve(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String anomalyId = request.getPathParameter("anomalyId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                ResolveRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), ResolveRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /anomalies/:anomalyId/resolve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.resolve(ctx, anomalyId, body.mitigationAction())
                        .map(anomaly -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, AnomalyResponse.from(anomaly));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("resolve anomaly", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to resolve anomaly", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleDismiss(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String anomalyId = request.getPathParameter("anomalyId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                DismissRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), DismissRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /anomalies/:anomalyId/dismiss", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.dismiss(ctx, anomalyId, body.reason())
                        .map(anomaly -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, AnomalyResponse.from(anomaly));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("dismiss anomaly", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to dismiss anomaly", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleListByWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.listByWorkspace(ctx)
                .map(anomalies -> jsonResponse(200, anomalies.stream().map(AnomalyResponse::from).collect(Collectors.toList())))
                .then(r -> Promise.of(r), e -> mapServiceError("list anomalies", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to list anomalies", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String anomalyId = request.getPathParameter("anomalyId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.findById(ctx, anomalyId)
                .map(opt -> opt.map(anomaly -> jsonResponse(200, AnomalyResponse.from(anomaly))).orElse(errorResponse(404, "Anomaly not found")))
                .then(r -> Promise.of(r), e -> mapServiceError("get anomaly", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get anomaly", e);
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
        return jsonResponse(code, new ErrorBody(code, message));
    }

    record PublishRequest(String campaignId, String severity, String metricName, String anomalyType, double expectedValue, double actualValue, double deviationPercentage, String description, Map<String, Object> context, String rationale) {}
    record AcknowledgeRequest(String acknowledgedBy) {}
    record ResolveRequest(String mitigationAction) {}
    record DismissRequest(String reason) {}

    record AnomalyResponse(
        String id,
        String tenantId,
        String workspaceId,
        String campaignId,
        String severity,
        String metricName,
        String anomalyType,
        double expectedValue,
        double actualValue,
        double deviationPercentage,
        String description,
        Map<String, Object> context,
        String rationale,
        String status,
        String acknowledgedBy,
        String mitigationAction,
        Instant detectedAt,
        Instant acknowledgedAt,
        Instant resolvedAt
    ) {
        static AnomalyResponse from(AnomalyDetectionResult anomaly) {
            return new AnomalyResponse(
                anomaly.getId(),
                anomaly.getTenantId(),
                anomaly.getWorkspaceId(),
                anomaly.getCampaignId(),
                anomaly.getSeverity().name(),
                anomaly.getMetricName(),
                anomaly.getAnomalyType(),
                anomaly.getExpectedValue(),
                anomaly.getActualValue(),
                anomaly.getDeviationPercentage(),
                anomaly.getDescription(),
                anomaly.getContext(),
                anomaly.getRationale(),
                anomaly.getStatus().name(),
                anomaly.getAcknowledgedBy(),
                anomaly.getMitigationAction(),
                anomaly.getDetectedAt(),
                anomaly.getAcknowledgedAt(),
                anomaly.getResolvedAt()
            );
        }
    }

    record ErrorBody(int status, String message) {}
}
