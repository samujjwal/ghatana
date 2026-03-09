package com.ghatana.platform.database.cache.pubsub;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Message structure for cache invalidation events.
 *
 * <p><b>Purpose</b><br>
 * Represents a cache invalidation event broadcast via Redis pub/sub.
 * Contains keys to invalidate, operation type, and metadata for tracking.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Invalidate specific keys
 * CacheInvalidationMessage msg = CacheInvalidationMessage.invalidateKeys(
 *     Set.of("user:123", "user:456"),
 *     "tenant-acme",
 *     "service-1"
 * );
 *
 * // Invalidate by pattern
 * CacheInvalidationMessage msg = CacheInvalidationMessage.invalidatePattern(
 *     "user:*",
 *     "tenant-acme",
 *     "service-1"
 * );
 *
 * // Clear entire namespace
 * CacheInvalidationMessage msg = CacheInvalidationMessage.clearNamespace(
 *     "tenant-acme",
 *     "service-1"
 * );
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @doc.type class
 * @doc.purpose Cache invalidation event message
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class CacheInvalidationMessage {
    
    /**
     * Operation types for cache invalidation
     */
    public enum Operation {
        /** Invalidate specific keys */
        INVALIDATE_KEYS,
        /** Invalidate keys matching pattern */
        INVALIDATE_PATTERN,
        /** Clear entire namespace */
        CLEAR_NAMESPACE
    }
    
    private final Operation operation;
    private final Set<String> keys;
    private final String pattern;
    private final String namespace;
    private final String sourceInstance;
    private final Instant timestamp;
    
    /**
     * Creates cache invalidation message (for Jackson deserialization)
     *
     * @param operation Operation type
     * @param keys Keys to invalidate (for INVALIDATE_KEYS)
     * @param pattern Pattern to match (for INVALIDATE_PATTERN)
     * @param namespace Cache namespace
     * @param sourceInstance Source instance identifier
     * @param timestamp Event timestamp
     */
    @JsonCreator
    public CacheInvalidationMessage(
            @JsonProperty("operation") Operation operation,
            @JsonProperty("keys") Set<String> keys,
            @JsonProperty("pattern") String pattern,
            @JsonProperty("namespace") String namespace,
            @JsonProperty("sourceInstance") String sourceInstance,
            @JsonProperty("timestamp") Instant timestamp) {
        this.operation = Objects.requireNonNull(operation, "Operation cannot be null");
        this.keys = keys;
        this.pattern = pattern;
        this.namespace = Objects.requireNonNull(namespace, "Namespace cannot be null");
        this.sourceInstance = Objects.requireNonNull(sourceInstance, "Source instance cannot be null");
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }
    
    /**
     * Create message to invalidate specific keys
     *
     * @param keys Keys to invalidate
     * @param namespace Cache namespace
     * @param sourceInstance Source instance identifier
     * @return Cache invalidation message
     */
    public static CacheInvalidationMessage invalidateKeys(
            Set<String> keys,
            String namespace,
            String sourceInstance) {
        Objects.requireNonNull(keys, "Keys cannot be null");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("Keys cannot be empty");
        }
        return new CacheInvalidationMessage(
                Operation.INVALIDATE_KEYS,
                keys,
                null,
                namespace,
                sourceInstance,
                Instant.now()
        );
    }
    
    /**
     * Create message to invalidate keys by pattern
     *
     * @param pattern Pattern to match (Redis glob pattern)
     * @param namespace Cache namespace
     * @param sourceInstance Source instance identifier
     * @return Cache invalidation message
     */
    public static CacheInvalidationMessage invalidatePattern(
            String pattern,
            String namespace,
            String sourceInstance) {
        Objects.requireNonNull(pattern, "Pattern cannot be null");
        if (pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be empty");
        }
        return new CacheInvalidationMessage(
                Operation.INVALIDATE_PATTERN,
                null,
                pattern,
                namespace,
                sourceInstance,
                Instant.now()
        );
    }
    
    /**
     * Create message to clear entire namespace
     *
     * @param namespace Cache namespace
     * @param sourceInstance Source instance identifier
     * @return Cache invalidation message
     */
    public static CacheInvalidationMessage clearNamespace(
            String namespace,
            String sourceInstance) {
        return new CacheInvalidationMessage(
                Operation.CLEAR_NAMESPACE,
                null,
                null,
                namespace,
                sourceInstance,
                Instant.now()
        );
    }
    
    // Getters
    
    public Operation getOperation() {
        return operation;
    }
    
    public Set<String> getKeys() {
        return keys;
    }
    
    public String getPattern() {
        return pattern;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getSourceInstance() {
        return sourceInstance;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheInvalidationMessage)) return false;
        CacheInvalidationMessage that = (CacheInvalidationMessage) o;
        return operation == that.operation &&
                Objects.equals(keys, that.keys) &&
                Objects.equals(pattern, that.pattern) &&
                Objects.equals(namespace, that.namespace) &&
                Objects.equals(sourceInstance, that.sourceInstance) &&
                Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(operation, keys, pattern, namespace, sourceInstance, timestamp);
    }
    
    @Override
    public String toString() {
        return "CacheInvalidationMessage{" +
                "operation=" + operation +
                ", keys=" + keys +
                ", pattern='" + pattern + '\'' +
                ", namespace='" + namespace + '\'' +
                ", sourceInstance='" + sourceInstance + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
