package com.ghatana.yappc.services.artifact;

import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Explicit server-derived scope for artifact graph service operations.
 *              P0: Now includes workspaceId for complete tenant/workspace/project isolation.
 * @doc.layer service
 * @doc.pattern DataTransferObject
 */
public record ArtifactRequestScope(
        String projectId,
        String tenantId,
        String workspaceId
) {
    public ArtifactRequestScope {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
    }

}