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
@DisplayName("TemplateContext")
class TemplateContextTest {

    @Nested
    @DisplayName("factory methods")
    class FactoryTests {

        @Test
        @DisplayName("of(Map) creates context with provided entries")
        void ofMapCreatesContext() {
            TemplateContext ctx = TemplateContext.of(Map.of("key", "value"));
            assertThat(ctx.get("key")).isEqualTo("value");
        }

        @Test
        @DisplayName("empty() creates context that throws on any get")
        void emptyContextThrowsOnGet() {
            TemplateContext ctx = TemplateContext.empty();
            assertThatThrownBy(() -> ctx.get("anything"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("anything");
        }

        @Test
        @DisplayName("of(Map) defensively copies the input map")
        void ofMapDefensivelyCopies() {
            Map<String, String> mutable = new HashMap<>();
            mutable.put("k", "v1");
            TemplateContext ctx = TemplateContext.of(mutable);

            mutable.put("k", "v2"); // mutate original

            assertThat(ctx.get("k")).isEqualTo("v1"); // context is unaffected
        }

        @Test
        @DisplayName("of(null) throws NullPointerException")
        void ofNullThrowsNPE() {
            assertThatNullPointerException().isThrownBy(() -> TemplateContext.of(null));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder() accumulates multiple puts")
        void builderAccumulatesEntries() {
            TemplateContext ctx = TemplateContext.builder()
                    .put("a", "1")
                    .put("b", "2")
                    .put("c", "3")
                    .build();

            assertThat(ctx.get("a")).isEqualTo("1");
            assertThat(ctx.get("b")).isEqualTo("2");
            assertThat(ctx.get("c")).isEqualTo("3");
        }

        @Test
        @DisplayName("putAll() merges a map into the builder")
        void putAllMergesMap() {
            TemplateContext ctx = TemplateContext.builder()
                    .put("existing", "yes")
                    .putAll(Map.of("added1", "v1", "added2", "v2"))
                    .build();

            assertThat(ctx.get("existing")).isEqualTo("yes");
            assertThat(ctx.get("added1")).isEqualTo("v1");
        }

        @Test
        @DisplayName("later put() overrides earlier put() for same key")
        void laterPutOverridesEarlier() {
            TemplateContext ctx = TemplateContext.builder()
                    .put("key", "first")
                    .put("key", "second")
                    .build();

            assertThat(ctx.get("key")).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("get()")
    class GetTests {

        @Test
        @DisplayName("throws ISE with helpful message listing available keys")
        void missingKeyThrowsIseWithHelpfulMessage() {
            TemplateContext ctx = TemplateContext.builder().put("model", "gpt-4o").build();

            assertThatThrownBy(() -> ctx.get("missingKey"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missingKey")
                    .hasMessageContaining("model"); // available keys listed
        }

        @Test
        @DisplayName("get(null) throws NullPointerException")
        void getNullKeyThrowsNPE() {
            TemplateContext ctx = TemplateContext.empty();
            assertThatNullPointerException().isThrownBy(() -> ctx.get(null));
        }

        @Test
        @DisplayName("has() returns true for known keys")
        void hasReturnsTrueForKnownKey() {
            TemplateContext ctx = TemplateContext.builder().put("x", "1").build();
            assertThat(ctx.has("x")).isTrue();
        }

        @Test
        @DisplayName("has() returns false for unknown keys")
        void hasReturnsFalseForUnknownKey() {
            TemplateContext ctx = TemplateContext.empty();
            assertThat(ctx.has("unknown")).isFalse();
        }
    }
}
