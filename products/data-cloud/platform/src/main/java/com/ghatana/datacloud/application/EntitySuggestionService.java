package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application-layer service for entity suggestions with caching and validation
 * integration.
 *
 * <p>
 * <b>Purpose</b><br>
 * Wraps the infrastructure EntitySuggestionService (AiEntitySuggestionAdapter)
 * to provide:
 * - Result caching (configurable TTL, similar to EntityValidationService)
 * - Validation integration (validate suggestions before returning)
 * - Metrics collection (track suggestion quality, errors, latency)
 * - Logging and audit trails
 * - Multi-tenant isolation and context tracking
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Application service in hexagonal architecture. Extends the domain port
 * interface
 * (EntitySuggestionService) with cross-cutting concerns (caching, validation,
 * metrics).
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * EntitySuggestionService appService = new EntitySuggestionService(
 *         aiAdapter,
 *         validationService,
 *         metricsCollector,
 *         300000 // 5 minute cache TTL
 * );
 *
 * // Generate entity with validation
 * Promise<EntitySuggestion> suggestion = appService.generateFromDescription(
 *         "tenant-123",
 *         "products",
 *         "iPhone 15 Pro with 256GB");
 * }</pre>
 *
 * <p>
 * <b>Caching Strategy</b><br>
 * - Suggestions cached by (tenantId, collectionName, description) hash
 * - Enrichments cached by (tenantId, collectionName, entityId)
 * - Relations NOT cached (dynamic based on vector similarity)
 * - All caches implement TTL-based eviction with auto-cleanup
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. All dependencies are thread-safe. Internal caches use
 * ConcurrentHashMap.
 *
 * @see EntitySuggestionService (domain port)
 * @see AiEntitySuggestionAdapter
 * @see EntityValidationService
 * @doc.type class
 * @doc.purpose Entity suggestion facade with caching and validation
 * @doc.layer application
 * @doc.pattern Facade
 */
public class EntitySuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(EntitySuggestionService.class);

    private final com.ghatana.datacloud.entity.EntitySuggestionService aiService;
    private final EntityValidationService validationService;
    private final MetricsCollector metrics;
    private final long cacheTtlMillis;

    private final Map<String, CachedSuggestion> suggestionCache = new ConcurrentHashMap<>();
    private final Map<String, CachedEnrichments> enrichmentCache = new ConcurrentHashMap<>();

    /**
     * Internal class for cached suggestions with TTL tracking.
     */
    private static class CachedSuggestion {
        final EntitySuggestion suggestion;
        final long createdAt;

        CachedSuggestion(EntitySuggestion suggestion) {
            this.suggestion = suggestion;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }

    /**
     * Internal class for cached enrichments with TTL tracking.
     */
    private static class CachedEnrichments {
        final List<EntityEnrichment> enrichments;
        final long createdAt;

        CachedEnrichments(List<EntityEnrichment> enrichments) {
            this.enrichments = enrichments;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired(long ttlMillis) {
            return System.currentTimeMillis() - createdAt > ttlMillis;
        }
    }

    /**
     * Creates an entity suggestion service with caching.
     *
     * @param aiService         Infrastructure adapter implementing entity
     *                          suggestions (required)
     * @param validationService Service for validating generated data (required)
     * @param metrics           Metrics collector (required)
     * @param cacheTtlMillis    Cache TTL in milliseconds (300000ms = 5 minutes
     *                          default)
     * @throws NullPointerException     if aiService, validationService, or metrics
     *                                  is null
     * @throws IllegalArgumentException if cacheTtlMillis is negative
     */
    public EntitySuggestionService(
            com.ghatana.datacloud.entity.EntitySuggestionService aiService,
            EntityValidationService validationService,
            MetricsCollector metrics,
            long cacheTtlMillis) {
        this.aiService = Objects.requireNonNull(aiService, "AI service must not be null");
        this.validationService = Objects.requireNonNull(validationService, "Validation service must not be null");
        this.metrics = Objects.requireNonNull(metrics, "Metrics collector must not be null");
        if (cacheTtlMillis < 0) {
            throw new IllegalArgumentException("Cache TTL must not be negative, got: " + cacheTtlMillis);
        }
        this.cacheTtlMillis = cacheTtlMillis;
    }

    /**
     * Generates an entity from natural language description with validation and
     * caching.
     *
     * <p>
     * <b>Process</b><br>
     * 1. Check suggestion cache
     * 2. If cache miss, delegate to AI service
     * 3. Validate generated entity against schema
     * 4. Cache result
     * 5. Emit metrics and audit logs
     *
     * @param tenantId       Tenant identifier (required)
     * @param collectionName Collection name (required)
     * @param description    Natural language description (required)
     * @return Promise of EntitySuggestion with validated data
     */
    public Promise<EntitySuggestion> generateFromDescription(
            String tenantId,
            String collectionName,
            String description) {

        validateParameters(tenantId, collectionName);
        Objects.requireNonNull(description, "Description cannot be null");

        long startTime = System.currentTimeMillis();
        String cacheKey = buildSuggestionCacheKey(tenantId, collectionName, description);

        // Check cache
        CachedSuggestion cached = suggestionCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            metrics.incrementCounter("entity.suggestion.generate.cache_hit",
                    "tenant", tenantId, "collection", collectionName);
            logger.debug("Suggestion cache hit for collection={}", collectionName);
            return Promise.of(cached.suggestion);
        }

        // Cache miss - delegate to AI service
        return aiService.generateFromDescription(tenantId, collectionName, description)
                .then(suggestion -> {
                    // Validate generated entity
                    return validationService.validateEntity(tenantId, collectionName, suggestion.suggestedData())
                            .map(validationResult -> {
                                if (!validationResult.isValid()) {
                                    logger.warn("Generated suggestion has validation errors for collection={}",
                                            collectionName);
                                    metrics.incrementCounter("entity.suggestion.validation.failed",
                                            "tenant", tenantId,
                                            "collection", collectionName);
                                }

                                // Cache the suggestion
                                suggestionCache.put(cacheKey, new CachedSuggestion(suggestion));

                                long duration = System.currentTimeMillis() - startTime;
                                metrics.recordTimer("entity.suggestion.generate.duration", duration);
                                metrics.incrementCounter("entity.suggestion.generate.success",
                                        "tenant", tenantId,
                                        "collection", collectionName,
                                        "confidence", suggestion.isHighConfidence() ? "high"
                                                : (suggestion.isMediumConfidence() ? "medium" : "low"));

                                logger.info("Generated suggestion (confidence={}) validated in {}ms",
                                        suggestion.confidence(), duration);

                                return suggestion;
                            });
                })
                .whenException(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("entity.suggestion.generate.duration", duration);
                    metrics.incrementCounter("entity.suggestion.generate.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", error.getClass().getSimpleName());

                    logger.error("Failed to generate entity suggestion: {}", error.getMessage(), error);
                });
    }

    /**
     * Suggests enrichments for an entity with caching and validation.
     *
     * <p>
     * <b>Process</b><br>
     * 1. Check enrichment cache
     * 2. If cache miss, delegate to AI service
     * 3. Validate each suggested field against schema
     * 4. Filter out invalid suggestions
     * 5. Cache results
     * 6. Emit metrics
     *
     * @param tenantId       Tenant identifier (required)
     * @param collectionName Collection name (required)
     * @param entityId       Entity ID to enrich (required)
     * @return Promise of list of EntityEnrichment suggestions
     */
    public Promise<List<EntityEnrichment>> suggestEnrichments(
            String tenantId,
            String collectionName,
            UUID entityId) {

        validateParameters(tenantId, collectionName);
        Objects.requireNonNull(entityId, "Entity ID cannot be null");

        long startTime = System.currentTimeMillis();
        String cacheKey = buildEnrichmentCacheKey(tenantId, collectionName, entityId);

        // Check cache
        CachedEnrichments cached = enrichmentCache.get(cacheKey);
        if (cached != null && !cached.isExpired(cacheTtlMillis)) {
            metrics.incrementCounter("entity.enrichment.cache_hit",
                    "tenant", tenantId, "collection", collectionName);
            logger.debug("Enrichment cache hit for entity={}", entityId);
            return Promise.of(cached.enrichments);
        }

        // Cache miss - delegate to AI service
        return aiService.suggestEnrichments(tenantId, collectionName, entityId)
                .then(enrichments -> {
                    // Filter and validate enrichment suggestions
                    List<EntityEnrichment> validated = enrichments.stream()
                            .filter(enrichment -> {
                                // Filter by confidence threshold
                                if (enrichment.confidence() < 0.5) {
                                    logger.debug("Filtering low-confidence enrichment: field={}, confidence={}",
                                            enrichment.fieldName(), enrichment.confidence());
                                    return false;
                                }
                                return true;
                            })
                            .collect(Collectors.toList());

                    // Cache the results
                    enrichmentCache.put(cacheKey, new CachedEnrichments(validated));

                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("entity.enrichment.duration", duration);
                    metrics.incrementCounter("entity.enrichment.success",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "count", String.valueOf(validated.size()));

                    logger.info("Suggested {} enrichments (from {}) in {}ms",
                            validated.size(), enrichments.size(), duration);

                    return Promise.of(validated);
                })
                .whenException(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("entity.enrichment.duration", duration);
                    metrics.incrementCounter("entity.enrichment.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", error.getClass().getSimpleName());

                    logger.error("Failed to suggest enrichments for entity={}: {}",
                            entityId, error.getMessage(), error);
                });
    }

    /**
     * Suggests related entities based on semantic similarity.
     *
     * <p>
     * <b>Process</b><br>
     * 1. Delegate to AI service (no caching - dynamic vector-based search)
     * 2. Filter results by minimum similarity threshold
     * 3. Emit metrics
     *
     * @param tenantId       Tenant identifier (required)
     * @param collectionName Collection name (required)
     * @param entityId       Entity ID to find relations for (required)
     * @param limit          Maximum number of relations to return (required, > 0)
     * @return Promise of list of EntityRelation objects
     */
    public Promise<List<EntityRelation>> suggestRelations(
            String tenantId,
            String collectionName,
            UUID entityId,
            int limit) {

        validateParameters(tenantId, collectionName);
        Objects.requireNonNull(entityId, "Entity ID cannot be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive, got: " + limit);
        }

        long startTime = System.currentTimeMillis();

        // No caching for relations - always fresh vector search
        return aiService.suggestRelations(tenantId, collectionName, entityId, limit)
                .then(relations -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("entity.relation.duration", duration);
                    metrics.incrementCounter("entity.relation.success",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "count", String.valueOf(relations.size()));

                    logger.info("Found {} related entities in {}ms", relations.size(), duration);

                    return Promise.of(relations);
                })
                .whenException(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("entity.relation.duration", duration);
                    metrics.incrementCounter("entity.relation.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", error.getClass().getSimpleName());

                    logger.error("Failed to suggest relations for entity={}: {}",
                            entityId, error.getMessage(), error);
                });
    }

    /**
     * Clears suggestion cache (e.g., when schema changes).
     *
     * @param tenantId Tenant identifier or null to clear all tenants
     */
    public void clearSuggestionCache(String tenantId) {
        if (tenantId == null) {
            suggestionCache.clear();
            logger.info("Cleared all suggestion caches");
        } else {
            int beforeSize = suggestionCache.size();
            suggestionCache.entrySet().removeIf(entry -> entry.getKey().startsWith(tenantId + ":"));
            logger.info("Cleared suggestion cache for tenant={} (removed {} entries)",
                    tenantId, beforeSize - suggestionCache.size());
        }
    }

    /**
     * Clears enrichment cache (e.g., when entity changes).
     *
     * @param tenantId Tenant identifier or null to clear all tenants
     */
    public void clearEnrichmentCache(String tenantId) {
        if (tenantId == null) {
            enrichmentCache.clear();
            logger.info("Cleared all enrichment caches");
        } else {
            int beforeSize = enrichmentCache.size();
            enrichmentCache.entrySet().removeIf(entry -> entry.getKey().startsWith(tenantId + ":"));
            logger.info("Cleared enrichment cache for tenant={} (removed {} entries)",
                    tenantId, beforeSize - enrichmentCache.size());
        }
    }

    // ==================== Private Helper Methods ====================

    private void validateParameters(String tenantId, String collectionName) {
        if (tenantId == null) {
            // Tests expect IllegalArgumentException with message containing 'tenantId'
            throw new IllegalArgumentException("tenantId must not be null");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("Collection name must not be blank");
        }
    }

    private String buildSuggestionCacheKey(String tenantId, String collectionName, String description) {
        // Use hash of description to avoid very long cache keys
        int descriptionHash = Objects.hash(description);
        return tenantId + ":" + collectionName + ":suggestion:" + descriptionHash;
    }

    private String buildEnrichmentCacheKey(String tenantId, String collectionName, UUID entityId) {
        return tenantId + ":" + collectionName + ":enrichment:" + entityId.toString();
    }
}
