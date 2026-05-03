package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.validation.ContentValidationService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationFinding;
import com.ghatana.digitalmarketing.domain.validation.ContentValidationResult;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP servlet for DMOS content validation endpoints (F1-021).
 *
 * <p>Exposes two routes:
 * <ul>
 *   <li>POST /v1/workspaces/:workspaceId/content-versions/:versionId/validate</li>
 *   <li>GET  /v1/workspaces/:workspaceId/content-versions/:versionId/validation-results</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS brand and claim validation API servlet for F1-021
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosContentValidationServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosContentValidationServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ContentValidationService validationService;
    private final Eventloop eventloop;

    public DmosContentValidationServlet(ContentValidationService validationService, Eventloop eventloop) {
        this.validationService = Objects.requireNonNull(validationService, "validationService must not be null");
        this.eventloop         = Objects.requireNonNull(eventloop,         "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for content validation endpoints.
     */
    public AsyncServlet routes() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST,
                "/v1/workspaces/:workspaceId/content-versions/:versionId/validate",
                this::handleValidate)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/content-versions/:versionId/validation-results",
                this::handleListResults)
            .build()
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleValidate(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String versionId   = request.getPathParameter("versionId");

        try {
            return request.loadBody()
                .then(body -> {
                    try {
                        ValidateRequest req;
                        try {
                            req = MAPPER.readValue(body.getString(StandardCharsets.UTF_8), ValidateRequest.class);
                        } catch (Exception e) {
                            LOG.warn("Invalid validate request body: {}", e.getMessage());
                            return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
                        }

                        DmOperationContext ctx = buildContext(request, workspaceId);
                        ContentValidationService.ValidateContentVersionCommand command =
                            new ContentValidationService.ValidateContentVersionCommand(
                                versionId,
                                req.forbiddenTerms() != null ? req.forbiddenTerms() : List.of(),
                                req.requiredClaimIds() != null ? req.requiredClaimIds() : List.of());

                        return validationService.validateVersion(ctx, command)
                            .map(this::toResponse)
                            .map(r -> jsonResponse(200, r))
                            .then(r -> Promise.of(r), e -> mapServiceError("validate", e));
                    } catch (IllegalArgumentException e) {
                        return Promise.of(badRequest("Invalid request: " + e.getMessage()));
                    } catch (Exception e) {
                        LOG.error("Unexpected error during validate", e);
                        return Promise.of(internalError("Unexpected error"));
                    }
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleValidate", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    private Promise<HttpResponse> handleListResults(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String versionId   = request.getPathParameter("versionId");

        try {
            DmOperationContext ctx = buildContext(request, workspaceId);
            return validationService.listResults(ctx, versionId)
                .map(results -> results.stream().map(this::toResponse).toList())
                .map(responses -> jsonResponse(200, new ListResultsResponse(responses)))
                .then(r -> Promise.of(r), e -> mapServiceError("list-results", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleListResults", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    // -------------------------------------------------------------------------
    // Context builder
    // -------------------------------------------------------------------------

    private DmOperationContext buildContext(HttpRequest request, String workspaceId) {
        String tenantId      = getRequiredHeader(request, "X-Tenant-ID");
        String principal     = getHeader(request, "X-Principal-ID", "anonymous");
        String correlationId = getHeader(request, "X-Correlation-ID", "no-correlation-id");
        String sessionId     = getHeader(request, "X-Session-ID", "no-session");
        Set<String> roles    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> perms    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);

        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlationId))
            .build();

        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext, sessionId, roles, perms, null);

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext, workspace, DmCorrelationId.of(correlationId), null);
    }

    // -------------------------------------------------------------------------
    // Response builders
    // -------------------------------------------------------------------------

    private ValidationResultResponse toResponse(ContentValidationResult result) {
        List<FindingResponse> findings = result.findings().stream()
            .map(f -> new FindingResponse(
                f.severity().name(),
                f.ruleCode(),
                f.affectedBlockId(),
                f.reason(),
                f.requiredAction(),
                f.approverRole()))
            .toList();
        return new ValidationResultResponse(
            result.versionId(),
            result.outcome().name(),
            findings,
            result.validatedAt(),
            result.validatedBy());
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable e) {
        LOG.warn("Content validation service error [{}]: {}", operation, e.getMessage());
        if (e instanceof SecurityException) {
            return Promise.of(forbidden(e.getMessage()));
        }
        if (e instanceof IllegalArgumentException) {
            return Promise.of(badRequest(e.getMessage()));
        }
        if (e instanceof NoSuchElementException) {
            return Promise.of(notFound(e.getMessage()));
        }
        if (e instanceof DmosFeatureDisabledException || e instanceof DmosConnectorDisabledException) {
            return Promise.of(locked(e.getMessage()));
        }
        return Promise.of(internalError("Unexpected error during " + operation));
    }

    private static HttpResponse locked(String message) {
        return errorResponse(423, message);
    }

    private static HttpResponse badRequest(String message) {
        return errorResponse(400, message);
    }

    private static HttpResponse forbidden(String message) {
        return errorResponse(403, message);
    }

    private static HttpResponse notFound(String message) {
        return errorResponse(404, message);
    }

    private static HttpResponse internalError(String message) {
        return errorResponse(500, message);
    }

    private static HttpResponse errorResponse(int code, String message) {
        try {
            String json = MAPPER.writeValueAsString(new ErrorBody(code, message));
            return HttpResponse.ofCode(code)
                .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(json.getBytes(StandardCharsets.UTF_8))
                .build();
        } catch (Exception ex) {
            return HttpResponse.ofCode(code).build();
        }
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

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String s : value.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isBlank()) {
                result.add(trimmed);
            }
        }
        return result;
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

    // -------------------------------------------------------------------------
    // Wire models
    // -------------------------------------------------------------------------

    record ValidateRequest(List<String> forbiddenTerms, List<String> requiredClaimIds) {}

    record FindingResponse(
            String severity,
            String ruleCode,
            String affectedBlockId,
            String reason,
            String requiredAction,
            String approverRole) {}

    record ValidationResultResponse(
            String versionId,
            String outcome,
            List<FindingResponse> findings,
            Instant validatedAt,
            String validatedBy) {}

    record ListResultsResponse(List<ValidationResultResponse> results) {}

    record ErrorBody(int code, String message) {}
}
