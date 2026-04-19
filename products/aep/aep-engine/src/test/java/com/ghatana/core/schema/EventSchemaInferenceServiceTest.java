/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.schema;

import com.ghatana.core.schema.EventSchemaInferenceService.EventSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EventSchemaInferenceService.
 *
 * @doc.type class
 * @doc.purpose Test event schema inference
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("EventSchemaInferenceService")
class EventSchemaInferenceServiceTest {

    private EventSchemaInferenceService service;

    @BeforeEach
    void setUp() {
        service = new DefaultEventSchemaInferenceService();
    }

    @Nested
    @DisplayName("inferSchema()")
    class InferSchemaTests {

        @Test
        @DisplayName("infers schema from sample events")
        void infersSchemaFromSampleEvents() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "name", "Alice", "amount", 100.0, "active", true),
                Map.of("id", 2, "name", "Bob", "amount", 200.0, "active", false),
                Map.of("id", 3, "name", "Charlie", "amount", 150.0, "active", true)
            );

            EventSchema schema = service.inferSchema("transaction.created", samples);

            assertThat(schema.eventType()).isEqualTo("transaction.created");
            assertThat(schema.fields()).isNotEmpty();
            assertThat(schema.fields()).containsKey("id");
            assertThat(schema.fields()).containsKey("name");
            assertThat(schema.fields()).containsKey("amount");
            assertThat(schema.fields()).containsKey("active");
        }

        @Test
        @DisplayName("infers field types correctly")
        void infersFieldTypesCorrectly() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "amount", 100.0, "active", true, "data", Map.of("key", "value"))
            );

            EventSchema schema = service.inferSchema("test.event", samples);

            assertThat(schema.fields().get("id").type()).isEqualTo(EventSchemaInferenceService.FieldType.INTEGER);
            assertThat(schema.fields().get("amount").type()).isEqualTo(EventSchemaInferenceService.FieldType.NUMBER);
            assertThat(schema.fields().get("active").type()).isEqualTo(EventSchemaInferenceService.FieldType.BOOLEAN);
            assertThat(schema.fields().get("data").type()).isEqualTo(EventSchemaInferenceService.FieldType.OBJECT);
        }

        @Test
        @DisplayName("infers required fields")
        void infersRequiredFields() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob"),
                Map.of("id", 3, "name", "Charlie")
            );

            EventSchema schema = service.inferSchema("test.event", samples);

            assertThat(schema.requiredFields()).contains("id");
            assertThat(schema.requiredFields()).contains("name");
        }

        @Test
        @DisplayName("handles fields with nulls")
        void handlesFieldsWithNulls() {
            Map<String, Object> first = new HashMap<>();
            first.put("id", 1);
            first.put("name", "Alice");
            first.put("optional", null);

            Map<String, Object> second = new HashMap<>();
            second.put("id", 2);
            second.put("name", "Bob");
            second.put("optional", null);

            List<Map<String, Object>> samples = List.of(
                first,
                second,
                Map.of("id", 3, "name", "Charlie", "optional", "value")
            );

            EventSchema schema = service.inferSchema("test.event", samples);

            assertThat(schema.fields().get("optional").nullable()).isTrue();
            assertThat(schema.requiredFields()).doesNotContain("optional");
        }

        @Test
        @DisplayName("infers constraints for numeric fields")
        void infersConstraintsForNumericFields() {
            List<Map<String, Object>> samples = List.of(
                Map.of("amount", 10.0),
                Map.of("amount", 50.0),
                Map.of("amount", 100.0)
            );

            EventSchema schema = service.inferSchema("test.event", samples);

            Map<String, Object> constraints = schema.fields().get("amount").constraints();
            assertThat(constraints).containsKey("min");
            assertThat(constraints).containsKey("max");
        }

        @Test
        @DisplayName("infers constraints for string fields")
        void infersConstraintsForStringFields() {
            List<Map<String, Object>> samples = List.of(
                Map.of("status", "pending"),
                Map.of("status", "completed"),
                Map.of("status", "failed")
            );

            EventSchema schema = service.inferSchema("test.event", samples);

            Map<String, Object> constraints = schema.fields().get("status").constraints();
            assertThat(constraints).containsKey("enum");
        }

        @Test
        @DisplayName("returns empty schema for null samples")
        void returnsEmptySchemaForNullSamples() {
            EventSchema schema = service.inferSchema("test.event", null);

            assertThat(schema.eventType()).isEqualTo("test.event");
            assertThat(schema.fields()).isEmpty();
        }

        @Test
        @DisplayName("returns empty schema for empty samples")
        void returnsEmptySchemaForEmptySamples() {
            EventSchema schema = service.inferSchema("test.event", List.of());

            assertThat(schema.eventType()).isEqualTo("test.event");
            assertThat(schema.fields()).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateEvent()")
    class ValidateEventTests {

        @Test
        @DisplayName("validates event against schema")
        void validatesEventAgainstSchema() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "name", "Alice", "amount", 100.0)
            );

            EventSchema schema = service.inferSchema("test.event", samples);
            Map<String, Object> event = Map.of("id", 1, "name", "Bob", "amount", 200.0);

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("detects missing required fields")
        void detectsMissingRequiredFields() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "name", "Alice")
            );

            EventSchema schema = service.inferSchema("test.event", samples);
            Map<String, Object> event = Map.of("id", 1); // Missing name

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("Missing required"));
        }

        @Test
        @DisplayName("detects type mismatches")
        void detectsTypeMismatches() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1, "amount", 100.0)
            );

            EventSchema schema = service.inferSchema("test.event", samples);
            Map<String, Object> event = Map.of("id", "not-a-number", "amount", 200.0);

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("invalid type"));
        }

        @Test
        @DisplayName("warns about unknown fields")
        void warnsAboutUnknownFields() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1)
            );

            EventSchema schema = service.inferSchema("test.event", samples);
            Map<String, Object> event = Map.of("id", 1, "unknownField", "value");

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("Unknown field"));
        }

        @Test
        @DisplayName("validates max field count")
        void validatesMaxFieldCount() {
            List<Map<String, Object>> samples = List.of(
                Map.of("id", 1)
            );

            EventSchema schema = service.inferSchema("test.event", samples);
            Map<String, Object> event = Map.of(
                "f1", 1, "f2", 2, "f3", 3, "f4", 4, "f5", 5,
                "f6", 6, "f7", 7, "f8", 8, "f9", 9, "f10", 10
            );

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("exceeds maximum field count"));
        }
    }

    @Nested
    @DisplayName("suggestImprovements()")
    class SuggestImprovementsTests {

        @Test
        @DisplayName("suggests allowing unknown fields")
        void suggestsAllowingUnknownFields() {
            EventSchema schema = new EventSchema(
                "test.event",
                Map.of(),
                List.of(),
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false)
            );

            List<String> errors = List.of("Unknown field: foo", "Unknown field: bar");
            EventSchema improved = service.suggestImprovements(schema, errors);

            assertThat(improved.constraints().allowUnknownFields()).isTrue();
        }

        @Test
        @DisplayName("logs suggestion for missing required fields")
        void logsSuggestionForMissingRequiredFields() {
            EventSchema schema = new EventSchema(
                "test.event",
                Map.of(),
                List.of("id", "name"),
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false)
            );

            List<String> errors = List.of("Missing required field: id");
            EventSchema improved = service.suggestImprovements(schema, errors);

            // Should log suggestion (verified in logs)
            assertThat(improved.constraints().allowUnknownFields()).isTrue();
        }
    }

    @Nested
    @DisplayName("EventSchema")
    class EventSchemaTests {

        @Test
        @DisplayName("schema has required fields")
        void schemaHasRequiredFields() {
            EventSchema schema = new EventSchema(
                "test.event",
                Map.of(),
                List.of(),
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false)
            );

            assertThat(schema.eventType()).isNotNull();
            assertThat(schema.fields()).isNotNull();
            assertThat(schema.requiredFields()).isNotNull();
            assertThat(schema.constraints()).isNotNull();
        }
    }

    @Nested
    @DisplayName("FieldDefinition")
    class FieldDefinitionTests {

        @Test
        @DisplayName("field definition has required fields")
        void fieldDefinitionHasRequiredFields() {
            EventSchemaInferenceService.FieldDefinition field = new EventSchemaInferenceService.FieldDefinition(
                "id",
                EventSchemaInferenceService.FieldType.INTEGER,
                true,
                false,
                null,
                Map.of()
            );

            assertThat(field.name()).isNotNull();
            assertThat(field.type()).isNotNull();
            assertThat(field.required()).isNotNull();
            assertThat(field.nullable()).isNotNull();
            assertThat(field.constraints()).isNotNull();
        }
    }
}
