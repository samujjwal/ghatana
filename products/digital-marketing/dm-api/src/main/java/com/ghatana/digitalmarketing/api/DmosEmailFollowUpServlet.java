package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.email.EmailFollowUpDraftService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.content.ClaimReference;
import com.ghatana.digitalmarketing.domain.content.ContentBlock;
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
 * HTTP servlet for DMOS email follow-up draft generation (F1-020).
 *
 * <p>Exposes two routes:</p>
 * <ul>
 *   <li>{@code POST /v1/workspaces/:workspaceId/content-items/:itemId/email-followup/generate}
 *       — generate a follow-up email draft</li>
 *   <li>{@code GET  /v1/workspaces/:workspaceId/content-items/:itemId/email-followup/latest-approved}
 *       — fetch the latest approved email draft</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DMOS email follow-up draft generator API servlet (F1-020)
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosEmailFollowUpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosEmailFollowUpServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final EmailFollowUpDraftService draftService;
    private final Eventloop eventloop;

    /**
     * Constructs a new {@code DmosEmailFollowUpServlet}.
     *
     * @param draftService email follow-up draft service; must not be null
     * @param eventloop    ActiveJ event loop; must not be null
     */
    public DmosEmailFollowUpServlet(EmailFollowUpDraftService draftService, Eventloop eventloop) {
        this.draftService = Objects.requireNonNull(draftService, "draftService must not be null");
        this.eventloop    = Objects.requireNonNull(eventloop,    "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for email follow-up endpoints.
     *
     * @return async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST,
                "/v1/workspaces/:workspaceId/content-items/:itemId/email-followup/generate",
                this::handleGenerateEmailDraft)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/content-items/:itemId/email-followup/latest-approved",
                this::handleGetLatestApproved)
            .build()
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleGenerateEmailDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String itemId      = request.getPathParameter("itemId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);

                GenerateEmailDraftRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateEmailDraftRequest.class
                );

                EmailFollowUpDraftService.GenerateEmailDraftCommand command =
                    new EmailFollowUpDraftService.GenerateEmailDraftCommand(
                        itemId,
                        body.contactId(),
                        body.strategyId(),
                        body.brandDisplayName(),
                        body.primaryOffer(),
                        body.senderName(),
                        body.replyToAddress(),
                        body.voiceTone(),
                        body.claimIds() != null ? body.claimIds() : List.of()
                    );

                return draftService.generateEmailDraft(ctx, command)
                    .map(version -> jsonResponse(201, ContentVersionResponse.from(version)))
                    .then(r -> Promise.of(r), e -> mapServiceError("generate email follow-up draft", e));

            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to parse generate email draft request", e);
                return Promise.of(errorResponse(500, "Internal server error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestApproved(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String itemId      = request.getPathParameter("itemId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);

            return draftService.getLatestApproved(ctx, itemId)
                .map(version -> jsonResponse(200, ContentVersionResponse.from(version)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest approved email draft", e));

        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to process get latest approved email draft request", e);
            return Promise.of(errorResponse(500, "Internal server error"));
        }
    }

    // -------------------------------------------------------------------------
    // Context builder
    // -------------------------------------------------------------------------

    private DmOperationContext buildContext(HttpRequest request, String workspaceId,
                                            boolean requireIdempotencyKey) {
        String tenantId      = getRequiredHeader(request, "X-Tenant-ID");
        String principal     = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID", "no-correlation-id");
        String sessionId     = getHeader(request, "X-Session-ID", "no-session");
        Set<String> roles    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> perms    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));
        String idkValue      = request.getHeader(HttpHeaders.of("X-Idempotency-Key"));

        if (requireIdempotencyKey && (idkValue == null || idkValue.isBlank())) {
            throw new IllegalArgumentException("X-Idempotency-Key header is required for write operations");
        }

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);
        DmIdempotencyKey idempotencyKey =
            (idkValue != null && !idkValue.isBlank()) ? DmIdempotencyKey.of(idkValue) : null;

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
            .filter(s -> !s.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String getRequiredHeader(HttpRequest request, String header) {
        String value = request.getHeader(HttpHeaders.of(header));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(header + " header is required");
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String header, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(header));
        return (value == null || value.isBlank()) ? defaultValue : value;
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
        if (error instanceof DmosFeatureDisabledException || error instanceof DmosConnectorDisabledException) {
            return Promise.of(errorResponse(423, error.getMessage()));
        }
        LOG.error("[DMOS] Unexpected error in operation: {}", operation, error);
        return Promise.of(errorResponse(500, "Internal server error"));
    }

    // -------------------------------------------------------------------------
    // DTOs
    // -------------------------------------------------------------------------

    record GenerateEmailDraftRequest(
            String contactId,
            String strategyId,
            String brandDisplayName,
            String primaryOffer,
            String senderName,
            String replyToAddress,
            String voiceTone,
            List<String> claimIds) {}

    record ErrorBody(int code, String message) {}

    record ContentVersionResponse(
            String versionId,
            String itemId,
            int versionNumber,
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) String createdAt,
            List<ContentBlockResponse> blocks,
            List<ClaimReferenceResponse> claimReferences,
            List<DisclosureReferenceResponse> disclosureReferences) {

        static ContentVersionResponse from(ContentVersion v) {
            List<ContentBlockResponse> blocks = v.getContentBlocks().stream()
                .map(b -> new ContentBlockResponse(b.blockId(), b.blockType(), b.bodyText(), b.ordering()))
                .toList();
            List<ClaimReferenceResponse> claims = v.getClaimReferences().stream()
                .map(c -> new ClaimReferenceResponse(c.claimId(), c.claimText(), c.claimSource()))
                .toList();
            List<DisclosureReferenceResponse> disclosures = v.getDisclosureReferences().stream()
                .map(d -> new DisclosureReferenceResponse(d.disclosureId(), d.disclosureText(), d.disclosureType()))
                .toList();
            Instant createdAt = v.getCreatedAt();
            return new ContentVersionResponse(
                v.getVersionId(),
                v.getItemId(),
                v.getVersionNumber(),
                v.getStatus().name(),
                createdAt != null ? createdAt.toString() : null,
                blocks,
                claims,
                disclosures
            );
        }
    }

    record ContentBlockResponse(String blockId, String blockType, String bodyText, int ordering) {}

    record ClaimReferenceResponse(String claimId, String claimText, String sourceUrl) {}

    record DisclosureReferenceResponse(String disclosureId, String text, String type) {}
}
