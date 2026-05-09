/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import io.activej.http.HttpRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical authorization service for YAPPC with resource-level permission checks.
 *
 * <p>Enforces workspace/project/artifact isolation by validating that the authenticated
 * principal has the required permissions for the requested resource scope. This service
 * replaces frontend-derived scope with backend-enforced authorization.
 *
 * <p>Usage in controllers:
 * <pre>{@code
 * // Get principal from request (attached by YappcApiAuthFilter)
 * Principal principal = request.getAttached(Principal.class);
 * if (principal == null) {
 *   return Promise.of(unauthorized());
 * }
 *
 * // Authorize workspace access
 * authorizationService.authorizeWorkspaceAccess(principal, workspaceId, Permission.WORKSPACE_READ);
 *
 * // Authorize project access
 * authorizationService.authorizeProjectAccess(principal, tenantId, workspaceId, projectId, Permission.PROJECT_READ);
 *
 * // Authorize admin-only routes
 * authorizationService.authorizeAdminAccess(principal);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical authorization service with resource-level permission checks for YAPPC
 * @doc.layer api
 * @doc.pattern Service
 */
public final class YappcAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(YappcAuthorizationService.class);

    private final SyncAuthorizationService authorizationService;

    /**
     * Creates a new YappcAuthorizationService.
     *
     * @param authorizationService the platform authorization service
     */
    public YappcAuthorizationService(@NotNull SyncAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Authorizes workspace-level access.
     *
     * @param principal the authenticated principal
     * @param workspaceId the workspace ID
     * @param permission the required permission
     * @throws AccessDeniedException if the principal lacks the required permission
     */
    public void authorizeWorkspaceAccess(
            @NotNull Principal principal,
            @NotNull String workspaceId,
            @NotNull String permission
    ) {
        if (!authorizationService.hasPermission(toUser(principal), permission)) {
            log.warn(
                "Workspace access denied: principal={}, workspaceId={}, requiredPermission={}",
                principal.getName(),
                workspaceId,
                permission
            );
            throw new AccessDeniedException(
                String.format(
                    "Principal %s does not have permission %s for workspace %s",
                    principal.getName(),
                    permission,
                    workspaceId
                )
            );
        }
        log.debug(
            "Workspace access granted: principal={}, workspaceId={}, permission={}",
            principal.getName(),
            workspaceId,
            permission
        );
    }

    /**
     * Authorizes project-level access with tenant/workspace isolation.
     *
     * <p>Validates that:
     * <ol>
     *   <li>The principal has the required project permission</li>
     *   <li>The principal's tenant matches the request tenant (if provided)</li>
     *   <li>The principal has workspace access (if workspace ID provided)</li>
     * </ol>
     *
     * @param principal the authenticated principal
     * @param tenantId the tenant ID from the request
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param permission the required permission
     * @throws AccessDeniedException if the principal lacks the required permission or scope mismatch
     */
    public void authorizeProjectAccess(
            @NotNull Principal principal,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String permission
    ) {
        // Validate tenant scope
        if (!principal.getTenantId().equals(tenantId)) {
            log.warn(
                "Tenant scope mismatch: principalTenant={}, requestTenant={}, projectId={}",
                principal.getTenantId(),
                tenantId,
                projectId
            );
            throw new AccessDeniedException(
                String.format(
                    "Principal %s tenant %s does not match request tenant %s for project %s",
                    principal.getName(),
                    principal.getTenantId(),
                    tenantId,
                    projectId
                )
            );
        }

        // Validate workspace access
        authorizeWorkspaceAccess(principal, workspaceId, Permission.WORKSPACE_READ);

        // Validate project permission
        if (!authorizationService.hasPermission(toUser(principal), permission)) {
            log.warn(
                "Project access denied: principal={}, projectId={}, requiredPermission={}",
                principal.getName(),
                projectId,
                permission
            );
            throw new AccessDeniedException(
                String.format(
                    "Principal %s does not have permission %s for project %s",
                    principal.getName(),
                    permission,
                    projectId
                )
            );
        }
        log.debug(
            "Project access granted: principal={}, projectId={}, permission={}",
            principal.getName(),
            projectId,
            permission
        );
    }

    /**
     * Authorizes artifact-level access with full resource isolation.
     *
     * @param principal the authenticated principal
     * @param tenantId the tenant ID from the request
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param artifactId the artifact ID
     * @param permission the required permission
     * @throws AccessDeniedException if the principal lacks the required permission or scope mismatch
     */
    public void authorizeArtifactAccess(
            @NotNull Principal principal,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String permission
    ) {
        // Validate project scope first (includes tenant and workspace validation)
        authorizeProjectAccess(principal, tenantId, workspaceId, projectId, Permission.PROJECT_READ);

        // Validate artifact permission
        if (!authorizationService.hasPermission(toUser(principal), permission)) {
            log.warn(
                "Artifact access denied: principal={}, artifactId={}, requiredPermission={}",
                principal.getName(),
                artifactId,
                permission
            );
            throw new AccessDeniedException(
                String.format(
                    "Principal %s does not have permission %s for artifact %s",
                    principal.getName(),
                    permission,
                    artifactId
                )
            );
        }
        log.debug(
            "Artifact access granted: principal={}, artifactId={}, permission={}",
            principal.getName(),
            artifactId,
            permission
        );
    }

    /**
     * Authorizes admin-only access.
     *
     * @param principal the authenticated principal
     * @throws AccessDeniedException if the principal lacks admin permission
     */
    public void authorizeAdminAccess(@NotNull Principal principal) {
        if (!authorizationService.hasPermission(toUser(principal), Permission.ADMIN_SYSTEM)) {
            log.warn(
                "Admin access denied: principal={}",
                principal.getName()
            );
            throw new AccessDeniedException(
                String.format(
                    "Principal %s does not have admin system permission",
                    principal.getName()
                )
            );
        }
        log.debug("Admin access granted: principal={}", principal.getName());
    }

    /**
     * Converts platform Principal to platform User for authorization service.
     */
    private com.ghatana.platform.security.model.User toUser(@NotNull Principal principal) {
        return new com.ghatana.platform.security.model.User(
            principal.getName(),
            principal.getRoles(),
            principal.getTenantId()
        );
    }
}
