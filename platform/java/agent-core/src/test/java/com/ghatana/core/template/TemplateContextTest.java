package com.ghatana.core.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TemplateContext} and its {@link TemplateContext.Builder}.
 *
 * @doc.type class
 * @doc.purpose Tests for TemplateContext — construction, retrieval, fail-fast semantics
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("TemplateContext [GH-90000]")
class TemplateContextTest {

    @Nested
    @DisplayName("factory methods [GH-90000]")
    class FactoryTests {

        @Test
        @DisplayName("of(Map) creates context with provided entries [GH-90000]")
        void ofMapCreatesContext() { // GH-90000
            TemplateContext ctx = TemplateContext.of(Map.of("key", "value")); // GH-90000
            assertThat(ctx.get("key [GH-90000]")).isEqualTo("value [GH-90000]");
        }

        @Test
        @DisplayName("empty() creates context that throws on any get [GH-90000]")
        void emptyContextThrowsOnGet() { // GH-90000
            TemplateContext ctx = TemplateContext.empty(); // GH-90000
            assertThatThrownBy(() -> ctx.get("anything [GH-90000]"))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("anything [GH-90000]");
        }

        @Test
        @DisplayName("of(Map) defensively copies the input map [GH-90000]")
        void ofMapDefensivelyCopies() { // GH-90000
            Map<String, String> mutable = new HashMap<>(); // GH-90000
            mutable.put("k", "v1"); // GH-90000
            TemplateContext ctx = TemplateContext.of(mutable); // GH-90000

            mutable.put("k", "v2"); // mutate original // GH-90000

            assertThat(ctx.get("k [GH-90000]")).isEqualTo("v1 [GH-90000]"); // context is unaffected
        }

        @Test
        @DisplayName("of(null) throws NullPointerException [GH-90000]")
        void ofNullThrowsNPE() { // GH-90000
            assertThatNullPointerException().isThrownBy(() -> TemplateContext.of(null)); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder() accumulates multiple puts [GH-90000]")
        void builderAccumulatesEntries() { // GH-90000
            TemplateContext ctx = TemplateContext.builder() // GH-90000
                    .put("a", "1") // GH-90000
                    .put("b", "2") // GH-90000
                    .put("c", "3") // GH-90000
                    .build(); // GH-90000

            assertThat(ctx.get("a [GH-90000]")).isEqualTo("1 [GH-90000]");
            assertThat(ctx.get("b [GH-90000]")).isEqualTo("2 [GH-90000]");
            assertThat(ctx.get("c [GH-90000]")).isEqualTo("3 [GH-90000]");
        }

        @Test
        @DisplayName("putAll() merges a map into the builder [GH-90000]")
        void putAllMergesMap() { // GH-90000
            TemplateContext ctx = TemplateContext.builder() // GH-90000
                    .put("existing", "yes") // GH-90000
                    .putAll(Map.of("added1", "v1", "added2", "v2")) // GH-90000
                    .build(); // GH-90000

            assertThat(ctx.get("existing [GH-90000]")).isEqualTo("yes [GH-90000]");
            assertThat(ctx.get("added1 [GH-90000]")).isEqualTo("v1 [GH-90000]");
        }

        @Test
        @DisplayName("later put() overrides earlier put() for same key [GH-90000]")
        void laterPutOverridesEarlier() { // GH-90000
            TemplateContext ctx = TemplateContext.builder() // GH-90000
                    .put("key", "first") // GH-90000
                    .put("key", "second") // GH-90000
                    .build(); // GH-90000

            assertThat(ctx.get("key [GH-90000]")).isEqualTo("second [GH-90000]");
        }
    }

    @Nested
    @DisplayName("get() [GH-90000]")
    class GetTests {

        @Test
        @DisplayName("throws ISE with helpful message listing available keys [GH-90000]")
        void missingKeyThrowsIseWithHelpfulMessage() { // GH-90000
            TemplateContext ctx = TemplateContext.builder().put("model", "gpt-4o").build(); // GH-90000

            assertThatThrownBy(() -> ctx.get("missingKey [GH-90000]"))
                    .isInstanceOf(IllegalStateException.class) // GH-90000
                    .hasMessageContaining("missingKey [GH-90000]")
                    .hasMessageContaining("model [GH-90000]"); // available keys listed
        }

        @Test
        @DisplayName("get(null) throws NullPointerException [GH-90000]")
        void getNullKeyThrowsNPE() { // GH-90000
            TemplateContext ctx = TemplateContext.empty(); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> ctx.get(null)); // GH-90000
        }

        @Test
        @DisplayName("has() returns true for known keys [GH-90000]")
        void hasReturnsTrueForKnownKey() { // GH-90000
            TemplateContext ctx = TemplateContext.builder().put("x", "1").build(); // GH-90000
            assertThat(ctx.has("x [GH-90000]")).isTrue();
        }

        @Test
        @DisplayName("has() returns false for unknown keys [GH-90000]")
        void hasReturnsFalseForUnknownKey() { // GH-90000
            TemplateContext ctx = TemplateContext.empty(); // GH-90000
            assertThat(ctx.has("unknown [GH-90000]")).isFalse();
        }
    }
}
