/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("Record Adapters [GH-90000]")
class RecordAdapterTest {

    private static final UUID TEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001 [GH-90000]");
    private static final String TEST_TENANT = "tenant-alpha";
    private static final String TEST_COLLECTION = "users";
    private static final Instant TEST_CREATED = Instant.parse("2026-01-15T10:00:00Z [GH-90000]");
    private static final Instant TEST_UPDATED = Instant.parse("2026-01-15T12:00:00Z [GH-90000]");
    private static final Map<String, Object> TEST_DATA = Map.of("name", "Alice", "age", 30); // GH-90000
    private static final Map<String, Object> TEST_METADATA = Map.of("source", "api", "version", "1.0"); // GH-90000

    // ═══════════════════════════════════════════════════════════════
    //  1. EntityRecordAdapter Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EntityRecordAdapter [GH-90000]")
    class EntityRecordAdapterTests {

        @Test
        @DisplayName("toTrait maps all common fields correctly [GH-90000]")
        void toTraitMapsAllFields() { // GH-90000
            EntityRecord jpa = EntityRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .data(TEST_DATA) // GH-90000
                    .metadata(TEST_METADATA) // GH-90000
                    .createdAt(TEST_CREATED) // GH-90000
                    .createdBy("system [GH-90000]")
                    .version(5) // GH-90000
                    .active(true) // GH-90000
                    .updatedAt(TEST_UPDATED) // GH-90000
                    .updatedBy("admin [GH-90000]")
                    .build(); // GH-90000

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.recordId()).isEqualTo(RecordId.of(TEST_ID)); // GH-90000
            assertThat(trait.tenantIdValue()).isEqualTo(TenantId.of(TEST_TENANT)); // GH-90000
            assertThat(trait.collectionName()).isEqualTo(TEST_COLLECTION); // GH-90000
            assertThat(trait.data()).containsAllEntriesOf(TEST_DATA); // GH-90000
            assertThat(trait.metadata()).containsAllEntriesOf(TEST_METADATA); // GH-90000
            assertThat(trait.version()).isEqualTo(5L); // GH-90000
            assertThat(trait.createdAt()).isEqualTo(TEST_CREATED); // GH-90000
            assertThat(trait.updatedAt()).isEqualTo(TEST_UPDATED); // GH-90000
            assertThat(trait.createdBy()).isEqualTo("system [GH-90000]");
            assertThat(trait.modifiedBy()).isEqualTo("admin [GH-90000]");
        }

        @Test
        @DisplayName("toTrait sets trait-only fields to null/empty [GH-90000]")
        void toTraitSetsTraitOnlyDefaults() { // GH-90000
            EntityRecord jpa = minimalJpaEntity(); // GH-90000

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.schemaVersionValue()).isNull(); // GH-90000
            assertThat(trait.aiMetadata()).isEmpty(); // GH-90000
            assertThat(trait.aiConfidenceValue()).isNull(); // GH-90000
            assertThat(trait.aiExplanationValue()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("toTrait handles null version as 0L [GH-90000]")
        void toTraitNullVersion() { // GH-90000
            EntityRecord jpa = EntityRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .version(null) // GH-90000
                    .build(); // GH-90000

            FullEntityRecord trait = EntityRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.version()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("toJpa maps all common fields correctly [GH-90000]")
        void toJpaMapsAllFields() { // GH-90000
            FullEntityRecord trait = new FullEntityRecord( // GH-90000
                    RecordId.of(TEST_ID), // GH-90000
                    TenantId.of(TEST_TENANT), // GH-90000
                    TEST_COLLECTION,
                    TEST_DATA,
                    TEST_METADATA,
                    7L,
                    TEST_CREATED,
                    TEST_UPDATED,
                    "system",
                    "admin",
                    "v2.0",
                    Map.of("model", "gpt-4"), // GH-90000
                    0.95,
                    "high confidence"
            );

            EntityRecord jpa = EntityRecordAdapter.toJpa(trait); // GH-90000

            assertThat(jpa.getId()).isEqualTo(TEST_ID); // GH-90000
            assertThat(jpa.getTenantId()).isEqualTo(TEST_TENANT); // GH-90000
            assertThat(jpa.getCollectionName()).isEqualTo(TEST_COLLECTION); // GH-90000
            assertThat(jpa.getData()).containsAllEntriesOf(TEST_DATA); // GH-90000
            assertThat(jpa.getMetadata()).containsAllEntriesOf(TEST_METADATA); // GH-90000
            assertThat(jpa.getVersion()).isEqualTo(7); // GH-90000
            assertThat(jpa.getCreatedAt()).isEqualTo(TEST_CREATED); // GH-90000
            assertThat(jpa.getUpdatedAt()).isEqualTo(TEST_UPDATED); // GH-90000
            assertThat(jpa.getCreatedBy()).isEqualTo("system [GH-90000]");
            assertThat(jpa.getUpdatedBy()).isEqualTo("admin [GH-90000]");
            assertThat(jpa.getActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("toJpa creates mutable maps [GH-90000]")
        void toJpaCreatesMutableMaps() { // GH-90000
            FullEntityRecord trait = minimalTraitEntity(); // GH-90000

            EntityRecord jpa = EntityRecordAdapter.toJpa(trait); // GH-90000

            // Maps should be mutable for JPA
            jpa.getData().put("new-key", "new-value"); // GH-90000
            assertThat(jpa.getData()).containsKey("new-key [GH-90000]");
        }

        @Test
        @DisplayName("round-trip JPA → trait → JPA preserves common fields [GH-90000]")
        void roundTripJpaTraitJpa() { // GH-90000
            EntityRecord original = EntityRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .data(TEST_DATA) // GH-90000
                    .metadata(TEST_METADATA) // GH-90000
                    .createdAt(TEST_CREATED) // GH-90000
                    .createdBy("system [GH-90000]")
                    .version(3) // GH-90000
                    .updatedAt(TEST_UPDATED) // GH-90000
                    .updatedBy("admin [GH-90000]")
                    .build(); // GH-90000

            FullEntityRecord trait = EntityRecordAdapter.toTrait(original); // GH-90000
            EntityRecord roundTripped = EntityRecordAdapter.toJpa(trait); // GH-90000

            assertThat(roundTripped.getId()).isEqualTo(original.getId()); // GH-90000
            assertThat(roundTripped.getTenantId()).isEqualTo(original.getTenantId()); // GH-90000
            assertThat(roundTripped.getCollectionName()).isEqualTo(original.getCollectionName()); // GH-90000
            assertThat(roundTripped.getData()).isEqualTo(original.getData()); // GH-90000
            assertThat(roundTripped.getMetadata()).isEqualTo(original.getMetadata()); // GH-90000
            assertThat(roundTripped.getVersion()).isEqualTo(original.getVersion()); // GH-90000
            assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt()); // GH-90000
            assertThat(roundTripped.getCreatedBy()).isEqualTo(original.getCreatedBy()); // GH-90000
            assertThat(roundTripped.getUpdatedAt()).isEqualTo(original.getUpdatedAt()); // GH-90000
            assertThat(roundTripped.getUpdatedBy()).isEqualTo(original.getUpdatedBy()); // GH-90000
        }

        @Test
        @DisplayName("toTrait throws on null input [GH-90000]")
        void toTraitThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> EntityRecordAdapter.toTrait(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("must not be null [GH-90000]");
        }

        @Test
        @DisplayName("toJpa throws on null input [GH-90000]")
        void toJpaThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> EntityRecordAdapter.toJpa(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("must not be null [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. EventRecordAdapter Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("EventRecordAdapter [GH-90000]")
    class EventRecordAdapterTests {

        private static final String TEST_STREAM = "order-events";
        private static final Instant TEST_OCCURRED = Instant.parse("2026-01-15T10:30:00Z [GH-90000]");
        private static final Instant TEST_INGESTED = Instant.parse("2026-01-15T10:30:05Z [GH-90000]");

        @Test
        @DisplayName("toTrait maps all common fields correctly [GH-90000]")
        void toTraitMapsAllFields() { // GH-90000
            EventRecord jpa = EventRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .data(TEST_DATA) // GH-90000
                    .metadata(TEST_METADATA) // GH-90000
                    .streamName(TEST_STREAM) // GH-90000
                    .eventOffset(42L) // GH-90000
                    .occurrenceTime(TEST_OCCURRED) // GH-90000
                    .detectionTime(TEST_INGESTED) // GH-90000
                    .correlationId("corr-123 [GH-90000]")
                    .causationId("cause-456 [GH-90000]")
                    .build(); // GH-90000

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.recordId()).isEqualTo(RecordId.of(TEST_ID)); // GH-90000
            assertThat(trait.tenantIdValue()).isEqualTo(TenantId.of(TEST_TENANT)); // GH-90000
            assertThat(trait.collectionName()).isEqualTo(TEST_COLLECTION); // GH-90000
            assertThat(trait.streamName()).isEqualTo(TEST_STREAM); // GH-90000
            assertThat(trait.offset()).isEqualTo(42L); // GH-90000
            assertThat(trait.data()).containsAllEntriesOf(TEST_DATA); // GH-90000
            assertThat(trait.headers()).containsAllEntriesOf(TEST_METADATA); // GH-90000
            assertThat(trait.occurredAt()).isEqualTo(TEST_OCCURRED); // GH-90000
            assertThat(trait.ingestedAt()).isEqualTo(TEST_INGESTED); // GH-90000
            assertThat(trait.correlationId()).isEqualTo("corr-123 [GH-90000]");
            assertThat(trait.causationId()).isEqualTo("cause-456 [GH-90000]");
        }

        @Test
        @DisplayName("toTrait maps JPA metadata to trait headers [GH-90000]")
        void toTraitMapsMetadataToHeaders() { // GH-90000
            EventRecord jpa = EventRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .streamName(TEST_STREAM) // GH-90000
                    .metadata(Map.of("content-type", "application/json")) // GH-90000
                    .occurrenceTime(TEST_OCCURRED) // GH-90000
                    .detectionTime(TEST_INGESTED) // GH-90000
                    .build(); // GH-90000

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.headers()).containsEntry("content-type", "application/json"); // GH-90000
        }

        @Test
        @DisplayName("toTrait handles null eventOffset as 0L [GH-90000]")
        void toTraitNullOffset() { // GH-90000
            EventRecord jpa = EventRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .streamName(TEST_STREAM) // GH-90000
                    .eventOffset(null) // GH-90000
                    .occurrenceTime(TEST_OCCURRED) // GH-90000
                    .detectionTime(TEST_INGESTED) // GH-90000
                    .build(); // GH-90000

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(jpa); // GH-90000

            assertThat(trait.offset()).isEqualTo(0L); // GH-90000
        }

        @Test
        @DisplayName("toJpa maps all common fields correctly [GH-90000]")
        void toJpaMapsAllFields() { // GH-90000
            ImmutableEventRecord trait = new ImmutableEventRecord( // GH-90000
                    RecordId.of(TEST_ID), // GH-90000
                    TenantId.of(TEST_TENANT), // GH-90000
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

            EventRecord jpa = EventRecordAdapter.toJpa(trait); // GH-90000

            assertThat(jpa.getId()).isEqualTo(TEST_ID); // GH-90000
            assertThat(jpa.getTenantId()).isEqualTo(TEST_TENANT); // GH-90000
            assertThat(jpa.getCollectionName()).isEqualTo(TEST_COLLECTION); // GH-90000
            assertThat(jpa.getStreamName()).isEqualTo(TEST_STREAM); // GH-90000
            assertThat(jpa.getEventOffset()).isEqualTo(42L); // GH-90000
            assertThat(jpa.getData()).containsAllEntriesOf(TEST_DATA); // GH-90000
            assertThat(jpa.getMetadata()).containsAllEntriesOf(TEST_METADATA); // GH-90000
            assertThat(jpa.getOccurrenceTime()).isEqualTo(TEST_OCCURRED); // GH-90000
            assertThat(jpa.getDetectionTime()).isEqualTo(TEST_INGESTED); // GH-90000
            assertThat(jpa.getCorrelationId()).isEqualTo("corr-123 [GH-90000]");
            assertThat(jpa.getCausationId()).isEqualTo("cause-456 [GH-90000]");
        }

        @Test
        @DisplayName("toJpa maps trait headers to JPA metadata [GH-90000]")
        void toJpaMapsHeadersToMetadata() { // GH-90000
            ImmutableEventRecord trait = new ImmutableEventRecord( // GH-90000
                    RecordId.of(TEST_ID), // GH-90000
                    TenantId.of(TEST_TENANT), // GH-90000
                    TEST_COLLECTION,
                    TEST_STREAM,
                    0L,
                    Map.of(), // GH-90000
                    Map.of("x-trace-id", "abc"), // GH-90000
                    TEST_OCCURRED,
                    TEST_INGESTED,
                    null,
                    null,
                    null
            );

            EventRecord jpa = EventRecordAdapter.toJpa(trait); // GH-90000

            assertThat(jpa.getMetadata()).containsEntry("x-trace-id", "abc"); // GH-90000
        }

        @Test
        @DisplayName("round-trip JPA → trait → JPA preserves common fields [GH-90000]")
        void roundTripJpaTraitJpa() { // GH-90000
            EventRecord original = EventRecord.builder() // GH-90000
                    .id(TEST_ID) // GH-90000
                    .tenantId(TEST_TENANT) // GH-90000
                    .collectionName(TEST_COLLECTION) // GH-90000
                    .data(TEST_DATA) // GH-90000
                    .metadata(TEST_METADATA) // GH-90000
                    .streamName(TEST_STREAM) // GH-90000
                    .eventOffset(100L) // GH-90000
                    .occurrenceTime(TEST_OCCURRED) // GH-90000
                    .detectionTime(TEST_INGESTED) // GH-90000
                    .correlationId("corr-abc [GH-90000]")
                    .causationId("cause-xyz [GH-90000]")
                    .build(); // GH-90000

            ImmutableEventRecord trait = EventRecordAdapter.toTrait(original); // GH-90000
            EventRecord roundTripped = EventRecordAdapter.toJpa(trait); // GH-90000

            assertThat(roundTripped.getId()).isEqualTo(original.getId()); // GH-90000
            assertThat(roundTripped.getTenantId()).isEqualTo(original.getTenantId()); // GH-90000
            assertThat(roundTripped.getCollectionName()).isEqualTo(original.getCollectionName()); // GH-90000
            assertThat(roundTripped.getStreamName()).isEqualTo(original.getStreamName()); // GH-90000
            assertThat(roundTripped.getEventOffset()).isEqualTo(original.getEventOffset()); // GH-90000
            assertThat(roundTripped.getData()).isEqualTo(original.getData()); // GH-90000
            assertThat(roundTripped.getOccurrenceTime()).isEqualTo(original.getOccurrenceTime()); // GH-90000
            assertThat(roundTripped.getDetectionTime()).isEqualTo(original.getDetectionTime()); // GH-90000
            assertThat(roundTripped.getCorrelationId()).isEqualTo(original.getCorrelationId()); // GH-90000
            assertThat(roundTripped.getCausationId()).isEqualTo(original.getCausationId()); // GH-90000
        }

        @Test
        @DisplayName("toTrait throws on null input [GH-90000]")
        void toTraitThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> EventRecordAdapter.toTrait(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("must not be null [GH-90000]");
        }

        @Test
        @DisplayName("toJpa throws on null input [GH-90000]")
        void toJpaThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> EventRecordAdapter.toJpa(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("must not be null [GH-90000]");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. RecordTypeMapper Tests
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("RecordTypeMapper [GH-90000]")
    class RecordTypeMapperTests {

        @Test
        @DisplayName("toTrait maps all 5 JPA record types [GH-90000]")
        void toTraitMapsAll() { // GH-90000
            assertThat(RecordTypeMapper.toTrait(RecordType.ENTITY)).isEqualTo(Record.RecordType.ENTITY); // GH-90000
            assertThat(RecordTypeMapper.toTrait(RecordType.EVENT)).isEqualTo(Record.RecordType.EVENT); // GH-90000
            assertThat(RecordTypeMapper.toTrait(RecordType.TIMESERIES)).isEqualTo(Record.RecordType.TIMESERIES); // GH-90000
            assertThat(RecordTypeMapper.toTrait(RecordType.DOCUMENT)).isEqualTo(Record.RecordType.DOCUMENT); // GH-90000
            assertThat(RecordTypeMapper.toTrait(RecordType.GRAPH)).isEqualTo(Record.RecordType.GRAPH); // GH-90000
        }

        @Test
        @DisplayName("toJpa maps all 5 trait record types [GH-90000]")
        void toJpaMapsAll() { // GH-90000
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.ENTITY)).isEqualTo(RecordType.ENTITY); // GH-90000
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.EVENT)).isEqualTo(RecordType.EVENT); // GH-90000
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.TIMESERIES)).isEqualTo(RecordType.TIMESERIES); // GH-90000
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.DOCUMENT)).isEqualTo(RecordType.DOCUMENT); // GH-90000
            assertThat(RecordTypeMapper.toJpa(Record.RecordType.GRAPH)).isEqualTo(RecordType.GRAPH); // GH-90000
        }

        @Test
        @DisplayName("round-trip preserves enum identity [GH-90000]")
        void roundTrips() { // GH-90000
            for (RecordType jpa : RecordType.values()) { // GH-90000
                Record.RecordType trait = RecordTypeMapper.toTrait(jpa); // GH-90000
                RecordType backToJpa = RecordTypeMapper.toJpa(trait); // GH-90000
                assertThat(backToJpa).isEqualTo(jpa); // GH-90000
            }
        }

        @Test
        @DisplayName("toTrait throws on null [GH-90000]")
        void toTraitThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> RecordTypeMapper.toTrait(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("toJpa throws on null [GH-90000]")
        void toJpaThrowsOnNull() { // GH-90000
            assertThatThrownBy(() -> RecordTypeMapper.toJpa(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private static EntityRecord minimalJpaEntity() { // GH-90000
        return EntityRecord.builder() // GH-90000
                .id(TEST_ID) // GH-90000
                .tenantId(TEST_TENANT) // GH-90000
                .collectionName(TEST_COLLECTION) // GH-90000
                .createdAt(TEST_CREATED) // GH-90000
                .build(); // GH-90000
    }

    private static FullEntityRecord minimalTraitEntity() { // GH-90000
        return new FullEntityRecord( // GH-90000
                RecordId.of(TEST_ID), // GH-90000
                TenantId.of(TEST_TENANT), // GH-90000
                TEST_COLLECTION,
                Map.of(), // GH-90000
                Map.of(), // GH-90000
                0L,
                TEST_CREATED,
                TEST_CREATED,
                null,
                null,
                null,
                Map.of(), // GH-90000
                null,
                null
        );
    }
}
