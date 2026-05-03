package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.RecordApprovalDecisionCommand;
import com.ghatana.digitalmarketing.application.approval.ApprovalWorkflowService.SubmitForApprovalCommand;
import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService;
import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService.IdempotentResponse;
import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmSecurityContextMapper;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.digitalmarketing.domain.DmosConnectorDisabledException;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.kernel.security.TenantSecurityContext;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
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
 * HTTP servlet for DMOS approval workflow endpoints (F1-022).
 *
 * <p>Exposes the following routes:
 * <ul>
 *   <li>POST /v1/workspaces/:workspaceId/approvals — submit an entity for approval</li>
 *   <li>POST /v1/workspaces/:workspaceId/approvals/:requestId/decide — approve or reject</li>
 *   <li>GET  /v1/workspaces/:workspaceId/approvals/:requestId — get approval status</li>
 *   <li>GET  /v1/workspaces/:workspaceId/approvals/:requestId/snapshot — get approval snapshot</li>
 *   <li>GET  /v1/workspaces/:workspaceId/approvals/pending/:subjectId — list pending approvals</li>
 * </ul>
 * </p>
 *
 * @doc.type class
 * @doc.purpose DMOS F1-022 approval workflow API servlet
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 */
public final class DmosApprovalServlet {

    private static final Logger LOG = LoggerFactory.getLogger(DmosApprovalServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ApprovalWorkflowService approvalService;
    private final Eventloop eventloop;
    private final IdempotencyService idempotencyService;

    public DmosApprovalServlet(ApprovalWorkflowService approvalService, Eventloop eventloop) {
        this(approvalService, eventloop, null);
    }

    public DmosApprovalServlet(ApprovalWorkflowService approvalService, Eventloop eventloop, IdempotencyService idempotencyService) {
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService must not be null");
        this.eventloop       = Objects.requireNonNull(eventloop,       "eventloop must not be null");
        this.idempotencyService = idempotencyService;
    }

    /**
     * Returns the routing servlet for approval endpoints.
     */
    public AsyncServlet routes() {
        return DmosApiRateLimiter.wrap(
        RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST,
                "/v1/workspaces/:workspaceId/approvals",
                this::handleSubmit)
            .with(HttpMethod.POST,
                "/v1/workspaces/:workspaceId/approvals/:requestId/decide",
                this::handleDecide)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/approvals/:requestId",
                this::handleGetStatus)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/approvals/:requestId/snapshot",
                this::handleGetSnapshot)
            .with(HttpMethod.GET,
                "/v1/workspaces/:workspaceId/approvals/pending/:subjectId",
                this::handleListPending)
            .build()
        );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private Promise<HttpResponse> handleSubmit(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");

        try {
            // P0-6.1: Check idempotency key if service is available
            if (idempotencyService != null) {
                String idempotencyKey = request.getHeader(HttpHeaders.of(IDEMPOTENCY_KEY_HEADER));
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                    DmOperationContext ctx = buildContext(request, workspaceId);
                    return idempotencyService.getCachedResponse(ctx, idempotencyKey)
                        .then(cached -> {
                            if (cached != null) {
                                LOG.info("[DMOS] Idempotency cache hit: returning cached response");
                                return Promise.of(jsonResponse(cached.statusCode(), cached.body()));
                            }
                            // Continue with normal processing
                            return handleSubmitInternal(request, workspaceId, ctx, idempotencyKey);
                        });
                }
            }
            return handleSubmitInternal(request, workspaceId, null, null);
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleSubmit", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    private Promise<HttpResponse> handleSubmitInternal(HttpRequest request, String workspaceId, 
                                                         DmOperationContext ctx, String idempotencyKey) {
        if (ctx == null) {
            ctx = buildContext(request, workspaceId);
        }

        return request.loadBody()
            .then(body -> {
                try {
                    SubmitRequest req;
                    try {
                        req = MAPPER.readValue(body.getString(StandardCharsets.UTF_8), SubmitRequest.class);
                    } catch (Exception e) {
                        LOG.warn("Invalid submit approval request body: {}", e.getMessage());
                        return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
                    }

                    ApprovalTargetType targetType;
                    try {
                        targetType = ApprovalTargetType.valueOf(req.targetType());
                    } catch (IllegalArgumentException e) {
                        return Promise.of(badRequest("Unknown targetType: " + req.targetType()));
                    }

                    SubmitForApprovalCommand command = new SubmitForApprovalCommand(
                        targetType,
                        req.targetId(),
                        req.description(),
                        req.riskLevel() != null ? req.riskLevel() : 1,
                        req.requiredApproverRole() != null ? req.requiredApproverRole() : "brand-manager",
                        req.validationResultId()
                    );

                    return approvalService.submitForApproval(ctx, command)
                        .map(this::toRecordResponse)
                        .then(record -> {
                            // P0-6.2: Store response for idempotency
                            if (idempotencyService != null && idempotencyKey != null) {
                                try {
                                    String responseBody = MAPPER.writeValueAsString(record);
                                    IdempotentResponse response = new IdempotentResponse(responseBody, 201, null);
                                    return idempotencyService.storeResponse(ctx, idempotencyKey, response)
                                        .then(__ -> Promise.of(jsonResponse(201, record)));
                                } catch (Exception e) {
                                    LOG.warn("Failed to store idempotency response", e);
                                    return Promise.of(jsonResponse(201, record));
                                }
                            }
                            return Promise.of(jsonResponse(201, record));
                        })
                        .then(r -> Promise.of(r), e -> mapServiceError("submit", e));
                } catch (IllegalArgumentException e) {
                    return Promise.of(badRequest("Invalid request: " + e.getMessage()));
                } catch (Exception e) {
                    LOG.error("Unexpected error during submit approval", e);
                    return Promise.of(internalError("Unexpected error"));
                }
            });
    }

    private Promise<HttpResponse> handleDecide(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String requestId   = request.getPathParameter("requestId");

        try {
            return request.loadBody()
                .then(body -> {
                    try {
                        DecideRequest req;
                        try {
                            req = MAPPER.readValue(body.getString(StandardCharsets.UTF_8), DecideRequest.class);
                        } catch (Exception e) {
                            LOG.warn("Invalid decide request body: {}", e.getMessage());
                            return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
                        }

                        ApprovalDecision decision;
                        try {
                            decision = ApprovalDecision.valueOf(req.decision());
                        } catch (IllegalArgumentException e) {
                            return Promise.of(badRequest("Unknown decision: " + req.decision()));
                        }

                        DmOperationContext ctx = buildContext(request, workspaceId);
                        RecordApprovalDecisionCommand command = new RecordApprovalDecisionCommand(
                            requestId,
                            decision,
                            req.notes()
                        );

                        return approvalService.recordDecision(ctx, command)
                            .map(this::toRecordResponse)
                            .map(r -> jsonResponse(200, r))
                            .then(r -> Promise.of(r), e -> mapServiceError("decide", e));
                    } catch (IllegalArgumentException e) {
                        return Promise.of(badRequest("Invalid request: " + e.getMessage()));
                    } catch (Exception e) {
                        LOG.error("Unexpected error during decide approval", e);
                        return Promise.of(internalError("Unexpected error"));
                    }
                });
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleDecide", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    private Promise<HttpResponse> handleGetStatus(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String requestId   = request.getPathParameter("requestId");

        try {
            DmOperationContext ctx = buildContext(request, workspaceId);
            return Promises.toList(
                    approvalService.getApprovalStatus(ctx, requestId),
                    approvalService.getSnapshot(ctx, requestId))
                .then(tuple -> {
                    var recordOpt = tuple.get(0);
                    var snapshotOpt = tuple.get(1);
                    if (recordOpt.isEmpty()) {
                        return Promise.of(notFound("Approval not found: " + requestId));
                    }
                    ApprovalRecord record = recordOpt.get();
                    ApprovalSnapshot snapshot = snapshotOpt.orElse(null);
                    DmosApprovalDto dto = toDmosApprovalDto(ctx, record, snapshot);
                    return Promise.of(jsonResponse(200, dto));
                })
                .then(r -> Promise.of(r), e -> mapServiceError("get-status", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleGetStatus", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    private Promise<HttpResponse> handleGetSnapshot(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String requestId   = request.getPathParameter("requestId");

        try {
            DmOperationContext ctx = buildContext(request, workspaceId);
            return approvalService.getSnapshot(ctx, requestId)
                .map(opt -> opt.map(s -> new SnapshotResponse(
                    s.requestId(),
                    s.targetType().name(),
                    s.targetId(),
                    s.targetWorkspaceId(),
                    s.snapshotSummary(),
                    s.validationResultId(),
                    s.riskLevel(),
                    s.requiredApproverRole(),
                    s.snapshotAt()
                )).orElse(null))
                .map(r -> r != null ? jsonResponse(200, r) : notFound("Snapshot not found: " + requestId))
                .then(r -> Promise.of(r), e -> mapServiceError("get-snapshot", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleGetSnapshot", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    private Promise<HttpResponse> handleListPending(HttpRequest request) {
        String workspaceId = request.getPathParameter("workspaceId");
        String subjectId   = request.getPathParameter("subjectId");

        try {
            DmOperationContext ctx = buildContext(request, workspaceId);
            return approvalService.listPendingApprovals(ctx, subjectId)
                .map(list -> list.stream().map(record -> toDmosApprovalDto(ctx, record, null)).toList())
                .map(list -> jsonResponse(200, new PendingListResponse(list)))
                .then(r -> Promise.of(r), e -> mapServiceError("list-pending", e));
        } catch (IllegalArgumentException e) {
            return Promise.of(badRequest("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            LOG.error("Error in handleListPending", e);
            return Promise.of(internalError("Error processing request"));
        }
    }

    // -------------------------------------------------------------------------
    // Context builder
    // -------------------------------------------------------------------------

    private DmOperationContext buildContext(HttpRequest request, String workspaceId) {
        // P0-3.3: Enforce mandatory headers - return 400 when missing
        String tenantId      = getRequiredHeader(request, "X-Tenant-ID");
        String principal     = getRequiredHeader(request, "X-Principal-ID");
        String correlationId = getRequiredHeader(request, "X-Correlation-ID");
        String sessionId     = getRequiredHeader(request, "X-Session-ID");
        
        // P0-3.4: Treat missing/empty roles as no privileges (deny by default)
        Set<String> roles    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Roles")));
        Set<String> perms    = parseCsvHeader(request.getHeader(HttpHeaders.of("X-Permissions")));

        DmWorkspaceId workspace = DmWorkspaceId.of(workspaceId);

        DmOperationContext baseContext = DmOperationContext.builder()
            .tenantId(DmTenantId.of(tenantId))
            .workspaceId(workspace)
            .actor(ActorRef.user(principal))
            .correlationId(DmCorrelationId.of(correlationId))
            .build();

        // Empty roles/permissions result in no privileges (deny by default)
        TenantSecurityContext securityContext = DmSecurityContextMapper.toTenantSecurityContext(
            baseContext, sessionId, roles, perms, null);

        return DmSecurityContextMapper.fromSecurityContext(
            securityContext, workspace, DmCorrelationId.of(correlationId), null);
    }

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

    private ApprovalRecordResponse toRecordResponse(ApprovalRecord record) {
        return new ApprovalRecordResponse(
            record.requestId(),
            record.subjectId(),
            record.requestedBy(),
            record.action(),
            record.status().name(),
            record.requestedAt(),
            record.expiresAt(),
            record.decidedAt(),
            record.reviewerId(),
            record.reviewerNotes()
        );
    }

    private DmosApprovalDto toDmosApprovalDto(DmOperationContext ctx, ApprovalRecord record, ApprovalSnapshot snapshot) {
        return new DmosApprovalDto(
            record.requestId(),
            ctx.getTenantId().getValue(),
            ctx.getWorkspaceId().getValue(),
            snapshot != null ? snapshot.targetType().name() : null,
            snapshot != null ? snapshot.targetId() : null,
            snapshot != null ? snapshot.snapshotSummary() : null,
            snapshot != null ? snapshot.riskLevel() : 1,
            snapshot != null ? snapshot.requiredApproverRole() : "brand-manager",
            record.status().name(),
            record.requestedAt(),
            record.requestedBy(),
            record.decidedAt(),
            record.reviewerId(),
            record.reviewerNotes(),
            snapshot != null ? snapshot.snapshotSummary() : null,
            snapshot != null ? snapshot.validationResultId() : null,
            snapshot != null ? snapshot.snapshotAt() : null
        );
    }

    private Promise<HttpResponse> mapServiceError(String operation, Throwable e) {
        LOG.warn("Approval service error [{}]: {}", operation, e.getMessage());
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

    // -------------------------------------------------------------------------
    // HTTP response helpers
    // -------------------------------------------------------------------------

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
            LOG.error("[DMOS] Serialization failure in approval servlet", e);
            return HttpResponse.ofCode(500).build();
        }
    }

    // -------------------------------------------------------------------------
    // Header helpers
    // -------------------------------------------------------------------------

    private static String getRequiredHeader(HttpRequest request, String name) {
        String value = request.getHeader(HttpHeaders.of(name));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required header: " + name);
        }
        return value;
    }

    private static String getHeader(HttpRequest request, String name, String defaultValue) {
        String value = request.getHeader(HttpHeaders.of(name));
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static Set<String> parseCsvHeader(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Request/Response records
    // -------------------------------------------------------------------------

    /** Request body for submitting an entity for approval. */
    record SubmitRequest(
            String targetType,
            String targetId,
            String description,
            Integer riskLevel,
            String requiredApproverRole,
            String validationResultId
    ) {}

    /** Request body for recording an approval decision. */
    record DecideRequest(
            String decision,
            String notes
    ) {}

    /** Response body for a single approval record. */
    record ApprovalRecordResponse(
            String requestId,
            String subjectId,
            String requestedBy,
            String action,
            String status,
            Instant requestedAt,
            Instant expiresAt,
            Instant decidedAt,
            String reviewerId,
            String reviewerNotes
    ) {}

    /** Combined DMOS approval DTO with record and snapshot fields. */
    record DmosApprovalDto(
            String requestId,
            String tenantId,
            String workspaceId,
            String targetType,
            String targetId,
            String description,
            int riskLevel,
            String requiredApproverRole,
            String status,
            Instant submittedAt,
            String submittedBy,
            Instant decidedAt,
            String decidedBy,
            String comment,
            String snapshotSummary,
            String validationResultId,
            Instant snapshotAt
    ) {}

    /** Response body for an approval snapshot. */
    record SnapshotResponse(
            String requestId,
            String targetType,
            String targetId,
            String targetWorkspaceId,
            String snapshotSummary,
            String validationResultId,
            int riskLevel,
            String requiredApproverRole,
            Instant snapshotAt
    ) {}

    /** Response body for listing pending approvals. */
    record PendingListResponse(List<ApprovalRecordResponse> items) {}

    /** Generic error response. */
    record ErrorBody(int code, String message) {}
}
