/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.record.adapter;

import com.ghatana.datacloud.EventRecord;
import com.ghatana.datacloud.record.RecordId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.datacloud.record.impl.ImmutableEventRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bidirectional adapter between JPA {@link EventRecord} and trait-based
 * {@link ImmutableEventRecord}.
 *
 * <p>Bridges the persistence boundary for event records. The immutable
 * {@link ImmutableEventRecord} is the canonical domain representation;
 * the JPA {@link EventRecord} is used only for ORM persistence.
 *
 * <h3>Field mapping</h3>
 * <pre>
 * JPA EventRecord               | Trait ImmutableEventRecord
 * ────────────────────────────  │ ──────────────────────────
 * id (UUID)                     → recordId (RecordId)
 * tenantId (String)             → tenantIdValue (TenantId)
 * collectionName                → collectionName
 * streamName                    → streamName
 * eventOffset (Long)            → offset (long)       ← name mismatch
 * data (mutable Map)            → data (immutable Map)
 * metadata (mutable Map)        → headers (Map)       ← semantic mapping
 * occurrenceTime                → occurredAt          ← name mismatch
 * detectionTime                 → ingestedAt          ← name mismatch
 * correlationId                 → correlationId
 * causationId                   → causationId
 * partitionId (Integer)         → (dropped — JPA-only)
 * idempotencyKey                → (dropped — JPA-only)
 * createdAt                     → (dropped — use occurredAt)
 * createdBy                     → (dropped — events are system-generated)
 * (none)                        → schemaVersionValue  ← trait-only
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Bidirectional adapter between JPA events and trait-based events
 * @doc.layer product
 * @doc.pattern Adapter
 * @see ImmutableEventRecord
 * @see EventRecord
 */
public final class EventRecordAdapter {

    private EventRecordAdapter() {
        // Utility class — no instantiation
    }

    /**
     * Converts a JPA {@link EventRecord} to a trait-based {@link ImmutableEventRecord}.
     *
     * <p>Value objects are created: UUID → {@link RecordId}, String → {@link TenantId}.
     * Mutable JPA maps become immutable copies via the record's compact constructor.
     * JPA {@code metadata} maps to trait {@code headers}. JPA-only fields
     * ({@code partitionId}, {@code idempotencyKey}) are dropped.
     *
     * @param jpaRecord the JPA event record (must not be null)
     * @return immutable trait-based event record
     * @throws NullPointerException if jpaRecord is null, or if required fields
     *                              (id, tenantId, collectionName, streamName) are null
     */
    public static ImmutableEventRecord toTrait(EventRecord jpaRecord) {
        Objects.requireNonNull(jpaRecord, "jpaRecord must not be null");

        return new ImmutableEventRecord(
                RecordId.of(jpaRecord.getId()),
                TenantId.of(jpaRecord.getTenantId()),
                jpaRecord.getCollectionName(),
                jpaRecord.getStreamName(),
                jpaRecord.getEventOffset() != null ? jpaRecord.getEventOffset() : 0L,
                jpaRecord.getData(),
                jpaRecord.getMetadata() != null ? jpaRecord.getMetadata() : Map.of(),
                jpaRecord.getOccurrenceTime(),
                jpaRecord.getDetectionTime(),
                jpaRecord.getCorrelationId(),
                jpaRecord.getCausationId(),
                null
        );
    }

    /**
     * Converts a trait-based {@link ImmutableEventRecord} to a JPA {@link EventRecord}.
     *
     * <p>Value objects are unwrapped: {@link RecordId} → UUID, {@link TenantId} → String.
     * Immutable maps become mutable copies for JPA persistence.
     * Trait {@code headers} maps to JPA {@code metadata}. JPA-only fields default:
     * {@code partitionId = 0}, {@code idempotencyKey = null}.
     *
     * @param traitRecord the trait-based event record (must not be null)
     * @return mutable JPA event record
     * @throws NullPointerException if traitRecord is null
     */
    public static EventRecord toJpa(ImmutableEventRecord traitRecord) {
        Objects.requireNonNull(traitRecord, "traitRecord must not be null");

        return EventRecord.builder()
                .id(traitRecord.recordId().value())
                .tenantId(traitRecord.tenantIdValue().value())
                .collectionName(traitRecord.collectionName())
                .data(new HashMap<>(traitRecord.data()))
                .metadata(new HashMap<>(traitRecord.headers()))
                .createdAt(traitRecord.occurredAt())
                .createdBy(null)
                .streamName(traitRecord.streamName())
                .eventOffset(traitRecord.offset())
                .occurrenceTime(traitRecord.occurredAt())
                .detectionTime(traitRecord.ingestedAt())
                .correlationId(traitRecord.correlationId())
                .causationId(traitRecord.causationId())
                .build();
    }
}
