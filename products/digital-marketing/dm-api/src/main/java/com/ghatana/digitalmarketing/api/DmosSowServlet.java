package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.sow.SowService;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.sow.SowClause;
import com.ghatana.digitalmarketing.domain.sow.SowDraft;
import com.ghatana.digitalmarketing.domain.sow.SowRiskFlag;
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
 * HTTP servlet for DMOS SOW draft generation, review, approval, and export (F1-016).
 *
 * <p>Exposes five routes:
 * <ul>
 *   <li>POST /v1/workspaces/:workspaceId/sow</li>
 *   <li>POST /v1/workspaces/:workspaceId/sow/:sowId/submit</li>
 *   <li>POST /v1/workspaces/:workspaceId/sow/:sowId/approve</li>
 *   <li>POST /v1/workspaces/:workspaceId/sow/:sowId/export</li>
 *   <li>GET  /v1/workspaces/:workspaceId/sow/:sowId</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS SOW draft API servlet for F1-016
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosSowServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosSowServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final SowService sowService;
    private final Eventloop eventloop;

    public DmosSowServlet(SowService sowService, Eventloop eventloop) {
        this.sowService  = Objects.requireNonNull(sowService,  "sowService must not be null");
        this.eventloop   = Objects.requireNonNull(eventloop,   "eventloop must not be null");
    }

    /**
     * Returns the routing servlet for SOW endpoints.
     *
     * @return configured async servlet
     */
    public AsyncServlet getServlet() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/sow",
                this::handleGenerateDraft)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/sow/:sowId/submit",
                this::handleSubmitForReview)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/sow/:sowId/approve",
                this::handleApproveDraft)
            .with(HttpMethod.POST, "/v1/workspaces/:workspaceId/sow/:sowId/export",
                this::handleExportDraft)
            .with(HttpMethod.GET, "/v1/workspaces/:workspaceId/sow/:sowId",
                this::handleGetDraft)
            .build()
        );
    }

    // ---- handlers ----

    private Promise<HttpResponse> handleGenerateDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                GenerateSowRequest body = MAPPER.readValue(
                    request.getBody().getString(StandardCharsets.UTF_8),
                    GenerateSowRequest.class
                );
                SowService.GenerateSowCommand command = new SowService.GenerateSowCommand(
                    body.proposalId(),
                    body.templateVersion(),
                    body.assumptions() != null ? body.assumptions() : "",
                    body.exclusions()  != null ? body.exclusions()  : ""
                );
                return sowService.generateDraft(ctx, command)
                    .map(d -> jsonResponse(201, SowDraftResponse.from(d)))
                    .then(r -> Promise.of(r), e -> mapServiceError("generate SOW draft", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to generate SOW draft", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleSubmitForReview(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String sowId = request.getPathParameter("sowId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                return sowService.submitForReview(ctx, sowId)
                    .map(d -> jsonResponse(200, SowDraftResponse.from(d)))
                    .then(r -> Promise.of(r), e -> mapServiceError("submit SOW for review", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to submit SOW for review", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleApproveDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String sowId = request.getPathParameter("sowId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                return sowService.approveDraft(ctx, sowId)
                    .map(d -> jsonResponse(200, SowDraftResponse.from(d)))
                    .then(r -> Promise.of(r), e -> mapServiceError("approve SOW draft", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to approve SOW draft", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleExportDraft(HttpRequest request) {
        return request.loadBody().then(__ -> {
            try {
                String workspaceId = request.getPathParameter("workspaceId");
                String sowId = request.getPathParameter("sowId");
                DmOperationContext ctx = buildContext(request, workspaceId, true);
                return sowService.exportDraft(ctx, sowId)
                    .map(d -> jsonResponse(200, SowDraftResponse.from(d)))
                    .then(r -> Promise.of(r), e -> mapServiceError("export SOW draft", e));
            } catch (IllegalArgumentException e) {
                return Promise.of(errorResponse(400, e.getMessage()));
            } catch (Exception e) {
                LOG.error("[DMOS] Failed to export SOW draft", e);
                return Promise.of(errorResponse(500, "Internal error"));
            }
        });
    }

    private Promise<HttpResponse> handleGetDraft(HttpRequest request) {
        try {
            String workspaceId = request.getPathParameter("workspaceId");
            String sowId = request.getPathParameter("sowId");
            DmOperationContext ctx = buildContext(request, workspaceId, false);
            return sowService.getDraft(ctx)
                .map(d -> {
                    if (!d.getSowId().equals(sowId)) {
                        return errorResponse(404, "SOW draft not found: " + sowId);
                    }
                    return jsonResponse(200, SowDraftResponse.from(d));
                })
                .then(r -> Promise.of(r), e -> mapServiceError("get SOW draft", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(errorResponse(400, e.getMessage()));
        } catch (Exception e) {
            LOG.error("[DMOS] Failed to get SOW draft", e);
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

    private record GenerateSowRequest(
            String proposalId,
            String templateVersion,
            String assumptions,
            String exclusions) {}

    private record SowRiskFlagResponse(String flagType, String description, String severity) {
        static SowRiskFlagResponse from(SowRiskFlag f) {
            return new SowRiskFlagResponse(
                    f.flagType().name(), f.description(), f.severity());
        }
    }

    private record SowClauseResponse(
            String clauseId,
            String clauseType,
            String version,
            String status) {
        static SowClauseResponse from(SowClause c) {
            return new SowClauseResponse(
                    c.clauseId(), c.clauseType(), c.version(), c.status().name());
        }
    }

    private record SowDraftResponse(
            String sowId,
            String workspaceId,
            String proposalId,
            String templateVersion,
            List<SowClauseResponse> selectedClauses,
            List<SowRiskFlagResponse> riskFlags,
            String assumptions,
            String exclusions,
            String disclaimer,
            String modelVersion,
            String status,
            String approvedBy,
            Instant approvedAt,
            Instant createdAt) {

        static SowDraftResponse from(SowDraft d) {
            return new SowDraftResponse(
                d.getSowId(),
                d.getWorkspaceId().getValue(),
                d.getProposalId(),
                d.getTemplateVersion(),
                d.getSelectedClauses().stream().map(SowClauseResponse::from).toList(),
                d.getRiskFlags().stream().map(SowRiskFlagResponse::from).toList(),
                d.getAssumptions(),
                d.getExclusions(),
                d.getDisclaimer(),
                d.getModelVersion(),
                d.getStatus().name(),
                d.getApprovedBy(),
                d.getApprovedAt(),
                d.getCreatedAt()
            );
        }
    }

    private record ErrorResponse(int code, String message) {}
}
