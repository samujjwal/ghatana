package com.ghatana.platform.observability.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a cached idempotency entry for a completed operation.
 *
 * <p>P0-07: Stores the response of an idempotent operation along with metadata
 * to enable safe replay on retries with the same idempotency key.
 *
 * @doc.type class
 * @doc.purpose Cached idempotency entry for completed operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class IdempotencyEntry {

    private final String idempotencyKey;
    private final String payloadHash;
    private final Object response;
    private final Instant createdAt;
    private final Instant expiresAt;

    /**
     * Creates a new idempotency entry.
     *
     * @param idempotencyKey the client-provided idempotency key
     * @param payloadHash hash of the request payload
     * @param response the cached response
     * @param createdAt when the entry was created
     * @param expiresAt when the entry expires (for cleanup)
     */
    public IdempotencyEntry(String idempotencyKey, String payloadHash, Object response,
                           Instant createdAt, Instant expiresAt) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        this.payloadHash = Objects.requireNonNull(payloadHash);
        this.response = Objects.requireNonNull(response);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    /**
     * Creates a new idempotency entry with a default TTL of 24 hours.
     */
    public IdempotencyEntry(String idempotencyKey, String payloadHash, Object response) {
        this(idempotencyKey, payloadHash, response, Instant.now(), Instant.now().plusSeconds(86400));
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String payloadHash() {
        return payloadHash;
    }

    public Object response() {
        return response;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this entry has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotencyEntry that = (IdempotencyEntry) o;
        return Objects.equals(idempotencyKey, that.idempotencyKey) &&
               Objects.equals(payloadHash, that.payloadHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idempotencyKey, payloadHash);
    }

    @Override
    public String toString() {
        return "IdempotencyEntry{" +
               "idempotencyKey='" + idempotencyKey + '\'' +
               ", payloadHash='" + payloadHash + '\'' +
               ", createdAt=" + createdAt +
               ", expiresAt=" + expiresAt +
               '}';
    }
}
