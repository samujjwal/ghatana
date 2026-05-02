package com.ghatana.datacloud.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("SchemaCompatibilityChecker")
class SchemaCompatibilityCheckerTest {

    private final SchemaCompatibilityChecker checker = new SchemaCompatibilityChecker(); 

    @Test
    @DisplayName("NONE mode accepts all changes")
    void noneModeAcceptsAllChanges() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.AVRO, List.of( 
                EventSchema.SchemaField.required("count", "int") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.NONE); 

        assertThat(result.compatible()).isTrue(); 
        assertThat(result.violations()).isEmpty(); 
    }

    @Test
    @DisplayName("format change is incompatible for enforced modes")
    void formatChangeIsIncompatible() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of()); 
        EventSchema newSchema = schema("subject-a", SchemaFormat.AVRO, List.of()); 

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); 

        assertThat(result.compatible()).isFalse(); 
        assertThat(result.violations()).singleElement().asString().contains("Schema format changed");
    }

    @Test
    @DisplayName("BACKWARD mode rejects new required field without default and type changes")
    void backwardModeRejectsUnsafeChanges() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.optional("status", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "uuid"), 
                EventSchema.SchemaField.optional("status", "string"), 
                EventSchema.SchemaField.required("tenant", "string") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); 

        assertThat(result.compatible()).isFalse(); 
        assertThat(result.violations()).anyMatch(v -> v.contains("New required field 'tenant'"));
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'id' type changed"));
    }

    @Test
    @DisplayName("BACKWARD mode allows new required field with default")
    void backwardModeAllowsRequiredFieldWithDefault() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.required("tenant", "string", "default-tenant") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.BACKWARD); 

        assertThat(result.compatible()).isTrue(); 
    }

    @Test
    @DisplayName("FORWARD mode rejects removed fields and type changes")
    void forwardModeRejectsRemovedFieldsAndTypeChanges() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.optional("status", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "uuid") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FORWARD); 

        assertThat(result.compatible()).isFalse(); 
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'status' removed"));
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'id' type changed"));
    }

    @Test
    @DisplayName("FULL mode rejects removed fields and new required fields")
    void fullModeRejectsRemovedAndNewRequiredFields() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.optional("status", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.required("tenant", "string") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FULL); 

        assertThat(result.compatible()).isFalse(); 
        assertThat(result.violations()).anyMatch(v -> v.contains("Field 'status' removed"));
        assertThat(result.violations()).anyMatch(v -> v.contains("New required field 'tenant'"));
    }

    @Test
    @DisplayName("compatible schemas return ok result")
    void compatibleSchemasReturnOk() { 
        EventSchema oldSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string") 
        ));
        EventSchema newSchema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of( 
                EventSchema.SchemaField.required("id", "string"), 
                EventSchema.SchemaField.optional("status", "string") 
        ));

        SchemaCompatibilityChecker.CompatibilityResult result = checker.check(oldSchema, newSchema, CompatibilityMode.FULL); 

        assertThat(result.compatible()).isTrue(); 
        assertThat(result.violations()).isEmpty(); 
    }

    @Test
    @DisplayName("check rejects null arguments")
    void checkRejectsNullArguments() { 
        EventSchema schema = schema("subject-a", SchemaFormat.JSON_SCHEMA, List.of()); 

        assertThatNullPointerException().isThrownBy(() -> checker.check(null, schema, CompatibilityMode.BACKWARD)); 
        assertThatNullPointerException().isThrownBy(() -> checker.check(schema, null, CompatibilityMode.BACKWARD)); 
        assertThatNullPointerException().isThrownBy(() -> checker.check(schema, schema, null)); 
    }

    private static EventSchema schema(String subject, SchemaFormat format, List<EventSchema.SchemaField> fields) { 
        return new EventSchema( 
                subject + "-id",
                subject,
                1,
                format,
                "{}",
                fields,
                java.util.Map.of(), 
                Instant.parse("2026-04-02T00:00:00Z")
        );
    }
}
