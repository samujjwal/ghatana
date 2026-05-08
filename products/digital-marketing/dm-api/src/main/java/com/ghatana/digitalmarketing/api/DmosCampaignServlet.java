package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.campaign.CampaignComplianceViolationException;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
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
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.kernel.security.TenantSecurityContext;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ActiveJ HTTP servlet exposing the DMOS campaign API.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   GET    /v1/workspaces/:workspaceId/campaigns           — List campaigns (paginated)
 *   POST   /v1/workspaces/:workspaceId/campaigns           — Create a campaign
 *   GET    /v1/workspaces/:workspaceId/campaigns/:id       — Get a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/launch — Launch a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/pause  — Pause a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/complete — Complete a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/archive — Archive a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/rollback — Rollback a campaign
 * </pre>
 *
 * <p>Tenant isolation is enforced through the {@code X-Tenant-ID} header.
 * All mutating operations require an {@code X-Idempotency-Key} header.
 * The {@code X-Correlation-ID} header is propagated; a new ID is generated if absent.</p>
 *
 * <p>Error responses follow the canonical DMOS error envelope:
 * {@code {error: string, message: string, status: number, correlationId: string, details?: object}}</p>
 *
 * @doc.type class
 * @doc.purpose DMOS HTTP API servlet for campaign lifecycle endpoints
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosCampaignServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosCampaignServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final CampaignService campaignService;
    private final Eventloop eventloop;
    private final DmosMetricsCollector metrics;
    private final DmosTelemetry telemetry;
    private final DmosHttpContextFactory httpContextFactory;

    /**
     * Creates the DMOS campaign servlet.
     *
     * @param campaignService   the campaign application service; must not be null
     * @param eventloop          the ActiveJ eventloop; must not be null
     * @param metrics            the metrics collector for request telemetry; must not be null
     * @param telemetry          the OpenTelemetry instrumentation; must not be null
     * @param httpContextFactory the shared HTTP context factory for fail-closed security; must not be null
     */
    public DmosCampaignServlet(CampaignService campaignService, Eventloop eventloop, DmosMetricsCollector metrics, DmosTelemetry telemetry, DmosHttpContextFactory httpContextFactory) {
        this.campaignService   = Objects.requireNonNull(campaignService,   "campaignService must not be null");
        this.eventloop          = Objects.requireNonNull(eventloop,          "eventloop must not be null");
        this.metrics            = Objects.requireNonNull(metrics,            "metrics must not be null");
        this.telemetry          = Objects.requireNonNull(telemetry,          "telemetry must not be null");
        this.httpContextFactory = Objects.requireNonNull(httpContextFactory, "httpContextFactory must not be null");
    }

    public DmosCampaignServlet(CampaignService campaignService, Eventloop eventloop) {
        this(
            campaignService,
            eventloop,
            DmosMetricsCollector.noop(),
            new DmosTelemetry(io.opentelemetry.api.OpenTelemetry.noop()),
            new DmosHttpContextFactory(false, null)
        );
    }

    /**
     * Returns the {@link AsyncServlet} routing for the DMOS campaign API.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
            RoutingServlet.builder(eventloop)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/campaigns",
                    this::handleListCampaigns)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns",
                    this::handleCreateCampaign)
                .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/campaigns/:id",
                    this::handleGetCampaign)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/launch",
                    this::handleLaunchCampaign)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/pause",
                    this::handlePauseCampaign)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/complete",
                    this::handleCompleteCampaign)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/archive",
                    this::handleArchiveCampaign)
                .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/rollback",
                    this::handleRollbackCampaign)
                .build(),
            metrics,
            "campaign"
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleListCampaigns(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            // Parse pagination parameters with defaults
            int limit = parseIntParam(request, "limit", 20, 1, 100);
            int offset = parseIntParam(request, "offset", 0, 0, Integer.MAX_VALUE);

            return campaignService.listCampaigns(ctx, limit, offset)
                .map(campaigns -> jsonResponse(200, new CampaignListResponse(
                    campaigns.stream().map(CampaignResponse::from).toList(),
                    campaigns.size(),
                    offset
                )))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to list campaigns", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to list campaigns", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handleCreateCampaign(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);
                CreateCampaignRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CreateCampaignRequest.class);

                // P1-026: Create span for campaign creation
                io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns", ctx).startSpan();
                try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                    return campaignService.createCampaign(ctx,
                        new CampaignService.CreateCampaignCommand(body.name(), body.type()))
                        .map(campaign -> {
                            telemetry.setCampaignId(campaign.getId());
                            span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                            span.end();
                            return jsonResponse(201, CampaignResponse.from(campaign));
                        })
                        .then(r -> Promise.of(r), e -> {
                            telemetry.recordException(span, e);
                            span.end();
                            if (e instanceof SecurityException) {
                                return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                            }
                            LOG.error("[DMOS] Failed to create campaign", e);
                            return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                        });
                }
            } catch (IllegalArgumentException e) {
                String correlationId = DmCorrelationId.generate().getValue();
                return Promise.of(errorResponse(400, e.getMessage(), correlationId));
            } catch (Exception e) {
                String correlationId = DmCorrelationId.generate().getValue();
                LOG.error("[DMOS] Failed to create campaign", e);
                return Promise.of(errorResponse(500, "Internal error", correlationId));
            }
        });
    }

    private Promise<HttpResponse> handleGetCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, false);

            return campaignService.getCampaign(ctx, campaignId)
                .map(campaign -> jsonResponse(200, CampaignResponse.from(campaign)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof java.util.NoSuchElementException) {
                        return Promise.of(errorResponse(404, "Campaign not found: " + campaignId, ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to get campaign", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to get campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handleLaunchCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            // P1-026: Create span for campaign launch
            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns/:id/launch", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                telemetry.setCampaignId(campaignId);
                return campaignService.launchCampaign(ctx, campaignId)
                    .map(campaign -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, CampaignResponse.from(campaign));
                    })
                    .then(r -> Promise.of(r), e -> {
                        telemetry.recordException(span, e);
                        span.end();
                        if (e instanceof SecurityException) {
                            return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                        }
                        if (e instanceof java.util.NoSuchElementException) {
                            return Promise.of(errorResponse(404, e.getMessage(), ctx.getCorrelationId().getValue()));
                        }
                        if (e instanceof IllegalStateException) {
                            return Promise.of(errorResponse(409, e.getMessage(), ctx.getCorrelationId().getValue()));
                        }
                        if (e instanceof CampaignComplianceViolationException) {
                            return Promise.of(errorResponse(422, e.getMessage(), ctx.getCorrelationId().getValue()));
                        }
                        LOG.error("[DMOS] Failed to launch campaign", e);
                        return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                    });
            }
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to launch campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handlePauseCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            // P1-001: Use shared fail-closed HTTP context factory
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            // P1-026: Create span for campaign pause
            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns/:id/pause", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                telemetry.setCampaignId(campaignId);
                return campaignService.pauseCampaign(ctx, campaignId)
                    .map(campaign -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, CampaignResponse.from(campaign));
                    })
                .then(r -> Promise.of(r), e -> {
                    telemetry.recordException(span, e);
                    span.end();
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof java.util.NoSuchElementException) {
                        return Promise.of(errorResponse(404, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(errorResponse(409, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to pause campaign", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
            }
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to pause campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handleCompleteCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns/:id/complete", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                telemetry.setCampaignId(campaignId);
                return campaignService.completeCampaign(ctx, campaignId)
                    .map(campaign -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, CampaignResponse.from(campaign));
                    })
                .then(r -> Promise.of(r), e -> {
                    telemetry.recordException(span, e);
                    span.end();
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof java.util.NoSuchElementException) {
                        return Promise.of(errorResponse(404, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(errorResponse(409, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to complete campaign", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
            }
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to complete campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handleArchiveCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns/:id/archive", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                telemetry.setCampaignId(campaignId);
                return campaignService.archiveCampaign(ctx, campaignId)
                    .map(campaign -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, CampaignResponse.from(campaign));
                    })
                .then(r -> Promise.of(r), e -> {
                    telemetry.recordException(span, e);
                    span.end();
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof java.util.NoSuchElementException) {
                        return Promise.of(errorResponse(404, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(errorResponse(409, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to archive campaign", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
            }
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to archive campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    private Promise<HttpResponse> handleRollbackCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = httpContextFactory.buildContext(request, workspaceId, true);

            io.opentelemetry.api.trace.Span span = telemetry.httpSpanBuilder("POST /campaigns/:id/rollback", ctx).startSpan();
            try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
                telemetry.setCampaignId(campaignId);
                return campaignService.rollbackCampaign(ctx, campaignId)
                    .map(campaign -> {
                        span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                        span.end();
                        return jsonResponse(200, CampaignResponse.from(campaign));
                    })
                .then(r -> Promise.of(r), e -> {
                    telemetry.recordException(span, e);
                    span.end();
                    if (e instanceof SecurityException) {
                        return Promise.of(errorResponse(403, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof java.util.NoSuchElementException) {
                        return Promise.of(errorResponse(404, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    if (e instanceof IllegalStateException) {
                        return Promise.of(errorResponse(409, e.getMessage(), ctx.getCorrelationId().getValue()));
                    }
                    LOG.error("[DMOS] Failed to rollback campaign", e);
                    return Promise.of(errorResponse(500, "Internal error", ctx.getCorrelationId().getValue()));
                });
            }
        } catch (IllegalArgumentException e) {
            String correlationId = DmCorrelationId.generate().getValue();
            return Promise.of(errorResponse(400, e.getMessage(), correlationId));
        } catch (Exception e) {
            String correlationId = DmCorrelationId.generate().getValue();
            LOG.error("[DMOS] Failed to rollback campaign", e);
            return Promise.of(errorResponse(500, "Internal error", correlationId));
        }
    }

    // -------------------------------------------------------------------------
    // Context building
    // -------------------------------------------------------------------------

    /**
     * Builds an operation context from HTTP headers.
     *
     * @param request     the incoming request
     * @param workspaceId the path-parameter workspace ID
     * @param requireIdk  whether an idempotency key header is required
     */
    // P1-001: Local buildContext method removed - using shared DmosHttpContextFactory

    // P1-001: Local helper methods removed - using shared DmosHttpContextFactory

    private static int parseIntParam(HttpRequest request, String name, int defaultValue, int min, int max) {
        String value = request.getQueryParameter(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.min(Math.max(parsed, min), max);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------
    // Response helpers
    // -------------------------------------------------------------------------

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

    /**
     * Creates a canonical DMOS error response with correlation ID.
     *
     * <p>Error envelope format: {@code {error, message, status, correlationId, details?}}</p>
     */
    private HttpResponse errorResponse(int code, String message, String correlationId) {
        String errorCode = mapStatusToErrorCode(code);
        return jsonResponse(code, new ErrorBody(errorCode, message, code, correlationId, null));
    }

    /**
     * Legacy error response without correlation ID - updates handlers should migrate to the
     * correlation-aware overload for production observability.
     */
    private HttpResponse errorResponse(int code, String message) {
        return errorResponse(code, message, DmCorrelationId.generate().getValue());
    }

    private static String mapStatusToErrorCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "UNPROCESSABLE_ENTITY";
            case 429 -> "RATE_LIMITED";
            case 500 -> "INTERNAL_ERROR";
            default -> "ERROR";
        };
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    /** Request body for campaign creation. */
    record CreateCampaignRequest(String name, CampaignType type) { }

    /** API response representation of a campaign. */
    record CampaignResponse(
        String id,
        String workspaceId,
        String name,
        String status,
        String type,
        String createdBy,
        String createdAt,
        String updatedAt
    ) {
        static CampaignResponse from(Campaign c) {
            return new CampaignResponse(
                c.getId(),
                c.getWorkspaceId().getValue(),
                c.getName(),
                c.getStatus().name(),
                c.getType().name(),
                c.getCreatedBy(),
                c.getCreatedAt().toString(),
                c.getUpdatedAt().toString()
            );
        }
    }

    /** API response for paginated campaign list. */
    record CampaignListResponse(
        List<CampaignResponse> items,
        int count,
        int offset
    ) { }

    /**
     * Canonical DMOS error response body.
     *
     * <p>Format: {@code {error, message, status, correlationId, details?}}</p>
     */
    record ErrorBody(
        String error,
        String message,
        int status,
        String correlationId,
        Object details
    ) { }
}
