package com.ghatana.datacloud.plugins.knowledgegraph.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;

/**
 * Query specification for graph operations.
 * 
 * <p>Immutable query object for filtering and searching graph nodes and edges.
 * Supports filtering by types, labels, properties, and pagination.
 * 
 * <p><b>Thread Safety:</b> Immutable and thread-safe
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * GraphQuery query = GraphQuery.builder()
 *     .nodeTypes(Set.of("CLASS", "SERVICE"))
 *     .labels(Set.of("java"))
 *     .propertyFilters(Map.of("package", "com.example"))
 *     .limit(100)
 *     .build();
 * }</pre>
 * 
 * @doc.type record
 * @doc.purpose Graph query specification
 * @doc.layer domain
 * @doc.pattern Query Object
 */
@Value
@Builder(toBuilder = true)
public class GraphQuery {
    
    /**
     * Filter by node types.
     * If null or empty, all types are included.
     */
    Set<String> nodeTypes;
    
    /**
     * Filter by relationship types.
     * If null or empty, all types are included.
     */
    Set<String> relationshipTypes;
    
    /**
     * Filter by node labels.
     * If null or empty, all labels are included.
     */
    Set<String> labels;
    
    /**
     * Filter by property key-value pairs.
     * All specified properties must match (AND logic).
     */
    Map<String, Object> propertyFilters;
    
    /**
     * Source node ID for edge queries.
     * If specified, only edges from this node are returned.
     */
    String sourceNodeId;
    
    /**
     * Target node ID for edge queries.
     * If specified, only edges to this node are returned.
     */
    String targetNodeId;
    
    /**
     * Maximum number of results to return.
     * Default is 100 if not specified.
     */
    Integer limit;
    
    /**
     * Number of results to skip for pagination.
     * Default is 0 if not specified.
     */
    Integer offset;
    
    /**
     * Tenant ID for multi-tenancy.
     * Required for all queries.
     */
    String tenantId;
    
    /**
     * Gets the effective limit (default 100).
     */
    public int getEffectiveLimit() {
        return limit != null ? limit : 100;
    }
    
    /**
     * Gets the effective offset (default 0).
     */
    public int getEffectiveOffset() {
        return offset != null ? offset : 0;
    }
    
    /**
     * Validates the query has required fields.
     */
    public void validate() {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Query tenantId cannot be null or blank");
        }
        if (limit != null && limit <= 0) {
            throw new IllegalArgumentException("Query limit must be positive");
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("Query offset cannot be negative");
        }
    }
    
    /**
     * Checks if this is a node query.
     */
    public boolean isNodeQuery() {
        return nodeTypes != null && !nodeTypes.isEmpty();
    }
    
    /**
     * Checks if this is an edge query.
     */
    public boolean isEdgeQuery() {
        return relationshipTypes != null && !relationshipTypes.isEmpty();
    }
    
    /**
     * Checks if this query has property filters.
     */
    public boolean hasPropertyFilters() {
        return propertyFilters != null && !propertyFilters.isEmpty();
    }
}
