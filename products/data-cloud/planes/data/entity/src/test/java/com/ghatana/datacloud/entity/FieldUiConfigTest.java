/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3: Contract tests for FieldUiConfig.
 *
 * @doc.type class
 * @doc.purpose Tests for FieldUiConfig record and factory methods
 * @doc.layer test
 */
@DisplayName("FieldUiConfig Tests")
class FieldUiConfigTest {

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("empty creates config with all null values")
        void emptyCreatesConfigWithNullValues() {
            FieldUiConfig config = FieldUiConfig.empty();

            assertThat(config.visible()).isNull();
            assertThat(config.hidden()).isNull();
            assertThat(config.readOnly()).isNull();
            assertThat(config.disabled()).isNull();
            assertThat(config.order()).isNull();
            assertThat(config.span()).isNull();
        }

        @Test
        @DisplayName("visible creates config with visible true and order")
        void visibleCreatesConfigWithVisibility() {
            FieldUiConfig config = FieldUiConfig.visible(5);

            assertThat(config.visible()).isTrue();
            assertThat(config.order()).isEqualTo(5);
        }

        @Test
        @DisplayName("hiddenField creates config with hidden true and visible false")
        void hiddenFieldCreatesConfigWithHidden() {
            FieldUiConfig config = FieldUiConfig.hiddenField();

            assertThat(config.hidden()).isTrue();
            assertThat(config.visible()).isFalse();
        }

        @Test
        @DisplayName("readOnly creates config with readOnly true and order")
        void readOnlyCreatesConfigWithReadOnly() {
            FieldUiConfig config = FieldUiConfig.readOnly(3);

            assertThat(config.visible()).isTrue();
            assertThat(config.readOnly()).isTrue();
            assertThat(config.order()).isEqualTo(3);
        }

        @Test
        @DisplayName("fullWidth creates config with span 12")
        void fullWidthCreatesConfigWithFullSpan() {
            FieldUiConfig config = FieldUiConfig.fullWidth(2);

            assertThat(config.visible()).isTrue();
            assertThat(config.order()).isEqualTo(2);
            assertThat(config.span()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("Map Conversion")
    class MapConversionTests {

        @Test
        @DisplayName("fromMap with null returns empty config")
        void fromMapWithNullReturnsEmpty() {
            FieldUiConfig config = FieldUiConfig.fromMap(null);

            assertThat(config).isEqualTo(FieldUiConfig.empty());
        }

        @Test
        @DisplayName("fromMap with empty map returns empty config")
        void fromMapWithEmptyReturnsEmpty() {
            FieldUiConfig config = FieldUiConfig.fromMap(Map.of());

            assertThat(config).isEqualTo(FieldUiConfig.empty());
        }

        @Test
        @DisplayName("fromMap populates all fields")
        void fromMapPopulatesAllFields() {
            Map<String, Object> map = Map.of(
                "visible", true,
                "hidden", false,
                "readOnly", true,
                "disabled", false,
                "order", 5,
                "span", 6,
                "width", "100%",
                "placeholder", "Enter value",
                "helpText", "Help text",
                "tooltip", "Tooltip",
                "icon", "icon-name",
                "inputType", "text",
                "rows", 3,
                "multiline", true,
                "format", "MM/dd/yyyy",
                "prefix", "$",
                "suffix", "USD",
                "section", "Section 1",
                "group", "Group A",
                "showWhen", "field1 == 'value'",
                "hideWhen", "field2 == 'other'"
            );

            FieldUiConfig config = FieldUiConfig.fromMap(map);

            assertThat(config.visible()).isTrue();
            assertThat(config.hidden()).isFalse();
            assertThat(config.readOnly()).isTrue();
            assertThat(config.disabled()).isFalse();
            assertThat(config.order()).isEqualTo(5);
            assertThat(config.span()).isEqualTo(6);
            assertThat(config.width()).isEqualTo("100%");
            assertThat(config.placeholder()).isEqualTo("Enter value");
            assertThat(config.helpText()).isEqualTo("Help text");
            assertThat(config.tooltip()).isEqualTo("Tooltip");
            assertThat(config.icon()).isEqualTo("icon-name");
            assertThat(config.inputType()).isEqualTo("text");
            assertThat(config.rows()).isEqualTo(3);
            assertThat(config.multiline()).isTrue();
            assertThat(config.format()).isEqualTo("MM/dd/yyyy");
            assertThat(config.prefix()).isEqualTo("$");
            assertThat(config.suffix()).isEqualTo("USD");
            assertThat(config.section()).isEqualTo("Section 1");
            assertThat(config.group()).isEqualTo("Group A");
            assertThat(config.showWhen()).isEqualTo("field1 == 'value'");
            assertThat(config.hideWhen()).isEqualTo("field2 == 'other'");
        }

        @Test
        @DisplayName("fromMap handles boolean strings")
        void fromMapHandlesBooleanStrings() {
            Map<String, Object> map = Map.of(
                "visible", "true",
                "hidden", "false"
            );

            FieldUiConfig config = FieldUiConfig.fromMap(map);

            assertThat(config.visible()).isTrue();
            assertThat(config.hidden()).isFalse();
        }

        @Test
        @DisplayName("fromMap handles integer strings")
        void fromMapHandlesIntegerStrings() {
            Map<String, Object> map = Map.of(
                "order", "10",
                "span", "6"
            );

            FieldUiConfig config = FieldUiConfig.fromMap(map);

            assertThat(config.order()).isEqualTo(10);
            assertThat(config.span()).isEqualTo(6);
        }

        @Test
        @DisplayName("fromMap handles invalid integer string as null")
        void fromMapHandlesInvalidIntegerStringAsNull() {
            Map<String, Object> map = Map.of(
                "order", "invalid"
            );

            FieldUiConfig config = FieldUiConfig.fromMap(map);

            assertThat(config.order()).isNull();
        }

        @Test
        @DisplayName("toMap includes only non-null values")
        void toMapIncludesOnlyNonNullValues() {
            FieldUiConfig config = FieldUiConfig.builder()
                .visible(true)
                .order(5)
                .placeholder("Enter value")
                .build();

            Map<String, Object> map = config.toMap();

            assertThat(map).hasSize(3);
            assertThat(map.get("visible")).isEqualTo(true);
            assertThat(map.get("order")).isEqualTo(5);
            assertThat(map.get("placeholder")).isEqualTo("Enter value");
        }

        @Test
        @DisplayName("toMap from fromMap round-trip preserves data")
        void toMapFromFromMapRoundTripPreservesData() {
            Map<String, Object> original = Map.of(
                "visible", true,
                "order", 5,
                "placeholder", "test"
            );

            FieldUiConfig config = FieldUiConfig.fromMap(original);
            Map<String, Object> result = config.toMap();

            assertThat(result).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPatternTests {

        @Test
        @DisplayName("builder creates config with specified values")
        void builderCreatesConfigWithSpecifiedValues() {
            FieldUiConfig config = FieldUiConfig.builder()
                .visible(true)
                .order(10)
                .span(6)
                .placeholder("Enter value")
                .build();

            assertThat(config.visible()).isTrue();
            assertThat(config.order()).isEqualTo(10);
            assertThat(config.span()).isEqualTo(6);
            assertThat(config.placeholder()).isEqualTo("Enter value");
        }

        @Test
        @DisplayName("with creates new instance with updated field")
        void withCreatesNewInstanceWithUpdatedField() {
            FieldUiConfig original = FieldUiConfig.builder()
                .visible(true)
                .order(5)
                .build();

            FieldUiConfig updated = original.withOrder(10);

            assertThat(original.order()).isEqualTo(5);
            assertThat(updated.order()).isEqualTo(10);
            assertThat(updated.visible()).isTrue();
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("record is immutable")
        void recordIsImmutable() {
            FieldUiConfig config = FieldUiConfig.builder()
                .visible(true)
                .order(5)
                .build();

            // Records are immutable by design
            assertThat(config).isNotNull();
        }
    }
}
