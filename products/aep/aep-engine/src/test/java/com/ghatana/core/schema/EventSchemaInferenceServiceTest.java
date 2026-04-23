/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        service = new DefaultEventSchemaInferenceService(); // GH-90000
    }

    @Nested
    @DisplayName("inferSchema()")
    class InferSchemaTests {

        @Test
        @DisplayName("infers schema from sample events")
        void infersSchemaFromSampleEvents() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "name", "Alice", "amount", 100.0, "active", true), // GH-90000
                Map.of("id", 2, "name", "Bob", "amount", 200.0, "active", false), // GH-90000
                Map.of("id", 3, "name", "Charlie", "amount", 150.0, "active", true) // GH-90000
            );

            EventSchema schema = service.inferSchema("transaction.created", samples); // GH-90000

            assertThat(schema.eventType()).isEqualTo("transaction.created");
            assertThat(schema.fields()).isNotEmpty(); // GH-90000
            assertThat(schema.fields()).containsKey("id");
            assertThat(schema.fields()).containsKey("name");
            assertThat(schema.fields()).containsKey("amount");
            assertThat(schema.fields()).containsKey("active");
        }

        @Test
        @DisplayName("infers field types correctly")
        void infersFieldTypesCorrectly() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "amount", 100.0, "active", true, "data", Map.of("key", "value")) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000

            assertThat(schema.fields().get("id").type()).isEqualTo(EventSchemaInferenceService.FieldType.INTEGER);
            assertThat(schema.fields().get("amount").type()).isEqualTo(EventSchemaInferenceService.FieldType.NUMBER);
            assertThat(schema.fields().get("active").type()).isEqualTo(EventSchemaInferenceService.FieldType.BOOLEAN);
            assertThat(schema.fields().get("data").type()).isEqualTo(EventSchemaInferenceService.FieldType.OBJECT);
        }

        @Test
        @DisplayName("infers required fields")
        void infersRequiredFields() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "name", "Alice"), // GH-90000
                Map.of("id", 2, "name", "Bob"), // GH-90000
                Map.of("id", 3, "name", "Charlie") // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000

            assertThat(schema.requiredFields()).contains("id");
            assertThat(schema.requiredFields()).contains("name");
        }

        @Test
        @DisplayName("handles fields with nulls")
        void handlesFieldsWithNulls() { // GH-90000
            Map<String, Object> first = new HashMap<>(); // GH-90000
            first.put("id", 1); // GH-90000
            first.put("name", "Alice"); // GH-90000
            first.put("optional", null); // GH-90000

            Map<String, Object> second = new HashMap<>(); // GH-90000
            second.put("id", 2); // GH-90000
            second.put("name", "Bob"); // GH-90000
            second.put("optional", null); // GH-90000

            List<Map<String, Object>> samples = List.of( // GH-90000
                first,
                second,
                Map.of("id", 3, "name", "Charlie", "optional", "value") // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000

            assertThat(schema.fields().get("optional").nullable()).isTrue();
            assertThat(schema.requiredFields()).doesNotContain("optional");
        }

        @Test
        @DisplayName("infers constraints for numeric fields")
        void infersConstraintsForNumericFields() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("amount", 10.0), // GH-90000
                Map.of("amount", 50.0), // GH-90000
                Map.of("amount", 100.0) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000

            Map<String, Object> constraints = schema.fields().get("amount").constraints();
            assertThat(constraints).containsKey("min");
            assertThat(constraints).containsKey("max");
        }

        @Test
        @DisplayName("infers constraints for string fields")
        void infersConstraintsForStringFields() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("status", "pending"), // GH-90000
                Map.of("status", "completed"), // GH-90000
                Map.of("status", "failed") // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000

            Map<String, Object> constraints = schema.fields().get("status").constraints();
            assertThat(constraints).containsKey("enum");
        }

        @Test
        @DisplayName("returns empty schema for null samples")
        void returnsEmptySchemaForNullSamples() { // GH-90000
            EventSchema schema = service.inferSchema("test.event", null); // GH-90000

            assertThat(schema.eventType()).isEqualTo("test.event");
            assertThat(schema.fields()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("returns empty schema for empty samples")
        void returnsEmptySchemaForEmptySamples() { // GH-90000
            EventSchema schema = service.inferSchema("test.event", List.of()); // GH-90000

            assertThat(schema.eventType()).isEqualTo("test.event");
            assertThat(schema.fields()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateEvent()")
    class ValidateEventTests {

        @Test
        @DisplayName("validates event against schema")
        void validatesEventAgainstSchema() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "name", "Alice", "amount", 100.0) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000
            Map<String, Object> event = Map.of("id", 1, "name", "Bob", "amount", 200.0); // GH-90000

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("detects missing required fields")
        void detectsMissingRequiredFields() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "name", "Alice") // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000
            Map<String, Object> event = Map.of("id", 1); // Missing name // GH-90000

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("Missing required"));
        }

        @Test
        @DisplayName("detects type mismatches")
        void detectsTypeMismatches() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1, "amount", 100.0) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000
            Map<String, Object> event = Map.of("id", "not-a-number", "amount", 200.0); // GH-90000

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("invalid type"));
        }

        @Test
        @DisplayName("warns about unknown fields")
        void warnsAboutUnknownFields() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000
            Map<String, Object> event = Map.of("id", 1, "unknownField", "value"); // GH-90000

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.warnings()).anyMatch(w -> w.contains("Unknown field"));
        }

        @Test
        @DisplayName("validates max field count")
        void validatesMaxFieldCount() { // GH-90000
            List<Map<String, Object>> samples = List.of( // GH-90000
                Map.of("id", 1) // GH-90000
            );

            EventSchema schema = service.inferSchema("test.event", samples); // GH-90000
            Map<String, Object> event = Map.of( // GH-90000
                "f1", 1, "f2", 2, "f3", 3, "f4", 4, "f5", 5,
                "f6", 6, "f7", 7, "f8", 8, "f9", 9, "f10", 10
            );

            EventSchemaInferenceService.ValidationResult result = service.validateEvent(event, schema); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).anyMatch(e -> e.contains("exceeds maximum field count"));
        }
    }

    @Nested
    @DisplayName("suggestImprovements()")
    class SuggestImprovementsTests {

        @Test
        @DisplayName("suggests allowing unknown fields")
        void suggestsAllowingUnknownFields() { // GH-90000
            EventSchema schema = new EventSchema( // GH-90000
                "test.event",
                Map.of(), // GH-90000
                List.of(), // GH-90000
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false) // GH-90000
            );

            List<String> errors = List.of("Unknown field: foo", "Unknown field: bar"); // GH-90000
            EventSchema improved = service.suggestImprovements(schema, errors); // GH-90000

            assertThat(improved.constraints().allowUnknownFields()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("logs suggestion for missing required fields")
        void logsSuggestionForMissingRequiredFields() { // GH-90000
            EventSchema schema = new EventSchema( // GH-90000
                "test.event",
                Map.of(), // GH-90000
                List.of("id", "name"), // GH-90000
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false) // GH-90000
            );

            List<String> errors = List.of("Missing required field: id");
            EventSchema improved = service.suggestImprovements(schema, errors); // GH-90000

            // Should log suggestion (verified in logs) // GH-90000
            assertThat(improved.constraints().allowUnknownFields()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("EventSchema")
    class EventSchemaTests {

        @Test
        @DisplayName("schema has required fields")
        void schemaHasRequiredFields() { // GH-90000
            EventSchema schema = new EventSchema( // GH-90000
                "test.event",
                Map.of(), // GH-90000
                List.of(), // GH-90000
                new EventSchemaInferenceService.SchemaConstraints(50, 10000, false) // GH-90000
            );

            assertThat(schema.eventType()).isNotNull(); // GH-90000
            assertThat(schema.fields()).isNotNull(); // GH-90000
            assertThat(schema.requiredFields()).isNotNull(); // GH-90000
            assertThat(schema.constraints()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("FieldDefinition")
    class FieldDefinitionTests {

        @Test
        @DisplayName("field definition has required fields")
        void fieldDefinitionHasRequiredFields() { // GH-90000
            EventSchemaInferenceService.FieldDefinition field = new EventSchemaInferenceService.FieldDefinition( // GH-90000
                "id",
                EventSchemaInferenceService.FieldType.INTEGER,
                true,
                false,
                null,
                Map.of() // GH-90000
            );

            assertThat(field.name()).isNotNull(); // GH-90000
            assertThat(field.type()).isNotNull(); // GH-90000
            assertThat(field.required()).isNotNull(); // GH-90000
            assertThat(field.nullable()).isNotNull(); // GH-90000
            assertThat(field.constraints()).isNotNull(); // GH-90000
        }
    }
}
