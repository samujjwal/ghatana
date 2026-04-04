package com.ghatana.datacloud.client;

import com.ghatana.datacloud.spi.ai.PredictionCapability;
import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Context document for LLM consumption (Tier-2).
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a piece of context derived from Tier-1 (deterministic facts) or
 * Tier-2 (summaries, insights, patterns) that can be fed to LLMs for:
 * <ul>
 * <li>Natural language query understanding</li>
 * <li>Explanation generation</li>
 * <li>Insight summarization</li>
 * <li>Conversational interfaces</li>
 * </ul>
 *
 * <p>
 * <b>Tier-1 vs Tier-2</b><br>
 * <ul>
 * <li><b>Tier-1</b>: Authoritative facts from StoragePlugin (events, entities,
 * time-series)</li>
 * <li><b>Tier-2</b>: Derived context (summaries, aggregations, patterns,
 * predictions)</li>
 * </ul>
 *
 * <p>
 * <b>Safety Guarantees</b><br>
 * <ul>
 * <li>LLMs receive context documents but NEVER write to Tier-1</li>
 * <li>All context includes confidence and determinism scores</li>
 * <li>Context has TTL and freshness tracking</li>
 * <li>Provenance links back to source data</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Create context from query results
 * ContextDocument doc = ContextDocument.builder()
 *     .contextType(ContextType.FACT)
 *     .tenantId("tenant-123")
 *     .source(ContextSource.builder()
 *         .collection("events")
 *         .queryId("query-123")
 *         .plugin("postgresql")
 *         .build())
 *     .content("Last 100 login events for user X")
 *     .structuredData(queryResults)
 *     .confidence(1.0)
 *     .determinism(DeterminismLevel.HIGH)
 *     .ttl(Duration.ofHours(1))
 *     .build();
 *
 * // Store for LLM retrieval
 * contextStore.store(doc);
 *
 * // LLM queries context
 * List<ContextDocument> context = contextStore.selectContext(
 *     "Tell me about recent login patterns",
 *     tenantId,
 *     maxTokens: 4000
 * );
 * }</pre>
 *
 * @see LearningSignal
 * @see com.ghatana.datacloud.spi.StoragePlugin
 * @doc.type record
 * @doc.purpose Context document for LLM gateway
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Value
@Builder
public class ContextDocument {

    /**
     * Unique context document ID.
     */
    @Builder.Default
    UUID contextId = UUID.randomUUID();

    /**
     * Timestamp when context was created.
     */
    @Builder.Default
    Instant createdAt = Instant.now();

    /**
     * Tenant ID for multi-tenancy.
     */
    String tenantId;

    /**
     * Type of context document.
     */
    ContextType contextType;

    /**
     * Source information (where this context came from).
     */
    ContextSource source;

    /**
     * Human-readable content summary.
     */
    String content;

    /**
     * Structured data (original query results, aggregates, etc.).
     */
    Map<String, Object> structuredData;

    /**
     * Confidence score (0.0 to 1.0).
     * <p>
     * - 1.0 for Tier-1 facts
     * - 0.0-1.0 for Tier-2 derived content
     */
    double confidence;

    /**
     * Determinism level.
     */
    PredictionCapability.DeterminismLevel determinism;

    /**
     * Time-to-live for this context.
     * <p>
     * Context becomes stale after TTL expires.
     */
    Duration ttl;

    /**
     * Expiration timestamp (createdAt + ttl).
     */
    @Builder.Default
    Instant expiresAt = Instant.now().plus(Duration.ofDays(30));

    /**
     * Embedding vector for semantic search (optional).
     */
    float[] embedding;

    /**
     * Tags for categorization and filtering.
     */
    Map<String, String> tags;

    /**
     * Size estimate in tokens (for LLM context window management).
     */
    Integer tokenCount;

    /**
     * Version for schema evolution.
     */
    @Builder.Default
    int version = 1;

    /**
     * Checks if context is still fresh.
     *
     * @return true if not expired
     */
    public boolean isFresh() {
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if context is high-confidence and deterministic enough for critical operations.
     *
     * @return true if suitable for critical use
     */
    public boolean isCriticalGrade() {
        return confidence >= 0.95 && determinism == PredictionCapability.DeterminismLevel.HIGH;
    }

    /**
     * Types of context documents.
     */
    public enum ContextType {
        /**
         * Direct fact from Tier-1 storage (event, entity).
         */
        FACT,

        /**
         * Summary or aggregation of multiple facts.
         */
        SUMMARY,

        /**
         * Execution trace (query plan, provenance).
         */
        TRACE,

        /**
         * Time-series aggregation.
         */
        AGGREGATION,

        /**
         * Pattern or insight detected by AI.
         */
        PATTERN,

        /**
         * Anomaly detection result.
         */
        ANOMALY,

        /**
         * Recommendation.
         */
        RECOMMENDATION,

        /**
         * Explanation.
         */
        EXPLANATION,

        /**
         * Custom context type.
         */
        CUSTOM
    }

    /**
     * Source information for context document.
     */
    @Value
    @Builder
    public static class ContextSource {
        /**
         * Source collection.
         */
        String collection;

        /**
         * Plugin that generated the data.
         */
        String plugin;

        /**
         * Query ID if from a query.
         */
        String queryId;

        /**
         * Operation that created this context.
         */
        String operation;

        /**
         * Actor (user or system).
         */
        String actor;

        /**
         * Provenance chain (lineage).
         */
        Map<String, String> provenance;
    }

    /**
     * Converts this document to a map for storage.
     *
     * @return Map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("contextId", contextId.toString());
        map.put("createdAt", createdAt.toString());
        map.put("tenantId", tenantId);
        map.put("contextType", contextType.name());
        map.put("source", sourceToMap());
        map.put("content", content);
        map.put("structuredData", structuredData != null ? structuredData : Map.of());
        map.put("confidence", confidence);
        map.put("determinism", determinism.name());
        map.put("ttl", ttl.toString());
        map.put("expiresAt", expiresAt.toString());
        map.put("tags", tags != null ? tags : Map.of());
        map.put("tokenCount", tokenCount != null ? tokenCount : 0);
        map.put("version", version);
        return map;
    }

    private Map<String, Object> sourceToMap() {
        return Map.of(
                "collection", source.collection != null ? source.collection : "",
                "plugin", source.plugin != null ? source.plugin : "",
                "queryId", source.queryId != null ? source.queryId : "",
                "operation", source.operation != null ? source.operation : "",
                "actor", source.actor != null ? source.actor : "",
                "provenance", source.provenance != null ? source.provenance : Map.of()
        );
    }
}

