package com.ghatana.yappc.domain.artifact;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Typed request DTO for residual island analysis
 * @doc.layer domain
 * @doc.pattern DTO
 *
 * P0: Replaces raw Map<String, Object> parsing with typed contract.
 * Ensures type safety and contract validation for residual analysis requests.
 */
public record ResidualAnalysisRequest(
    String projectId,
    String tenantId,
    String workspaceId,
    List<ResidualIslandDto> residualIslands
) {
    public ResidualAnalysisRequest {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (residualIslands == null) {
            residualIslands = List.of();
        }
    }
}
