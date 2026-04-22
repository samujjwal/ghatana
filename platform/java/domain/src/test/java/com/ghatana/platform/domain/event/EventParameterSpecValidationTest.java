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
@DisplayName("EventParameterSpec.validate() [GH-90000]")
class EventParameterSpecValidationTest {

    // ── Required / Optional ────────────────────────────────────────────

    @Nested
    @DisplayName("Required parameter checks [GH-90000]")
    class RequiredChecks {

        @Test
        @DisplayName("throws NullPointerException when required parameter is null [GH-90000]")
        void requiredParameterRejectsNull() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("userId [GH-90000]")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("userId [GH-90000]")
                    .hasMessageContaining("required [GH-90000]");
        }

        @Test
        @DisplayName("accepts null for optional parameter [GH-90000]")
        void optionalParameterAcceptsNull() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tag [GH-90000]")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(false) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(null)).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ── Type Compatibility ─────────────────────────────────────────────

    @Nested
    @DisplayName("Type compatibility checks [GH-90000]")
    class TypeCompatibility {

        @Test
        @DisplayName("accepts compatible string value [GH-90000]")
        void acceptsCompatibleString() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("name [GH-90000]")
                    .type(EventParameterType.STRING) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("hello [GH-90000]")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts compatible integer value (boxed) [GH-90000]")
        void acceptsCompatibleInteger() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("count [GH-90000]")
                    .type(EventParameterType.INTEGER) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("accepts compatible boolean value [GH-90000]")
        void acceptsCompatibleBoolean() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("flag [GH-90000]")
                    .type(EventParameterType.BOOLEAN) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(true)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("accepts compatible double value [GH-90000]")
        void acceptsCompatibleDouble() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("score [GH-90000]")
                    .type(EventParameterType.DOUBLE) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(3.14)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects incompatible type [GH-90000]")
        void rejectsIncompatibleType() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("count [GH-90000]")
                    .type(EventParameterType.INTEGER) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate("not-a-number [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("count [GH-90000]")
                    .hasMessageContaining("Integer [GH-90000]");
        }
    }

    // ── Enum Allowlist ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Enum allowlist checks [GH-90000]")
    class EnumAllowlist {

        @Test
        @DisplayName("accepts value in enum set [GH-90000]")
        void acceptsAllowedValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("status [GH-90000]")
                    .type(EventParameterType.STRING) // GH-90000
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED"))) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("ACTIVE [GH-90000]")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects value not in enum set [GH-90000]")
        void rejectsDisallowedValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("status [GH-90000]")
                    .type(EventParameterType.STRING) // GH-90000
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED"))) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate("UNKNOWN [GH-90000]"))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("status [GH-90000]")
                    .hasMessageContaining("not in allowed values [GH-90000]");
        }
    }

    // ── Recursive: Array / List ────────────────────────────────────────

    @Nested
    @DisplayName("Array/List recursive validation [GH-90000]")
    class ArrayListValidation {

        @Test
        @DisplayName("validates list elements against itemsSpec [GH-90000]")
        void validatesListElements() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tags [GH-90000]")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("tag [GH-90000]")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(List.of("a", "b", "c"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects list with invalid element type [GH-90000]")
        void rejectsInvalidListElement() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("tags [GH-90000]")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("tag [GH-90000]")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(List.of("a", 42, "c"))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tags[1] [GH-90000]");
        }

        @Test
        @DisplayName("rejects list element that is null when required [GH-90000]")
        void rejectsNullListElement() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("ids [GH-90000]")
                    .type(EventParameterType.LIST) // GH-90000
                    .itemsSpec(EventParameterSpec.builder() // GH-90000
                            .name("id [GH-90000]")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Arrays.asList("a", null))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("ids[1] [GH-90000]");
        }
    }

    // ── Recursive: Map ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Map recursive validation [GH-90000]")
    class MapValidation {

        @Test
        @DisplayName("validates map values against valueSpec [GH-90000]")
        void validatesMapValues() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("metadata [GH-90000]")
                    .type(EventParameterType.MAP) // GH-90000
                    .valueSpec(EventParameterSpec.builder() // GH-90000
                            .name("value [GH-90000]")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate(Map.of("k1", "v1", "k2", "v2"))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("rejects map with invalid value type [GH-90000]")
        void rejectsInvalidMapValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("metadata [GH-90000]")
                    .type(EventParameterType.MAP) // GH-90000
                    .valueSpec(EventParameterSpec.builder() // GH-90000
                            .name("value [GH-90000]")
                            .type(EventParameterType.STRING) // GH-90000
                            .required(true) // GH-90000
                            .build()) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of("k1", "v1", "k2", 42))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("metadata [GH-90000]")
                    .hasMessageContaining("k2 [GH-90000]");
        }
    }

    // ── Recursive: Object ──────────────────────────────────────────────

    @Nested
    @DisplayName("Object recursive validation [GH-90000]")
    class ObjectValidation {

        @Test
        @DisplayName("validates object properties recursively [GH-90000]")
        void validatesObjectProperties() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("user [GH-90000]")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "name", EventParameterSpec.builder() // GH-90000
                                    .name("name [GH-90000]")
                                    .type(EventParameterType.STRING) // GH-90000
                                    .required(true) // GH-90000
                                    .build(), // GH-90000
                            "age", EventParameterSpec.builder() // GH-90000
                                    .name("age [GH-90000]")
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
        @DisplayName("rejects object with missing required property [GH-90000]")
        void rejectsMissingRequiredProperty() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("user [GH-90000]")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "name", EventParameterSpec.builder() // GH-90000
                                    .name("name [GH-90000]")
                                    .type(EventParameterType.STRING) // GH-90000
                                    .required(true) // GH-90000
                                    .build() // GH-90000
                    ))
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("user.name [GH-90000]");
        }

        @Test
        @DisplayName("rejects object with wrong property type [GH-90000]")
        void rejectsWrongPropertyType() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("config [GH-90000]")
                    .type(EventParameterType.OBJECT) // GH-90000
                    .properties(Map.of( // GH-90000
                            "retries", EventParameterSpec.builder() // GH-90000
                                    .name("retries [GH-90000]")
                                    .type(EventParameterType.INTEGER) // GH-90000
                                    .required(true) // GH-90000
                                    .build() // GH-90000
                    ))
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatThrownBy(() -> spec.validate(Map.of("retries", "not-a-number"))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("config.retries [GH-90000]");
        }
    }

    // ── Edge Cases ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases [GH-90000]")
    class EdgeCases {

        @Test
        @DisplayName("validates without type set (null type accepts any value) [GH-90000]")
        void nullTypeAcceptsAnyValue() { // GH-90000
            EventParameterSpec spec = EventParameterSpec.builder() // GH-90000
                    .name("anything [GH-90000]")
                    .required(true) // GH-90000
                    .build(); // GH-90000

            assertThatCode(() -> spec.validate("hello [GH-90000]")).doesNotThrowAnyException();
            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("validates pre-defined constant specs [GH-90000]")
        void predefinedConstantsAcceptValidValues() { // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate("corr-123 [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(0.95)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_AUDIT_TRAIL.validate(true)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("pre-defined optional constants accept null [GH-90000]")
        void predefinedConstantsAcceptNull() { // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate(null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(null)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }
}
