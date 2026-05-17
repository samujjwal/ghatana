package com.ghatana.yappc.domain.artifact;

import java.util.List;

/**
 * @doc.type class
 * @doc.purpose Typed request DTO for artifact graph queries with pagination and scope parameters
 * @doc.layer domain
 * @doc.pattern DTO
 * 
 * P0: Replaces raw map parsing in controller with type-safe request DTO.
 * Ensures all query parameters are validated and typed at the boundary.
 */
public record ArtifactGraphQueryRequest(
    /**
     * Query type: orphaned, dependencies, dependents, stats
     */
    String queryType,
    
    /**
     * Seed node IDs for dependency/dependent queries
     */
    List<String> seedNodeIds,
    
    /**
     * Pagination cursor for fetching next page
     */
    String cursor,
    
    /**
     * Maximum number of items per page (1-1000)
     */
    Integer limit,
    
    /**
     * Snapshot ID to query specific snapshot version
     */
    String snapshotId,
    
    /**
     * Whether to include unresolved edges in the result
     */
    Boolean includeUnresolvedEdges
) {
    /**
     * Validate and sanitize the request parameters.
     * @throws IllegalArgumentException if validation fails
     */
    public ArtifactGraphQueryRequest {
        if (queryType == null || queryType.isBlank()) {
            throw new IllegalArgumentException("queryType is required");
        }
        
        if (limit != null && (limit < 1 || limit > 1000)) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        
        // Set defaults for optional parameters
        if (limit == null) {
            limit = 100;
        }
        if (includeUnresolvedEdges == null) {
            includeUnresolvedEdges = false;
        }
    }
    
    /**
     * Create a request from raw map (for backward compatibility during migration).
     */
    public static ArtifactGraphQueryRequest fromMap(java.util.Map<String, Object> map) {
        String queryType = (String) map.get("queryType");
        @SuppressWarnings("unchecked")
        List<String> seedNodeIds = (List<String>) map.getOrDefault("seedNodeIds", List.of());
        String cursor = (String) map.get("cursor");
        Integer limit = map.get("limit") != null ? ((Number) map.get("limit")).intValue() : null;
        String snapshotId = (String) map.get("snapshotId");
        Boolean includeUnresolvedEdges = (Boolean) map.get("includeUnresolvedEdges");
        
        return new ArtifactGraphQueryRequest(queryType, seedNodeIds, cursor, limit, snapshotId, includeUnresolvedEdges);
    }
}
