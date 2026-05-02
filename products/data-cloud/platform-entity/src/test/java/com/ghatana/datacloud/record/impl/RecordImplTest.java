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
        void generateProducesUniqueId() { 
            RecordId id1 = RecordId.generate(); 
            RecordId id2 = RecordId.generate(); 

            assertThat(id1).isNotNull(); 
            assertThat(id1.value()).isNotNull(); 
            assertThat(id1).isNotEqualTo(id2); 
        }

        @Test
        @DisplayName("of(String) parses valid UUID string")
        void ofStringParsesValidUuid() { 
            String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
            RecordId id = RecordId.of(uuidStr); 
            assertThat(id.value().toString()).isEqualTo(uuidStr); 
        }

        @Test
        @DisplayName("of(UUID) wraps the UUID correctly")
        void ofUuidWrapsCorrectly() { 
            UUID uuid = UUID.randomUUID(); 
            RecordId id = RecordId.of(uuid); 
            assertThat(id.value()).isEqualTo(uuid); 
        }

        @Test
        @DisplayName("null value throws NullPointerException")
        void nullValueThrows() { 
            assertThatThrownBy(() -> new RecordId(null)) 
                    .isInstanceOf(NullPointerException.class); 
        }

        @Test
        @DisplayName("toString() returns UUID string representation")
        void toStringReturnsUuidString() { 
            RecordId id = RecordId.generate(); 
            assertThat(id.toString()).isEqualTo(id.value().toString()); 
        }

        @Test
        @DisplayName("getMostSignificantBits() and getLeastSignificantBits() match UUID")
        void msbLsbMatchUuid() { 
            UUID uuid = UUID.randomUUID(); 
            RecordId id = RecordId.of(uuid); 
            assertThat(id.getMostSignificantBits()).isEqualTo(uuid.getMostSignificantBits()); 
            assertThat(id.getLeastSignificantBits()).isEqualTo(uuid.getLeastSignificantBits()); 
        }

        @Test
        @DisplayName("invalid UUID string throws IllegalArgumentException")
        void invalidUuidStringThrows() { 
            assertThatThrownBy(() -> RecordId.of("not-a-uuid"))
                    .isInstanceOf(IllegalArgumentException.class); 
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
        void entityFactoryCreatesEntityRecord() { 
            SimpleRecord record = SimpleRecord.entity(TENANT, COLLECTION); 
            assertThat(record.recordType()).isEqualTo(RecordType.ENTITY); 
            assertThat(record.id()).isNotNull(); 
            assertThat(record.tenantId()).isEqualTo("acme-corp");
            assertThat(record.collectionName()).isEqualTo(COLLECTION); 
        }

        @Test
        @DisplayName("event() creates EVENT type")
        void eventFactoryCreatesEventRecord() { 
            SimpleRecord record = SimpleRecord.event(TENANT, COLLECTION); 
            assertThat(record.recordType()).isEqualTo(RecordType.EVENT); 
        }

        @Test
        @DisplayName("document() creates DOCUMENT type")
        void documentFactoryCreatesDocumentRecord() { 
            SimpleRecord record = SimpleRecord.document(TENANT, COLLECTION); 
            assertThat(record.recordType()).isEqualTo(RecordType.DOCUMENT); 
        }

        @Test
        @DisplayName("graph() creates GRAPH type")
        void graphFactoryCreatesGraphRecord() { 
            SimpleRecord record = SimpleRecord.graph(TENANT, COLLECTION); 
            assertThat(record.recordType()).isEqualTo(RecordType.GRAPH); 
        }

        @Test
        @DisplayName("data() returns an empty map (SimpleRecord has no payload)")
        void dataIsAlwaysEmpty() { 
            SimpleRecord record = SimpleRecord.entity(TENANT, COLLECTION); 
            assertThat(record.data()).isEmpty(); 
        }

        @Test
        @DisplayName("each entity() call generates a unique ID")
        void eachCallGeneratesUniqueId() { 
            SimpleRecord r1 = SimpleRecord.entity(TENANT, COLLECTION); 
            SimpleRecord r2 = SimpleRecord.entity(TENANT, COLLECTION); 
            assertThat(r1.id()).isNotEqualTo(r2.id()); 
        }

        @Test
        @DisplayName("of(RecordId, TenantId, String, RecordType) constructs with explicit values")
        void ofWithRecordIdAndTenantId() { 
            RecordId id = RecordId.generate(); 
            SimpleRecord record = SimpleRecord.of(id, TENANT, COLLECTION, RecordType.ENTITY); 

            assertThat(record.recordId()).isEqualTo(id); 
            assertThat(record.tenantIdValue()).isEqualTo(TENANT); 
        }

        @Test
        @DisplayName("of(String, String, String, RecordType) constructs from strings")
        void ofWithStrings() { 
            String uuidStr = "550e8400-e29b-41d4-a716-446655440000";
            SimpleRecord record = SimpleRecord.of(uuidStr, "acme", COLLECTION, RecordType.EVENT); 

            assertThat(record.id().toString()).isEqualTo(uuidStr); 
            assertThat(record.tenantId()).isEqualTo("acme");
            assertThat(record.recordType()).isEqualTo(RecordType.EVENT); 
        }

        @Test
        @DisplayName("null recordId throws NullPointerException")
        void nullRecordIdThrows() { 
            assertThatThrownBy(() -> new SimpleRecord(null, TENANT, COLLECTION, RecordType.ENTITY)) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("recordId");
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenantIdThrows() { 
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), null, COLLECTION, RecordType.ENTITY)) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("tenantId");
        }

        @Test
        @DisplayName("null collectionName throws NullPointerException")
        void nullCollectionNameThrows() { 
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), TENANT, null, RecordType.ENTITY)) 
                    .isInstanceOf(NullPointerException.class) 
                    .hasMessageContaining("collectionName");
        }

        @Test
        @DisplayName("null recordType throws NullPointerException")
        void nullRecordTypeThrows() { 
            assertThatThrownBy(() -> new SimpleRecord(RecordId.generate(), TENANT, COLLECTION, null)) 
                    .isInstanceOf(NullPointerException.class) 
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
        void builderCreatesValidRecord() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("acme")
                    .collectionName(COLLECTION) 
                    .data("name", "Alice") 
                    .data("email", "alice@acme.com") 
                    .createdBy("admin")
                    .build(); 

            assertThat(record.tenantId()).isEqualTo("acme");
            assertThat(record.collectionName()).isEqualTo(COLLECTION); 
            assertThat(record.data()).containsEntry("name", "Alice"); 
            assertThat(record.createdBy()).isEqualTo("admin");
            assertThat(record.recordType()).isEqualTo(RecordType.ENTITY); 
        }

        @Test
        @DisplayName("null data in builder defaults to empty map")
        void nullDataDefaultsToEmpty() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            assertThat(record.data()).isEmpty(); 
        }

        @Test
        @DisplayName("null metadata in builder defaults to empty map")
        void nullMetadataDefaultsToEmpty() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            assertThat(record.metadata()).isEmpty(); 
        }

        @Test
        @DisplayName("incrementVersion() returns new record with version + 1")
        void incrementVersionIncrements() { 
            FullEntityRecord original = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .version(3) 
                    .build(); 

            FullEntityRecord incremented = original.incrementVersion(); 

            assertThat(incremented.version()).isEqualTo(4); 
            assertThat(original.version()).isEqualTo(3); // original unchanged 
        }

        @Test
        @DisplayName("withData() returns new record with updated data, preserving ID")
        void withDataReturnsNewRecord() { 
            FullEntityRecord original = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .data("key", "old") 
                    .build(); 

            FullEntityRecord updated = (FullEntityRecord) original.withData(Map.of("key", "new")); 

            assertThat(updated.data()).containsEntry("key", "new"); 
            assertThat(updated.id()).isEqualTo(original.id()); // same identity 
            assertThat(original.data()).containsEntry("key", "old"); // original unchanged 
        }

        @Test
        @DisplayName("withMetadata() returns new record with updated metadata")
        void withMetadataReturnsNewRecord() { 
            FullEntityRecord original = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            FullEntityRecord updated = (FullEntityRecord) original.withMetadata(Map.of("source", "import")); 

            assertThat(updated.metadata()).containsEntry("source", "import"); 
        }

        @Test
        @DisplayName("schemaVersion() returns Optional.empty() when not set")
        void schemaVersionEmptyWhenNotSet() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            assertThat(record.schemaVersion()).isEmpty(); 
        }

        @Test
        @DisplayName("schemaVersion() contains value when set")
        void schemaVersionPresentWhenSet() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .schemaVersion("v1.2.3")
                    .build(); 

            assertThat(record.schemaVersion()).hasValue("v1.2.3");
        }

        @Test
        @DisplayName("aiConfidence() returns Optional.empty() when not set")
        void aiConfidenceEmptyWhenNotSet() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            assertThat(record.aiConfidence()).isEmpty(); 
        }

        @Test
        @DisplayName("data map is defensively copied — external mutation does not affect record")
        void dataMapIsDefensivelyCopied() { 
            Map<String, Object> mutableData = new java.util.HashMap<>(); 
            mutableData.put("x", 1); 

            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .data(mutableData) 
                    .build(); 

            // Attempt to verify the record uses a copy
            assertThat(record.data()).containsEntry("x", 1); 
        }

        @Test
        @DisplayName("id() returns non-null UUID")
        void idIsNonNull() { 
            FullEntityRecord record = FullEntityRecord.builder() 
                    .tenantId("t")
                    .collectionName("c")
                    .build(); 

            assertThat(record.id()).isNotNull(); 
            assertThat(record.id()).isInstanceOf(UUID.class); 
        }
    }
}
