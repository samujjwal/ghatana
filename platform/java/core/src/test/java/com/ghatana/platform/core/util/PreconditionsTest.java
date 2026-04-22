package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for Preconditions argument validation helpers
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("Preconditions — argument validation guards [GH-90000]")
class PreconditionsTest {

    // ── requireNonNull ───────────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonNull returns value when non-null [GH-90000]")
    void requireNonNullReturnsValue() { // GH-90000
        String result = Preconditions.requireNonNull("hello", "param"); // GH-90000
        assertThat(result).isEqualTo("hello [GH-90000]");
    }

    @Test
    @DisplayName("requireNonNull throws NullPointerException for null [GH-90000]")
    void requireNonNullThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonNull(null, "myParam")) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("myParam [GH-90000]");
    }

    @Test
    @DisplayName("requireNonNull includes parameter name in message [GH-90000]")
    void requireNonNullIncludesParamName() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonNull(null, "userId")) // GH-90000
                .hasMessageContaining("userId [GH-90000]");
    }

    // ── requireNonEmpty (String) ───────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("requireNonEmpty returns value when non-empty [GH-90000]")
    void requireNonEmptyStringReturnsValue() { // GH-90000
        String result = Preconditions.requireNonEmpty("test", "name"); // GH-90000
        assertThat(result).isEqualTo("test [GH-90000]");
    }

    @Test
    @DisplayName("requireNonEmpty throws for null string [GH-90000]")
    void requireNonEmptyThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonEmpty((String) null, "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field [GH-90000]");
    }

    @Test
    @DisplayName("requireNonEmpty throws for empty string [GH-90000]")
    void requireNonEmptyThrowsForEmpty() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonEmpty("", "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field [GH-90000]");
    }

    // ── requireNonBlank ──────────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonBlank returns trimmed value when non-blank [GH-90000]")
    void requireNonBlankReturnsValue() { // GH-90000
        String result = Preconditions.requireNonBlank("  value  ", "param"); // GH-90000
        assertThat(result).isEqualTo("  value   [GH-90000]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"}) // GH-90000
    @DisplayName("requireNonBlank throws for blank strings [GH-90000]")
    void requireNonBlankThrowsForBlankStrings(String blank) { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonBlank(blank, "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field [GH-90000]");
    }

    @Test
    @DisplayName("requireNonBlank throws for null [GH-90000]")
    void requireNonBlankThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonBlank(null, "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field [GH-90000]");
    }

    // ── requireNonEmpty (Collection) ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("requireNonEmpty collection returns collection when non-empty [GH-90000]")
    void requireNonEmptyCollectionReturnsValue() { // GH-90000
        List<String> list = List.of("a", "b"); // GH-90000
        Collection<String> result = Preconditions.requireNonEmpty(list, "items"); // GH-90000
        assertThat(result).containsExactly("a", "b"); // GH-90000
    }

    @Test
    @DisplayName("requireNonEmpty collection throws for null collection [GH-90000]")
    void requireNonEmptyCollectionThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonEmpty((Collection<?>) null, "items")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("items [GH-90000]");
    }

    @Test
    @DisplayName("requireNonEmpty collection throws for empty collection [GH-90000]")
    void requireNonEmptyCollectionThrowsForEmpty() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonEmpty(Collections.emptyList(), "items")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("items [GH-90000]");
    }

    // ── require (boolean condition) ────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("require does not throw when condition is true [GH-90000]")
    void requireDoesNotThrowWhenTrue() { // GH-90000
        assertThatCode(() -> Preconditions.require(true, "should not fail")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("require throws IllegalArgumentException when condition is false [GH-90000]")
    void requireThrowsWhenFalse() { // GH-90000
        assertThatThrownBy(() -> Preconditions.require(false, "condition failed")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessage("condition failed [GH-90000]");
    }

    // ── requirePositive ──────────────────────────────────────────────────────

    @Test
    @DisplayName("requirePositive returns value when positive [GH-90000]")
    void requirePositiveReturnsValue() { // GH-90000
        assertThat(Preconditions.requirePositive(5, "count")).isEqualTo(5); // GH-90000
    }

    @Test
    @DisplayName("requirePositive throws for zero [GH-90000]")
    void requirePositiveThrowsForZero() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requirePositive(0, "count")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("count [GH-90000]");
    }

    @Test
    @DisplayName("requirePositive throws for negative value [GH-90000]")
    void requirePositiveThrowsForNegative() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requirePositive(-1, "timeout")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("timeout [GH-90000]");
    }

    @Test
    @DisplayName("requirePositive(long) returns value when positive [GH-90000]")
    void requirePositiveLongReturnsValue() { // GH-90000
        assertThat(Preconditions.requirePositive(100L, "size")).isEqualTo(100L); // GH-90000
    }

    @Test
    @DisplayName("requirePositive(long) throws for zero [GH-90000]")
    void requirePositiveLongThrowsForZero() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requirePositive(0L, "size")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("size [GH-90000]");
    }

    // ── requireNonNegative ───────────────────────────────────────────────────

    @Test
    @DisplayName("requireNonNegative accepts zero [GH-90000]")
    void requireNonNegativeAcceptsZero() { // GH-90000
        assertThat(Preconditions.requireNonNegative(0, "index")).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("requireNonNegative accepts positive value [GH-90000]")
    void requireNonNegativeAcceptsPositive() { // GH-90000
        assertThat(Preconditions.requireNonNegative(42, "index")).isEqualTo(42); // GH-90000
    }

    @Test
    @DisplayName("requireNonNegative throws for negative value [GH-90000]")
    void requireNonNegativeThrowsForNegative() { // GH-90000
        assertThatThrownBy(() -> Preconditions.requireNonNegative(-1, "index")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("index [GH-90000]");
    }
}
