package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FieldDefinition}.
 */
@DisplayName("FieldDefinition")
class FieldDefinitionTest {

    @Test
    @DisplayName("builder creates field with required fields")
    void builder_createsField() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .type(FieldDefinition.FieldType.STRING)
                .build();

        assertThat(field.getName()).isEqualTo("email");
        assertThat(field.getType()).isEqualTo(FieldDefinition.FieldType.STRING);
    }

    @Test
    @DisplayName("builder sets default values")
    void builder_setsDefaultValues() {
        FieldDefinition field = FieldDefinition.builder()
                .name("test")
                .build();

        assertThat(field.getType()).isEqualTo(FieldDefinition.FieldType.STRING);
        assertThat(field.getRequired()).isFalse();
        assertThat(field.getUnique()).isFalse();
        assertThat(field.getIndexed()).isFalse();
    }

    @Test
    @DisplayName("required fluent setter sets required to true")
    void required_setsRequiredToTrue() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .required(true)
                .build();

        assertThat(field.getRequired()).isTrue();
    }

    @Test
    @DisplayName("unique fluent setter sets unique to true")
    void unique_setsUniqueToTrue() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .unique(true)
                .build();

        assertThat(field.getUnique()).isTrue();
    }

    @Test
    @DisplayName("indexed fluent setter sets indexed to true")
    void indexed_setsIndexedToTrue() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .indexed(true)
                .build();

        assertThat(field.getIndexed()).isTrue();
    }

    @Test
    @DisplayName("label can be set")
    void label_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .label("Email Address")
                .build();

        assertThat(field.getLabel()).isEqualTo("Email Address");
    }

    @Test
    @DisplayName("description can be set")
    void description_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .description("User email address")
                .build();

        assertThat(field.getDescription()).isEqualTo("User email address");
    }

    @Test
    @DisplayName("defaultValue can be set")
    void defaultValue_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("status")
                .defaultValue("active")
                .build();

        assertThat(field.getDefaultValue()).isEqualTo("active");
    }

    @Test
    @DisplayName("min can be set")
    void min_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("age")
                .min(0)
                .build();

        assertThat(field.getMin()).isEqualTo(0);
    }

    @Test
    @DisplayName("max can be set")
    void max_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("age")
                .max(150)
                .build();

        assertThat(field.getMax()).isEqualTo(150);
    }

    @Test
    @DisplayName("pattern can be set")
    void pattern_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .pattern("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$")
                .build();

        assertThat(field.getPattern()).isEqualTo("^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$");
    }

    @Test
    @DisplayName("allowedValues can be set")
    void allowedValues_canBeSet() {
        List<Object> values = List.of("active", "inactive", "pending");
        FieldDefinition field = FieldDefinition.builder()
                .name("status")
                .allowedValues(values)
                .build();

        assertThat(field.getAllowedValues()).isEqualTo(values);
    }

    @Test
    @DisplayName("nestedFields can be set")
    void nestedFields_canBeSet() {
        FieldDefinition nested = FieldDefinition.builder()
                .name("street")
                .type(FieldDefinition.FieldType.STRING)
                .build();
        FieldDefinition field = FieldDefinition.builder()
                .name("address")
                .type(FieldDefinition.FieldType.OBJECT)
                .nestedFields(List.of(nested))
                .build();

        assertThat(field.getNestedFields()).hasSize(1);
    }

    @Test
    @DisplayName("elementType can be set")
    void elementType_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("tags")
                .type(FieldDefinition.FieldType.ARRAY)
                .elementType(FieldDefinition.FieldType.STRING)
                .build();

        assertThat(field.getElementType()).isEqualTo(FieldDefinition.FieldType.STRING);
    }

    @Test
    @DisplayName("referenceCollection can be set")
    void referenceCollection_canBeSet() {
        FieldDefinition field = FieldDefinition.builder()
                .name("userId")
                .type(FieldDefinition.FieldType.REFERENCE)
                .referenceCollection("users")
                .build();

        assertThat(field.getReferenceCollection()).isEqualTo("users");
    }

    @Test
    @DisplayName("config can be set")
    void config_canBeSet() {
        java.util.Map<String, Object> config = java.util.Map.of("format", "json");
        FieldDefinition field = FieldDefinition.builder()
                .name("metadata")
                .config(config)
                .build();

        assertThat(field.getConfig()).isEqualTo(config);
    }

    @Test
    @DisplayName("FieldType enum contains all expected types")
    void fieldTypeEnum_containsAllExpectedTypes() {
        FieldDefinition.FieldType[] types = FieldDefinition.FieldType.values();
        assertThat(types).contains(
                FieldDefinition.FieldType.STRING,
                FieldDefinition.FieldType.INTEGER,
                FieldDefinition.FieldType.LONG,
                FieldDefinition.FieldType.DECIMAL,
                FieldDefinition.FieldType.BOOLEAN,
                FieldDefinition.FieldType.DATE,
                FieldDefinition.FieldType.DATETIME,
                FieldDefinition.FieldType.TIMESTAMP,
                FieldDefinition.FieldType.UUID,
                FieldDefinition.FieldType.OBJECT,
                FieldDefinition.FieldType.ARRAY,
                FieldDefinition.FieldType.REFERENCE,
                FieldDefinition.FieldType.JSON,
                FieldDefinition.FieldType.BINARY
        );
    }
}
