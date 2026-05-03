package com.ghatana.digitalmarketing.persistence.idempotency;

import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService.IdempotentResponse;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;

/**
 * Repository for idempotency token persistence.
 *
 * @doc.type interface
 * @doc.purpose Idempotency token repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface IdempotencyTokenRepository {

    /**
     * Finds a cached response by idempotency key within a workspace.
     *
     * @param workspaceId the workspace identifier
     * @param idempotencyKey the idempotency key
     * @return promise resolving to an optional cached response
     */
    Promise<IdempotentResponse> findByKey(DmWorkspaceId workspaceId, String idempotencyKey);

    /**
     * Stores a response for an idempotency key.
     *
     * @param workspaceId the workspace identifier
     * @param idempotencyKey the idempotency key
     * @param response the response to cache
     * @param expiresAt when the token should expire
     * @return promise resolving when stored
     */
    Promise<Void> store(DmWorkspaceId workspaceId, String idempotencyKey, IdempotentResponse response, java.time.Instant expiresAt);

    /**
     * Deletes expired idempotency tokens.
     *
     * @return promise resolving to the number of tokens deleted
     */
    Promise<Integer> deleteExpired();
}
