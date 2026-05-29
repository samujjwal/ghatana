/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.idempotency;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotency manager for mutating Action Plane routes.
 * 
 * P7.2: Ensures that mutating operations can be safely retried without side effects.
 * Stores request/response pairs keyed by idempotency keys with TTL.
 * 
 * @doc.type class
 * @doc.purpose Idempotency management for safe retry of mutating operations
 * @doc.layer product
 * @doc.pattern Idempotency
 */
public final class IdempotencyManager {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyManager.class);
    
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    private final Duration ttl;

    /**
     * Creates an idempotency manager with the specified TTL.
     *
     * @param ttl time-to-live for cached responses
     */
    public IdempotencyManager(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Checks if a request with the given idempotency key has already been processed.
     *
     * @param idempotencyKey the idempotency key
     * @return the cached response if exists and not expired, null otherwise
     */
    public CachedResponse getCachedResponse(String idempotencyKey) {
        CachedResponse cached = cache.get(idempotencyKey);
        if (cached == null) {
            return null;
        }
        
        if (cached.isExpired(ttl)) {
            cache.remove(idempotencyKey);
            logger.debug("Idempotency cache expired for key={}", idempotencyKey);
            return null;
        }
        
        logger.debug("Idempotency cache hit for key={}", idempotencyKey);
        return cached;
    }

    /**
     * Stores a response for the given idempotency key.
     *
     * @param idempotencyKey the idempotency key
     * @param response the response to cache
     */
    public void storeResponse(String idempotencyKey, CachedResponse response) {
        cache.put(idempotencyKey, response);
        logger.debug("Stored idempotency response for key={}", idempotencyKey);
    }

    /**
     * Executes an operation with idempotency checking.
     * If the idempotency key exists in cache, returns the cached response.
     * Otherwise, executes the operation and stores the result.
     *
     * @param idempotencyKey the idempotency key
     * @param operation the operation to execute
     * @return a Promise that resolves to the response
     */
    public Promise<CachedResponse> executeWithIdempotency(
            String idempotencyKey,
            Promise<CachedResponse> operation) {
        
        CachedResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) {
            return Promise.of(cached);
        }
        
        return operation
            .whenResult(response -> storeResponse(idempotencyKey, response))
            .whenException(e -> logger.warn(
                "Idempotency operation failed for key={}, not caching: {}",
                idempotencyKey, e.getMessage()));
    }

    /**
     * Clears expired entries from the cache.
     */
    public void cleanupExpired() {
        Instant now = Instant.now();
        cache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(ttl);
            if (expired) {
                logger.debug("Cleaning up expired idempotency key={}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        cache.clear();
        logger.info("Cleared all idempotency cache entries");
    }

    /**
     * Returns the current cache size.
     *
     * @return number of cached entries
     */
    public int size() {
        return cache.size();
    }

    /**
     * Cached response with timestamp for TTL checking.
     *
     * @param statusCode HTTP status code
     * @param body response body
     * @param headers response headers
     * @param timestamp when the response was cached
     */
    public record CachedResponse(
            int statusCode,
            String body,
            Map<String, String> headers,
            Instant timestamp) {

        public CachedResponse(int statusCode, String body, Map<String, String> headers) {
            this(statusCode, body, headers, Instant.now());
        }

        public boolean isExpired(Duration ttl) {
            return timestamp.plus(ttl).isBefore(Instant.now());
        }
    }
}
