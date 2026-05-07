/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.record.adapter;

import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.record.RecordId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.datacloud.record.impl.FullEntityRecord;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bidirectional adapter between JPA {@link EntityRecord} and trait-based
 * {@link FullEntityRecord}.
 *
 * <p>Bridges the persistence boundary: all application services use the
 * immutable trait-based {@link FullEntityRecord}, while the JPA layer uses
 * the mutable {@link EntityRecord} for ORM operations.
 *
 * <h3>Field mapping</h3>
 * <pre>
 * JPA EntityRecord              | Trait FullEntityRecord
 * ────────────────────────────  │ ──────────────────────────
 * id (UUID)                     → recordId (RecordId)
 * tenantId (String)             → tenantIdValue (TenantId)
 * collectionName                → collectionName
 * data (mutable Map)            → data (immutable Map)
 * metadata (mutable Map)        → metadata (immutable Map)
 * version (Integer)             → version (long)
 * createdAt                     → createdAt
 * updatedAt                     → updatedAt
 * createdBy                     → createdBy
 * updatedBy                     → modifiedBy         ← name mismatch
 * active (Boolean)              → (dropped — entity-only JPA field)
 * (none)                        → schemaVersionValue  ← trait-only
 * (none)                        → aiMetadata          ← trait-only
 * (none)                        → aiConfidenceValue   ← trait-only
 * (none)                        → aiExplanationValue  ← trait-only
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Bidirectional adapter between JPA entities and trait-based records
 * @doc.layer product
 * @doc.pattern Adapter
 * @see FullEntityRecord
 * @see EntityRecord
 */
public final class EntityRecordAdapter {

    private EntityRecordAdapter() {
        // Utility class — no instantiation
    }

    /**
     * Converts a JPA {@link EntityRecord} to a trait-based {@link FullEntityRecord}.
     *
     * <p>Value objects are created: UUID → {@link RecordId}, String → {@link TenantId}.
     * Mutable JPA maps become immutable copies via the record's compact constructor.
     * JPA-only fields ({@code active}) are dropped. Trait-only fields
     * ({@code schemaVersionValue}, AI fields) default to null/empty.
     *
     * @param jpaRecord the JPA entity record (must not be null)
     * @return immutable trait-based record
     * @throws NullPointerException if jpaRecord is null, or if required fields
     *                              (id, tenantId, collectionName) are null
     */
    public static FullEntityRecord toTrait(EntityRecord jpaRecord) {
        Objects.requireNonNull(jpaRecord, "jpaRecord must not be null");

        return new FullEntityRecord(
                RecordId.of(jpaRecord.getId()),
                TenantId.of(jpaRecord.getTenantId()),
                jpaRecord.getCollectionName(),
                jpaRecord.getData(),
                jpaRecord.getMetadata(),
                jpaRecord.getVersion() != null ? jpaRecord.getVersion().longValue() : 0L,
                jpaRecord.getCreatedAt(),
                jpaRecord.getUpdatedAt(),
                jpaRecord.getCreatedBy(),
                jpaRecord.getUpdatedBy(),
                null,
                Map.of(),
                null,
                null
        );
    }

    /**
     * Converts a trait-based {@link FullEntityRecord} to a JPA {@link EntityRecord}.
     *
     * <p>Value objects are unwrapped: {@link RecordId} → UUID, {@link TenantId} → String.
     * Immutable maps become mutable copies for JPA persistence.
     * Trait-only fields ({@code schemaVersionValue}, AI fields) are dropped.
     * JPA-only fields default: {@code active = true}.
     *
     * @param traitRecord the trait-based record (must not be null)
     * @return mutable JPA entity record
     * @throws NullPointerException if traitRecord is null
     */
    public static EntityRecord toJpa(FullEntityRecord traitRecord) {
        Objects.requireNonNull(traitRecord, "traitRecord must not be null");

        return EntityRecord.builder()
                .id(traitRecord.recordId().value())
                .tenantId(traitRecord.tenantIdValue().value())
                .collectionName(traitRecord.collectionName())
                .data(new HashMap<>(traitRecord.data()))
                .metadata(new HashMap<>(traitRecord.metadata()))
                .createdAt(traitRecord.createdAt())
                .createdBy(traitRecord.createdBy())
                .version((int) traitRecord.version())
                .active(true)
                .updatedAt(traitRecord.updatedAt())
                .updatedBy(traitRecord.modifiedBy())
                .build();
    }
}
