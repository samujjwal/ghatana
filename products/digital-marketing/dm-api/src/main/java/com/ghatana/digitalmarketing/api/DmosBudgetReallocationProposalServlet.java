package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.optimization.BudgetReallocationProposalService;
import com.ghatana.digitalmarketing.application.optimization.BudgetReallocationStatus;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.api.observability.DmosTelemetry;
import com.ghatana.digitalmarketing.api.security.DmosHttpContextFactory;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationProposal;
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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * HTTP servlet for budget reallocation proposal management (P3-004).
 *
 * @doc.type class
 * @doc.purpose DMOS budget reallocation proposal API servlet (P3-004)
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosBudgetReallocationProposalServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosBudgetReallocationProposalServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final BudgetReallocationProposalService service;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    public DmosBudgetReallocationProposalServlet(BudgetReallocationProposalService service, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosBudgetReallocationProposalServlet(BudgetReallocationProposalService service, Eventloop eventloop) {
        this(service, eventloop, DmosMetricsCollector.noop(), new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()), new DmosHttpContextFactory(false, null));
    }

    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-reallocation-proposals", this::handlePublish)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-reallocation-proposals/:proposalId/approve", this::handleApprove)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-reallocation-proposals/:proposalId/reject", this::handleReject)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/budget-reallocation-proposals/:proposalId/execute", this::handleExecute)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/budget-reallocation-proposals", this::handleListByWorkspace)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/budget-reallocation-proposals/:proposalId", this::handleGetById)
                .build(),
            metrics,
            "budget-reallocation"
        );
    }

    private Promise<HttpResponse> handlePublish(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                PublishRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), PublishRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-reallocation-proposals", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    BudgetReallocationProposalService.PublishProposalCommand command =
                        new BudgetReallocationProposalService.PublishProposalCommand(
                            body.budgetRecommendationId(),
                            body.title(),
                            body.description(),
                            body.adjustments(),
                            body.totalReallocatedAmount(),
                            body.rationale(),
                            body.expiresAt()
                        );
                    return service.publish(ctx, command)
                        .map(proposal -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, BudgetReallocationProposalResponse.from(proposal));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("publish proposal", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to publish budget reallocation proposal", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleApprove(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String proposalId = request.getPathParameter("proposalId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                ApproveRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), ApproveRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-reallocation-proposals/:proposalId/approve", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.approve(ctx, proposalId, body.approvedBy())
                        .map(proposal -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, BudgetReallocationProposalResponse.from(proposal));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("approve proposal", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve budget reallocation proposal", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleReject(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String proposalId = request.getPathParameter("proposalId");
                DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                RejectRequest body = MAPPER.readValue(request.getBody().getString(StandardCharsets.UTF_8), RejectRequest.class);

                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-reallocation-proposals/:proposalId/reject", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return service.reject(ctx, proposalId, body.reason())
                        .map(proposal -> {
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(200, BudgetReallocationProposalResponse.from(proposal));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            return mapServiceError("reject proposal", e);
                        });
                }
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to reject budget reallocation proposal", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleExecute(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String proposalId = request.getPathParameter("proposalId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /budget-reallocation-proposals/:proposalId/execute", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                return service.execute(ctx, proposalId)
                    .map(proposal -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, BudgetReallocationProposalResponse.from(proposal));
                    })
                    .then(r -> Promise.of(r), e -> {
                        telemetry.recordException(span, e);
                        span.end();
                        return mapServiceError("execute proposal", e);
                    });
            }
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to execute budget reallocation proposal", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleListByWorkspace(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.listByWorkspace(ctx)
                .map(proposals -> jsonResponse(200, proposals.stream().map(BudgetReallocationProposalResponse::from).collect(Collectors.toList())))
                .then(r -> Promise.of(r), e -> mapServiceError("list proposals", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to list budget reallocation proposals", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleGetById(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String proposalId = request.getPathParameter("proposalId");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);
            return service.findById(ctx, proposalId)
                .map(opt -> opt.map(proposal -> jsonResponse(200, BudgetReallocationProposalResponse.from(proposal))).orElse(errorResponse(404, "Proposal not found")))
                .then(r -> Promise.of(r), e -> mapServiceError("get proposal", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get budget reallocation proposal", e);
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

    record PublishRequest(String budgetRecommendationId, String title, String description, List<BudgetReallocationProposal.BudgetAdjustment> adjustments, double totalReallocatedAmount, String rationale, Instant expiresAt) {}
    record ApproveRequest(String approvedBy) {}
    record RejectRequest(String reason) {}

    record BudgetReallocationProposalResponse(
        String id,
        String tenantId,
        String workspaceId,
        String budgetRecommendationId,
        String title,
        String description,
        List<BudgetAdjustmentDto> adjustments,
        double totalReallocatedAmount,
        String rationale,
        String status,
        String rejectionReason,
        String approvedBy,
        Instant createdAt,
        Instant approvedAt,
        Instant executedAt,
        Instant expiresAt
    ) {
        static BudgetReallocationProposalResponse from(BudgetReallocationProposal proposal) {
            return new BudgetReallocationProposalResponse(
                proposal.getId(),
                proposal.getTenantId(),
                proposal.getWorkspaceId(),
                proposal.getBudgetRecommendationId(),
                proposal.getTitle(),
                proposal.getDescription(),
                proposal.getAdjustments().stream().map(BudgetAdjustmentDto::from).collect(Collectors.toList()),
                proposal.getTotalReallocatedAmount(),
                proposal.getRationale(),
                proposal.getStatus().name(),
                proposal.getRejectionReason(),
                proposal.getApprovedBy(),
                proposal.getCreatedAt(),
                proposal.getApprovedAt(),
                proposal.getExecutedAt(),
                proposal.getExpiresAt()
            );
        }
    }

    record BudgetAdjustmentDto(String campaignId, String channelType, double currentAmount, double proposedAmount, double adjustmentAmount, String reason) {
        static BudgetAdjustmentDto from(BudgetReallocationProposal.BudgetAdjustment adj) {
            return new BudgetAdjustmentDto(adj.getCampaignId(), adj.getChannelType(), adj.getCurrentAmount(), adj.getProposedAmount(), adj.getAdjustmentAmount(), adj.getReason());
        }
    }

    record ErrorBody(int status, String message) {}
}
