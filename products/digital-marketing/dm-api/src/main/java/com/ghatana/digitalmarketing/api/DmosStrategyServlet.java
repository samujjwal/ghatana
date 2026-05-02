package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.strategy.StrategyGeneratorService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
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

    public DmosStrategyServlet(StrategyGeneratorService strategyService, Eventloop eventloop) {
        this.strategyService = Objects.requireNonNull(strategyService, "strategyService must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for strategy endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy", this::handleGenerateStrategy)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy/:strategyId/submit", this::handleSubmitForApproval)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/strategy/:strategyId/approve", this::handleApproveStrategy)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/strategy", this::handleGetLatestStrategy)
            .build();
    }

    private Promise<HttpResponse> handleGenerateStrategy(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                GenerateStrategyRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateStrategyRequest.class
                );

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
                    .map(strategy -> jsonResponse(200, StrategyResponse.from(strategy)))
                    .then(r -> Promise.of(r), e -> mapServiceError("generate strategy", e));
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
                DmOperationContext ctx = buildContext(request, workspaceId, true);

                return strategyService.submitForApproval(ctx, strategyId)
                    .map(strategy -> jsonResponse(200, StrategyResponse.from(strategy)))
                    .then(r -> Promise.of(r), e -> mapServiceError("submit strategy for approval", e));
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
                DmOperationContext ctx = buildContext(request, workspaceId, true);

                return strategyService.approveStrategy(ctx, strategyId)
                    .map(strategy -> jsonResponse(200, StrategyResponse.from(strategy)))
                    .then(r -> Promise.of(r), e -> mapServiceError("approve strategy", e));
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
            DmOperationContext ctx = buildContext(request, workspaceId, false);

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

    private DmOperationContext buildContext(HttpRequest request, String workspaceId, boolean requireIdempotencyKey) {
        String tenantId = getRequiredHeader(request, "X-Tenant-ID");
        String principal = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID", DmCorrelationId.generate().getValue());
        String idempotencyKeyValue = getHeader(request, "X-Idempotency-Key", null);
        String sessionId = getHeader(request, "X-Session-ID", null);
        Set<String> roles = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> permissions = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        if (requireIdempotencyKey && (idempotencyKeyValue == null || idempotencyKeyValue.isBlank())) {
            throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
        }

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);
        DmIdempotencyKey idempotencyKey =
            (idempotencyKeyValue != null && !idempotencyKeyValue.isBlank())
                ? DmIdempotencyKey.of(idempotencyKeyValue)
                : null;

        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlationId))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext,
            sessionId,
            roles,
            permissions,
            null
        );

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext,
            workspace,
            DmCorrelationId.of(correlationId),
            idempotencyKey
        );
    }

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getRequiredHeader(HttpRequest request, String name) {
        String value = request.getHeader(HttpHeaders.of(name));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required header missing: " + name);
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return (value != null && !value.isBlank()) ? value : defaultValue;
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
        int budgetCap,
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
