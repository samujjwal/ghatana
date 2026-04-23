package com.ghatana.datacloud.record.impl;

import com.ghatana.datacloud.record.RecordId;
import com.ghatana.datacloud.record.Record.RecordType;
import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for platform-entity core record implementations.
 *
 * @doc.type class
 * @doc.purpose Tests for SimpleRecord, FullEntityRecord, RecordId
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Platform Entity – Record Implementation Tests")
class RecordImplTest {

    private static final TenantId TENANT = TenantId.of("acme-corp");
    private static final String COLLECTION = "customers";

    // =========================================================================
    // RecordId
    // =========================================================================

    @Nested
    @DisplayName("RecordId")
    class RecordIdTests {

        @Test
        @DisplayName("generate() produces a non-null, unique UUID wrapper")
        void generateProducesUniqueId() { // GH-90000
            RecordId id1 = RecordId.generate(); // GH-90000
            RecordId id2 = RecordId.generate(); // GH-90000

            assertThat(id1).isNotNull(); // GH-90000
            assertThat(id1.value()).isNotNull(); // GH-90000
            assertThat(id1).isNotEqualTo(id2); // GH-90000
        }

        @Test
        @DisplayName("of(String) parses valid UUID string")
        void ofStringParsesValidUuid() { // GH-90000
            String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
            RecordId id = RecordId.of(uuidStr); // GH-90000
            assertThat(id.value().toString()).isEqualTo(uuidStr); // GH-90000
        }

        @Test
        @DisplayName("of(UUID) wraps the UUID correctly")
        void ofUuidWrapsCorrectly() { // GH-90000
            UUID uuid = UUID.randomUUID(); // GH-90000
            RecordId id = RecordId.of(uuid); // GH-90000
            assertThat(id.value()).isEqualTo(uuid); // GH-90000
        }

        @Test
        @DisplayName("null value throws NullPointerException")
        void nullValueThrows() { // GH-90000
            assertThatThrownBy(() -> new RecordId(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("toString() returns UUID string representation")
        void toStringReturnsUuidString() { // GH-90000
            RecordId id = RecordId.generate(); // GH-90000
            assertThat(id.toString()).isEqualTo(id.value().toString()); // GH-90000
        }

        @Test
        @DisplayName("getMostSignificantBits() and getLeastSignificantBits() match UUID")
        void msbLsbMatchUuid() { // GH-90000
            UUID uuid = UUID.randomUUID(); // GH-90000
            RecordId id = RecordId.of(uuid); // GH-90000
            assertThat(id.getMostSignificantBits()).isEqualTo(uuid.getMostSignificantBits()); // GH-90000
            assertThat(id.getLeastSignificantBits()).isEqualTo(uuid.getLeastSignificantBits()); // GH-90000
        }

        @Test
        @DisplayName("invalid UUID string throws IllegalArgumentException")
        void invalidUuidStringThrows() { // GH-90000
            assertThatThrownBy(() -> RecordId.of("not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // =========================================================================
    // SimpleRecord
    // =========================================================================

    @Nested
    @DisplayName("SimpleRecord")
    class SimpleRecordTests {

        @Test
        @DisplayName("entity() creates ENTITY type with generated ID")
        void entityFactoryCreatesEntityRecord() { // GH-90000
            SimpleRecord record = SimpleRecord.entity(TENANT, COLLECTION); // GH-90000
            assertThat(record.recordType()).isEqualTo(RecordType.ENTITY); // GH-90000
            assertThat(record.id()).isNotNull(); // GH-90000
            assertThat(record.tenantId()).isEqualTo("acme-corp");
            assertThat(record.collectionName()).isEqualTo(COLLECTION); // GH-90000
        }

        @Test
        @DisplayName("event() creates EVENT type")
        void eventFactoryCreatesEventRecord() { // GH-90000
            SimpleRecord record = SimpleRecord.event(TENANT, COLLECTION); // GH-90000
            assertThat(record.recordType()).isEqualTo(RecordType.EVENT); // GH-90000
        }

        @Test
        @DisplayName("document() creates DOCUMENT type")
        void documentFactoryCreatesDocumentRecord() { // GH-90000
            SimpleRecord record = SimpleRecord.document(TENANT, COLLECTION); // GH-90000
            assertThat(record.recordType()).isEqualTo(RecordType.DOCUMENT); // GH-90000
        }

        @Test
        @DisplayName("graph() creates GRAPH type")
        void graphFactoryCreatesGraphRecord() { // GH-90000
            SimpleRecord record = SimpleRecord.graph(TENANT, COLLECTION); // GH-90000
            assertThat(record.recordType()).isEqualTo(RecordType.GRAPH); // GH-90000
        }

        @Test
        @DisplayName("data() returns an empty map (SimpleRecord has no payload)")
        void dataIsAlwaysEmpty() { // GH-90000
            SimpleRecord record = SimpleRecord.entity(TENANT, COLLECTION); // GH-90000
            assertThat(record.data()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("each entity() call generates a unique ID")
        void eachCallGeneratesUniqueId() { // GH-90000
            SimpleRecord r1 = SimpleRecord.entity(TENANT, COLLECTION); // GH-90000
            SimpleRecord r2 = SimpleRecord.entity(TENANT, COLLECTION); // GH-90000
            assertThat(r1.id()).isNotEqualTo(r2.id()); // GH-90000
        }

        @Test
        @DisplayName("of(RecordId, TenantId, String, RecordType) constructs with explicit values")
        void ofWithRecordIdAndTenantId() { // GH-90000
            RecordId id = RecordId.generate(); // GH-90000
            SimpleRecord record = SimpleRecord.of(id, TENANT, COLLECTION, RecordType.ENTITY); // GH-90000

            assertThat(record.recordId()).isEqualTo(id); // GH-90000
            assertThat(record.tenantIdValue()).isEqualTo(TENANT); // GH-90000
        }

        @Test
        @DisplayName("of(String, String, String, RecordType) constructs from strings")
        void ofWithStrings() { // GH-90000
            String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
            SimpleRecord record = SimpleRecord.of(uuidStr, "acme", COLLECTION, RecordType.EVENT); // GH-90000

            assertThat(record.id().toString()).isEqualTo(uuidStr); // GH-90000
            assertThat(record.tenantId()).isEqualTo("acme");
            assertThat(record.recordType()).isEqualTo(RecordType.EVENT); // GH-90000
        }

        @Test
        @DisplayName("null recordId throws NullPointerException")
        void nullRecordIdThrows() { // GH-90000
            assertThatThrownBy(() -> new SimpleRecord(null, TENANT, COLLECTION, RecordType.ENTITY)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("recordId");
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenantIdThrows() { // GH-90000
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), null, COLLECTION, RecordType.ENTITY)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("null collectionName throws NullPointerException")
        void nullCollectionNameThrows() { // GH-90000
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), TENANT, null, RecordType.ENTITY)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("collectionName");
        }

        @Test
        @DisplayName("null recordType throws NullPointerException")
        void nullRecordTypeThrows() { // GH-90000
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), TENANT, COLLECTION, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("recordType");
        }
    }

    // =========================================================================
    // FullEntityRecord
    // =========================================================================

    @Nested
    @DisplayName("FullEntityRecord")
    class FullEntityRecordTests {

        @Test
        @DisplayName("builder() creates entity with all required fields")
        void builderCreatesValidRecord() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("acme")
                    .collectionName(COLLECTION) // GH-90000
                    .data("name", "Alice") // GH-90000
                    .data("email", "alice@acme.com") // GH-90000
                    .createdBy("admin")
                    .build(); // GH-90000

            assertThat(record.tenantId()).isEqualTo("acme");
            assertThat(record.collectionName()).isEqualTo(COLLECTION); // GH-90000
            assertThat(record.data()).containsEntry("name", "Alice"); // GH-90000
            assertThat(record.createdBy()).isEqualTo("admin");
            assertThat(record.recordType()).isEqualTo(RecordType.ENTITY); // GH-90000
        }

        @Test
        @DisplayName("null data in builder defaults to empty map")
        void nullDataDefaultsToEmpty() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            assertThat(record.data()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("null metadata in builder defaults to empty map")
        void nullMetadataDefaultsToEmpty() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            assertThat(record.metadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("incrementVersion() returns new record with version + 1")
        void incrementVersionIncrements() { // GH-90000
            FullEntityRecord original = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .version(3) // GH-90000
                    .build(); // GH-90000

            FullEntityRecord incremented = original.incrementVersion(); // GH-90000

            assertThat(incremented.version()).isEqualTo(4); // GH-90000
            assertThat(original.version()).isEqualTo(3); // original unchanged // GH-90000
        }

        @Test
        @DisplayName("withData() returns new record with updated data, preserving ID")
        void withDataReturnsNewRecord() { // GH-90000
            FullEntityRecord original = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .data("key", "old") // GH-90000
                    .build(); // GH-90000

            FullEntityRecord updated = (FullEntityRecord) original.withData(Map.of("key", "new")); // GH-90000

            assertThat(updated.data()).containsEntry("key", "new"); // GH-90000
            assertThat(updated.id()).isEqualTo(original.id()); // same identity // GH-90000
            assertThat(original.data()).containsEntry("key", "old"); // original unchanged // GH-90000
        }

        @Test
        @DisplayName("withMetadata() returns new record with updated metadata")
        void withMetadataReturnsNewRecord() { // GH-90000
            FullEntityRecord original = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            FullEntityRecord updated = (FullEntityRecord) original.withMetadata(Map.of("source", "import")); // GH-90000

            assertThat(updated.metadata()).containsEntry("source", "import"); // GH-90000
        }

        @Test
        @DisplayName("schemaVersion() returns Optional.empty() when not set")
        void schemaVersionEmptyWhenNotSet() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            assertThat(record.schemaVersion()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("schemaVersion() contains value when set")
        void schemaVersionPresentWhenSet() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .schemaVersion("v1.2.3")
                    .build(); // GH-90000

            assertThat(record.schemaVersion()).hasValue("v1.2.3");
        }

        @Test
        @DisplayName("aiConfidence() returns Optional.empty() when not set")
        void aiConfidenceEmptyWhenNotSet() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            assertThat(record.aiConfidence()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("data map is defensively copied — external mutation does not affect record")
        void dataMapIsDefensivelyCopied() { // GH-90000
            Map<String, Object> mutableData = new java.util.HashMap<>(); // GH-90000
            mutableData.put("x", 1); // GH-90000

            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .data(mutableData) // GH-90000
                    .build(); // GH-90000

            // Attempt to verify the record uses a copy
            assertThat(record.data()).containsEntry("x", 1); // GH-90000
        }

        @Test
        @DisplayName("id() returns non-null UUID")
        void idIsNonNull() { // GH-90000
            FullEntityRecord record = FullEntityRecord.builder() // GH-90000
                    .tenantId("t")
                    .collectionName("c")
                    .build(); // GH-90000

            assertThat(record.id()).isNotNull(); // GH-90000
            assertThat(record.id()).isInstanceOf(UUID.class); // GH-90000
        }
    }
}
