package com.ghatana.datacloud.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public service interface for Context Plane operations.
 *
 * <p>F3: Provides context management including get, put, delete, snapshot,
 * collection context, RAG grounding check, and lineage/trust lookup.
 *
 * @doc.type interface
 * @doc.purpose Context Plane service interface
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ContextService {

    /**
     * Retrieves context for a specific tenant and scope.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @return the context record if found
     */
    Optional<ContextRecord> getContext(String tenantId, String collectionId, String entityId);

    /**
     * Stores or updates context for a specific tenant and scope.
     *
     * @param context the context record to store
     * @return the stored context record
     */
    ContextRecord putContext(ContextRecord context);

    /**
     * Deletes context for a specific tenant and scope.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @return true if the context was deleted, false if not found
     */
    boolean deleteContext(String tenantId, String collectionId, String entityId);

    /**
     * Creates a snapshot of all context for a tenant.
     *
     * @param tenantId tenant identifier
     * @return a list of all context records for the tenant
     */
    List<ContextRecord> snapshot(String tenantId);

    /**
     * Retrieves collection-level context.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection identifier
     * @return the collection context if found
     */
    Optional<ContextRecord> getCollectionContext(String tenantId, String collectionId);

    /**
     * Checks if context is grounded in RAG (Retrieval-Augmented Generation).
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @return true if the context is RAG-grounded
     */
    boolean isRagGrounded(String tenantId, String collectionId, String entityId);

    /**
     * Looks up lineage information for context.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @return lineage information as a map
     */
    Map<String, Object> lookupLineage(String tenantId, String collectionId, String entityId);

    /**
     * Looks up trust information for context.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @return trust information including trust score and factors
     */
    Map<String, Object> lookupTrust(String tenantId, String collectionId, String entityId);

    /**
     * Updates the freshness timestamp for context.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @param timestamp the new freshness timestamp
     * @return true if the context was updated, false if not found
     */
    boolean updateFreshness(String tenantId, String collectionId, String entityId, Instant timestamp);

    /**
     * Updates the trust score for context.
     *
     * @param tenantId tenant identifier
     * @param collectionId collection scope (optional)
     * @param entityId entity scope (optional)
     * @param trustScore the new trust score (0-100)
     * @return true if the context was updated, false if not found
     */
    boolean updateTrustScore(String tenantId, String collectionId, String entityId, int trustScore);
}
