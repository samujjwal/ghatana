package com.ghatana.platform.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for ValidationUtils static boundary validators
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("ValidationUtils — boundary validation guards")
class ValidationUtilsTest {

    // ── validateNotBlank ─────────────────────────────────────────────────────

    @Test
    @DisplayName("validateNotBlank accepts non-blank string")
    void validateNotBlankAcceptsNonBlank() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateNotBlank("hello", "name")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateNotBlank throws for null")
    void validateNotBlankThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateNotBlank(null, "name")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"}) // GH-90000
    @DisplayName("validateNotBlank throws for blank strings")
    void validateNotBlankThrowsForBlank(String blank) { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateNotBlank(blank, "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field");
    }

    // ── validateNotNull ──────────────────────────────────────────────────────

    @Test
    @DisplayName("validateNotNull accepts non-null object")
    void validateNotNullAcceptsNonNull() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateNotNull(new Object(), "obj")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateNotNull throws for null")
    void validateNotNullThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateNotNull(null, "myField")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("myField");
    }

    // ── validateMaxLength ────────────────────────────────────────────────────

    @Test
    @DisplayName("validateMaxLength accepts string at max boundary")
    void validateMaxLengthAcceptsAtBoundary() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateMaxLength("abcde", 5, "name")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateMaxLength throws when string exceeds max")
    void validateMaxLengthThrowsWhenExceeded() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateMaxLength("toolong", 3, "field")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("field");
    }

    @Test
    @DisplayName("validateMaxLength accepts null (null is skipped)")
    void validateMaxLengthAcceptsNull() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateMaxLength(null, 10, "field")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── validateMinLength ────────────────────────────────────────────────────

    @Test
    @DisplayName("validateMinLength accepts string at min boundary")
    void validateMinLengthAcceptsAtBoundary() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateMinLength("abc", 3, "code")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateMinLength throws when string is shorter than min")
    void validateMinLengthThrowsWhenTooShort() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateMinLength("ab", 5, "code")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("validateMinLength accepts null (null is skipped)")
    void validateMinLengthAcceptsNull() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateMinLength(null, 5, "code")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── validateRange (int) ────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("validateRange accepts value within range")
    void validateRangeIntAcceptsWithin() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateRange(5, 1, 10, "count")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateRange accepts value at inclusive bounds")
    void validateRangeIntAcceptsAtBounds() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateRange(1, 1, 10, "count")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        assertThatCode(() -> ValidationUtils.validateRange(10, 1, 10, "count")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateRange throws for value below min")
    void validateRangeIntThrowsBelowMin() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateRange(0, 1, 10, "count")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validateRange throws for value above max")
    void validateRangeIntThrowsAboveMax() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateRange(11, 1, 10, "count")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("count");
    }

    // ── validatePattern ──────────────────────────────────────────────────────

    @Test
    @DisplayName("validatePattern accepts value matching pattern")
    void validatePatternAcceptsMatch() { // GH-90000
        Pattern emailPattern = Pattern.compile("[a-z]+@[a-z]+\\.com");
        assertThatCode(() -> ValidationUtils.validatePattern("user@domain.com", emailPattern, "email")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validatePattern throws for value not matching pattern")
    void validatePatternThrowsForNonMatch() { // GH-90000
        Pattern alphanumeric = Pattern.compile("[a-zA-Z0-9]+");
        assertThatThrownBy(() -> ValidationUtils.validatePattern("invalid-value!", alphanumeric, "code")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("validatePattern accepts null (null is skipped)")
    void validatePatternAcceptsNull() { // GH-90000
        Pattern alphanumeric = Pattern.compile("[a-zA-Z0-9]+");
        assertThatCode(() -> ValidationUtils.validatePattern(null, alphanumeric, "code")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── validateNotEmpty (Collection) ──────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("validateNotEmpty accepts non-empty collection")
    void validateNotEmptyAcceptsNonEmpty() { // GH-90000
        assertThatCode(() -> ValidationUtils.validateNotEmpty(List.of("item"), "items"))
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validateNotEmpty throws for null collection")
    void validateNotEmptyThrowsForNull() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateNotEmpty(null, "items")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("items");
    }

    @Test
    @DisplayName("validateNotEmpty throws for empty collection")
    void validateNotEmptyThrowsForEmpty() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validateNotEmpty(Collections.emptyList(), "items")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("items");
    }

    // ── validatePositive ─────────────────────────────────────────────────────

    @Test
    @DisplayName("validatePositive(int) accepts positive value")
    void validatePositiveIntAccepts() { // GH-90000
        assertThatCode(() -> ValidationUtils.validatePositive(1, "count")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validatePositive(int) throws for zero")
    void validatePositiveIntThrowsForZero() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validatePositive(0, "count")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validatePositive(int) throws for negative")
    void validatePositiveIntThrowsForNegative() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validatePositive(-5, "count")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validatePositive(long) accepts positive value")
    void validatePositiveLongAccepts() { // GH-90000
        assertThatCode(() -> ValidationUtils.validatePositive(100L, "size")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("validatePositive(long) throws for zero")
    void validatePositiveLongThrowsForZero() { // GH-90000
        assertThatThrownBy(() -> ValidationUtils.validatePositive(0L, "size")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("size");
    }
}
