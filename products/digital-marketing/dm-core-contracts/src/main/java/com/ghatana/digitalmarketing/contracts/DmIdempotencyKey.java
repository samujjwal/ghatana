package com.ghatana.digitalmarketing.contracts;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed wrapper for a DMOS idempotency key.
 *
 * <p>All write commands that may be retried must carry an {@code DmIdempotencyKey}.
 * Retrying the same command with the same key must produce the same observable
 * result as the first execution without creating duplicate side-effects.</p>
 *
 * <p>Keys should be stable across retries. Use {@link #forCommand(String, String)}
 * to derive a deterministic key from a command type and entity ID, or
 * {@link #generate()} for one-shot commands.</p>
 *
 * @doc.type class
 * @doc.purpose Idempotency key value object preventing duplicate write side-effects
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmIdempotencyKey {

    private final String value;

    private DmIdempotencyKey(String value) {
        this.value = value;
    }

    /**
     * Generates a random idempotency key (suitable for one-shot, non-retried operations).
     */
    public static DmIdempotencyKey generate() {
        return new DmIdempotencyKey(UUID.randomUUID().toString());
    }

    /**
     * Creates a deterministic idempotency key from a command type and entity identifier.
     * Using the same command type and entity ID always produces the same key,
     * making it safe to retry with the same key.
     *
     * @param commandType the fully-qualified command type name
     * @param entityId    the entity being acted upon
     * @return a deterministic idempotency key
     */
    public static DmIdempotencyKey forCommand(String commandType, String entityId) {
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        return new DmIdempotencyKey(commandType + ":" + entityId);
    }

    /**
     * Creates an idempotency key from a raw string.
     *
     * @param value the key string; must not be blank
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public static DmIdempotencyKey of(String value) {
        Objects.requireNonNull(value, "idempotencyKey must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        return new DmIdempotencyKey(value);
    }

    /** Returns the raw string value. Never {@code null} or blank. */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return value.equals(((DmIdempotencyKey) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "DmIdempotencyKey{" + value + '}';
    }
}
