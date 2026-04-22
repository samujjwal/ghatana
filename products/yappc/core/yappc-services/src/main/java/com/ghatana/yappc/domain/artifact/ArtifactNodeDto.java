package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Lightweight DTO for artifact node ingestion from TypeScript scanner
 * @doc.layer domain
 * @doc.pattern DataTransferObject
 */
public record ArtifactNodeDto(
    String id,
    String type,
    String name,
    String filePath,
    String content,
    Map<String, Object> properties,
    List<String> tags,
    String tenantId,
    String projectId
) {
}
