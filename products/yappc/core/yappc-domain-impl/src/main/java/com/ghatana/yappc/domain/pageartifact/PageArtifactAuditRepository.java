package com.ghatana.yappc.domain.pageartifact;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * @doc.type interface
 * @doc.purpose Persists page artifact audit events for compliance and traceability
 * @doc.layer product
 * @doc.pattern Repository Contract
 */
public interface PageArtifactAuditRepository {

    Promise<Void> record(
            @NotNull String action,
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String artifactId,
            @NotNull String actor,
            @NotNull String summary
    );
}
