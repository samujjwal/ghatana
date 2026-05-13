package com.ghatana.platform.database.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @doc.type class
 * @doc.purpose In-memory Kernel idempotency store for local and test profiles
 * @doc.layer platform
 * @doc.pattern Repository
 */
public final class InMemoryIdempotencyStore<T> implements IdempotencyStore<T> {

    private final ConcurrentHashMap<String, IdempotencyRecord<T>> records = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;
    private volatile IdempotencyAuditEvent lastAuditEvent;

    public InMemoryIdempotencyStore(Duration ttl, Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
    }

    @Override
    public IdempotencyReplayDecision<T> findReplay(String operation, String key, String fingerprint) {
        validateRequest(operation, key, fingerprint);
        String storageKey = storageKey(operation, key);
        IdempotencyRecord<T> existing = records.get(storageKey);
        Instant now = Instant.now(clock);

        if (existing == null) {
            return replayDecision(audit(operation, key, fingerprint, now, IdempotencyDecision.MISS));
        }

        if (existing.isExpired(now)) {
            records.remove(storageKey, existing);
            return replayDecision(audit(operation, key, fingerprint, now, IdempotencyDecision.EXPIRED));
        }

        if (!existing.fingerprint().equals(fingerprint)) {
            audit(operation, key, fingerprint, now, IdempotencyDecision.CONFLICT);
            throw new IdempotencyConflictException(operation, key);
        }

        IdempotencyAuditEvent auditEvent = audit(
            operation,
            key,
            fingerprint,
            now,
            IdempotencyDecision.COMPLETED
        );
        return new IdempotencyReplayDecision<>(
            IdempotencyDecision.COMPLETED,
            Optional.of(existing.result()),
            auditEvent
        );
    }

    @Override
    public T putIfAbsent(String operation, String key, String fingerprint, T result) {
        validateRequest(operation, key, fingerprint);
        Objects.requireNonNull(result, "result must not be null");

        String storageKey = storageKey(operation, key);
        while (true) {
            Instant now = Instant.now(clock);
            IdempotencyRecord<T> candidate = new IdempotencyRecord<>(
                operation,
                key,
                fingerprint,
                result,
                now,
                now.plus(ttl)
            );
            IdempotencyRecord<T> existing = records.putIfAbsent(storageKey, candidate);
            if (existing == null) {
                audit(operation, key, fingerprint, now, IdempotencyDecision.MISS);
                return result;
            }

            if (existing.isExpired(now)) {
                if (records.remove(storageKey, existing)) {
                    audit(operation, key, fingerprint, now, IdempotencyDecision.EXPIRED);
                }
                continue;
            }

            if (!existing.fingerprint().equals(fingerprint)) {
                audit(operation, key, fingerprint, now, IdempotencyDecision.CONFLICT);
                throw new IdempotencyConflictException(operation, key);
            }

            audit(operation, key, fingerprint, now, IdempotencyDecision.COMPLETED);
            return existing.result();
        }
    }

    @Override
    public IdempotencyAuditEvent lastAuditEvent() {
        return lastAuditEvent;
    }

    public int size() {
        return records.size();
    }

    public void clear() {
        records.clear();
        lastAuditEvent = null;
    }

    private IdempotencyReplayDecision<T> replayDecision(IdempotencyAuditEvent auditEvent) {
        return new IdempotencyReplayDecision<>(auditEvent.decision(), Optional.empty(), auditEvent);
    }

    private IdempotencyAuditEvent audit(
            String operation,
            String key,
            String fingerprint,
            Instant now,
            IdempotencyDecision decision) {
        IdempotencyAuditEvent auditEvent = new IdempotencyAuditEvent(
            decision,
            operation,
            key,
            fingerprint,
            now,
            decision == IdempotencyDecision.COMPLETED,
            decision == IdempotencyDecision.EXPIRED,
            decision == IdempotencyDecision.CONFLICT
        );
        lastAuditEvent = auditEvent;
        return auditEvent;
    }

    private static void validateRequest(String operation, String key, String fingerprint) {
        requireNonBlank(operation, "operation");
        requireNonBlank(key, "key");
        requireNonBlank(fingerprint, "fingerprint");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static String storageKey(String operation, String key) {
        return operation + "\u001F" + key;
    }
}
