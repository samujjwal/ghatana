package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Service for AI-assisted entity creation and enrichment.
 *
 * <p><b>Purpose</b><br>
 * Provides AI-powered capabilities for generating entity data from natural language,
 * suggesting enrichments for existing entities, and discovering related entities
 * through semantic similarity.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntitySuggestionService suggestionService = ...;
 *
 * // Generate entity from description
 * Promise<EntitySuggestion> suggestion = suggestionService.generateFromDescription(
 *     "tenant-123",
 *     "products",
 *     "iPhone 15 Pro with 256GB storage in titanium blue"
 * );
 *
 * // Suggest enrichments for sparse entity
 * Promise<List<EntityEnrichment>> enrichments = suggestionService.suggestEnrichments(
 *     "tenant-123",
 *     entityId
 * );
 *
 * // Find related entities
 * Promise<List<EntityRelation>> relations = suggestionService.suggestRelations(
 *     "tenant-123",
 *     entityId,
 *     10
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * This is a port in the hexagonal architecture. Implementations (adapters) in the
 * infrastructure layer integrate with AI services (LLM, embeddings, vector store).
 *
 * <p><b>Integration Points</b><br>
 * - Consumed by: EntityService (application layer)
 * - Implemented by: AiEntitySuggestionAdapter (infrastructure layer)
 * - Depends on: AI services (EmbeddingService, CompletionService, VectorStore)
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe as they will be used concurrently.
 *
 * @see EntitySuggestion
 * @see EntityEnrichment
 * @see EntityRelation
 * @see com.ghatana.datacloud.application.EntityService
 * @doc.type interface
 * @doc.purpose AI-powered entity suggestions and enrichment
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface EntitySuggestionService {

    /**
     * Generates entity data from natural language description using AI.
     *
     * <p><b>Process</b><br>
     * 1. Fetches collection schema to understand required fields
     * 2. Builds LLM prompt with schema + user description
     * 3. Generates structured entity data via LLM
     * 4. Validates generated data against schema
     * 5. Generates embedding for the description
     * 6. Returns suggestion with confidence score
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Input: "MacBook Pro 16-inch M3 Max, 64GB RAM, 2TB SSD"
     * // Output: {
     * //   "name": "MacBook Pro 16-inch",
     * //   "brand": "Apple",
     * //   "processor": "M3 Max",
     * //   "ram": "64GB",
     * //   "storage": "2TB SSD",
     * //   "category": "laptops"
     * // }
     * }</pre>
     *
     * @param tenantId Tenant identifier for multi-tenancy isolation
     * @param collectionName Target collection name
     * @param description Natural language description of the entity
     * @return Promise of EntitySuggestion with generated data and confidence
     * @throws IllegalArgumentException if tenantId, collectionName, or description is null/blank
     */
    Promise<EntitySuggestion> generateFromDescription(
        String tenantId,
        String collectionName,
        String description
    );

    /**
     * Suggests enrichments for missing or sparse fields in an entity.
     *
     * <p><b>Purpose</b><br>
     * Analyzes an existing entity and suggests values for missing or incomplete fields
     * based on existing data and LLM understanding of typical patterns.
     *
     * <p><b>Process</b><br>
     * 1. Fetch entity and collection schema
     * 2. Identify missing or sparse fields (null, empty string, etc.)
     * 3. Generate LLM prompt with existing data + missing fields
     * 4. Parse enrichment suggestions with confidence scores
     * 5. Return list of suggestions
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Entity with only name and price
     * Entity entity = Entity.builder()
     *     .data(Map.of("name", "iPhone 15 Pro", "price", 999.00))
     *     .build();
     *
     * // Suggest enrichments
     * List<EntityEnrichment> enrichments = service.suggestEnrichments(
     *     "tenant-123",
     *     "products",
     *     entityId
     * ).getResult();
     *
     * // Results might include:
     * // {field=description, value="Latest flagship...", confidence=0.85}
     * // {field=category, value="Electronics > Smartphones", confidence=0.92}
     * // {field=brand, value="Apple", confidence=0.98}
     * }</pre>
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the entity ID to enrich (required)
     * @return Promise of list of enrichment suggestions
     */
    Promise<List<EntityEnrichment>> suggestEnrichments(
            String tenantId,
            String collectionName,
            UUID entityId);        /**
     * Suggests related entities based on similarity.
     *
     * <p><b>Purpose</b><br>
     * Finds entities similar to the source entity using vector embeddings and semantic search.
     * Useful for "You may also like" recommendations, duplicate detection, and relationship discovery.
     *
     * <p><b>Process</b><br>
     * 1. Fetch source entity
     * 2. Generate embedding for entity content
     * 3. Store embedding in vector store (if not exists)
     * 4. Search for similar vectors
     * 5. Filter by tenant and exclude self
     * 6. Return top N relations with similarity scores
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Find similar products
     * List<EntityRelation> relations = service.suggestRelations(
     *     "tenant-123",
     *     "products",
     *     macbookEntityId,
     *     10
     * ).getResult();
     *
     * // Results might include:
     * // {relatedEntityId=..., collectionName="products", type=SIMILAR, similarity=0.87}
     * // {relatedEntityId=..., collectionName="products", type=SIMILAR, similarity=0.82}
     * }</pre>
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param entityId the source entity ID (required)
     * @param limit the maximum number of relations to return (required, must be > 0)
     * @return Promise of list of entity relations sorted by similarity descending
     */
    Promise<List<EntityRelation>> suggestRelations(
            String tenantId,
            String collectionName,
            UUID entityId,
            int limit);
}
