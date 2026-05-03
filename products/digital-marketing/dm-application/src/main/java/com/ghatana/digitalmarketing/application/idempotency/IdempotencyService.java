package com.ghatana.digitalmarketing.application.idempotency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

/**
 * Service for handling idempotency to prevent duplicate request execution.
 *
 * <p>Idempotency (DMOS-P0-6): Clients can provide an idempotency key with their request.
 * If the same key is seen again within the expiration window, the cached response
 * is returned without re-executing the operation.</p>
 *
 * @doc.type interface
 * @doc.purpose Idempotency service for duplicate request prevention
 * @doc.layer product
 * @doc.pattern Service
 */
public interface IdempotencyService {

    /**
     * Checks if an idempotency key exists and returns the cached response if present.
     *
     * @param ctx the operation context
     * @param idempotencyKey the client-provided idempotency key
     * @return promise resolving to an optional cached response
     */
    Promise<IdempotentResponse> getCachedResponse(DmOperationContext ctx, String idempotencyKey);

    /**
     * Stores a response for an idempotency key.
     *
     * @param ctx the operation context
     * @param idempotencyKey the client-provided idempotency key
     * @param response the response to cache
     * @return promise resolving when the response is stored
     */
    Promise<Void> storeResponse(DmOperationContext ctx, String idempotencyKey, IdempotentResponse response);

    /**
     * Generates a unique idempotency key if not provided by the client.
     *
     * @return a unique idempotency key (UUID)
     */
    String generateIdempotencyKey();

    /**
     * Cached response for idempotent operations.
     */
    record IdempotentResponse(
            String body,
            int statusCode,
            String headers
    ) {
        public IdempotentResponse {
            if (body == null) throw new IllegalArgumentException("body must not be null");
            if (statusCode < 100 || statusCode >= 600) {
                throw new IllegalArgumentException("Invalid status code: " + statusCode);
            }
        }
    }
}
