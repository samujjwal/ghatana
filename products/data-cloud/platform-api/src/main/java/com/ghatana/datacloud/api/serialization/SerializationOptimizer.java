/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optimizer for JSON serialization operations.
 *
 * <p>Provides optimized serialization with:
 * <ul>
 *   <li>Configured ObjectMapper with performance optimizations</li>
 *   <li>Serialization cache for immutable DTOs</li>
 *   <li>Thread-safe operations</li>
 *   <li>Statistics tracking</li>
 * </ul>
 *
 * <p>Optimizations applied:
 * <ul>
 *   <li>JavaTimeModule for Instant serialization</li>
 *   <li>Disabled serialization of unknown properties</li>
 *   <li>Caching of serialized JSON for immutable objects</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Optimized JSON serialization with caching and performance tuning
 * @doc.layer product
 * @doc.pattern Cache, Optimization
 */
public class SerializationOptimizer {

    private static final Logger log = LoggerFactory.getLogger(SerializationOptimizer.class);
    private static final int MAX_CACHE_SIZE = 10000;

    private final ObjectMapper objectMapper;
    private final Map<SerializationKey, String> serializationCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Executor executor;

    /**
     * Creates a serialization optimizer with default executor.
     */
    public SerializationOptimizer() {
        this(ForkJoinPool.commonPool());
    }

    /**
     * Creates a serialization optimizer with custom executor.
     *
     * @param executor executor for blocking operations
     */
    public SerializationOptimizer(Executor executor) {
        this.executor = executor;
        this.objectMapper = createOptimizedObjectMapper();
        log.info("SerializationOptimizer initialized");
    }

    /**
     * Creates an optimized ObjectMapper configuration.
     *
     * @return configured ObjectMapper
     */
    private ObjectMapper createOptimizedObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register JavaTimeModule for Instant serialization
        mapper.registerModule(new JavaTimeModule());
        
        // Disable serialization of unknown properties for performance
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        return mapper;
    }

    /**
     * Get the configured ObjectMapper.
     *
     * @return optimized ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Serialize an object to JSON string asynchronously.
     *
     * @param object object to serialize
     * @return promise of JSON string
     */
    public Promise<String> serializeAsync(Object object) {
        return Promise.ofBlocking(executor, () -> {
            try {
                // Check cache for immutable objects
                if (object != null && isCacheable(object)) {
                    SerializationKey key = new SerializationKey(object.getClass(), object.hashCode());
                    String cached = serializationCache.get(key);
                    if (cached != null) {
                        cacheHits.incrementAndGet();
                        return cached;
                    }
                }

                String json = objectMapper.writeValueAsString(object);
                
                // Cache the result for cacheable objects
                if (object != null && isCacheable(object)) {
                    SerializationKey key = new SerializationKey(object.getClass(), object.hashCode());
                    if (serializationCache.size() < MAX_CACHE_SIZE) {
                        serializationCache.put(key, json);
                    }
                    cacheMisses.incrementAndGet();
                }

                return json;
            } catch (Exception e) {
                log.error("Serialization failed for object: {}", object.getClass().getSimpleName(), e);
                throw new RuntimeException("Serialization failed", e);
            }
        });
    }

    /**
     * Serialize an object to JSON bytes asynchronously.
     *
     * @param object object to serialize
     * @return promise of JSON bytes
     */
    public Promise<byte[]> serializeToBytesAsync(Object object) {
        return serializeAsync(object)
                .map(json -> json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserialize JSON string to object asynchronously.
     *
     * @param json JSON string
     * @param clazz target class
     * @param <T> type parameter
     * @return promise of deserialized object
     */
    public <T> Promise<T> deserializeAsync(String json, Class<T> clazz) {
        return Promise.ofBlocking(executor, () -> {
            try {
                return objectMapper.readValue(json, clazz);
            } catch (Exception e) {
                log.error("Deserialization failed for class: {}", clazz.getSimpleName(), e);
                throw new RuntimeException("Deserialization failed", e);
            }
        });
    }

    /**
     * Check if an object is cacheable (immutable).
     *
     * @param object object to check
     * @return true if cacheable
     */
    private boolean isCacheable(Object object) {
        // Cache records and other immutable types
        return object.getClass().isRecord() 
                || object instanceof String
                || object instanceof Number
                || object instanceof Boolean;
    }

    /**
     * Clear the serialization cache.
     */
    public Promise<Void> clearCache() {
        return Promise.ofBlocking(executor, () -> {
            long size = serializationCache.size();
            serializationCache.clear();
            cacheHits.set(0);
            cacheMisses.set(0);
            log.info("Cleared serialization cache (count: {})", size);
            return null;
        });
    }

    /**
     * Get cache statistics.
     *
     * @return cache statistics
     */
    public CacheStats getStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        
        return new CacheStats(
            hits,
            misses,
            serializationCache.size(),
            hitRate
        );
    }

    /**
     * Cache key for serialization entries.
     */
    private static class SerializationKey {
        private final Class<?> clazz;
        private final int hashCode;

        SerializationKey(Class<?> clazz, int hashCode) {
            this.clazz = clazz;
            this.hashCode = hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SerializationKey that = (SerializationKey) o;
            return hashCode == that.hashCode && clazz.equals(that.clazz);
        }

        @Override
        public int hashCode() {
            return 31 * clazz.hashCode() + hashCode;
        }
    }

    /**
     * Cache statistics.
     */
    public record CacheStats(
        long hits,
        long misses,
        long size,
        double hitRate
    ) {}
}
