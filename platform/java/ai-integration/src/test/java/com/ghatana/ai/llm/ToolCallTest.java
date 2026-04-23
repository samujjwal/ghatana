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
        void ofGeneratesId() { // GH-90000
            ToolCall tc = ToolCall.of("search", Map.of("query", "test")); // GH-90000
            assertThat(tc.getId()).isNotNull().isNotEmpty(); // GH-90000
            assertThat(tc.getName()).isEqualTo("search");
            assertThat(tc.getArguments()).containsEntry("query", "test"); // GH-90000
        }

        @Test
        @DisplayName("of(id, name, args) uses provided id")
        void ofWithExplicitId() { // GH-90000
            ToolCall tc = ToolCall.of("call-123", "search", Map.of("q", "test")); // GH-90000
            assertThat(tc.getId()).isEqualTo("call-123");
            assertThat(tc.getName()).isEqualTo("search");
        }

        @Test
        @DisplayName("two calls to of(name, args) generate different ids")
        void uniqueIds() { // GH-90000
            ToolCall tc1 = ToolCall.of("search", Map.of()); // GH-90000
            ToolCall tc2 = ToolCall.of("search", Map.of()); // GH-90000
            assertThat(tc1.getId()).isNotEqualTo(tc2.getId()); // GH-90000
        }

        @Test
        @DisplayName("null arguments treated as empty map")
        void nullArguments() { // GH-90000
            ToolCall tc = ToolCall.of("tool", null); // GH-90000
            assertThat(tc.getArguments()).isNotNull().isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("argument immutability")
    class ArgumentImmutability {

        @Test
        @DisplayName("arguments map is unmodifiable")
        void argumentsUnmodifiable() { // GH-90000
            ToolCall tc = ToolCall.of("tool", Map.of("key", "value")); // GH-90000
            assertThatThrownBy(() -> tc.getArguments().put("new", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("getArgument type conversion")
    class GetArgument {

        @Test
        @DisplayName("returns string value directly")
        void stringDirect() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("name", "Alice")); // GH-90000
            assertThat(tc.getArgument("name", String.class)).isEqualTo("Alice");
        }

        @Test
        @DisplayName("converts non-string to string via valueOf")
        void nonStringToString() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("count", 42)); // GH-90000
            assertThat(tc.getArgument("count", String.class)).isEqualTo("42");
        }

        @Test
        @DisplayName("converts Number to Integer")
        void numberToInteger() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("count", 42L)); // GH-90000
            assertThat(tc.getArgument("count", Integer.class)).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("converts Number to Long")
        void numberToLong() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("count", 42)); // GH-90000
            assertThat(tc.getArgument("count", Long.class)).isEqualTo(42L); // GH-90000
        }

        @Test
        @DisplayName("converts Number to Double")
        void numberToDouble() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("rate", 3.14f)); // GH-90000
            assertThat(tc.getArgument("rate", Double.class)).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.01)); // GH-90000
        }

        @Test
        @DisplayName("returns Boolean directly")
        void booleanDirect() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("flag", true)); // GH-90000
            assertThat(tc.getArgument("flag", Boolean.class)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("returns null for missing key")
        void missingKeyReturnsNull() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of()); // GH-90000
            assertThat(tc.getArgument("missing", String.class)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("throws on incompatible type conversion")
        void incompatibleTypeThrows() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("data", java.util.List.of(1, 2))); // GH-90000
            assertThatThrownBy(() -> tc.getArgument("data", Integer.class)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("convenience accessors")
    class ConvenienceAccessors {

        @Test
        @DisplayName("getString returns string value")
        void getStringValue() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("name", "Bob")); // GH-90000
            assertThat(tc.getString("name", "default")).isEqualTo("Bob");
        }

        @Test
        @DisplayName("getString returns default for missing key")
        void getStringDefault() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of()); // GH-90000
            assertThat(tc.getString("name", "default")).isEqualTo("default");
        }

        @Test
        @DisplayName("getInt returns integer value")
        void getIntValue() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("count", 5)); // GH-90000
            assertThat(tc.getInt("count", 0)).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("getInt returns default for missing key")
        void getIntDefault() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of()); // GH-90000
            assertThat(tc.getInt("count", 10)).isEqualTo(10); // GH-90000
        }

        @Test
        @DisplayName("getBoolean returns boolean value")
        void getBooleanValue() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of("verbose", true)); // GH-90000
            assertThat(tc.getBoolean("verbose", false)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("getBoolean returns default for missing key")
        void getBooleanDefault() { // GH-90000
            ToolCall tc = ToolCall.of("t", Map.of()); // GH-90000
            assertThat(tc.getBoolean("verbose", true)).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("equality and hashCode")
    class Equality {

        @Test
        @DisplayName("equal ToolCalls are equal")
        void equalToolCalls() { // GH-90000
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of("q", "test")); // GH-90000
            ToolCall tc2 = ToolCall.of("c1", "search", Map.of("q", "test")); // GH-90000
            assertThat(tc1).isEqualTo(tc2); // GH-90000
            assertThat(tc1.hashCode()).isEqualTo(tc2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("different ids not equal")
        void differentIds() { // GH-90000
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of()); // GH-90000
            ToolCall tc2 = ToolCall.of("c2", "search", Map.of()); // GH-90000
            assertThat(tc1).isNotEqualTo(tc2); // GH-90000
        }

        @Test
        @DisplayName("different names not equal")
        void differentNames() { // GH-90000
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of()); // GH-90000
            ToolCall tc2 = ToolCall.of("c1", "lookup", Map.of()); // GH-90000
            assertThat(tc1).isNotEqualTo(tc2); // GH-90000
        }

        @Test
        @DisplayName("different arguments not equal")
        void differentArgs() { // GH-90000
            ToolCall tc1 = ToolCall.of("c1", "search", Map.of("q", "a")); // GH-90000
            ToolCall tc2 = ToolCall.of("c1", "search", Map.of("q", "b")); // GH-90000
            assertThat(tc1).isNotEqualTo(tc2); // GH-90000
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualNull() { // GH-90000
            assertThat(ToolCall.of("search", Map.of())).isNotEqualTo(null); // GH-90000
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("toString contains name and id")
        void containsNameAndId() { // GH-90000
            ToolCall tc = ToolCall.of("call-1", "search", Map.of("q", "hello")); // GH-90000
            String str = tc.toString(); // GH-90000
            assertThat(str).contains("call-1").contains("search");
        }
    }
}
