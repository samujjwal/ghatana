package com.ghatana.appplatform.eventstore.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps a handler with idempotency enforcement: if a key was already seen,
 * returns the cached response hash without re-executing the handler.
 *
 * <p>Usage:
 * <pre>
 *   IdempotencyGuard guard = new IdempotencyGuard(idempotencyStore, 3600);
 *   String result = guard.guard(tenantId, requestKey, payload, () -> process(payload));
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Idempotency enforcement middleware (STORY-K05-014)
 * @doc.layer product
 * @doc.pattern Decorator
 */
public class IdempotencyGuard {

    private final IdempotencyStore store;
    private final int defaultTtlSeconds;

    public IdempotencyGuard(IdempotencyStore store, int defaultTtlSeconds) {
        this.store = store;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    /**
     * Execute {@code handler} exactly once for the given idempotency key.
     * If the key was already processed, returns the cached response without re-execution.
     *
     * @param tenantId       Tenant scope
     * @param idempotencyKey Unique client key
     * @param payload        Raw payload (used to derive hash for cache reply)
     * @param handler        The operation to execute once
     * @return handler result or cached result on duplicate
     */
    public String guard(String tenantId, String idempotencyKey, String payload, Supplier<String> handler) {
        Optional<String> cached = store.getResponseHash(tenantId, idempotencyKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        String result = handler.get();
        String responseHash = sha256Hex(result);
        store.claim(tenantId, idempotencyKey, responseHash, defaultTtlSeconds);
        return result;
    }

    /**
     * Check if an idempotency key has already been processed.
     *
     * @return true if this key has a cached result
     */
    public boolean isDuplicate(String tenantId, String idempotencyKey) {
        return store.getResponseHash(tenantId, idempotencyKey).isPresent();
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
