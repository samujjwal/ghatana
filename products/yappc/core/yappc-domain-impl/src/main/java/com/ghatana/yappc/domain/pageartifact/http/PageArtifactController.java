/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.domain.pageartifact.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.Traced;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import com.ghatana.platform.security.model.User;
import com.ghatana.yappc.domain.pageartifact.PageArtifactAuditRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactAtomicMutationRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactConflictException;
import com.ghatana.yappc.domain.pageartifact.PageArtifactDocument;
import com.ghatana.yappc.domain.pageartifact.PageArtifactPermission;
import com.ghatana.yappc.domain.pageartifact.PageArtifactRepository;
import com.ghatana.yappc.domain.pageartifact.PageArtifactResourceScopeAuthorizer;
import com.ghatana.yappc.domain.pageartifact.PageArtifactValidator;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP controller for page artifact operations.
 * <p>
 * Exposes REST endpoints for:
 * <ul>
 *   <li>PUT /api/v1/page-artifacts/:artifactId/document - Save page artifact document</li>
 *   <li>GET /api/v1/page-artifacts/:artifactId/document - Load page artifact document</li>
 * </ul>
 * All endpoints require tenant/workspace/project scoping via headers, support
 * optimistic concurrency via If-Match header, and enforce authorization checks.
 *
 * @doc.type class
 * @doc.purpose HTTP controller for page artifact REST API with authorization
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class PageArtifactController {

    private static final Logger LOG = LoggerFactory.getLogger(PageArtifactController.class);
    private static final Pattern ARTIFACT_ID_PATTERN =
            Pattern.compile("/api/v1/page-artifacts/([^/]+)/document");
    private static final Pattern REVIEW_DECISION_ARTIFACT_ID_PATTERN =
            Pattern.compile("/api/v1/page-artifacts/([^/]+)/review-decisions");
    private static final Pattern OPERATION_LOG_EXPORT_ARTIFACT_ID_PATTERN =
            Pattern.compile("/api/v1/page-artifacts/([^/]+)/operation-log/export");

    private final PageArtifactRepository repository;
    private final PageArtifactAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final SyncAuthorizationService authorizationService;
    private final MetricsCollector metrics;
    private final PageArtifactResourceScopeAuthorizer resourceScopeAuthorizer;

    private record ReviewDecisionRequest(
            String actionId,
            String decision,
            List<String> evidence
    ) {
    }

    /**
     * Creates a new PageArtifactController.
     * <p>
     * The supplied {@code repository} must also implement
     * {@link PageArtifactAtomicMutationRepository} so that save and audit events
     * commit together; non-atomic paths are not supported.
     *
     * @param repository               Page artifact repository (must also implement PageArtifactAtomicMutationRepository)
     * @param auditRepository          Audit trail repository
     * @param objectMapper             JSON object mapper
     * @param authorizationService     Authorization service for permission checks
     * @param metrics                  Metrics collector for observability
     * @param resourceScopeAuthorizer  DB-backed workspace/project scope authorizer
     */
    public PageArtifactController(
            @NotNull PageArtifactRepository repository,
            @NotNull PageArtifactAuditRepository auditRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull SyncAuthorizationService authorizationService,
            @NotNull MetricsCollector metrics,
            @NotNull PageArtifactResourceScopeAuthorizer resourceScopeAuthorizer
    ) {
        if (!(repository instanceof PageArtifactAtomicMutationRepository)) {
            throw new IllegalArgumentException(
                "PageArtifactController requires a PageArtifactAtomicMutationRepository implementation. "
                + "Non-atomic save paths are not supported: " + repository.getClass().getName()
            );
        }
        this.repository = repository;
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.metrics = metrics;
        this.resourceScopeAuthorizer = resourceScopeAuthorizer;
    }

    /**
     * PUT /api/v1/page-artifacts/:artifactId/document
     * <p>
     * Saves a page artifact document with optimistic concurrency control.
     * Requires If-Match header with the current documentId as ETag.
     * Returns 409 with X-Current-Version header on conflict.
     * Requires page_artifact.edit permission.
     */
    @Traced(value = "page_artifact.save", kind = Traced.SpanKind.SERVER)
    public Promise<HttpResponse> saveDocument(HttpRequest request) {
        // Validate required headers
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        Principal principal = request.getAttachment(Principal.class);
        String userId = principal != null ? principal.getName() : null;

        // Check authorization
        try {
            checkAuthorization(principal, PageArtifactPermission.EDIT);
        } catch (AccessDeniedException e) {
            LOG.warn("Authorization denied for saveDocument: {}", e.getMessage());
            return Promise.of(forbidden(e.getMessage()));
        }

        // Validate required headers
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (!tenantId.equals(principal.getTenantId())) {
            return Promise.of(forbidden("Tenant context mismatch between authenticated principal and request header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        // Extract artifactId from path
        Optional<String> artifactIdOpt = extractArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        return authorizeResourceScope(userId, tenantId, workspaceId, projectId, artifactId, PageArtifactPermission.EDIT)
            .then(() -> repository.load(tenantId, workspaceId, projectId, artifactId))
            .then($ -> request.loadBody().then(loadedBody -> {
            try {
                byte[] body = loadedBody.asArray();
                PageArtifactDocument document = objectMapper.readValue(body, PageArtifactDocument.class);

                // Validate that artifactId in path matches document
                if (!artifactId.equals(document.artifactId())) {
                    return Promise.of(badRequest(
                            "Artifact ID in path (" + artifactId + ") does not match document (" + document.artifactId() + ")"
                    ));
                }

                // Validate document structure
                PageArtifactValidator.ValidationResult validation = PageArtifactValidator.validate(document);
                if (!validation.valid()) {
                    LOG.warn("Document validation failed for artifact {}: {}", artifactId, validation.getSummary());
                    metrics.incrementCounter("yappc.page_artifact.validation_failed",
                            "tenant_id", tenantId,
                            "error_count", String.valueOf(validation.errors().size()));
                    recordAuditEvent(
                        "validation-failed",
                        tenantId,
                        workspaceId,
                        projectId,
                        artifactId,
                        userId,
                        "Validation failed with " + validation.errors().size() + " error(s)"
                    ).whenException(ex -> LOG.error("Failed to persist validation audit event", ex));
                    return Promise.of(unprocessableEntity(
                            "Document validation failed: " + String.join(", ", validation.errors())
                    ));
                }
                if (validation.hasWarnings()) {
                    LOG.info("Document validation warnings for artifact {}: {}", artifactId, String.join(", ", validation.warnings()));
                    metrics.incrementCounter("yappc.page_artifact.validation_warnings",
                            "tenant_id", tenantId,
                            "warning_count", String.valueOf(validation.warnings().size()));
                }

                // Validate If-Match header for optimistic concurrency
                String ifMatch = request.getHeader(HttpHeaders.of("If-Match"));
                if (ifMatch == null || !ifMatch.equals(document.documentId())) {
                    return Promise.of(badRequest("If-Match header must match document.documentId"));
                }

                LOG.info("Saving page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                        tenantId, workspaceId, projectId, artifactId);

                String auditActor = userId == null || userId.isBlank() ? "system" : userId;
                String auditSummary = "Document persisted";

                // Always use the atomic path — constructor guarantees PageArtifactAtomicMutationRepository
                PageArtifactAtomicMutationRepository atomicRepository =
                    (PageArtifactAtomicMutationRepository) repository;
                Promise<PageArtifactDocument> savePromise = atomicRepository.saveWithAudit(
                    tenantId,
                    workspaceId,
                    projectId,
                    document,
                    "saved",
                    auditActor,
                    auditSummary
                );

                return savePromise
                        .then(persistedDocument -> {
                            metrics.incrementCounter("yappc.page_artifact.saved",
                                    "tenant_id", tenantId,
                                    "sync_status", persistedDocument.syncStatus(),
                                    "trust_level", persistedDocument.trustLevel());
                            LOG.info("Saved page artifact: tenant={} workspace={} project={} artifactId={} version={}",
                                    tenantId, workspaceId, projectId, artifactId, persistedDocument.documentId());
                            return Promise.of(ResponseBuilder.ok()
                                .header("ETag", persistedDocument.documentId())
                                .json(Map.of(
                                        "artifactId", artifactId,
                                        "documentId", persistedDocument.documentId(),
                                        "syncStatus", persistedDocument.syncStatus()
                                ))
                                .build());
                        })
                    .then(
                        response -> Promise.of(response),
                        ex -> {
                            if (ex instanceof PageArtifactConflictException) {
                            PageArtifactConflictException conflict = (PageArtifactConflictException) ex;
                            LOG.warn("Conflict saving artifact {}: remote version={}", artifactId, conflict.remoteVersion());
                            metrics.incrementCounter("yappc.page_artifact.conflict",
                                    "tenant_id", tenantId);
                                recordAuditEvent(
                                    "conflict",
                                    tenantId,
                                    workspaceId,
                                    projectId,
                                    artifactId,
                                    userId,
                                    "Conflict detected. Remote version=" + conflict.remoteVersion()
                                ).whenException(auditEx -> LOG.error("Failed to persist conflict audit event", auditEx));
                            return Promise.of(ResponseBuilder.conflict()
                                .header("X-Current-Version", conflict.remoteVersion())
                                .json(Map.of(
                                    "error", "Conflict",
                                    "message", conflict.getMessage(),
                                    "remoteVersion", conflict.remoteVersion()
                                ))
                                .build());
                            }
                            LOG.error("Failed to save page artifact", ex);
                            metrics.recordError("yappc.page_artifact.save_failed", toException(ex),
                                    Map.of("tenant_id", tenantId));
                            return Promise.of(ResponseBuilder.internalServerError()
                                .json(Map.of(
                                    "error", "Internal Server Error",
                                    "message", "Failed to save page artifact"
                                ))
                                .build());
                        });

            } catch (Exception e) {
                LOG.error("Failed to parse request body", e);
                return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
            }
                }))
                .then(
                        response -> Promise.of(response),
                        ex -> {
                            if (ex instanceof AccessDeniedException accessDeniedException) {
                                return Promise.of(forbidden(accessDeniedException.getMessage()));
                            }
                            return Promise.ofException(ex);
                        });
    }

    /**
     * GET /api/v1/page-artifacts/:artifactId/document
     * <p>
     * Loads a page artifact document by artifact ID.
     * Returns the document with ETag header for optimistic concurrency.
     * Requires page_artifact.read permission.
     */
    @Traced(value = "page_artifact.load", kind = Traced.SpanKind.SERVER)
    public Promise<HttpResponse> loadDocument(HttpRequest request) {
        // Validate required headers
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        Principal principal = request.getAttachment(Principal.class);
        String userId = principal != null ? principal.getName() : null;

        // Check authorization
        try {
            checkAuthorization(principal, PageArtifactPermission.READ);
        } catch (AccessDeniedException e) {
            LOG.warn("Authorization denied for loadDocument: {}", e.getMessage());
            return Promise.of(forbidden(e.getMessage()));
        }

        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (!tenantId.equals(principal.getTenantId())) {
            return Promise.of(forbidden("Tenant context mismatch between authenticated principal and request header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        // Extract artifactId from path
        Optional<String> artifactIdOpt = extractArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        LOG.info("Loading page artifact: tenant={}, workspace={}, project={}, artifactId={}",
                tenantId, workspaceId, projectId, artifactId);

        return authorizeResourceScope(userId, tenantId, workspaceId, projectId, artifactId, PageArtifactPermission.READ)
                .then(() -> repository.load(tenantId, workspaceId, projectId, artifactId))
                .then(document -> {
                    if (document == null) {
                        metrics.incrementCounter("yappc.page_artifact.not_found",
                                "tenant_id", tenantId);
                        return Promise.of(ResponseBuilder.notFound()
                                .json(Map.of(
                                        "error", "Not Found",
                                        "message", "Page artifact not found: " + artifactId
                                ))
                                .build());
                    }

                    metrics.incrementCounter("yappc.page_artifact.loaded",
                            "tenant_id", tenantId,
                            "sync_status", document.syncStatus(),
                            "trust_level", document.trustLevel());
                    HttpResponse response = ResponseBuilder.ok()
                            .header("ETag", document.documentId())
                            .json(document)
                            .build();
                    return recordAuditEvent(
                            "loaded",
                            tenantId,
                            workspaceId,
                            projectId,
                            artifactId,
                            userId,
                            "Document loaded with version " + document.documentId()
                    ).map($ -> response);
                })
                .then(
                    response -> Promise.of(response),
                    ex -> {
                        if (ex instanceof AccessDeniedException accessDeniedException) {
                            return Promise.of(forbidden(accessDeniedException.getMessage()));
                        }
                        LOG.error("Failed to load page artifact", ex);
                        metrics.recordError("yappc.page_artifact.load_failed", toException(ex),
                                Map.of("tenant_id", tenantId));
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(Map.of(
                                        "error", "Internal Server Error",
                                        "message", "Failed to load page artifact: " + ex.getMessage()
                                ))
                                .build());
                        });
    }

    /**
     * GET /api/v1/page-artifacts/:artifactId/operation-log/export
     * <p>
     * Exports a deterministic replay snapshot for a page artifact operation log.
     * Requires page_artifact.read permission.
     */
    @Traced(value = "page_artifact.operation_log_export", kind = Traced.SpanKind.SERVER)
    public Promise<HttpResponse> exportOperationLog(HttpRequest request) {
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        Principal principal = request.getAttachment(Principal.class);
        String userId = principal != null ? principal.getName() : null;

        try {
            checkAuthorization(principal, PageArtifactPermission.READ);
        } catch (AccessDeniedException e) {
            LOG.warn("Authorization denied for exportOperationLog: {}", e.getMessage());
            return Promise.of(forbidden(e.getMessage()));
        }

        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (!tenantId.equals(principal.getTenantId())) {
            return Promise.of(forbidden("Tenant context mismatch between authenticated principal and request header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        Optional<String> artifactIdOpt = extractOperationLogExportArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        return authorizeResourceScope(userId, tenantId, workspaceId, projectId, artifactId, PageArtifactPermission.READ)
                .then(() -> repository.load(tenantId, workspaceId, projectId, artifactId))
                .then(document -> {
                    if (document == null) {
                        metrics.incrementCounter("yappc.page_artifact.not_found",
                                "tenant_id", tenantId);
                        return Promise.of(ResponseBuilder.notFound()
                                .json(Map.of(
                                        "error", "Not Found",
                                        "message", "Page artifact not found: " + artifactId
                                ))
                                .build());
                    }

                    PageArtifactDocument.OperationLogExport export = buildOperationLogExport(document);
                    metrics.incrementCounter(
                            "yappc.page_artifact.operation_log_exported",
                            "tenant_id", tenantId,
                            "record_count", String.valueOf(export.records().size())
                    );

                    HttpResponse response = ResponseBuilder.ok()
                            .header("ETag", document.documentId())
                            .json(export)
                            .build();
                    return recordAuditEvent(
                            "operation-log-exported",
                            tenantId,
                            workspaceId,
                            projectId,
                            artifactId,
                            userId,
                            "Operation log exported with " + export.records().size() + " record(s)"
                    ).map($ -> response);
                })
                .then(
                        response -> Promise.of(response),
                        ex -> {
                            if (ex instanceof AccessDeniedException accessDeniedException) {
                                return Promise.of(forbidden(accessDeniedException.getMessage()));
                            }
                            LOG.error("Failed to export page artifact operation log", ex);
                            metrics.recordError("yappc.page_artifact.operation_log_export_failed", toException(ex),
                                    Map.of("tenant_id", tenantId));
                            return Promise.of(ResponseBuilder.internalServerError()
                                    .json(Map.of(
                                            "error", "Internal Server Error",
                                            "message", "Failed to export page artifact operation log"
                                    ))
                                    .build());
                        });
    }

    /**
     * POST /api/v1/page-artifacts/:artifactId/review-decisions
     * <p>
     * Persists an automation/governance review decision against an existing
     * page artifact lineage record and writes the matching audit event through
     * the atomic repository path.
     */
    @Traced(value = "page_artifact.review_decision", kind = Traced.SpanKind.SERVER)
    public Promise<HttpResponse> recordReviewDecision(HttpRequest request) {
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        Principal principal = request.getAttachment(Principal.class);
        String userId = principal != null ? principal.getName() : null;

        try {
            checkAuthorization(principal, PageArtifactPermission.EDIT);
        } catch (AccessDeniedException e) {
            LOG.warn("Authorization denied for recordReviewDecision: {}", e.getMessage());
            return Promise.of(forbidden(e.getMessage()));
        }

        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Tenant-ID header"));
        }
        if (!tenantId.equals(principal.getTenantId())) {
            return Promise.of(forbidden("Tenant context mismatch between authenticated principal and request header"));
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Workspace-ID header"));
        }
        if (projectId == null || projectId.isBlank()) {
            return Promise.of(badRequest("Missing required X-Project-ID header"));
        }

        Optional<String> artifactIdOpt = extractReviewDecisionArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        return authorizeResourceScope(userId, tenantId, workspaceId, projectId, artifactId, PageArtifactPermission.EDIT)
                .then(() -> request.loadBody())
                .then(body -> {
                    try {
                        ReviewDecisionRequest decisionRequest =
                                objectMapper.readValue(body.asArray(), ReviewDecisionRequest.class);
                        if (decisionRequest.actionId() == null || decisionRequest.actionId().isBlank()) {
                            return Promise.of(badRequest("actionId is required"));
                        }
                        if (!"accepted".equals(decisionRequest.decision()) && !"rejected".equals(decisionRequest.decision())) {
                            return Promise.of(badRequest("decision must be accepted or rejected"));
                        }

                        return repository.load(tenantId, workspaceId, projectId, artifactId)
                                .then(document -> {
                                    if (document == null) {
                                        return Promise.of(ResponseBuilder.notFound()
                                                .json(Map.of(
                                                        "error", "Not Found",
                                                        "message", "Page artifact not found: " + artifactId
                                                ))
                                                .build());
                                    }

                                    PageArtifactDocument updatedDocument =
                                            withReviewDecision(document, decisionRequest.actionId(), decisionRequest.decision(), decisionRequest.evidence());
                                    if (updatedDocument == document) {
                                        return Promise.of(ResponseBuilder.notFound()
                                                .json(Map.of(
                                                        "error", "Not Found",
                                                        "message", "Governance action not found: " + decisionRequest.actionId()
                                                ))
                                                .build());
                                    }

                                    String actor = userId == null || userId.isBlank() ? "system" : userId;
                                    String summary = "Review decision " + decisionRequest.decision()
                                            + " recorded for governance action " + decisionRequest.actionId();
                                    PageArtifactAtomicMutationRepository atomicRepository =
                                            (PageArtifactAtomicMutationRepository) repository;

                                    return atomicRepository.saveWithAudit(
                                                    tenantId,
                                                    workspaceId,
                                                    projectId,
                                                    updatedDocument,
                                                    "governance-review-decision",
                                                    actor,
                                                    summary
                                            )
                                            .map(persisted -> {
                                                PageArtifactDocument.GovernanceLineage lineage =
                                                        findLineage(persisted, decisionRequest.actionId()).orElseThrow();
                                                Instant decidedAt = Instant.now();
                                                metrics.incrementCounter(
                                                        "yappc.page_artifact.review_decision",
                                                        "tenant_id", tenantId,
                                                        "decision", decisionRequest.decision()
                                                );
                                                LOG.info(
                                                        "Recorded page artifact review decision: tenant={} workspace={} project={} artifact={} action={} decision={} actor={}",
                                                        tenantId,
                                                        workspaceId,
                                                        projectId,
                                                        artifactId,
                                                        decisionRequest.actionId(),
                                                        decisionRequest.decision(),
                                                        actor
                                                );
                                                return ResponseBuilder.ok()
                                                        .json(Map.of(
                                                                "artifactId", artifactId,
                                                                "documentId", persisted.documentId(),
                                                                "actionId", decisionRequest.actionId(),
                                                                "decision", decisionRequest.decision(),
                                                                "actor", actor,
                                                                "decidedAt", decidedAt.toString(),
                                                                "confidence", lineage.confidence(),
                                                                "changedNodeIds", lineage.affectedNodeIds(),
                                                                "reversible", lineage.reversible(),
                                                                "evidence", lineage.evidence()
                                                        ))
                                                        .build();
                                            });
                                });
                    } catch (Exception e) {
                        LOG.error("Failed to parse review decision request", e);
                        return Promise.of(badRequest("Invalid request body: " + e.getMessage()));
                    }
                })
                .then(
                        response -> Promise.of(response),
                        ex -> {
                            if (ex instanceof AccessDeniedException accessDeniedException) {
                                return Promise.of(forbidden(accessDeniedException.getMessage()));
                            }
                            LOG.error("Failed to record review decision", ex);
                            metrics.recordError("yappc.page_artifact.review_decision_failed", toException(ex),
                                    Map.of("tenant_id", tenantId));
                            return Promise.of(ResponseBuilder.internalServerError()
                                    .json(Map.of(
                                            "error", "Internal Server Error",
                                            "message", "Failed to record review decision"
                                    ))
                                    .build());
                        });
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private Optional<String> extractArtifactId(String path) {
        Matcher matcher = ARTIFACT_ID_PATTERN.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> extractReviewDecisionArtifactId(String path) {
        Matcher matcher = REVIEW_DECISION_ARTIFACT_ID_PATTERN.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private Optional<String> extractOperationLogExportArtifactId(String path) {
        Matcher matcher = OPERATION_LOG_EXPORT_ARTIFACT_ID_PATTERN.matcher(path);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private PageArtifactDocument.OperationLogExport buildOperationLogExport(PageArtifactDocument document) {
        List<PageArtifactDocument.OperationRecord> records = document.operationLog().stream()
                .sorted(Comparator.comparing(
                        PageArtifactDocument.OperationRecord::createdAt,
                        Comparator.nullsLast(String::compareTo)
                ))
                .toList();
        Map<String, Long> byOperation = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (PageArtifactDocument.OperationRecord record : records) {
            incrementCount(byOperation, record.operation());
            incrementCount(byStatus, record.status());
        }

        PageArtifactDocument.OperationRecord latest = records.isEmpty() ? null : records.get(records.size() - 1);
        return new PageArtifactDocument.OperationLogExport(
                1,
                document.artifactId(),
                document.documentId(),
                Instant.now().toString(),
                latest != null ? latest.id() : null,
                new PageArtifactDocument.OperationLogSummary(
                        records.size(),
                        byOperation,
                        byStatus,
                        latest != null ? latest.createdAt() : null
                ),
                records
        );
    }

    private void incrementCount(Map<String, Long> counts, String key) {
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        counts.merge(safeKey, 1L, Long::sum);
    }

    private Optional<PageArtifactDocument.GovernanceLineage> findLineage(
            PageArtifactDocument document,
            String actionId
    ) {
        return document.aiChangeRecords().stream()
                .map(PageArtifactDocument.GovernanceRecord::lineage)
                .filter(lineage -> actionId.equals(lineage.actionId()))
                .findFirst();
    }

    private PageArtifactDocument withReviewDecision(
            PageArtifactDocument document,
            String actionId,
            String decision,
            List<String> reviewEvidence
    ) {
        boolean[] changed = new boolean[] { false };
        List<PageArtifactDocument.GovernanceRecord> updatedRecords = document.aiChangeRecords().stream()
                .map(record -> {
                    PageArtifactDocument.GovernanceLineage lineage = record.lineage();
                    if (!actionId.equals(lineage.actionId())) {
                        return record;
                    }

                    changed[0] = true;
                    List<String> evidence = reviewEvidence == null || reviewEvidence.isEmpty()
                            ? lineage.evidence()
                            : List.copyOf(reviewEvidence);
                    return new PageArtifactDocument.GovernanceRecord(
                            record.artifactId(),
                            record.documentId(),
                            new PageArtifactDocument.GovernanceLineage(
                                    lineage.actionId(),
                                    lineage.hookKind(),
                                    lineage.reason(),
                                    lineage.confidence(),
                                    lineage.reversible(),
                                    decision,
                                    lineage.affectedNodeIds(),
                                    lineage.appliedAt(),
                                    evidence
                            )
                    );
                })
                .toList();

        if (!changed[0]) {
            return document;
        }

        return new PageArtifactDocument(
                document.artifactId(),
                document.documentId(),
                document.name(),
                document.createdBy(),
                document.createdAt(),
                Instant.now(),
                document.syncStatus(),
                document.trustLevel(),
                document.dataClassification(),
                document.builderDocument(),
                document.validationSummary(),
                updatedRecords,
                document.source(),
                document.residualIslandCount(),
                document.roundTripFidelity(),
                document.operationLog()
        );
    }

    private HttpResponse badRequest(String message) {
        return ResponseBuilder.badRequest()
                .json(Map.of(
                        "error", "Bad Request",
                        "message", message
                ))
                .build();
    }

    private HttpResponse forbidden(String message) {
        return ResponseBuilder.forbidden()
                .json(Map.of(
                        "error", "Forbidden",
                        "message", message
                ))
                .build();
    }

    private HttpResponse unprocessableEntity(String message) {
        return ResponseBuilder.status(422)
                .json(Map.of(
                        "error", "Unprocessable Entity",
                        "message", message
                ))
                .build();
    }

    private void checkAuthorization(@Nullable Principal principal, String requiredPermission) {
        if (principal == null) {
            throw new AccessDeniedException("Authenticated principal is required");
        }
        List<String> principalRoles = principal.getRoles();
        if (principalRoles == null || principalRoles.isEmpty()) {
            throw new AccessDeniedException("Principal roles are required for authorization");
        }

        User user = new User(principal.getName(), principal.getName(), Set.copyOf(principalRoles));
        boolean hasPermission = authorizationService.hasPermission(user, requiredPermission);
        if (!hasPermission) {
            throw new AccessDeniedException(
                    "User does not have required permission: " + requiredPermission
            );
        }
        LOG.debug("Authorization check passed for user={} permission={}", principal.getName(), requiredPermission);
    }

    private Exception toException(Throwable throwable) {
        return throwable instanceof Exception exception
                ? exception
                : new RuntimeException(throwable);
    }

    private Promise<Void> authorizeResourceScope(
            @Nullable String userId,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String requiredPermission
    ) {
        return resourceScopeAuthorizer.authorize(
                userId,
                tenantId,
                workspaceId,
                projectId,
                artifactId,
                requiredPermission
        );
    }

    private Promise<Void> recordAuditEvent(
            String action,
            String tenantId,
            String workspaceId,
            String projectId,
            String artifactId,
            @Nullable String userId,
            String summary
    ) {
        String actor = userId == null || userId.isBlank() ? "system" : userId;
        LOG.info(
                "Audit: page-artifact {} tenant={} workspace={} project={} artifact={} actor={} summary={}",
                action,
                tenantId,
                workspaceId,
                projectId,
                artifactId,
                actor,
                summary
        );

        return auditRepository.record(action, tenantId, workspaceId, projectId, artifactId, actor, summary)
                .whenException(ex -> {
                    LOG.error("Failed to persist page artifact audit event", ex);
                    metrics.recordError("yappc.page_artifact.audit_write_failed", toException(ex),
                        Map.of(
                            "tenant_id", tenantId,
                            "action", action
                        ));
                });
    }
}
