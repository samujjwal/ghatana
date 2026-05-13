package com.ghatana.platform.database.idempotency;

/**
 * @doc.type class
 * @doc.purpose Signals conflicting reuse of an idempotency key with different request content
 * @doc.layer platform
 * @doc.pattern Exception
 */
public final class IdempotencyConflictException extends IllegalStateException {

    public IdempotencyConflictException(String operation, String key) {
        super("Idempotency key '" + key + "' for operation '" + operation
            + "' was already used with different content");
    }
}
