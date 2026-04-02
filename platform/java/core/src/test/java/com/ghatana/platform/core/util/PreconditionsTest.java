package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for Preconditions argument validation helpers
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Preconditions — argument validation guards")
class PreconditionsTest {

    // ── requireNonNull ───────────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonNull returns value when non-null")
    void requireNonNullReturnsValue() {
        String result = Preconditions.requireNonNull("hello", "param");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    @DisplayName("requireNonNull throws NullPointerException for null")
    void requireNonNullThrowsForNull() {
        assertThatThrownBy(() -> Preconditions.requireNonNull(null, "myParam"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("myParam");
    }

    @Test
    @DisplayName("requireNonNull includes parameter name in message")
    void requireNonNullIncludesParamName() {
        assertThatThrownBy(() -> Preconditions.requireNonNull(null, "userId"))
                .hasMessageContaining("userId");
    }

    // ── requireNonEmpty (String) ─────────────────────────────────────────────

    @Test
    @DisplayName("requireNonEmpty returns value when non-empty")
    void requireNonEmptyStringReturnsValue() {
        String result = Preconditions.requireNonEmpty("test", "name");
        assertThat(result).isEqualTo("test");
    }

    @Test
    @DisplayName("requireNonEmpty throws for null string")
    void requireNonEmptyThrowsForNull() {
        assertThatThrownBy(() -> Preconditions.requireNonEmpty((String) null, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    @DisplayName("requireNonEmpty throws for empty string")
    void requireNonEmptyThrowsForEmpty() {
        assertThatThrownBy(() -> Preconditions.requireNonEmpty("", "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    // ── requireNonBlank ──────────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonBlank returns trimmed value when non-blank")
    void requireNonBlankReturnsValue() {
        String result = Preconditions.requireNonBlank("  value  ", "param");
        assertThat(result).isEqualTo("  value  ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("requireNonBlank throws for blank strings")
    void requireNonBlankThrowsForBlankStrings(String blank) {
        assertThatThrownBy(() -> Preconditions.requireNonBlank(blank, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    @DisplayName("requireNonBlank throws for null")
    void requireNonBlankThrowsForNull() {
        assertThatThrownBy(() -> Preconditions.requireNonBlank(null, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    // ── requireNonEmpty (Collection) ────────────────────────────────────────

    @Test
    @DisplayName("requireNonEmpty collection returns collection when non-empty")
    void requireNonEmptyCollectionReturnsValue() {
        List<String> list = List.of("a", "b");
        Collection<String> result = Preconditions.requireNonEmpty(list, "items");
        assertThat(result).containsExactly("a", "b");
    }

    @Test
    @DisplayName("requireNonEmpty collection throws for null collection")
    void requireNonEmptyCollectionThrowsForNull() {
        assertThatThrownBy(() -> Preconditions.requireNonEmpty((Collection<?>) null, "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    @DisplayName("requireNonEmpty collection throws for empty collection")
    void requireNonEmptyCollectionThrowsForEmpty() {
        assertThatThrownBy(() -> Preconditions.requireNonEmpty(Collections.emptyList(), "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    // ── require (boolean condition) ──────────────────────────────────────────

    @Test
    @DisplayName("require does not throw when condition is true")
    void requireDoesNotThrowWhenTrue() {
        assertThatCode(() -> Preconditions.require(true, "should not fail"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("require throws IllegalArgumentException when condition is false")
    void requireThrowsWhenFalse() {
        assertThatThrownBy(() -> Preconditions.require(false, "condition failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("condition failed");
    }

    // ── requirePositive ──────────────────────────────────────────────────────

    @Test
    @DisplayName("requirePositive returns value when positive")
    void requirePositiveReturnsValue() {
        assertThat(Preconditions.requirePositive(5, "count")).isEqualTo(5);
    }

    @Test
    @DisplayName("requirePositive throws for zero")
    void requirePositiveThrowsForZero() {
        assertThatThrownBy(() -> Preconditions.requirePositive(0, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("requirePositive throws for negative value")
    void requirePositiveThrowsForNegative() {
        assertThatThrownBy(() -> Preconditions.requirePositive(-1, "timeout"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("requirePositive(long) returns value when positive")
    void requirePositiveLongReturnsValue() {
        assertThat(Preconditions.requirePositive(100L, "size")).isEqualTo(100L);
    }

    @Test
    @DisplayName("requirePositive(long) throws for zero")
    void requirePositiveLongThrowsForZero() {
        assertThatThrownBy(() -> Preconditions.requirePositive(0L, "size"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    // ── requireNonNegative ───────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonNegative accepts zero")
    void requireNonNegativeAcceptsZero() {
        assertThat(Preconditions.requireNonNegative(0, "index")).isEqualTo(0);
    }

    @Test
    @DisplayName("requireNonNegative accepts positive value")
    void requireNonNegativeAcceptsPositive() {
        assertThat(Preconditions.requireNonNegative(42, "index")).isEqualTo(42);
    }

    @Test
    @DisplayName("requireNonNegative throws for negative value")
    void requireNonNegativeThrowsForNegative() {
        assertThatThrownBy(() -> Preconditions.requireNonNegative(-1, "index"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index");
    }
}
