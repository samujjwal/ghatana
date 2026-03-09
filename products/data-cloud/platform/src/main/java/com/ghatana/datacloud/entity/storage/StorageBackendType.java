package com.ghatana.datacloud.entity.storage;

/**
 * Enumeration of supported storage backend families.
 *
 * <p><b>Purpose</b><br>
 * Defines the type of storage backend (relational, time-series, data warehouse, key-value, etc.)
 * for routing and configuration purposes. Used by StorageProfile to declare supported backends.
 *
 * <p><b>Backend Types</b><br>
 * - RELATIONAL: Traditional SQL databases (PostgreSQL, MySQL, etc.)
 * - TIMESERIES: Time-series databases optimized for metrics (InfluxDB, TimescaleDB, etc.)
 * - LAKEHOUSE: Data warehouses/lakes (Snowflake, BigQuery, Parquet, etc.)
 * - KEY_VALUE: Key-value stores (Redis, DynamoDB, etc.)
 * - BLOB: Object/blob storage (S3, GCS, Azure Blob, etc.)
 * - IN_MEMORY: In-memory caches and stores (for fast access or testing)
 * - SEARCH: Specialized search engines (Elasticsearch, OpenSearch, etc.)
 * - GRAPH: Graph databases (Neo4j, etc.)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StorageBackendType type = StorageBackendType.RELATIONAL;
 * boolean isTimeSeries = type == StorageBackendType.TIMESERIES;
 * String label = type.getLabel(); // "Relational (SQL)"
 * }</pre>
 *
 * <p><b>Performance Characteristics</b><br>
 * Each backend has different latency, throughput, and consistency profiles:
 * - RELATIONAL: High consistency, good for transactional data
 * - TIMESERIES: Optimized for sequential time-based queries
 * - LAKEHOUSE: Optimized for analytical queries on large datasets
 * - KEY_VALUE: Ultra-low latency, good for caching
 * - BLOB: High throughput, optimized for immutable objects
 *
 * @see StorageProfile
 * @doc.type enum
 * @doc.purpose Classification of storage backend families
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum StorageBackendType {
    /**
     * Traditional relational databases (PostgreSQL, MySQL, etc.).
     * Best for: Structured data, ACID transactions, complex queries.
     * Latency: ~5-50ms typical
     */
    RELATIONAL("Relational (SQL)", "sql_db"),

    /**
     * Time-series optimized databases (InfluxDB, TimescaleDB, etc.).
     * Best for: Metrics, traces, time-windowed aggregations.
     * Latency: ~5-20ms typical
     */
    TIMESERIES("Time-Series", "timeseries"),

    /**
     * Data warehouse/lake solutions (Snowflake, BigQuery, Parquet, etc.).
     * Best for: Analytical queries, large dataset scans, OLAP.
     * Latency: ~500ms-5s typical (higher consistency)
     */
    LAKEHOUSE("Data Warehouse/Lake", "warehouse"),

    /**
     * Key-value stores (Redis, DynamoDB, Memcached, etc.).
     * Best for: Fast lookups, caching, session storage.
     * Latency: ~1-5ms typical (ultra-low)
     */
    KEY_VALUE("Key-Value", "kv_store"),

    /**
     * Object/blob storage (S3, GCS, Azure Blob Storage, etc.).
     * Best for: Large files, archives, immutable objects.
     * Latency: ~100-500ms typical (higher at scale)
     */
    BLOB("Object/Blob Storage", "blob"),

    /**
     * In-memory caches and stores (for fast access, testing, staging).
     * Best for: Temporary data, distributed caching, local testing.
     * Latency: <1ms typical (in-process)
     */
    IN_MEMORY("In-Memory", "memory"),

    /**
     * Search-optimized engines (Elasticsearch, OpenSearch, Solr, etc.).
     * Best for: Full-text search, fuzzy matching, faceted navigation.
     * Latency: ~50-200ms typical
     */
    SEARCH("Search Engine", "search"),

    /**
     * Graph databases (Neo4j, Amazon Neptune, etc.).
     * Best for: Relationship queries, pattern matching, graph algorithms.
     * Latency: ~10-100ms typical
     */
    GRAPH("Graph Database", "graph");

    private final String label;
    private final String identifier;

    /**
     * Create storage backend type.
     *
     * @param label Human-readable label for UI/display
     * @param identifier URL-safe identifier for serialization
     */
    StorageBackendType(String label, String identifier) {
        this.label = label;
        this.identifier = identifier;
    }

    /**
     * Get human-readable label.
     *
     * @return Label for display in UIs
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get URL-safe identifier.
     *
     * @return Identifier for serialization/configuration
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * Check if this backend is optimized for time-windowed queries.
     *
     * @return true for TIMESERIES, LAKEHOUSE, SEARCH
     */
    public boolean isWindowedQueryOptimized() {
        return this == TIMESERIES || this == LAKEHOUSE || this == SEARCH;
    }

    /**
     * Check if this backend is suitable for fast lookups.
     *
     * @return true for KEY_VALUE, IN_MEMORY, SEARCH
     */
    public boolean isFastLookup() {
        return this == KEY_VALUE || this == IN_MEMORY || this == SEARCH;
    }

    /**
     * Check if this backend is suitable for analytics/aggregation.
     *
     * @return true for LAKEHOUSE, TIMESERIES, SEARCH
     */
    public boolean isAnalyticsOptimized() {
        return this == LAKEHOUSE || this == TIMESERIES || this == SEARCH;
    }

    /**
     * Parse backend type from identifier string (case-insensitive).
     *
     * @param identifier Identifier to parse
     * @return Matching backend type
     * @throws IllegalArgumentException if identifier not recognized
     */
    public static StorageBackendType fromIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }

        for (StorageBackendType type : StorageBackendType.values()) {
            if (type.identifier.equalsIgnoreCase(identifier)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unknown storage backend type identifier: " + identifier);
    }

    /**
     * Get all analytics-optimized backends.
     *
     * @return Array of backends suitable for analytics
     */
    public static StorageBackendType[] getAnalyticsBackends() {
        return new StorageBackendType[]{LAKEHOUSE, TIMESERIES, SEARCH};
    }

    /**
     * Get all fast-access backends.
     *
     * @return Array of backends for fast lookups
     */
    public static StorageBackendType[] getFastAccessBackends() {
        return new StorageBackendType[]{KEY_VALUE, IN_MEMORY};
    }

    @Override
    public String toString() {
        return label + " (" + identifier + ")";
    }
}
