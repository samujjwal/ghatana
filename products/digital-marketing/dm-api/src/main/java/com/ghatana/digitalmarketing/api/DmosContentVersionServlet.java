package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.content.ContentItemService;
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
import com.ghatana.digitalmarketing.domain.content.ContentItem;
import com.ghatana.digitalmarketing.domain.content.ContentItemType;
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
 * HTTP servlet for DMOS enriched content item and version management (F1-017).
 *
 * <p>Exposes five routes:
 * <ul>
 *   <li>POST /v1/workspaces/:workspaceId/content-items</li>
 *   <li>POST /v1/workspaces/:workspaceId/content-items/:itemId/versions</li>
 *   <li>GET  /v1/workspaces/:workspaceId/content-items/:itemId/versions/latest-approved</li>
 *   <li>POST /v1/workspaces/:workspaceId/content-items/:itemId/versions/:versionId/approve</li>
 *   <li>GET  /v1/workspaces/:workspaceId/content-items/:itemId/versions</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS enriched content version API servlet for F1-017
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosContentVersionServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosContentVersionServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ContentItemService contentItemService;
    private final Eventloop eventloop;

    public DmosContentVersionServlet(ContentItemService contentItemService, Eventloop eventloop) {
        this.contentItemService = Objects.requireNonNull(contentItemService, "contentItemService must not be null");
        this.eventloop          = Objects.requireNonNull(eventloop,          "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for content item/version endpoints.
     *
     * @return configured async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/content-items",
                this::handleCreateItem)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/content-items/:itemId/versions",
                this::handleCreateVersion)
            .with(HttpMethod.GET,  "/v1/workspaces/:workspaceId/content-items/:itemId/versions/latest-approved",
                this::handleGetLatestApproved)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/content-items/:itemId/versions/:versionId/approve",
                this::handleApproveVersion)
            .with(HttpMethod.GET,  "/v1/workspaces/:workspaceId/content-items/:itemId/versions",
                this::handleGetVersionHistory)
            .build()
        );
    }

    // ---- handlers ----

    private Promise<HttpResponse> handleCreateItem(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                CreateContentItemRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CreateContentItemRequest.class
                );
                ContentItemType itemType;
                try {
                    itemType = ContentItemType.valueOf(body.itemType());
                } catch (IllegalArgumentException | NullPointerException ex) {
                    return Promise.of(errorResponse(400, "Invalid or missing itemType: " + body.itemType()));
                }
                ContentItemService.CreateContentItemCommand command =
                    new ContentItemService.CreateContentItemCommand(
                        body.title(),
                        itemType,
                        body.description()
                    );
                return contentItemService.createItem(ctx, command)
                    .map(item -> jsonResponse(201, ContentItemResponse.from(item)))
                    .then(r -> Promise.of(r), e -> mapServiceError("create content item", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to create content item", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleCreateVersion(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String itemId = request.getPathParameter("itemId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                CreateContentVersionRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    CreateContentVersionRequest.class
                );
                ContentItemService.CreateContentVersionCommand command =
                    new ContentItemService.CreateContentVersionCommand(
                        itemId,
                        body.contentBlocks() != null ? body.contentBlocks() : List.of(),
                        body.claimReferences() != null ? body.claimReferences() : List.of(),
                        body.disclosureReferences() != null ? body.disclosureReferences() : List.of(),
                        body.modelVersion(),
                        body.promptVersion(),
                        body.sourceStrategy()
                    );
                return contentItemService.createVersion(ctx, command)
                    .map(version -> jsonResponse(201, ContentVersionResponse.from(version)))
                    .then(r -> Promise.of(r), e -> mapServiceError("create content version", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to create content version", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetLatestApproved(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String itemId = request.getPathParameter("itemId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);
            return contentItemService.getLatestApproved(ctx, itemId)
                .map(version -> jsonResponse(200, ContentVersionResponse.from(version)))
                .then(r -> Promise.of(r), e -> mapServiceError("get latest approved version", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get latest approved version", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    private Promise<HttpResponse> handleApproveVersion(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String versionId = request.getPathParameter("versionId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                return contentItemService.approveVersion(ctx, versionId)
                    .map(version -> jsonResponse(200, ContentVersionResponse.from(version)))
                    .then(r -> Promise.of(r), e -> mapServiceError("approve content version", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve content version", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetVersionHistory(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String itemId = request.getPathParameter("itemId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);
            return contentItemService.getVersionHistory(ctx, itemId)
                .map(versions -> jsonResponse(200, versions.stream()
                    .map(ContentVersionResponse::from)
                    .collect(Collectors.toList())))
                .then(r -> Promise.of(r), e -> mapServiceError("get version history", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get version history", e);
            return Promise.of(errorResponse(500, "Internal error"));
        }
    }

    // ---- error mapping ----

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
        LOG.error("[DMOS] Failed to {}", operation, error);
        return Promise.of(errorResponse(500, "Internal error"));
    }

    // ---- context builder ----

    private DmOperationContext buildContext(
            HttpRequest request, String workspaceId, boolean requireIdempotencyKey) {
        String tenantId = getRequiredHeader(request, "X-Tenant-ID");
        String principal = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID",
            DmCorrelationId.generate().getValue());
        String idempotencyKeyValue = getHeader(request, "X-Idempotency-Key", null);
        String sessionId = getHeader(request, "X-Session-ID", null);
        Set<String> roles = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> permissions = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        if (requireIdempotencyKey && (idempotencyKeyValue == null || idempotencyKeyValue.isBlank())) {
            throw new IllegalArgumentException(
                "X-Idempotency-Key header is required for write operations");
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
            baseContext, sessionId, roles, permissions, null);

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext, workspace, DmCorrelationId.of(correlationId), idempotencyKey);
    }

    // ---- utilities ----

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
        try {
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(new ErrorResponse(code, message)))
                .build();
        } catch (Exception e) {
            return HttpResponse.ofCode(code).build();
        }
    }

    // ---- request / response DTOs ----

    private record CreateContentItemRequest(
            String title,
            String itemType,
            String description) {}

    private record CreateContentVersionRequest(
            List<ContentBlock> contentBlocks,
            List<ClaimReference> claimReferences,
            List<DisclosureReference> disclosureReferences,
            String modelVersion,
            String promptVersion,
            String sourceStrategy) {}

    private record ContentItemResponse(
            String itemId,
            String workspaceId,
            String title,
            String itemType,
            String description,
            String createdAt,
            String createdBy) {

        static ContentItemResponse from(ContentItem item) {
            return new ContentItemResponse(
                item.getItemId(),
                item.getWorkspaceId().getValue(),
                item.getTitle(),
                item.getItemType().name(),
                item.getDescription(),
                item.getCreatedAt().toString(),
                item.getCreatedBy()
            );
        }
    }

    private record ContentVersionResponse(
            String versionId,
            String itemId,
            String workspaceId,
            int versionNumber,
            String status,
            String approvedBy,
            Instant approvedAt,
            String createdAt,
            String createdBy) {

        static ContentVersionResponse from(ContentVersion version) {
            return new ContentVersionResponse(
                version.getVersionId(),
                version.getItemId(),
                version.getWorkspaceId().getValue(),
                version.getVersionNumber(),
                version.getStatus().name(),
                version.getApprovedBy(),
                version.getApprovedAt(),
                version.getCreatedAt().toString(),
                version.getCreatedBy()
            );
        }
    }

    private record ErrorResponse(int code, String message) {}
}
