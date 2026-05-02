package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.campaign.CampaignComplianceViolationException;
import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
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
import java.util.Objects;

/**
 * ActiveJ HTTP servlet exposing the DMOS campaign API.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /v1/workspaces/:workspaceId/campaigns           — Create a campaign
 *   GET    /v1/workspaces/:workspaceId/campaigns/:id       — Get a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/launch — Launch a campaign
 *   POST   /v1/workspaces/:workspaceId/campaigns/:id/pause  — Pause a campaign
 * </pre>
 *
 * <p>Tenant isolation is enforced through the {@code X-Tenant-ID} header.
 * All mutating operations require an {@code X-Idempotency-Key} header.
 * The {@code X-Correlation-ID} header is propagated; a new ID is generated if absent.</p>
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

    /**
     * Creates the DMOS campaign servlet.
     *
     * @param campaignService the campaign application service; must not be null
     * @param eventloop       the ActiveJ eventloop; must not be null
     */
    public DmosCampaignServlet(CampaignService campaignService, Eventloop eventloop) {
        this.campaignService = Objects.requireNonNull(campaignService, "campaignService must not be null");
        this.eventloop       = Objects.requireNonNull(eventloop,       "eventloop must not be null");
    }

    /**
     * Returns the {@link AsyncServlet} routing for the DMOS campaign API.
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns",
                this::handleCreateCampaign)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/campaigns/:id",
                this::handleGetCampaign)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/launch",
                this::handleLaunchCampaign)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/campaigns/:id/pause",
                this::handlePauseCampaign)
            .build();
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleCreateCampaign(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                CreateCampaignRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CreateCampaignRequest.class);

                return campaignService.createCampaign(ctx,
                    new CampaignService.CreateCampaignCommand(body.name(), body.type()))
                    .map(campaign -> jsonResponse(201, CampaignResponse.from(campaign)))
                    .then(r -> Promise.of(r), e -> {
                        if (e instanceof SecurityException) return Promise.of(errorResponse(403, e.getMessage()));
                        LOG.error("[DMOS] Failed to create campaign", e);
                        return Promise.of(errorResponse(500, "Internal error"));
                    });
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (SecurityException e) {
                return Promise.of(errorResponse(403, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to create campaign", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return campaignService.getCampaign(ctx, campaignId)
                .map(campaign -> jsonResponse(200, CampaignResponse.from(campaign)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException)             return Promise.of(errorResponse(403, e.getMessage()));
                    if (e instanceof java.util.NoSuchElementException) return Promise.of(errorResponse(404, "Campaign not found: " + campaignId));
                    LOG.error("[DMOS] Failed to get campaign", e);
                    return Promise.of(errorResponse(500, "Internal error"));
                });
        } catch (SecurityException e) {
            return Promise.of(errorResponse(403, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get campaign", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleLaunchCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = buildContext(request, workspaceId, true);

            return campaignService.launchCampaign(ctx, campaignId)
                .map(campaign -> jsonResponse(200, CampaignResponse.from(campaign)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException)             return Promise.of(errorResponse(403, e.getMessage()));
                    if (e instanceof java.util.NoSuchElementException) return Promise.of(errorResponse(404, e.getMessage()));
                    if (e instanceof IllegalStateException)         return Promise.of(errorResponse(409, e.getMessage()));
                    if (e instanceof CampaignComplianceViolationException) return Promise.of(errorResponse(422, e.getMessage()));
                    LOG.error("[DMOS] Failed to launch campaign", e);
                    return Promise.of(errorResponse(500, "Internal error"));
                });
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to launch campaign", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handlePauseCampaign(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String campaignId  = request.getPathParameter("id");
            DmOperationContext ctx = buildContext(request, workspaceId, true);

            return campaignService.pauseCampaign(ctx, campaignId)
                .map(campaign -> jsonResponse(200, CampaignResponse.from(campaign)))
                .then(r -> Promise.of(r), e -> {
                    if (e instanceof SecurityException)             return Promise.of(errorResponse(403, e.getMessage()));
                    if (e instanceof java.util.NoSuchElementException) return Promise.of(errorResponse(404, e.getMessage()));
                    if (e instanceof IllegalStateException)         return Promise.of(errorResponse(409, e.getMessage()));
                    LOG.error("[DMOS] Failed to pause campaign", e);
                    return Promise.of(errorResponse(500, "Internal error"));
                });
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to pause campaign", e);
            return Promise.of(errorResponse(500, "Internal error"));
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
    private DmOperationContext buildContext(HttpRequest request, String workspaceId, boolean requireIdk) {
        String tenantId   = getRequiredHeader(request, "X-Tenant-ID");
        String principal  = getHeader(request, "X-Principal-ID", "anonymous");
        String correlId   = getHeader(request, "X-Correlation-ID", DmCorrelationId.generate().getValue());
        String idkValue   = getHeader(request, "X-Idempotency-Key", null);

        if (requireIdk && (idkValue == null || idkValue.isBlank())) {
            throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
        }

        DmOperationContext.Builder builder = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlId));

        if (idkValue != null && !idkValue.isBlank()) {
            builder.idempotencyKey(DmIdempotencyKey.of(idkValue));
        }

        return builder.build();
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

    private HttpResponse errorResponse(int code, String message) {
        return jsonResponse(code, new ErrorBody(code, message));
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    /** Request body for campaign creation. */
    record CreateCampaignRequest(String name, CampaignType type) {}

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

    /** Error response body. */
    record ErrorBody(int status, String message) {}
}
