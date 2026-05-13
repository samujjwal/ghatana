package com.ghatana.platform.database.idempotency;

/**
 * @doc.type interface
 * @doc.purpose Kernel SPI for replay-safe mutation idempotency storage
 * @doc.layer platform
 * @doc.pattern Port
 */
public interface IdempotencyStore<T> {

    IdempotencyReplayDecision<T> findReplay(String operation, String key, String fingerprint);

    T putIfAbsent(String operation, String key, String fingerprint, T result);

    IdempotencyAuditEvent lastAuditEvent();
}
