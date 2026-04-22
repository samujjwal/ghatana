package com.ghatana.kernel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TypedDataSerializer} — the ISSUE-X03 discriminator injection utility.
 */
@DisplayName("TypedDataSerializer [GH-90000]")
class TypedDataSerializerTest {

    // ==================== Test fixture ====================

    record SampleRecord(String id, String name, Instant createdAt) {} // GH-90000

    // ==================== toBytes / toString ====================

    @Nested
    @DisplayName("toBytes() [GH-90000]")
    class ToBytesTests {

        @Test
        @DisplayName("injects _type field into serialized JSON [GH-90000]")
        void shouldInjectTypeField() { // GH-90000
            SampleRecord rec = new SampleRecord("r1", "Alpha", Instant.EPOCH); // GH-90000

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 1); // GH-90000
            String json = new String(bytes, StandardCharsets.UTF_8); // GH-90000

            assertThat(json).contains("\"_type\":\"SampleRecord\""); // GH-90000
        }

        @Test
        @DisplayName("injects _schema_version field into serialized JSON [GH-90000]")
        void shouldInjectSchemaVersionField() { // GH-90000
            SampleRecord rec = new SampleRecord("r2", "Beta", Instant.EPOCH); // GH-90000

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 3); // GH-90000
            String json = new String(bytes, StandardCharsets.UTF_8); // GH-90000

            assertThat(json).contains("\"_schema_version\":3"); // GH-90000
        }

        @Test
        @DisplayName("preserves all original fields alongside discriminators [GH-90000]")
        void shouldPreserveOriginalFields() { // GH-90000
            SampleRecord rec = new SampleRecord("r3", "Gamma", Instant.EPOCH); // GH-90000

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 1); // GH-90000
            String json = new String(bytes, StandardCharsets.UTF_8); // GH-90000

            assertThat(json).contains("\"id\":\"r3\""); // GH-90000
            assertThat(json).contains("\"name\":\"Gamma\""); // GH-90000
        }

        @Test
        @DisplayName("throws IllegalArgumentException when type is blank [GH-90000]")
        void shouldThrowOnBlankType() { // GH-90000
            SampleRecord rec = new SampleRecord("r4", "Delta", Instant.EPOCH); // GH-90000

            assertThatThrownBy(() -> TypedDataSerializer.toBytes(rec, "", 1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("type must not be blank [GH-90000]");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when schemaVersion is less than 1 [GH-90000]")
        void shouldThrowOnZeroSchemaVersion() { // GH-90000
            SampleRecord rec = new SampleRecord("r5", "Epsilon", Instant.EPOCH); // GH-90000

            assertThatThrownBy(() -> TypedDataSerializer.toBytes(rec, "SampleRecord", 0)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("schemaVersion must be >= 1 [GH-90000]");
        }
    }

    // ==================== fromBytes ====================

    @Nested
    @DisplayName("fromBytes() [GH-90000]")
    class FromBytesTests {

        @Test
        @DisplayName("deserializes a typed record back to the original value [GH-90000]")
        void shouldDeserializeToOriginalValue() { // GH-90000
            SampleRecord original = new SampleRecord("r6", "Zeta", Instant.EPOCH); // GH-90000
            byte[] bytes = TypedDataSerializer.toBytes(original, "SampleRecord", 1); // GH-90000

            SampleRecord recovered = TypedDataSerializer.fromBytes(bytes, SampleRecord.class); // GH-90000

            assertThat(recovered).isNotNull(); // GH-90000
            assertThat(recovered.id()).isEqualTo("r6 [GH-90000]");
            assertThat(recovered.name()).isEqualTo("Zeta [GH-90000]");
        }

        @Test
        @DisplayName("ignores _type and _schema_version discriminator fields during deserialization [GH-90000]")
        void shouldIgnoreDiscriminatorFields() { // GH-90000
            // A JSON payload containing discriminators AND the target class fields
            String json = """
                    {"_type":"SampleRecord","_schema_version":2,"id":"r7","name":"Eta","createdAt":"1970-01-01T00:00:00Z"}
                    """;

            SampleRecord recovered = TypedDataSerializer.fromBytes(json.getBytes(StandardCharsets.UTF_8), SampleRecord.class); // GH-90000

            assertThat(recovered).isNotNull(); // GH-90000
            assertThat(recovered.id()).isEqualTo("r7 [GH-90000]");
        }

        @Test
        @DisplayName("returns null when data is null [GH-90000]")
        void shouldReturnNullForNullData() { // GH-90000
            SampleRecord result = TypedDataSerializer.fromBytes(null, SampleRecord.class); // GH-90000
            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("returns null when data is empty array [GH-90000]")
        void shouldReturnNullForEmptyData() { // GH-90000
            SampleRecord result = TypedDataSerializer.fromBytes(new byte[0], SampleRecord.class); // GH-90000
            assertThat(result).isNull(); // GH-90000
        }
    }

    // ==================== readSchemaVersion ====================

    @Nested
    @DisplayName("readSchemaVersion() [GH-90000]")
    class ReadSchemaVersionTests {

        @Test
        @DisplayName("reads version 1 from freshly serialized bytes [GH-90000]")
        void shouldReadVersionOne() { // GH-90000
            byte[] bytes = TypedDataSerializer.toBytes( // GH-90000
                    new SampleRecord("r8", "Theta", Instant.EPOCH), "SampleRecord", 1); // GH-90000

            assertThat(TypedDataSerializer.readSchemaVersion(bytes)).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("reads custom version number [GH-90000]")
        void shouldReadCustomVersion() { // GH-90000
            byte[] bytes = TypedDataSerializer.toBytes( // GH-90000
                    new SampleRecord("r9", "Iota", Instant.EPOCH), "SampleRecord", 7); // GH-90000

            assertThat(TypedDataSerializer.readSchemaVersion(bytes)).isEqualTo(7); // GH-90000
        }

        @Test
        @DisplayName("returns -1 for legacy payload without _schema_version field [GH-90000]")
        void shouldReturnMinusOneForLegacyPayload() { // GH-90000
            // Legacy payload — no discriminators
            String json = "{\"id\":\"r10\",\"name\":\"Kappa\"}";
            int version = TypedDataSerializer.readSchemaVersion(json.getBytes(StandardCharsets.UTF_8)); // GH-90000

            assertThat(version).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("returns -1 for null input [GH-90000]")
        void shouldReturnMinusOneForNull() { // GH-90000
            assertThat(TypedDataSerializer.readSchemaVersion(null)).isEqualTo(-1); // GH-90000
        }
    }

    // ==================== readType ====================

    @Nested
    @DisplayName("readType() [GH-90000]")
    class ReadTypeTests {

        @Test
        @DisplayName("reads the type discriminator from serialized bytes [GH-90000]")
        void shouldReadTypeDiscriminator() { // GH-90000
            byte[] bytes = TypedDataSerializer.toBytes( // GH-90000
                    new SampleRecord("r11", "Lambda", Instant.EPOCH), "SampleRecord", 1); // GH-90000

            assertThat(TypedDataSerializer.readType(bytes)).isEqualTo("SampleRecord [GH-90000]");
        }

        @Test
        @DisplayName("returns null for legacy payload without _type field [GH-90000]")
        void shouldReturnNullForLegacyPayload() { // GH-90000
            String json = "{\"id\":\"r12\",\"name\":\"Mu\"}";
            assertThat(TypedDataSerializer.readType(json.getBytes(StandardCharsets.UTF_8))).isNull(); // GH-90000
        }

        @Test
        @DisplayName("returns null for null input [GH-90000]")
        void shouldReturnNullForNull() { // GH-90000
            assertThat(TypedDataSerializer.readType(null)).isNull(); // GH-90000
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip serialization [GH-90000]")
    class RoundTripTests {

        @Test
        @DisplayName("serialize then deserialize yields structurally equal record [GH-90000]")
        void shouldRoundTrip() { // GH-90000
            SampleRecord original = new SampleRecord("r13", "Nu", Instant.parse("2024-06-15T00:00:00Z [GH-90000]"));

            byte[] bytes = TypedDataSerializer.toBytes(original, "SampleRecord", 1); // GH-90000
            SampleRecord recovered = TypedDataSerializer.fromBytes(bytes, SampleRecord.class); // GH-90000

            assertThat(recovered).isNotNull(); // GH-90000
            assertThat(recovered.id()).isEqualTo(original.id()); // GH-90000
            assertThat(recovered.name()).isEqualTo(original.name()); // GH-90000
            assertThat(recovered.createdAt()).isEqualTo(original.createdAt()); // GH-90000
        }

        @Test
        @DisplayName("discriminators do not corrupt the data fields across multiple versions [GH-90000]")
        void shouldRoundTripAcrossVersions() { // GH-90000
            SampleRecord original = new SampleRecord("r14", "Xi", Instant.EPOCH); // GH-90000

            byte[] v1 = TypedDataSerializer.toBytes(original, "SampleRecord", 1); // GH-90000
            byte[] v2 = TypedDataSerializer.toBytes(original, "SampleRecord", 2); // GH-90000

            SampleRecord fromV1 = TypedDataSerializer.fromBytes(v1, SampleRecord.class); // GH-90000
            SampleRecord fromV2 = TypedDataSerializer.fromBytes(v2, SampleRecord.class); // GH-90000

            assertThat(fromV1.id()).isEqualTo("r14 [GH-90000]");
            assertThat(fromV2.id()).isEqualTo("r14 [GH-90000]");
            assertThat(TypedDataSerializer.readSchemaVersion(v1)).isEqualTo(1); // GH-90000
            assertThat(TypedDataSerializer.readSchemaVersion(v2)).isEqualTo(2); // GH-90000
        }
    }
}
