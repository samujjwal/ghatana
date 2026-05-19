package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;

import java.util.Objects;

/**
 * Resolves and authorizes artifact tenant/workspace/project scope.
 *
 * @doc.type class
 * @doc.purpose Extracts and validates tenant, workspace, and project scope from HTTP requests for artifact access
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class ArtifactScopeResolver {

    @FunctionalInterface
    public interface ScopeAuthorizer {
        boolean canAccess(Principal principal, String workspaceId, String projectId);
    }

    private final ScopeAuthorizer authorizer;

    public ArtifactScopeResolver() {
        this((principal, workspaceId, projectId) -> true);
    }

    public ArtifactScopeResolver(ScopeAuthorizer authorizer) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer must not be null");
    }

    public ArtifactRequestScope resolve(
            HttpRequest request,
            Principal principal,
            String requestTenantId,
            String requestProjectId) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(principal, "principal must not be null");

        String tenantId = principal.getTenantId();
        if (requestTenantId != null && !requestTenantId.isBlank() && !tenantId.equals(requestTenantId)) {
            throw new ScopeResolutionException(403, "Forbidden: tenant scope mismatch");
        }

        String workspaceId = request.getHeader(HttpHeaders.of("X-Workspace-ID"));
        String projectId = request.getHeader(HttpHeaders.of("X-Project-ID"));
        if (workspaceId == null || workspaceId.isBlank() || projectId == null || projectId.isBlank()) {
            throw new ScopeResolutionException(400, "Bad Request: missing X-Workspace-ID or X-Project-ID scope header");
        }

        if (requestProjectId != null && !requestProjectId.isBlank() && !projectId.equals(requestProjectId)) {
            throw new ScopeResolutionException(403, "Forbidden: project scope mismatch");
        }

        if (!authorizer.canAccess(principal, workspaceId, projectId)) {
            throw new ScopeResolutionException(403, "Forbidden: workspace/project scope not authorized");
        }

        return new ArtifactRequestScope(projectId, tenantId, workspaceId);
    }

    public static final class ScopeResolutionException extends RuntimeException {
        private final int statusCode;

        public ScopeResolutionException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }
}
