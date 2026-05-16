package com.ghatana.yappc.domain.artifact;

import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Typed response DTO for artifact graph queries with pagination and scope metadata
 * @doc.layer domain
 * @doc.pattern DTO
 * 
 * P3-1: Improves graph query response ergonomics by returning structured
 * items, nextCursor, totalEstimate, and scope metadata instead of generic Map<String, Object>.
 */
public record ArtifactGraphQueryResponse(
    /**
     * Query-specific data items (e.g., orphaned nodes, dependencies, stats)
     */
    Map<String, Object> items,
    
    /**
     * Cursor for fetching the next page of results (null if no more pages)
     */
    String nextCursor,
    
    /**
     * Estimated total number of items across all pages (may be approximate)
     */
    Long totalEstimate,
    
    /**
     * Scope metadata for the query (tenant, product, query type, page size)
     */
    ScopeMetadata scope
) {
    public record ScopeMetadata(
        String tenantId,
        String productId,
        String queryType,
        int pageSize,
        boolean hasMore
    ) {}
    
    /**
     * Create a response indicating no more pages available.
     */
    public static ArtifactGraphQueryResponse lastPage(
        Map<String, Object> items,
        Long totalEstimate,
        ScopeMetadata scope
    ) {
        return new ArtifactGraphQueryResponse(items, null, totalEstimate, scope);
    }
    
    /**
     * Create a response with a cursor for the next page.
     */
    public static ArtifactGraphQueryResponse withNextPage(
        Map<String, Object> items,
        String nextCursor,
        Long totalEstimate,
        ScopeMetadata scope
    ) {
        return new ArtifactGraphQueryResponse(items, nextCursor, totalEstimate, scope);
    }
}
