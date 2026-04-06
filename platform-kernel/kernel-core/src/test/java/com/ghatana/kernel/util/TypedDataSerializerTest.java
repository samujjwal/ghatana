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
@DisplayName("TypedDataSerializer")
class TypedDataSerializerTest {

    // ==================== Test fixture ====================

    record SampleRecord(String id, String name, Instant createdAt) {}

    // ==================== toBytes / toString ====================

    @Nested
    @DisplayName("toBytes()")
    class ToBytesTests {

        @Test
        @DisplayName("injects _type field into serialized JSON")
        void shouldInjectTypeField() {
            SampleRecord rec = new SampleRecord("r1", "Alpha", Instant.EPOCH);

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 1);
            String json = new String(bytes, StandardCharsets.UTF_8);

            assertThat(json).contains("\"_type\":\"SampleRecord\"");
        }

        @Test
        @DisplayName("injects _schema_version field into serialized JSON")
        void shouldInjectSchemaVersionField() {
            SampleRecord rec = new SampleRecord("r2", "Beta", Instant.EPOCH);

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 3);
            String json = new String(bytes, StandardCharsets.UTF_8);

            assertThat(json).contains("\"_schema_version\":3");
        }

        @Test
        @DisplayName("preserves all original fields alongside discriminators")
        void shouldPreserveOriginalFields() {
            SampleRecord rec = new SampleRecord("r3", "Gamma", Instant.EPOCH);

            byte[] bytes = TypedDataSerializer.toBytes(rec, "SampleRecord", 1);
            String json = new String(bytes, StandardCharsets.UTF_8);

            assertThat(json).contains("\"id\":\"r3\"");
            assertThat(json).contains("\"name\":\"Gamma\"");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when type is blank")
        void shouldThrowOnBlankType() {
            SampleRecord rec = new SampleRecord("r4", "Delta", Instant.EPOCH);

            assertThatThrownBy(() -> TypedDataSerializer.toBytes(rec, "", 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("type must not be blank");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when schemaVersion is less than 1")
        void shouldThrowOnZeroSchemaVersion() {
            SampleRecord rec = new SampleRecord("r5", "Epsilon", Instant.EPOCH);

            assertThatThrownBy(() -> TypedDataSerializer.toBytes(rec, "SampleRecord", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("schemaVersion must be >= 1");
        }
    }

    // ==================== fromBytes ====================

    @Nested
    @DisplayName("fromBytes()")
    class FromBytesTests {

        @Test
        @DisplayName("deserializes a typed record back to the original value")
        void shouldDeserializeToOriginalValue() {
            SampleRecord original = new SampleRecord("r6", "Zeta", Instant.EPOCH);
            byte[] bytes = TypedDataSerializer.toBytes(original, "SampleRecord", 1);

            SampleRecord recovered = TypedDataSerializer.fromBytes(bytes, SampleRecord.class);

            assertThat(recovered).isNotNull();
            assertThat(recovered.id()).isEqualTo("r6");
            assertThat(recovered.name()).isEqualTo("Zeta");
        }

        @Test
        @DisplayName("ignores _type and _schema_version discriminator fields during deserialization")
        void shouldIgnoreDiscriminatorFields() {
            // A JSON payload containing discriminators AND the target class fields
            String json = """
                    {"_type":"SampleRecord","_schema_version":2,"id":"r7","name":"Eta","createdAt":"1970-01-01T00:00:00Z"}
                    """;

            SampleRecord recovered = TypedDataSerializer.fromBytes(json.getBytes(StandardCharsets.UTF_8), SampleRecord.class);

            assertThat(recovered).isNotNull();
            assertThat(recovered.id()).isEqualTo("r7");
        }

        @Test
        @DisplayName("returns null when data is null")
        void shouldReturnNullForNullData() {
            SampleRecord result = TypedDataSerializer.fromBytes(null, SampleRecord.class);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("returns null when data is empty array")
        void shouldReturnNullForEmptyData() {
            SampleRecord result = TypedDataSerializer.fromBytes(new byte[0], SampleRecord.class);
            assertThat(result).isNull();
        }
    }

    // ==================== readSchemaVersion ====================

    @Nested
    @DisplayName("readSchemaVersion()")
    class ReadSchemaVersionTests {

        @Test
        @DisplayName("reads version 1 from freshly serialized bytes")
        void shouldReadVersionOne() {
            byte[] bytes = TypedDataSerializer.toBytes(
                    new SampleRecord("r8", "Theta", Instant.EPOCH), "SampleRecord", 1);

            assertThat(TypedDataSerializer.readSchemaVersion(bytes)).isEqualTo(1);
        }

        @Test
        @DisplayName("reads custom version number")
        void shouldReadCustomVersion() {
            byte[] bytes = TypedDataSerializer.toBytes(
                    new SampleRecord("r9", "Iota", Instant.EPOCH), "SampleRecord", 7);

            assertThat(TypedDataSerializer.readSchemaVersion(bytes)).isEqualTo(7);
        }

        @Test
        @DisplayName("returns -1 for legacy payload without _schema_version field")
        void shouldReturnMinusOneForLegacyPayload() {
            // Legacy payload — no discriminators
            String json = "{\"id\":\"r10\",\"name\":\"Kappa\"}";
            int version = TypedDataSerializer.readSchemaVersion(json.getBytes(StandardCharsets.UTF_8));

            assertThat(version).isEqualTo(-1);
        }

        @Test
        @DisplayName("returns -1 for null input")
        void shouldReturnMinusOneForNull() {
            assertThat(TypedDataSerializer.readSchemaVersion(null)).isEqualTo(-1);
        }
    }

    // ==================== readType ====================

    @Nested
    @DisplayName("readType()")
    class ReadTypeTests {

        @Test
        @DisplayName("reads the type discriminator from serialized bytes")
        void shouldReadTypeDiscriminator() {
            byte[] bytes = TypedDataSerializer.toBytes(
                    new SampleRecord("r11", "Lambda", Instant.EPOCH), "SampleRecord", 1);

            assertThat(TypedDataSerializer.readType(bytes)).isEqualTo("SampleRecord");
        }

        @Test
        @DisplayName("returns null for legacy payload without _type field")
        void shouldReturnNullForLegacyPayload() {
            String json = "{\"id\":\"r12\",\"name\":\"Mu\"}";
            assertThat(TypedDataSerializer.readType(json.getBytes(StandardCharsets.UTF_8))).isNull();
        }

        @Test
        @DisplayName("returns null for null input")
        void shouldReturnNullForNull() {
            assertThat(TypedDataSerializer.readType(null)).isNull();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip serialization")
    class RoundTripTests {

        @Test
        @DisplayName("serialize then deserialize yields structurally equal record")
        void shouldRoundTrip() {
            SampleRecord original = new SampleRecord("r13", "Nu", Instant.parse("2024-06-15T00:00:00Z"));

            byte[] bytes = TypedDataSerializer.toBytes(original, "SampleRecord", 1);
            SampleRecord recovered = TypedDataSerializer.fromBytes(bytes, SampleRecord.class);

            assertThat(recovered).isNotNull();
            assertThat(recovered.id()).isEqualTo(original.id());
            assertThat(recovered.name()).isEqualTo(original.name());
            assertThat(recovered.createdAt()).isEqualTo(original.createdAt());
        }

        @Test
        @DisplayName("discriminators do not corrupt the data fields across multiple versions")
        void shouldRoundTripAcrossVersions() {
            SampleRecord original = new SampleRecord("r14", "Xi", Instant.EPOCH);

            byte[] v1 = TypedDataSerializer.toBytes(original, "SampleRecord", 1);
            byte[] v2 = TypedDataSerializer.toBytes(original, "SampleRecord", 2);

            SampleRecord fromV1 = TypedDataSerializer.fromBytes(v1, SampleRecord.class);
            SampleRecord fromV2 = TypedDataSerializer.fromBytes(v2, SampleRecord.class);

            assertThat(fromV1.id()).isEqualTo("r14");
            assertThat(fromV2.id()).isEqualTo("r14");
            assertThat(TypedDataSerializer.readSchemaVersion(v1)).isEqualTo(1);
            assertThat(TypedDataSerializer.readSchemaVersion(v2)).isEqualTo(2);
        }
    }
}
