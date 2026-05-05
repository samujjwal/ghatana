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
import java.util.Set;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Arrays;
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

    private final PageArtifactRepository repository;
    private final PageArtifactAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final SyncAuthorizationService authorizationService;
    private final MetricsCollector metrics;
    private final PageArtifactResourceScopeAuthorizer resourceScopeAuthorizer;

    /**
     * Creates a new PageArtifactController.
     *
     * @param repository The page artifact repository
     * @param objectMapper JSON object mapper
     * @param authorizationService Authorization service for permission checks
     * @param metrics Metrics collector for observability
     */
    public PageArtifactController(
            @NotNull PageArtifactRepository repository,
            @NotNull PageArtifactAuditRepository auditRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull SyncAuthorizationService authorizationService,
            @NotNull MetricsCollector metrics
    ) {
        this(repository, auditRepository, objectMapper, authorizationService, metrics, PageArtifactResourceScopeAuthorizer.allowAll());
    }

    public PageArtifactController(
            @NotNull PageArtifactRepository repository,
            @NotNull PageArtifactAuditRepository auditRepository,
            @NotNull ObjectMapper objectMapper,
            @NotNull SyncAuthorizationService authorizationService,
            @NotNull MetricsCollector metrics,
            @NotNull PageArtifactResourceScopeAuthorizer resourceScopeAuthorizer
    ) {
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
        try {
            enforceResourceScope(request, workspaceId, projectId);
        } catch (AccessDeniedException e) {
            return Promise.of(forbidden(e.getMessage()));
        }

        // Extract artifactId from path
        Optional<String> artifactIdOpt = extractArtifactId(request.getPath());
        if (artifactIdOpt.isEmpty()) {
            return Promise.of(badRequest("Artifact ID is required"));
        }
        String artifactId = artifactIdOpt.get();

        return authorizeResourceScope(userId, tenantId, workspaceId, projectId, artifactId, PageArtifactPermission.EDIT)
            .then(() -> repository.load(tenantId, workspaceId, projectId, artifactId))
            .then(existingDocument -> request.loadBody().then(loadedBody -> {
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
                            "artifact_id", artifactId,
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
                            "artifact_id", artifactId,
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

                Promise<PageArtifactDocument> savePromise;
                if (repository instanceof PageArtifactAtomicMutationRepository atomicRepository) {
                    savePromise = atomicRepository.saveWithAudit(
                        tenantId,
                        workspaceId,
                        projectId,
                        document,
                        "saved",
                        auditActor,
                        auditSummary
                    );
                } else {
                    savePromise = repository.save(tenantId, workspaceId, projectId, document);
                }

                return savePromise
                        .then(persistedDocument -> {
                            metrics.incrementCounter("yappc.page_artifact.saved",
                                    "tenant_id", tenantId,
                                    "artifact_id", artifactId,
                                    "sync_status", persistedDocument.syncStatus(),
                                    "trust_level", persistedDocument.trustLevel());
                            HttpResponse response = ResponseBuilder.ok()
                                .header("ETag", persistedDocument.documentId())
                                .json(Map.of(
                                        "artifactId", artifactId,
                                        "documentId", persistedDocument.documentId(),
                                        "syncStatus", persistedDocument.syncStatus()
                                ))
                                .build();
                            if (repository instanceof PageArtifactAtomicMutationRepository) {
                                return Promise.of(response);
                            }
                            return recordAuditEvent(
                                    "saved",
                                    tenantId,
                                    workspaceId,
                                    projectId,
                                    artifactId,
                                    userId,
                                    "Document persisted with version " + persistedDocument.documentId()
                        ).then(
                            $ -> Promise.of(response),
                            auditEx -> rollbackPersistedDocument(
                                tenantId,
                                workspaceId,
                                projectId,
                                artifactId,
                                existingDocument,
                                persistedDocument
                            ).map($ -> ResponseBuilder.internalServerError()
                                .json(Map.of(
                                    "error", "Internal Server Error",
                                    "message", "Failed to persist audit trail for page artifact mutation"
                                ))
                                .build())
                        );
                        })
                    .then(
                        response -> Promise.of(response),
                        ex -> {
                            if (ex instanceof PageArtifactConflictException) {
                            PageArtifactConflictException conflict = (PageArtifactConflictException) ex;
                            LOG.warn("Conflict saving artifact {}: remote version={}", artifactId, conflict.remoteVersion());
                            metrics.incrementCounter("yappc.page_artifact.conflict",
                                    "tenant_id", tenantId,
                                    "artifact_id", artifactId,
                                    "remote_version", conflict.remoteVersion());
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
                                    Map.of("tenant_id", tenantId, "artifact_id", artifactId));
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
        try {
            enforceResourceScope(request, workspaceId, projectId);
        } catch (AccessDeniedException e) {
            return Promise.of(forbidden(e.getMessage()));
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
                                "tenant_id", tenantId,
                                "artifact_id", artifactId);
                        return Promise.of(ResponseBuilder.notFound()
                                .json(Map.of(
                                        "error", "Not Found",
                                        "message", "Page artifact not found: " + artifactId
                                ))
                                .build());
                    }

                    metrics.incrementCounter("yappc.page_artifact.loaded",
                            "tenant_id", tenantId,
                            "artifact_id", artifactId,
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
                                Map.of("tenant_id", tenantId, "artifact_id", artifactId, "error", ex.getMessage()));
                        return Promise.of(ResponseBuilder.internalServerError()
                                .json(Map.of(
                                        "error", "Internal Server Error",
                                        "message", "Failed to load page artifact: " + ex.getMessage()
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

    private void enforceResourceScope(
            @NotNull HttpRequest request,
            @NotNull String workspaceId,
            @NotNull String projectId
    ) {
        enforceScopeHeader(request.getHeader(HttpHeaders.of("X-Authorized-Workspace-IDs")), workspaceId, "workspace");
        enforceScopeHeader(request.getHeader(HttpHeaders.of("X-Authorized-Project-IDs")), projectId, "project");
    }

    private void enforceScopeHeader(@Nullable String allowedValuesHeader, @NotNull String requestedId, @NotNull String resourceType) {
        if (allowedValuesHeader == null || allowedValuesHeader.isBlank()) {
            throw new AccessDeniedException(
                "Missing authorized " + resourceType + " scope header"
            );
        }

        boolean allowed = Arrays.stream(allowedValuesHeader.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .anyMatch(requestedId::equals);

        if (!allowed) {
            throw new AccessDeniedException(
                    "User is not authorized for requested " + resourceType + ": " + requestedId
            );
        }
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
                            "workspace_id", workspaceId,
                            "project_id", projectId,
                            "artifact_id", artifactId,
                            "action", action
                        ));
                });
    }

                private Promise<Void> rollbackPersistedDocument(
                    @NotNull String tenantId,
                    @NotNull String workspaceId,
                    @NotNull String projectId,
                    @NotNull String artifactId,
                    @Nullable PageArtifactDocument priorDocument,
                    @NotNull PageArtifactDocument persistedDocument
                ) {
                LOG.error(
                    "Audit write failed after mutation. Rolling back page artifact state: tenant={} workspace={} project={} artifact={} persistedVersion={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    artifactId,
                    persistedDocument.documentId()
                );

                Promise<Void> rollbackPromise;
                if (priorDocument == null) {
                    rollbackPromise = repository.delete(tenantId, workspaceId, projectId, artifactId);
                } else {
                    rollbackPromise = repository.delete(tenantId, workspaceId, projectId, artifactId)
                        .then(() -> repository.save(tenantId, workspaceId, projectId, priorDocument))
                        .map($ -> null);
                }

                return rollbackPromise.whenException(ex -> {
                    LOG.error("Rollback failed for page artifact mutation after audit failure", ex);
                    metrics.recordError("yappc.page_artifact.rollback_failed", toException(ex),
                        Map.of(
                            "tenant_id", tenantId,
                            "workspace_id", workspaceId,
                            "project_id", projectId,
                            "artifact_id", artifactId
                        ));
                });
                }
}
