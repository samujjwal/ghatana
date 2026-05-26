package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.landingpage.LandingPageGeneratorService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentVersion;
import com.ghatana.digitalmarketing.domain.content.DisclosureReference;
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
 * HTTP servlet for DMOS landing page draft generation (F1-018).
 *
 * <p>Exposes two routes:</p>
 * <ul>
 *   <li>{@code POST /v1/workspaces/:workspaceId/content-items/:itemId/landing-page/generate} — generate a draft</li>
 *   <li>{@code GET  /v1/workspaces/:workspaceId/content-items/:itemId/landing-page/latest-approved} — fetch approved draft</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DMOS landing page generator API servlet for F1-018 draft generation and retrieval
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosLandingPageServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosLandingPageServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final LandingPageGeneratorService generatorService;
    private final Eventloop eventloop;

    /**
     * Constructs a new {@code DmosLandingPageServlet}.
     *
     * @param generatorService landing page generator service; must not be null
     * @param eventloop        ActiveJ event loop; must not be null
     */
    public DmosLandingPageServlet(LandingPageGeneratorService generatorService, Eventloop eventloop) {
        this.generatorService = Objects.requireNonNull(generatorService, "generatorService must not be null");
        this.eventloop        = Objects.requireNonNull(eventloop,        "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for landing page endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST,
                "/v1/workspaces/:workspaceId/content-items/:itemId/landing-page/generate",
                this::handleGenerateDraft)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/content-items/:itemId/landing-page/latest-approved",
                this::handleGetLatestApproved)
            .build(),
            DmosMetricsCollector.disabled(),
            "landing-page"
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleGenerateDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String itemId      = request.getPathParameter("itemId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);

                GenerateLandingPageRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateLandingPageRequest.class
                );

                LandingPageGeneratorService.GenerateLandingPageCommand command =
                    new LandingPageGeneratorService.GenerateLandingPageCommand(
                        itemId,
                        body.strategyId(),
                        body.brandDisplayName(),
                        body.voiceTone()       != null ? body.voiceTone()       : "",
                        body.primaryOffer(),
                        body.offerDescription() != null ? body.offerDescription() : "",
                        body.serviceArea(),
                        body.proofPoints()      != null ? body.proofPoints()      : List.of(),
                        body.disclosureTexts()  != null ? body.disclosureTexts()  : List.of(),
                        body.claimIds()         != null ? body.claimIds()         : List.of()
                    );

                return generatorService.generateDraft(ctx, command)
                    .map(version -> jsonResponse(201, ContentVersionResponse.from(version)))
                    .then(r -> Promise.of(r), e -> mapServiceError("generate landing page draft", e, request));

            } catch (IllegalArgumentException e) {
                return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to parse generate landing page request", e);
                return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", request));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestApproved(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String itemId      = request.getPathParameter("itemId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return generatorService.getLatestApproved(ctx, itemId)
                .map(version -> jsonResponse(200, ContentVersionResponse.from(version)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest approved landing page", e, request));

        } catch (IllegalArgumentException e) {
            return Promise.of(DmosApiErrorResponses.error(400, e.getMessage(), request));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to process get latest approved request", e);
            return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", request));
        }
    }

    // -------------------------------------------------------------------------
    // Context builder
    // -------------------------------------------------------------------------

    private DmOperationContext buildContext(HttpRequest request, String workspaceId, boolean requireIdempotencyKey) {
        String tenantId     = getRequiredHeader(request, "X-Tenant-ID");
        String principal    = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID", "no-correlation-id");
        String sessionId    = getHeader(request, "X-Session-ID", "no-session");
        Set<String> roles   = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> perms   = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));
        String idempotencyKeyValue = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));

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
            baseContext, sessionId, roles, perms, null);

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext, workspace, DmCorrelationId.of(correlationId), idempotencyKey);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private Promise<HttpResponse> mapServiceError(String operation, Throwable error, HttpRequest request) {
        if (error instanceof SecurityException) {
            return Promise.of(DmosApiErrorResponses.error(403, error.getMessage(), request));
        }
        if (error instanceof NoSuchElementException) {
            return Promise.of(DmosApiErrorResponses.error(404, error.getMessage(), request));
        }
        if (error instanceof IllegalArgumentException) {
            return Promise.of(DmosApiErrorResponses.error(400, error.getMessage(), request));
        }
        if (error instanceof IllegalStateException) {
            return Promise.of(DmosApiErrorResponses.error(409, error.getMessage(), request));
        }
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(DmosApiErrorResponses.error(423, error.getMessage(), request));
        }
        LOG.error("[DMOS] Unexpected error in operation: {}", operation, error);
        return Promise.of(DmosApiErrorResponses.error(500, "Internal server error", request));
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    record GenerateLandingPageRequest(
        String strategyId,
        String brandDisplayName,
        String voiceTone,
        String primaryOffer,
        String offerDescription,
        String serviceArea,
        List<String> proofPoints,
        List<String> disclosureTexts,
        List<String> claimIds
    ) {}

    record ContentBlockDto(String blockId, String blockType, String bodyText, int ordering) {
        static ContentBlockDto from(ContentBlock b) {
            return new ContentBlockDto(b.blockId(), b.blockType(), b.bodyText(), b.ordering());
        }
    }

    record ClaimReferenceDto(String claimId, String claimText, String claimSource) {
        static ClaimReferenceDto from(ClaimReference c) {
            return new ClaimReferenceDto(c.claimId(), c.claimText(), c.claimSource());
        }
    }

    record DisclosureReferenceDto(String disclosureId, String disclosureText, String disclosureType) {
        static DisclosureReferenceDto from(DisclosureReference d) {
            return new DisclosureReferenceDto(d.disclosureId(), d.disclosureText(), d.disclosureType());
        }
    }

    record ContentVersionResponse(
        String versionId,
        String itemId,
        String workspaceId,
        int versionNumber,
        String status,
        List<ContentBlockDto> contentBlocks,
        List<ClaimReferenceDto> claimReferences,
        List<DisclosureReferenceDto> disclosureReferences,
        String approvedBy,
        Instant approvedAt,
        Instant createdAt,
        String createdBy
    ) {
        static ContentVersionResponse from(ContentVersion v) {
            return new ContentVersionResponse(
                v.getVersionId(),
                v.getItemId(),
                v.getWorkspaceId().getValue(),
                v.getVersionNumber(),
                v.getStatus().name(),
                v.getContentBlocks().stream().map(ContentBlockDto::from).collect(Collectors.toList()),
                v.getClaimReferences().stream().map(ClaimReferenceDto::from).collect(Collectors.toList()),
                v.getDisclosureReferences().stream().map(DisclosureReferenceDto::from).collect(Collectors.toList()),
                v.getApprovedBy(),
                v.getApprovedAt(),
                v.getCreatedAt(),
                v.getCreatedBy()
            );
        }
    }

}
