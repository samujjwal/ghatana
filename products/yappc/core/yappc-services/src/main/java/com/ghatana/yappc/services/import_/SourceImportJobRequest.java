package com.ghatana.yappc.services.import_;

import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Request DTO for submitting a source import job
 * @doc.layer service
 * @doc.pattern DataTransferObject
 */
public record SourceImportJobRequest(
    String projectId,
    String workspaceId,
    String tenantId,
    String sourceUrl,
    String sourceType,
    String submittedBy,
    Map<String, String> metadata
) {
    public SourceImportJobRequest {
        Objects.requireNonNull(projectId, "projectId is required");
        Objects.requireNonNull(workspaceId, "workspaceId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(sourceUrl, "sourceUrl is required");
        Objects.requireNonNull(sourceType, "sourceType is required");
        Objects.requireNonNull(submittedBy, "submittedBy is required");
    }
}
