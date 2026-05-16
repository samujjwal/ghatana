package com.ghatana.yappc.domain.artifact;

import java.util.Map;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Data transfer object for artifact nodes from the TypeScript artifact compiler scanner.
 * @doc.layer domain
 * @doc.pattern DTO
 */
public record ArtifactNodeDto(
    String id,
    String type,
    String tenantId,
    String projectId,
    String name,
    Map<String, Object> properties,
    Set<String> tags
) {
    public ArtifactNodeDto {
        id = id != null ? id : "";
        type = type != null ? type : "unknown";
        tenantId = tenantId != null ? tenantId : "";
        projectId = projectId != null ? projectId : "";
        name = name != null ? name : "";
        properties = properties != null ? Map.copyOf(properties) : Map.of();
        tags = tags != null ? Set.copyOf(tags) : Set.of();
    }
}
