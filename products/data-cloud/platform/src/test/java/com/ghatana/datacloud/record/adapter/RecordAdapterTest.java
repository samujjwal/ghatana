/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.record.adapter;

import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.EventRecord;
import com.ghatana.datacloud.RecordType;
import com.ghatana.datacloud.record.Record;
import com.ghatana.datacloud.record.RecordId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.datacloud.record.impl.FullEntityRecord;
import com.ghatana.datacloud.record.impl.ImmutableEventRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for the JPA ↔ trait record adapters.
 *
 * <p>Verifies bidirectional conversion, field mapping correctness,
 * null handling, and round-trip fidelity.
 */
@DisplayName("Record Adapters")
class RecordAdapterTest {

    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TEST_TENANT = "tenant-alpha";
    private static final String TEST_COLLECTION = "users";
    private static final Instant TEST_CREATED = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant TEST_UPDATED = Instant.parse("2026-01-15T12:00:00Z");
    private static final Map<String, Object> TEST_DATA = Map.of("name", "Alice", "age", 30);
    private static final Map<String, Object> TEST_METADATA = Map.of("source", "api", "version", "1.0");

    // ═══════════════════════════════════════════════════════════════
    //  1. EntityRecordAdapter Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EntityRecordAdapter")
    class EntityRecordAdapterTests {

        @Test
        @DisplayName("toTrait maps all common fields correctly")
        void toTraitMapsAllFields() {
            EntityRecord jpa = EntityRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .data(TEST_DATA)
                    .metadata(TEST_METADATA)
                    .createdAt(TEST_CREATED)
                    .createdBy("system")
                    .version(5)
                    .active(true)
                    .updatedAt(TEST_UPDATED)
                    .updatedBy("admin")
                    .build();

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa);

            assertThat(trait.recordId()).isEqualTo(RecordId.of(TEST_ID));
            assertThat(trait.tenantIdValue()).isEqualTo(TenantId.of(TEST_TENANT));
            assertThat(trait.collectionName()).isEqualTo(TEST_COLLECTION);
            assertThat(trait.data()).containsAllEntriesOf(TEST_DATA);
            assertThat(trait.metadata()).containsAllEntriesOf(TEST_METADATA);
            assertThat(trait.version()).isEqualTo(5L);
            assertThat(trait.createdAt()).isEqualTo(TEST_CREATED);
            assertThat(trait.updatedAt()).isEqualTo(TEST_UPDATED);
            assertThat(trait.createdBy()).isEqualTo("system");
            assertThat(trait.modifiedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("toTrait sets trait-only fields to null/empty")
        void toTraitSetsTraitOnlyDefaults() {
            EntityRecord jpa = minimalJpaEntity();

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa);

            assertThat(trait.schemaVersionValue()).isNull();
            assertThat(trait.aiMetadata()).isEmpty();
            assertThat(trait.aiConfidenceValue()).isNull();
            assertThat(trait.aiExplanationValue()).isNull();
        }

        @Test
        @DisplayName("toTrait handles null version as 0L")
        void toTraitNullVersion() {
            EntityRecord jpa = EntityRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .version(null)
                    .build();

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa);

            assertThat(trait.version()).isEqualTo(0L);
        }

        @Test
        @DisplayName("toJpa maps all common fields correctly")
        void toJpaMapsAllFields() {
            FullEntityRecord trait = new FullEntityRecord(
                    RecordId.of(TEST_ID),
                    TenantId.of(TEST_TENANT),
                    TEST_COLLECTION,
                    TEST_DATA,
                    TEST_METADATA,
                    7L,
                    TEST_CREATED,
                    TEST_UPDATED,
                    "system",
                    "admin",
                    "v2.0",
                    Map.of("model", "gpt-4"),
                    0.95,
                    "high confidence"
            );

            EntityRecord jpa = EntityRecordAdapter.toJpa(trait);

            assertThat(jpa.getId()).isEqualTo(TEST_ID);
            assertThat(jpa.getTenantId()).isEqualTo(TEST_TENANT);
            assertThat(jpa.getCollectionName()).isEqualTo(TEST_COLLECTION);
            assertThat(jpa.getData()).containsAllEntriesOf(TEST_DATA);
            assertThat(jpa.getMetadata()).containsAllEntriesOf(TEST_METADATA);
            assertThat(jpa.getVersion()).isEqualTo(7);
            assertThat(jpa.getCreatedAt()).isEqualTo(TEST_CREATED);
            assertThat(jpa.getUpdatedAt()).isEqualTo(TEST_UPDATED);
            assertThat(jpa.getCreatedBy()).isEqualTo("system");
            assertThat(jpa.getUpdatedBy()).isEqualTo("admin");
            assertThat(jpa.getActive()).isTrue();
        }

        @Test
        @DisplayName("toJpa creates mutable maps")
        void toJpaCreatesMutableMaps() {
            FullEntityRecord trait = minimalTraitEntity();

            EntityRecord jpa = EntityRecordAdapter.toJpa(trait);

            // Maps should be mutable for JPA
            jpa.getData().put("new-key", "new-value");
            assertThat(jpa.getData()).containsKey("new-key");
        }

        @Test
        @DisplayName("round-trip JPA → trait → JPA preserves common fields")
        void roundTripJpaTraitJpa() {
            EntityRecord original = EntityRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .data(TEST_DATA)
                    .metadata(TEST_METADATA)
                    .createdAt(TEST_CREATED)
                    .createdBy("system")
                    .version(3)
                    .updatedAt(TEST_UPDATED)
                    .updatedBy("admin")
                    .build();

            FullEntityRecord trait = EntityRecordAdapter.toTrait(original);
            EntityRecord roundTripped = EntityRecordAdapter.toJpa(trait);

            assertThat(roundTripped.getId()).isEqualTo(original.getId());
            assertThat(roundTripped.getTenantId()).isEqualTo(original.getTenantId());
            assertThat(roundTripped.getCollectionName()).isEqualTo(original.getCollectionName());
            assertThat(roundTripped.getData()).isEqualTo(original.getData());
            assertThat(roundTripped.getMetadata()).isEqualTo(original.getMetadata());
            assertThat(roundTripped.getVersion()).isEqualTo(original.getVersion());
            assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
            assertThat(roundTripped.getCreatedBy()).isEqualTo(original.getCreatedBy());
            assertThat(roundTripped.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
            assertThat(roundTripped.getUpdatedBy()).isEqualTo(original.getUpdatedBy());
        }

        @Test
        @DisplayName("toTrait throws on null input")
        void toTraitThrowsOnNull() {
            assertThatThrownBy(() -> EntityRecordAdapter.toTrait(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("toJpa throws on null input")
        void toJpaThrowsOnNull() {
            assertThatThrownBy(() -> EntityRecordAdapter.toJpa(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. EventRecordAdapter Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventRecordAdapter")
    class EventRecordAdapterTests {

        private static final String TEST_STREAM = "order-events";
        private static final Instant TEST_OCCURRED = Instant.parse("2026-01-15T10:30:00Z");
        private static final Instant TEST_INGESTED = Instant.parse("2026-01-15T10:30:05Z");

        @Test
        @DisplayName("toTrait maps all common fields correctly")
        void toTraitMapsAllFields() {
            EventRecord jpa = EventRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .data(TEST_DATA)
                    .metadata(TEST_METADATA)
                    .streamName(TEST_STREAM)
                    .eventOffset(42L)
                    .occurrenceTime(TEST_OCCURRED)
                    .detectionTime(TEST_INGESTED)
                    .correlationId("corr-123")
                    .causationId("cause-456")
                    .build();

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa);

            assertThat(trait.recordId()).isEqualTo(RecordId.of(TEST_ID));
            assertThat(trait.tenantIdValue()).isEqualTo(TenantId.of(TEST_TENANT));
            assertThat(trait.collectionName()).isEqualTo(TEST_COLLECTION);
            assertThat(trait.streamName()).isEqualTo(TEST_STREAM);
            assertThat(trait.offset()).isEqualTo(42L);
            assertThat(trait.data()).containsAllEntriesOf(TEST_DATA);
            assertThat(trait.headers()).containsAllEntriesOf(TEST_METADATA);
            assertThat(trait.occurredAt()).isEqualTo(TEST_OCCURRED);
            assertThat(trait.ingestedAt()).isEqualTo(TEST_INGESTED);
            assertThat(trait.correlationId()).isEqualTo("corr-123");
            assertThat(trait.causationId()).isEqualTo("cause-456");
        }

        @Test
        @DisplayName("toTrait maps JPA metadata to trait headers")
        void toTraitMapsMetadataToHeaders() {
            EventRecord jpa = EventRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .streamName(TEST_STREAM)
                    .metadata(Map.of("content-type", "application/json"))
                    .occurrenceTime(TEST_OCCURRED)
                    .detectionTime(TEST_INGESTED)
                    .build();

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa);

            assertThat(trait.headers()).containsEntry("content-type", "application/json");
        }

        @Test
        @DisplayName("toTrait handles null eventOffset as 0L")
        void toTraitNullOffset() {
            EventRecord jpa = EventRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .streamName(TEST_STREAM)
                    .eventOffset(null)
                    .occurrenceTime(TEST_OCCURRED)
                    .detectionTime(TEST_INGESTED)
                    .build();

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa);

            assertThat(trait.offset()).isEqualTo(0L);
        }

        @Test
        @DisplayName("toJpa maps all common fields correctly")
        void toJpaMapsAllFields() {
            ImmutableEventRecord trait = new ImmutableEventRecord(
                    RecordId.of(TEST_ID),
                    TenantId.of(TEST_TENANT),
                    TEST_COLLECTION,
                    TEST_STREAM,
                    42L,
                    TEST_DATA,
                    TEST_METADATA,
                    TEST_OCCURRED,
                    TEST_INGESTED,
                    "corr-123",
                    "cause-456",
                    "v1.0"
            );

            EventRecord jpa = EventRecordAdapter.toJpa(trait);

            assertThat(jpa.getId()).isEqualTo(TEST_ID);
            assertThat(jpa.getTenantId()).isEqualTo(TEST_TENANT);
            assertThat(jpa.getCollectionName()).isEqualTo(TEST_COLLECTION);
            assertThat(jpa.getStreamName()).isEqualTo(TEST_STREAM);
            assertThat(jpa.getEventOffset()).isEqualTo(42L);
            assertThat(jpa.getData()).containsAllEntriesOf(TEST_DATA);
            assertThat(jpa.getMetadata()).containsAllEntriesOf(TEST_METADATA);
            assertThat(jpa.getOccurrenceTime()).isEqualTo(TEST_OCCURRED);
            assertThat(jpa.getDetectionTime()).isEqualTo(TEST_INGESTED);
            assertThat(jpa.getCorrelationId()).isEqualTo("corr-123");
            assertThat(jpa.getCausationId()).isEqualTo("cause-456");
        }

        @Test
        @DisplayName("toJpa maps trait headers to JPA metadata")
        void toJpaMapsHeadersToMetadata() {
            ImmutableEventRecord trait = new ImmutableEventRecord(
                    RecordId.of(TEST_ID),
                    TenantId.of(TEST_TENANT),
                    TEST_COLLECTION,
                    TEST_STREAM,
                    0L,
                    Map.of(),
                    Map.of("x-trace-id", "abc"),
                    TEST_OCCURRED,
                    TEST_INGESTED,
                    null,
                    null,
                    null
            );

            EventRecord jpa = EventRecordAdapter.toJpa(trait);

            assertThat(jpa.getMetadata()).containsEntry("x-trace-id", "abc");
        }

        @Test
        @DisplayName("round-trip JPA → trait → JPA preserves common fields")
        void roundTripJpaTraitJpa() {
            EventRecord original = EventRecord.builder()
                    .id(TEST_ID)
                    .tenantId(TEST_TENANT)
                    .collectionName(TEST_COLLECTION)
                    .data(TEST_DATA)
                    .metadata(TEST_METADATA)
                    .streamName(TEST_STREAM)
                    .eventOffset(100L)
                    .occurrenceTime(TEST_OCCURRED)
                    .detectionTime(TEST_INGESTED)
                    .correlationId("corr-abc")
                    .causationId("cause-xyz")
                    .build();

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(original);
            EventRecord roundTripped = EventRecordAdapter.toJpa(trait);

            assertThat(roundTripped.getId()).isEqualTo(original.getId());
            assertThat(roundTripped.getTenantId()).isEqualTo(original.getTenantId());
            assertThat(roundTripped.getCollectionName()).isEqualTo(original.getCollectionName());
            assertThat(roundTripped.getStreamName()).isEqualTo(original.getStreamName());
            assertThat(roundTripped.getEventOffset()).isEqualTo(original.getEventOffset());
            assertThat(roundTripped.getData()).isEqualTo(original.getData());
            assertThat(roundTripped.getOccurrenceTime()).isEqualTo(original.getOccurrenceTime());
            assertThat(roundTripped.getDetectionTime()).isEqualTo(original.getDetectionTime());
            assertThat(roundTripped.getCorrelationId()).isEqualTo(original.getCorrelationId());
            assertThat(roundTripped.getCausationId()).isEqualTo(original.getCausationId());
        }

        @Test
        @DisplayName("toTrait throws on null input")
        void toTraitThrowsOnNull() {
            assertThatThrownBy(() -> EventRecordAdapter.toTrait(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("toJpa throws on null input")
        void toJpaThrowsOnNull() {
            assertThatThrownBy(() -> EventRecordAdapter.toJpa(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("must not be null");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. RecordTypeMapper Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RecordTypeMapper")
    class RecordTypeMapperTests {

        @Test
        @DisplayName("toTrait maps all 5 JPA record types")
        void toTraitMapsAll() {
            assertThat(RecordTypeMapper.toTrait(RecordType.ENTITY)).isEqualTo(Record.RecordType.ENTITY);
            assertThat(RecordTypeMapper.toTrait(RecordType.EVENT)).isEqualTo(Record.RecordType.EVENT);
            assertThat(RecordTypeMapper.toTrait(RecordType.TIMESERIES)).isEqualTo(Record.RecordType.TIMESERIES);
            assertThat(RecordTypeMapper.toTrait(RecordType.DOCUMENT)).isEqualTo(Record.RecordType.DOCUMENT);
            assertThat(RecordTypeMapper.toTrait(RecordType.GRAPH)).isEqualTo(Record.RecordType.GRAPH);
        }

        @Test
        @DisplayName("toJpa maps all 5 trait record types")
        void toJpaMapsAll() {
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.ENTITY)).isEqualTo(RecordType.ENTITY);
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.EVENT)).isEqualTo(RecordType.EVENT);
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.TIMESERIES)).isEqualTo(RecordType.TIMESERIES);
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.DOCUMENT)).isEqualTo(RecordType.DOCUMENT);
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.GRAPH)).isEqualTo(RecordType.GRAPH);
        }

        @Test
        @DisplayName("round-trip preserves enum identity")
        void roundTrips() {
            for (RecordType jpa : RecordType.values()) {
                Record.RecordType trait = RecordTypeMapper.toTrait(jpa);
                RecordType backToJpa = RecordTypeMapper.toJpa(trait);
                assertThat(backToJpa).isEqualTo(jpa);
            }
        }

        @Test
        @DisplayName("toTrait throws on null")
        void toTraitThrowsOnNull() {
            assertThatThrownBy(() -> RecordTypeMapper.toTrait(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("toJpa throws on null")
        void toJpaThrowsOnNull() {
            assertThatThrownBy(() -> RecordTypeMapper.toJpa(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private static EntityRecord minimalJpaEntity() {
        return EntityRecord.builder()
                .id(TEST_ID)
                .tenantId(TEST_TENANT)
                .collectionName(TEST_COLLECTION)
                .createdAt(TEST_CREATED)
                .build();
    }

    private static FullEntityRecord minimalTraitEntity() {
        return new FullEntityRecord(
                RecordId.of(TEST_ID),
                TenantId.of(TEST_TENANT),
                TEST_COLLECTION,
                Map.of(),
                Map.of(),
                0L,
                TEST_CREATED,
                TEST_CREATED,
                null,
                null,
                null,
                Map.of(),
                null,
                null
        );
    }
}
