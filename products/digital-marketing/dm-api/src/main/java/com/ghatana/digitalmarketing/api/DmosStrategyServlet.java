package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
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
import com.ghatana.digitalmarketing.domain.strategy.CampaignPlan;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyGoal;
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
 * HTTP servlet for 30-day marketing strategy generation and management.
 *
 * @doc.type class
 * @doc.purpose DMOS strategy API servlet for F1-013 strategy generation, submission, approval, and retrieval
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosStrategyServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosStrategyServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final StrategyGeneratorService strategyService;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosStrategyServlet(StrategyGeneratorService strategyService, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.strategyService   = Objects.requireNonNull(strategyService,   "strategyService must not be null");
        this.eventloop          = Objects.requireNonNull(eventloop,          "eventloop must not be null");
        this.metrics            = Objects.requireNonNull(metrics,            "metrics must not be null");
        this.telemetry          = Objects.requireNonNull(telemetry,          "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosStrategyServlet(StrategyGeneratorService strategyService, Eventloop eventloop) {
        this(
            strategyService,
            eventloop,
            DmosMetricsCollector.noop(),
            new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()),
            new DmosHttpContextFactory(false, null)
        );
    }

    /**
     * Returns the routing servlet for strategy endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy", this::handleGenerateStrategy)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy/:strategyId/submit", this::handleSubmitForApproval)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy/:strategyId/approve", this::handleApproveStrategy)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/strategy", this::handleGetLatestStrategy)
                .build(),
            metrics,
            "strategy"
        );
    }

    private Promise<HttpResponse> handleGenerateStrategy(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                GenerateStrategyRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateStrategyRequest.class
                );

                // P1-026: Create span for strategy generation
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /strategy", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    StrategyGeneratorService.GenerateStrategyCommand command =
                        new StrategyGeneratorService.GenerateStrategyCommand(
                            body.intakeCompletionPct(),
                            body.serviceArea(),
                            body.monthlyBudget(),
                            body.auditFindingCount(),
                            body.trackingGapsDetected(),
                            body.keywordOpportunityCount(),
                            body.topCompetitorCount(),
                            body.primaryOffer()
                        );

                    return strategyService.generateStrategy(ctx, command)
                        .map(strategy -> {
                            telemetry.setStrategyId(strategy.getStrategyId());
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, StrategyResponse.from(strategy));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("generate strategy", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to generate strategy", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleSubmitForApproval(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String strategyId = request.getPathParameter("strategyId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

                // P1-026: Create span for strategy submission
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /strategy/:strategyId/submit", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    telemetry.setStrategyId(strategyId);
                    return strategyService.submitForApproval(ctx, strategyId)
                        .map(strategy -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, StrategyResponse.from(strategy));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("submit strategy for approval", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to submit strategy for approval", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleApproveStrategy(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String strategyId = request.getPathParameter("strategyId");
                // P1-001: Use shared fail-closed HTTP context factory
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

                // P1-026: Create span for strategy approval
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /strategy/:strategyId/approve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    telemetry.setStrategyId(strategyId);
                    return strategyService.approveStrategy(ctx, strategyId)
                        .map(strategy -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, StrategyResponse.from(strategy));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("approve strategy", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve strategy", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestStrategy(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return strategyService.getLatestStrategy(ctx)
                .map(strategy -> jsonResponse(200, StrategyResponse.from(strategy)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest strategy", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get latest strategy", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

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

    // P1-001: Local buildContext method removed - using shared DmosHttpContextFactory

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

    record GenerateStrategyRequest(
        int intakeCompletionPct,
        String serviceArea,
        int monthlyBudget,
        int auditFindingCount,
        boolean trackingGapsDetected,
        int keywordOpportunityCount,
        int topCompetitorCount,
        String primaryOffer
    ) {
    }

    record CampaignPlanDto(
        String channelType,
        String objective,
        int estimatedBudget,
        List<String> keyMessages,
        List<String> targetKeywords
    ) {
        static CampaignPlanDto from(CampaignPlan p) {
            return new CampaignPlanDto(
                p.channelType().name(),
                p.objective(),
                p.estimatedBudget(),
                p.keyMessages(),
                p.targetKeywords()
            );
        }
    }

    record StrategyGoalDto(String goalType, String description, String targetMetric, String measurementMethod) {
        static StrategyGoalDto from(StrategyGoal g) {
            return new StrategyGoalDto(g.goalType(), g.description(), g.targetMetric(), g.measurementMethod());
        }
    }

    record StrategyResponse(
        String strategyId,
        String workspaceId,
        String status,
        List<StrategyGoalDto> goals,
        List<CampaignPlanDto> channelPlans,
        double budgetCap,
        String rationale,
        String assumptions,
        String measurementPlan,
        String contentPlan,
        String modelVersion,
        Instant generatedAt,
        String generatedBy,
        Instant approvedAt,
        String approvedBy
    ) {
        static StrategyResponse from(MarketingStrategy s) {
            return new StrategyResponse(
                s.getStrategyId(),
                s.getWorkspaceId().getValue(),
                s.getStatus().name(),
                s.getGoals().stream().map(StrategyGoalDto::from).collect(Collectors.toList()),
                s.getChannelPlans().stream().map(CampaignPlanDto::from).collect(Collectors.toList()),
                s.getBudgetCap(),
                s.getRationale(),
                s.getAssumptions(),
                s.getMeasurementPlan(),
                s.getContentPlan(),
                s.getModelVersion(),
                s.getGeneratedAt(),
                s.getGeneratedBy(),
                s.getApprovedAt(),
                s.getApprovedBy()
            );
        }
    }

    record ErrorBody(int status, String message) {
    }
}
