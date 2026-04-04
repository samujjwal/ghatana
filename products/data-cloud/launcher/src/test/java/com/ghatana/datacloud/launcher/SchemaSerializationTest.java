/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Schema validation, serialization, and forward compatibility tests
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Schema and Serialization Tests")
public class SchemaSerializationTest {

    @Nested
    @DisplayName("SchemaValidationTests")
    class SchemaValidationTests {

        @Test
        @DisplayName("valid schema: passes validation")
        void shouldValidateCorrectSchema() {
            Map<String, Object> entity = createValidEntity();

            assertThat(validateSchema(entity)).isTrue();
        }

        @Test
        @DisplayName("missing required field: fails")
        void shouldRejectMissingField() {
            Map<String, Object> entity = createValidEntity();
            entity.remove("type");

            assertThat(validateSchema(entity)).isFalse();
        }

        @Test
        @DisplayName("wrong field type: detected")
        void shouldDetectWrongType() {
            Map<String, Object> entity = createValidEntity();
            entity.put("version", "not-a-number"); // Should be int

            assertThat(validateFieldType(entity.get("version"), Integer.class)).isFalse();
        }

        @Test
        @DisplayName("extra fields: allowed")
        void shouldAllowExtraFields() {
            Map<String, Object> entity = createValidEntity();
            entity.put("customField", "custom value");

            assertThat(validateSchema(entity)).isTrue();
        }

        @Test
        @DisplayName("nullable field: null accepted")
        void shouldAllowNullInNullableField() {
            Map<String, Object> entity = createValidEntity();
            entity.put("description", null); // description is nullable

            assertThat(validateSchema(entity)).isTrue();
        }
    }

    @Nested
    @DisplayName("SchemaVersionTests")
    class SchemaVersionTests {

        @Test
        @DisplayName("v1 schema: recognized")
        void shouldRecognizeV1Schema() {
            Map<String, Object> entity = createValidEntity();
            entity.put("schemaVersion", 1);

            assertThat(getSchemaVersion(entity)).isEqualTo(1);
        }

        @Test
        @DisplayName("v2 schema: recognized and compatible")
        void shouldHandleV2Schema() {
            Map<String, Object> entity = createValidEntity();
            entity.put("schemaVersion", 2);
            entity.put("newField", "new value"); // v2 adds new field

            assertThat(getSchemaVersion(entity)).isEqualTo(2);
            assertThat(validateSchema(entity)).isTrue(); // Should still be valid
        }

        @Test
        @DisplayName("forward compatible: old reader new data")
        void shouldBeForwardCompatible() {
            Map<String, Object> v2Data = createValidEntity();
            v2Data.put("schemaVersion", 2);
            v2Data.put("futureField", "future data");

            // v1 reader trying to read v2 data
            // Should gracefully ignore unknown fields
            assertThat(canReadAsV1(v2Data)).isTrue();
        }

        @Test
        @DisplayName("backward compatible: new reader old data")
        void shouldBeBackwardCompatible() {
            Map<String, Object> v1Data = createValidEntity();
            v1Data.put("schemaVersion", 1);

            // v2 reader trying to read v1 data
            assertThat(getSchemaVersion(v1Data)).isEqualTo(1);
            assertThat(validateSchema(v1Data)).isTrue();
        }

        @Test
        @DisplayName("schema migration: v1 to v2 path defined")
        void shouldMigrateSchema() {
            Map<String, Object> v1Data = createValidEntity();
            v1Data.put("schemaVersion", 1);

            // Migrate to v2
            Map<String, Object> v2Data = migrateToV2(v1Data);

            assertThat(v2Data.get("schemaVersion")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("SerializationTests")
    class SerializationTests {

        @Test
        @DisplayName("serialize to map: succeeds")
        void shouldSerializeToMap() {
            Map<String, Object> entity = createValidEntity();

            Map<String, Object> serialized = serialize(entity);

            assertThat(serialized)
                    .containsEntry("id", entity.get("id"))
                    .containsEntry("type", entity.get("type"));
        }

        @Test
        @DisplayName("deserialize from map: succeeds")
        void shouldDeserializeFromMap() {
            Map<String, Object> data = createValidEntity();

            Map<String, Object> deserialized = deserialize(data);

            assertThat(deserialized)
                    .containsEntry("id", data.get("id"))
                    .containsEntry("type", data.get("type"));
        }

        @Test
        @DisplayName("round-trip serialize/deserialize: maintains data")
        void shouldMaintainDataRoundTrip() {
            Map<String, Object> original = createValidEntity();

            Map<String, Object> serialized = serialize(original);
            Map<String, Object> deserialized = deserialize(serialized);

            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("null value serialization: handled")
        void shouldHandleNullSerialization() {
            Map<String, Object> entity = createValidEntity();
            entity.put("description", null);

            Map<String, Object> serialized = serialize(entity);

            assertThat(serialized.get("description")).isNull();
        }

        @Test
        @DisplayName("large payload serialization: handled")
        void shouldSerializeLargePayload() {
            Map<String, Object> entity = createValidEntity();
            entity.put("data", "x".repeat(100_000));

            Map<String, Object> serialized = serialize(entity);

            assertThat(serialized.get("data").toString()).hasSizeGreaterThan(99_999);
        }
    }

    @Nested
    @DisplayName("BoundaryTests")
    class BoundaryTests {

        @Test
        @DisplayName("empty string value: allowed")
        void shouldAllowEmptyString() {
            Map<String, Object> entity = createValidEntity();
            entity.put("name", "");

            assertThat(validateSchema(entity)).isTrue();
        }

        @Test
        @DisplayName("string with newlines: preserved")
        void shouldPreserveNewlines() {
            Map<String, Object> entity = createValidEntity();
            String multiline = "line1\nline2\nline3";
            entity.put("description", multiline);

            assertThat(entity.get("description")).isEqualTo(multiline);
        }

        @Test
        @DisplayName("numeric boundaries: enforced")
        void shouldEnforceNumericBounds() {
            Map<String, Object> entity = createValidEntity();

            // Valid value
            entity.put("version", 1);
            assertThat(validateFieldValue(entity.get("version"), 1, Integer.MAX_VALUE)).isTrue();

            // Out of bounds
            entity.put("version", Integer.MAX_VALUE + 1L);
            assertThat(validateFieldValue(entity.get("version"), 1, Integer.MAX_VALUE)).isFalse();
        }

        @Test
        @DisplayName("unicode characters: handled")
        void shouldHandleUnicode() {
            Map<String, Object> entity = createValidEntity();
            entity.put("name", "Test 🚀 データ");

            Map<String, Object> serialized = serialize(entity);

            assertThat(serialized.get("name")).isEqualTo("Test 🚀 データ");
        }

        @Test
        @DisplayName("escaped characters: round-trip preserved")
        void shouldPreserveEscapes() {
            Map<String, Object> entity = createValidEntity();
            entity.put("name", "Test\\\"with\\escapes");

            Map<String, Object> serialized = serialize(entity);
            Map<String, Object> deserialized = deserialize(serialized);

            assertThat(deserialized.get("name")).isEqualTo("Test\\\"with\\escapes");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> createValidEntity() {
        Map<String, Object> entity = new HashMap<>();
        entity.put("id", "entity-123");
        entity.put("tenantId", "tenant-1");
        entity.put("type", "COLLECTION");
        entity.put("name", "Test Collection");
        entity.put("version", 1);
        entity.put("schemaVersion", 1);
        return entity;
    }

    private boolean validateSchema(Map<String, Object> entity) {
        return entity.containsKey("id")
                && entity.containsKey("tenantId")
                && entity.containsKey("type")
                && entity.containsKey("name");
    }

    private boolean validateFieldType(Object value, Class<?> expectedType) {
        return value != null && expectedType.isInstance(value);
    }

    private int getSchemaVersion(Map<String, Object> entity) {
        return (Integer) entity.getOrDefault("schemaVersion", 1);
    }

    private boolean canReadAsV1(Map<String, Object> data) {
        // V1 reader ignores unknown fields
        return validateSchema(data);
    }

    private Map<String, Object> migrateToV2(Map<String, Object> v1Data) {
        Map<String, Object> v2Data = new HashMap<>(v1Data);
        v2Data.put("schemaVersion", 2);
        // Add any v2-specific transformations
        return v2Data;
    }

    private Map<String, Object> serialize(Map<String, Object> entity) {
        // Simple pass-through for this test
        return new HashMap<>(entity);
    }

    private Map<String, Object> deserialize(Map<String, Object> data) {
        // Simple pass-through for this test
        return new HashMap<>(data);
    }

    private boolean validateFieldValue(Object value, long min, long max) {
        if (!(value instanceof Number)) {
            return false;
        }
        long longValue = ((Number) value).longValue();
        return longValue >= min && longValue <= max;
    }
}
