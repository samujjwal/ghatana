package com.ghatana.platform.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link EventParameterSpec#validate(Object)}. // GH-90000
 *
 * Covers: required checks, type compatibility, enum allowlist,
 * recursive array/list/map/object validation, and edge cases.
 */
@DisplayName("EventParameterSpec.validate()")
class EventParameterSpecValidationTest {

    // ── Required / Optional ────────────────────────────────────────────

    @Nested
    @DisplayName("Required parameter checks")
    class RequiredChecks {

        @Test
        @DisplayName("throws NullPointerException when required parameter is null")
        void requiredParameterRejectsNull() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("userId")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("userId")
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("accepts null for optional parameter")
        void optionalParameterAcceptsNull() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tag")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(false) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(null)).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ── Type Compatibility ─────────────────────────────────────────────

    @Nested
    @DisplayName("Type compatibility checks")
    class TypeCompatibility {

        @Test
        @DisplayName("accepts compatible string value")
        void acceptsCompatibleString() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("name")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("hello")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts compatible integer value (boxed)")
        void acceptsCompatibleInteger() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("count")
                    .type(EventParameterType.INTEGER) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("accepts compatible boolean value")
        void acceptsCompatibleBoolean() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("flag")
                    .type(EventParameterType.BOOLEAN) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(true)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("accepts compatible double value")
        void acceptsCompatibleDouble() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("score")
                    .type(EventParameterType.DOUBLE) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(3.14)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects incompatible type")
        void rejectsIncompatibleType() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("count")
                    .type(EventParameterType.INTEGER) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("count")
                    .hasMessageContaining("Integer");
        }
    }

    // ── Enum Allowlist ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Enum allowlist checks")
    class EnumAllowlist {

        @Test
        @DisplayName("accepts value in enum set")
        void acceptsAllowedValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("status")
                    .type(EventParameterType.STRING) // GH-90000
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED"))) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("ACTIVE")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects value not in enum set")
        void rejectsDisallowedValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("status")
                    .type(EventParameterType.STRING) // GH-90000
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED"))) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("status")
                    .hasMessageContaining("not in allowed values");
        }
    }

    // ── Recursive: Array / List ────────────────────────────────────────

    @Nested
    @DisplayName("Array/List recursive validation")
    class ArrayListValidation {

        @Test
        @DisplayName("validates list elements against itemsSpec")
        void validatesListElements() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tags")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("tag")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(List.of("a", "b", "c"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects list with invalid element type")
        void rejectsInvalidListElement() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tags")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("tag")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(List.of("a", 42, "c"))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tags[1]");
        }

        @Test
        @DisplayName("rejects list element that is null when required")
        void rejectsNullListElement() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("ids")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("id")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Arrays.asList("a", null))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("ids[1]");
        }
    }

    // ── Recursive: Map ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Map recursive validation")
    class MapValidation {

        @Test
        @DisplayName("validates map values against valueSpec")
        void validatesMapValues() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("metadata")
                    .type(EventParameterType.MAP) // GH-90000
                    .valueSpec(EventParameterSpec.builder() // GH-90000
                            .name("value")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(Map.of("k1", "v1", "k2", "v2"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects map with invalid value type")
        void rejectsInvalidMapValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("metadata")
                    .type(EventParameterType.MAP) // GH-90000
                    .valueSpec(EventParameterSpec.builder() // GH-90000
                            .name("value")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of("k1", "v1", "k2", 42))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("metadata")
                    .hasMessageContaining("k2");
        }
    }

    // ── Recursive: Object ──────────────────────────────────────────────

    @Nested
    @DisplayName("Object recursive validation")
    class ObjectValidation {

        @Test
        @DisplayName("validates object properties recursively")
        void validatesObjectProperties() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("user")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "name", EventParameterSpec.builder() // GH-90000
                                    .name("name")
                                    .type(EventParameterType.STRING) // GH-90000
                                    .required(true) // GH-90000
                                    .build(), // GH-90000
                            "age", EventParameterSpec.builder() // GH-90000
                                    .name("age")
                                    .type(EventParameterType.INTEGER) // GH-90000
                                    .required(false) // GH-90000
                                    .build() // GH-90000
                    ))
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(Map.of("name", "Alice", "age", 30))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects object with missing required property")
        void rejectsMissingRequiredProperty() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("user")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "name", EventParameterSpec.builder() // GH-90000
                                    .name("name")
                                    .type(EventParameterType.STRING) // GH-90000
                                    .required(true) // GH-90000
                                    .build() // GH-90000
                    ))
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("user.name");
        }

        @Test
        @DisplayName("rejects object with wrong property type")
        void rejectsWrongPropertyType() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("config")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "retries", EventParameterSpec.builder() // GH-90000
                                    .name("retries")
                                    .type(EventParameterType.INTEGER) // GH-90000
                                    .required(true) // GH-90000
                                    .build() // GH-90000
                    ))
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of("retries", "not-a-number"))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("config.retries");
        }
    }

    // ── Edge Cases ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("validates without type set (null type accepts any value)")
        void nullTypeAcceptsAnyValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("anything")
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("hello")).doesNotThrowAnyException();
            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("validates pre-defined constant specs")
        void predefinedConstantsAcceptValidValues() { // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate("corr-123"))
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(0.95)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_AUDIT_TRAIL.validate(true)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("pre-defined optional constants accept null")
        void predefinedConstantsAcceptNull() { // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate(null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }
}
