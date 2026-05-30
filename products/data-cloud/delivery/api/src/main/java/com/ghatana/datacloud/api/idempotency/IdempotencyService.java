/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.idempotency;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service for managing idempotency keys to ensure safe retry of mutating operations.
 *
 * <p>Stores request/response pairs keyed by idempotency key. When a request with
 * an existing key is received, the stored response is returned instead of executing
 * the operation again.
 *
 * @doc.type interface
 * @doc.purpose Idempotency key storage and validation
 * @doc.layer product
 * @doc.pattern Service
 */
public interface IdempotencyService {

    /**
     * Check if an idempotency key has been used before.
     *
     * @param key idempotency key
     * @return promise of stored response (empty if key not used)
     */
    Promise<Optional<IdempotencyRecord>> get(String key);

    /**
     * Store an idempotency key with its response.
     *
     * @param key idempotency key
     * @param record response record to store
     * @return promise completing when stored
     */
    Promise<Void> store(String key, IdempotencyRecord record);

    /**
     * Delete an idempotency key (for cleanup).
     *
     * @param key idempotency key
     * @return promise completing when deleted
     */
    Promise<Void> delete(String key);

    /**
     * Check if an operation is idempotent.
     *
     * @param method HTTP method
     * @param path request path
     * @return true if operation supports idempotency
     */
    boolean isIdempotentOperation(String method, String path);

    /**
     * Record of an idempotent request/response pair.
     */
    record IdempotencyRecord(
        String key,
        String method,
        String path,
        String requestBodyHash,
        int statusCode,
        String responseBody,
        long createdAt
    ) {
        public IdempotencyRecord {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            if (method == null || method.isBlank()) {
                throw new IllegalArgumentException("method must not be blank");
            }
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("path must not be blank");
            }
            if (statusCode < 100 || statusCode >= 600) {
                throw new IllegalArgumentException("statusCode must be valid HTTP status");
            }
        }
    }
}
