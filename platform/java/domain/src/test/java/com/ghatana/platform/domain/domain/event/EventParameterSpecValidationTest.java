package com.ghatana.platform.domain.domain.event;

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
 * Tests for {@link EventParameterSpec#validate(Object)}.
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
        void requiredParameterRejectsNull() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("userId")
                    .type(EventParameterType.STRING)
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("userId")
                    .hasMessageContaining("required");
        }

        @Test
        @DisplayName("accepts null for optional parameter")
        void optionalParameterAcceptsNull() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("tag")
                    .type(EventParameterType.STRING)
                    .required(false)
                    .build();

            assertThatCode(() -> spec.validate(null)).doesNotThrowAnyException();
        }
    }

    // ── Type Compatibility ─────────────────────────────────────────────

    @Nested
    @DisplayName("Type compatibility checks")
    class TypeCompatibility {

        @Test
        @DisplayName("accepts compatible string value")
        void acceptsCompatibleString() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("name")
                    .type(EventParameterType.STRING)
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate("hello")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts compatible integer value (boxed)")
        void acceptsCompatibleInteger() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("count")
                    .type(EventParameterType.INTEGER)
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts compatible boolean value")
        void acceptsCompatibleBoolean() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("flag")
                    .type(EventParameterType.BOOLEAN)
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(true)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts compatible double value")
        void acceptsCompatibleDouble() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("score")
                    .type(EventParameterType.DOUBLE)
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(3.14)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects incompatible type")
        void rejectsIncompatibleType() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("count")
                    .type(EventParameterType.INTEGER)
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate("not-a-number"))
                    .isInstanceOf(IllegalArgumentException.class)
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
        void acceptsAllowedValue() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("status")
                    .type(EventParameterType.STRING)
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED")))
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate("ACTIVE")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects value not in enum set")
        void rejectsDisallowedValue() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("status")
                    .type(EventParameterType.STRING)
                    .enumValues(new LinkedHashSet<>(Arrays.asList("PENDING", "ACTIVE", "COMPLETED")))
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
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
        void validatesListElements() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("tags")
                    .type(EventParameterType.LIST)
                    .itemsSpec(EventParameterSpec.builder()
                            .name("tag")
                            .type(EventParameterType.STRING)
                            .required(true)
                            .build())
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(List.of("a", "b", "c")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects list with invalid element type")
        void rejectsInvalidListElement() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("tags")
                    .type(EventParameterType.LIST)
                    .itemsSpec(EventParameterSpec.builder()
                            .name("tag")
                            .type(EventParameterType.STRING)
                            .required(true)
                            .build())
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(List.of("a", 42, "c")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tags[1]");
        }

        @Test
        @DisplayName("rejects list element that is null when required")
        void rejectsNullListElement() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("ids")
                    .type(EventParameterType.LIST)
                    .itemsSpec(EventParameterSpec.builder()
                            .name("id")
                            .type(EventParameterType.STRING)
                            .required(true)
                            .build())
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(Arrays.asList("a", null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ids[1]");
        }
    }

    // ── Recursive: Map ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Map recursive validation")
    class MapValidation {

        @Test
        @DisplayName("validates map values against valueSpec")
        void validatesMapValues() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("metadata")
                    .type(EventParameterType.MAP)
                    .valueSpec(EventParameterSpec.builder()
                            .name("value")
                            .type(EventParameterType.STRING)
                            .required(true)
                            .build())
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(Map.of("k1", "v1", "k2", "v2")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects map with invalid value type")
        void rejectsInvalidMapValue() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("metadata")
                    .type(EventParameterType.MAP)
                    .valueSpec(EventParameterSpec.builder()
                            .name("value")
                            .type(EventParameterType.STRING)
                            .required(true)
                            .build())
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(Map.of("k1", "v1", "k2", 42)))
                    .isInstanceOf(IllegalArgumentException.class)
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
        void validatesObjectProperties() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("user")
                    .type(EventParameterType.OBJECT)
                    .properties(Map.of(
                            "name", EventParameterSpec.builder()
                                    .name("name")
                                    .type(EventParameterType.STRING)
                                    .required(true)
                                    .build(),
                            "age", EventParameterSpec.builder()
                                    .name("age")
                                    .type(EventParameterType.INTEGER)
                                    .required(false)
                                    .build()
                    ))
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate(Map.of("name", "Alice", "age", 30)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects object with missing required property")
        void rejectsMissingRequiredProperty() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("user")
                    .type(EventParameterType.OBJECT)
                    .properties(Map.of(
                            "name", EventParameterSpec.builder()
                                    .name("name")
                                    .type(EventParameterType.STRING)
                                    .required(true)
                                    .build()
                    ))
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("user.name");
        }

        @Test
        @DisplayName("rejects object with wrong property type")
        void rejectsWrongPropertyType() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("config")
                    .type(EventParameterType.OBJECT)
                    .properties(Map.of(
                            "retries", EventParameterSpec.builder()
                                    .name("retries")
                                    .type(EventParameterType.INTEGER)
                                    .required(true)
                                    .build()
                    ))
                    .required(true)
                    .build();

            assertThatThrownBy(() -> spec.validate(Map.of("retries", "not-a-number")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("config.retries");
        }
    }

    // ── Edge Cases ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("validates without type set (null type accepts any value)")
        void nullTypeAcceptsAnyValue() {
            EventParameterSpec spec = EventParameterSpec.builder()
                    .name("anything")
                    .required(true)
                    .build();

            assertThatCode(() -> spec.validate("hello")).doesNotThrowAnyException();
            assertThatCode(() -> spec.validate(42)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("validates pre-defined constant specs")
        void predefinedConstantsAcceptValidValues() {
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate("corr-123"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(0.95))
                    .doesNotThrowAnyException();
            assertThatCode(() -> EventParameterSpec.EVENT_AUDIT_TRAIL.validate(true))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("pre-defined optional constants accept null")
        void predefinedConstantsAcceptNull() {
            assertThatCode(() -> EventParameterSpec.EVENT_CORRELATION_ID.validate(null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> EventParameterSpec.EVENT_CONFIDENCE.validate(null))
                    .doesNotThrowAnyException();
        }
    }
}
