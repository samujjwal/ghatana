package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ToolCall}.
 *
 * Covers factory methods, type-safe argument extraction, convenience accessors,
 * immutability, null handling, and equality.
 */
@DisplayName("ToolCall")
class ToolCallTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(name, args) auto-generates UUID id")
        void ofGeneratesId() {
            ToolCall tc = ToolCall.of("search", Map.of("query", "test"));
            assertThat(tc.getId()).isNotNull().isNotEmpty();
            assertThat(tc.getName()).isEqualTo("search");
            assertThat(tc.getArguments()).containsEntry("query", "test");
        }

        @Test
        @DisplayName("of(id, name, args) uses provided id")
        void ofWithExplicitId() {
            ToolCall tc = ToolCall.of("call-123", "search", Map.of("q", "test"));
            assertThat(tc.getId()).isEqualTo("call-123");
            assertThat(tc.getName()).isEqualTo("search");
        }

        @Test
        @DisplayName("two calls to of(name, args) generate different ids")
        void uniqueIds() {
            ToolCall tc1 = ToolCall.of("search", Map.of());
            ToolCall tc2 = ToolCall.of("search", Map.of());
            assertThat(tc1.getId()).isNotEqualTo(tc2.getId());
        }

        @Test
        @DisplayName("null arguments treated as empty map")
        void nullArguments() {
            ToolCall tc = ToolCall.of("tool", null);
            assertThat(tc.getArguments()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("argument immutability")
    class ArgumentImmutability {

        @Test
        @DisplayName("arguments map is unmodifiable")
        void argumentsUnmodifiable() {
            ToolCall tc = ToolCall.of("tool", Map.of("key", "value"));
            assertThatThrownBy(() -> tc.getArguments().put("new", "val"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("getArgument type conversion")
    class GetArgument {

        @Test
        @DisplayName("returns string value directly")
        void stringDirect() {
            ToolCall tc = ToolCall.of("t", Map.of("name", "Alice"));
            assertThat(tc.getArgument("name", String.class)).isEqualTo("Alice");
        }

        @Test
        @DisplayName("converts non-string to string via valueOf")
        void nonStringToString() {
            ToolCall tc = ToolCall.of("t", Map.of("count", 42));
            assertThat(tc.getArgument("count", String.class)).isEqualTo("42");
        }

        @Test
        @DisplayName("converts Number to Integer")
        void numberToInteger() {
            ToolCall tc = ToolCall.of("t", Map.of("count", 42L));
            assertThat(tc.getArgument("count", Integer.class)).isEqualTo(42);
        }

        @Test
        @DisplayName("converts Number to Long")
        void numberToLong() {
            ToolCall tc = ToolCall.of("t", Map.of("count", 42));
            assertThat(tc.getArgument("count", Long.class)).isEqualTo(42L);
        }

        @Test
        @DisplayName("converts Number to Double")
        void numberToDouble() {
            ToolCall tc = ToolCall.of("t", Map.of("rate", 3.14f));
            assertThat(tc.getArgument("rate", Double.class)).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("returns Boolean directly")
        void booleanDirect() {
            ToolCall tc = ToolCall.of("t", Map.of("flag", true));
            assertThat(tc.getArgument("flag", Boolean.class)).isTrue();
        }

        @Test
        @DisplayName("returns null for missing key")
        void missingKeyReturnsNull() {
            ToolCall tc = ToolCall.of("t", Map.of());
            assertThat(tc.getArgument("missing", String.class)).isNull();
        }

        @Test
        @DisplayName("throws on incompatible type conversion")
        void incompatibleTypeThrows() {
            ToolCall tc = ToolCall.of("t", Map.of("data", java.util.List.of(1, 2)));
            assertThatThrownBy(() -> tc.getArgument("data", Integer.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("convenience accessors")
    class ConvenienceAccessors {

        @Test
        @DisplayName("getString returns string value")
        void getStringValue() {
            ToolCall tc = ToolCall.of("t", Map.of("name", "Bob"));
            assertThat(tc.getString("name", "default")).isEqualTo("Bob");
        }

        @Test
        @DisplayName("getString returns default for missing key")
        void getStringDefault() {
            ToolCall tc = ToolCall.of("t", Map.of());
            assertThat(tc.getString("name", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("getInt returns integer value")
        void getIntValue() {
            ToolCall tc = ToolCall.of("t", Map.of("count", 5));
            assertThat(tc.getInt("count", 0)).isEqualTo(5);
        }

        @Test
        @DisplayName("getInt returns default for missing key")
        void getIntDefault() {
            ToolCall tc = ToolCall.of("t", Map.of());
            assertThat(tc.getInt("count", 10)).isEqualTo(10);
        }

        @Test
        @DisplayName("getBoolean returns boolean value")
        void getBooleanValue() {
            ToolCall tc = ToolCall.of("t", Map.of("verbose", true));
            assertThat(tc.getBoolean("verbose", false)).isTrue();
        }

        @Test
        @DisplayName("getBoolean returns default for missing key")
        void getBooleanDefault() {
            ToolCall tc = ToolCall.of("t", Map.of());
            assertThat(tc.getBoolean("verbose", true)).isTrue();
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal ToolCalls are equal")
        void equalToolCalls() {
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of("q", "test"));
            ToolCall tc2 = ToolCall.of("c1", "search", Map.of("q", "test"));
            assertThat(tc1).isEqualTo(tc2);
            assertThat(tc1.hashCode()).isEqualTo(tc2.hashCode());
        }

        @Test
        @DisplayName("different ids not equal")
        void differentIds() {
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of());
            ToolCall tc2 = ToolCall.of("c2", "search", Map.of());
            assertThat(tc1).isNotEqualTo(tc2);
        }

        @Test
        @DisplayName("different names not equal")
        void differentNames() {
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of());
            ToolCall tc2 = ToolCall.of("c1", "lookup", Map.of());
            assertThat(tc1).isNotEqualTo(tc2);
        }

        @Test
        @DisplayName("different arguments not equal")
        void differentArgs() {
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of("q", "a"));
            ToolCall tc2 = ToolCall.of("c1", "search", Map.of("q", "b"));
            assertThat(tc1).isNotEqualTo(tc2);
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualNull() {
            assertThat(ToolCall.of("search", Map.of())).isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains name and id")
        void containsNameAndId() {
            ToolCall tc = ToolCall.of("call-1", "search", Map.of("q", "hello"));
            String str = tc.toString();
            assertThat(str).contains("call-1").contains("search");
        }
    }
}
