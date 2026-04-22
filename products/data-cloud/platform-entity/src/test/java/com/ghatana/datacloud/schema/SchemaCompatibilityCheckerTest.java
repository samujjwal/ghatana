package com.ghatana.datacloud.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("SchemaCompatibilityChecker [GH-90000]")
class SchemaCompatibilityCheckerTest {

    private final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker(); // GH-90000

    @Test
    @DisplayName("NONE mode accepts all changes [GH-90000]")
    void noneModeAcceptsAllChanges() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.AVRO, List.of( // GH-90000
                EventSchema.SchemaField.required("count", "int") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.NONE); // GH-90000

        assertThat(result.compatible()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("format change is incompatible for enforced modes [GH-90000]")
    void formatChangeIsIncompatible() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of()); // GH-90000
        EventSchema newSchema = schema("subject-a", SchemaFormat.AVRO, List.of()); // GH-90000

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); // GH-90000

        assertThat(result.compatible()).isFalse(); // GH-90000
        assertThat(result.violations()).singleElement().asString().contains("Schema format changed [GH-90000]");
    }

    @Test
    @DisplayName("BACKWARD mode rejects new required field without default and type changes [GH-90000]")
    void backwardModeRejectsUnsafeChanges() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.optional("status", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "uuid"), // GH-90000
                EventSchema.SchemaField.optional("status", "string"), // GH-90000
                EventSchema.SchemaField.required("tenant", "string") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); // GH-90000

        assertThat(result.compatible()).isFalse(); // GH-90000
        assertThat(result.violations()).anyMatch(v -> v.contains("New required field 'tenant' [GH-90000]"));
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'id' type changed [GH-90000]"));
    }

    @Test
    @DisplayName("BACKWARD mode allows new required field with default [GH-90000]")
    void backwardModeAllowsRequiredFieldWithDefault() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.required("tenant", "string", "default-tenant") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); // GH-90000

        assertThat(result.compatible()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("FORWARD mode rejects removed fields and type changes [GH-90000]")
    void forwardModeRejectsRemovedFieldsAndTypeChanges() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.optional("status", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "uuid") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FORWARD); // GH-90000

        assertThat(result.compatible()).isFalse(); // GH-90000
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'status' removed [GH-90000]"));
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'id' type changed [GH-90000]"));
    }

    @Test
    @DisplayName("FULL mode rejects removed fields and new required fields [GH-90000]")
    void fullModeRejectsRemovedAndNewRequiredFields() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.optional("status", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.required("tenant", "string") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FULL); // GH-90000

        assertThat(result.compatible()).isFalse(); // GH-90000
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'status' removed [GH-90000]"));
        assertThat(result.violations()).anyMatch(v -> v.contains("New required field 'tenant' [GH-90000]"));
    }

    @Test
    @DisplayName("compatible schemas return ok result [GH-90000]")
    void compatibleSchemasReturnOk() { // GH-90000
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string") // GH-90000
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( // GH-90000
                EventSchema.SchemaField.required("id", "string"), // GH-90000
                EventSchema.SchemaField.optional("status", "string") // GH-90000
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FULL); // GH-90000

        assertThat(result.compatible()).isTrue(); // GH-90000
        assertThat(result.violations()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("check rejects null arguments [GH-90000]")
    void checkRejectsNullArguments() { // GH-90000
        EventSchema schema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of()); // GH-90000

        assertThatNullPointerException().isThrownBy(() -> checker.check(null, schema, CompatibilityMode.BACKWARD)); // GH-90000
        assertThatNullPointerException().isThrownBy(() -> checker.check(schema, null, CompatibilityMode.BACKWARD)); // GH-90000
        assertThatNullPointerException().isThrownBy(() -> checker.check(schema, schema, null)); // GH-90000
    }

    private static EventSchema schema(String subject, SchemaFormat format, List<EventSchema.SchemaField> fields) { // GH-90000
        return new EventSchema( // GH-90000
                subject + "-id",
                subject,
                1,
                format,
                "{}",
                fields,
                java.util.Map.of(), // GH-90000
                Instant.parse("2026-04-02T00:00:00Z [GH-90000]")
        );
    }
}
