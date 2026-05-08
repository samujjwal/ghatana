package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.budget.BudgetRecommendationService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.budget.BudgetChannelAllocation;
import com.ghatana.digitalmarketing.domain.budget.BudgetRecommendation;
import com.ghatana.kernel.security.TenantSecurityContext;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HTTP servlet for budget recommendation generation, approval, and retrieval (F1-014).
 *
 * <p>Exposes four routes:
 * <ul>
 *   <li>POST  /v1/workspaces/:workspaceId/budget-recommendation</li>
 *   <li>POST  /v1/workspaces/:workspaceId/budget-recommendation/:recId/submit</li>
 *   <li>POST  /v1/workspaces/:workspaceId/budget-recommendation/:recId/approve</li>
 *   <li>GET   /v1/workspaces/:workspaceId/budget-recommendation</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS budget recommendation API servlet for F1-014
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosBudgetRecommendationServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosBudgetRecommendationServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final BudgetRecommendationService budgetService;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosBudgetRecommendationServlet(BudgetRecommendationService budgetService, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.budgetService   = Objects.requireNonNull(budgetService,   "budgetService must not be null");
        this.eventloop          = Objects.requireNonNull(eventloop,          "eventloop must not be null");
        this.metrics            = Objects.requireNonNull(metrics,            "metrics must not be null");
        this.telemetry          = Objects.requireNonNull(telemetry,          "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosBudgetRecommendationServlet(BudgetRecommendationService budgetService, Eventloop eventloop) {
        this(
            budgetService,
            eventloop,
            DmosMetricsCollector.noop(),
            new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()),
            new DmosHttpContextFactory(false, null)
        );
    }

    /**
     * Returns the routing servlet for budget recommendation endpoints.
     *
     * @return configured async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-recommendation",
                    this::handleRecommendBudget)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-recommendation/:recId/submit",
                    this::handleSubmitForApproval)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-recommendation/:recId/approve",
                    this::handleApproveRecommendation)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/budget-recommendation",
                    this::handleGetLatestRecommendation)
                .build(),
            metrics,
            "budget"
        );
    }

    // ---- handlers ----

    private Promise<HttpResponse> handleRecommendBudget(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                GenerateBudgetRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateBudgetRequest.class
                );

                // P1-026: Create span for budget recommendation
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-recommendation", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    BudgetRecommendationService.GenerateBudgetCommand command =
                        new BudgetRecommendationService.GenerateBudgetCommand(
                            body.strategyId(),
                            body.totalMonthlyCap(),
                            body.changeThreshold()
                        );
                    return budgetService.recommendBudget(ctx, command)
                        .map(rec -> {
                            telemetry.setBudgetId(rec.getRecommendationId());
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, BudgetRecommendationResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("recommend budget", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to recommend budget", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleSubmitForApproval(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String recId = request.getPathParameter("recId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

                // P1-026: Create span for budget submission
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-recommendation/:recId/submit", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    telemetry.setBudgetId(recId);
                    return budgetService.submitForApproval(ctx, recId)
                        .map(rec -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, BudgetRecommendationResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("submit for approval", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to submit budget recommendation for approval", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleApproveRecommendation(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String recId = request.getPathParameter("recId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

                // P1-026: Create span for budget approval
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-recommendation/:recId/approve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    telemetry.setBudgetId(recId);
                    return budgetService.approveRecommendation(ctx, recId)
                        .map(rec -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, BudgetRecommendationResponse.from(rec));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("approve recommendation", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve budget recommendation", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestRecommendation(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return budgetService.getLatestRecommendation(ctx)
                .map(rec -> jsonResponse(200, BudgetRecommendationResponse.from(rec)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest recommendation", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get latest budget recommendation", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    // ---- error mapping ----

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error) {
        if (error instanceof SecurityException) {
            return Promise.of(errorResponse(403, "Access denied"));
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
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(errorResponse(423, error.getMessage()));
        }
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error"));
    }

    // ---- context builder ----

    // P1-001: Local buildContext method removed - using shared DmosHttpContextFactory

    // ---- utilities ----

    // P1-001: Local helper methods removed - using shared DmosHttpContextFactory

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

    // ---- request / response records ----

    record GenerateBudgetRequest(String strategyId, double totalMonthlyCap, double changeThreshold) {
    }

    record ChannelAllocationDto(String channelType, double recommendedAmount, double dailyCap, String rationale) {
        static ChannelAllocationDto from(BudgetChannelAllocation a) {
            return new ChannelAllocationDto(a.channelType(), a.recommendedAmount(), a.dailyCap(), a.rationale());
        }
    }

    record BudgetRecommendationResponse(
        String recommendationId,
        String workspaceId,
        String strategyId,
        String status,
        double totalMonthlyCap,
        double changeThresholdPct,
        List<ChannelAllocationDto> channelAllocations,
        String rationale,
        String assumptions,
        String modelVersion,
        Instant generatedAt,
        String generatedBy,
        Instant approvedAt,
        String approvedBy
    ) {
        static BudgetRecommendationResponse from(BudgetRecommendation rec) {
            return new BudgetRecommendationResponse(
                rec.getRecommendationId(),
                rec.getWorkspaceId().getValue(),
                rec.getStrategyId(),
                rec.getStatus().name(),
                rec.getTotalMonthlyCap(),
                rec.getChangeThresholdPct(),
                rec.getChannelAllocations().stream().map(ChannelAllocationDto::from).collect(Collectors.toList()),
                rec.getRationale(),
                rec.getAssumptions(),
                rec.getModelVersion(),
                rec.getGeneratedAt(),
                rec.getGeneratedBy(),
                rec.getApprovedAt(),
                rec.getApprovedBy()
            );
        }
    }

    record ErrorBody(int status, String message) {
    }
}
