/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable domain record representing a single outbox entry (K17-001).
 *
 * <p>An outbox entry is created atomically with its corresponding {@code Journal}
 * post in the same DB transaction. A relay service then reads unpublished entries
 * and delivers them to the message broker, ensuring exactly-once delivery via
 * {@code SELECT FOR UPDATE SKIP LOCKED}.
 *
 * @doc.type record
 * @doc.purpose Single outbox entry for transactional outbox pattern (K17-001)
 * @doc.layer core
 * @doc.pattern EventSourced
 */
public record OutboxEntry(
        UUID id,
        UUID aggregateId,
        String aggregateType,
        String eventType,
        String payload,         // JSON string
        UUID tenantId,
        Instant createdAt,
        boolean published,
        Instant publishedAt,
        int publishAttempts,
        String lastError
) {

    /**
     * Factory for creating a new, unpublished outbox entry.
     *
     * @param aggregateId    the aggregate's identifier
     * @param aggregateType  e.g., "Journal", "Account"
     * @param eventType      e.g., "JournalPosted", "AccountCreated"
     * @param payload        JSON string representing the event payload
     * @param tenantId       tenant scoping
     * @return new unpublished outbox entry
     */
    public static OutboxEntry create(UUID aggregateId, String aggregateType,
                                     String eventType, String payload, UUID tenantId) {
        return new OutboxEntry(
                UUID.randomUUID(),
                aggregateId,
                aggregateType,
                eventType,
                payload,
                tenantId,
                Instant.now(),
                false,
                null,
                0,
                null
        );
    }

    /**
     * Returns true when this entry is ready for delivery (unpublished, attempts < 5).
     */
    public boolean isEligibleForDelivery() {
        return !published && publishAttempts < 5;
    }
}
