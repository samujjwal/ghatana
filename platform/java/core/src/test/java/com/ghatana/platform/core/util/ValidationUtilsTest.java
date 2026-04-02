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
    void validateNotBlankAcceptsNonBlank() {
        assertThatCode(() -> ValidationUtils.validateNotBlank("hello", "name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNotBlank throws for null")
    void validateNotBlankThrowsForNull() {
        assertThatThrownBy(() -> ValidationUtils.validateNotBlank(null, "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("validateNotBlank throws for blank strings")
    void validateNotBlankThrowsForBlank(String blank) {
        assertThatThrownBy(() -> ValidationUtils.validateNotBlank(blank, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    // ── validateNotNull ──────────────────────────────────────────────────────

    @Test
    @DisplayName("validateNotNull accepts non-null object")
    void validateNotNullAcceptsNonNull() {
        assertThatCode(() -> ValidationUtils.validateNotNull(new Object(), "obj"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNotNull throws for null")
    void validateNotNullThrowsForNull() {
        assertThatThrownBy(() -> ValidationUtils.validateNotNull(null, "myField"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("myField");
    }

    // ── validateMaxLength ────────────────────────────────────────────────────

    @Test
    @DisplayName("validateMaxLength accepts string at max boundary")
    void validateMaxLengthAcceptsAtBoundary() {
        assertThatCode(() -> ValidationUtils.validateMaxLength("abcde", 5, "name"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateMaxLength throws when string exceeds max")
    void validateMaxLengthThrowsWhenExceeded() {
        assertThatThrownBy(() -> ValidationUtils.validateMaxLength("toolong", 3, "field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field");
    }

    @Test
    @DisplayName("validateMaxLength accepts null (null is skipped)")
    void validateMaxLengthAcceptsNull() {
        assertThatCode(() -> ValidationUtils.validateMaxLength(null, 10, "field"))
                .doesNotThrowAnyException();
    }

    // ── validateMinLength ────────────────────────────────────────────────────

    @Test
    @DisplayName("validateMinLength accepts string at min boundary")
    void validateMinLengthAcceptsAtBoundary() {
        assertThatCode(() -> ValidationUtils.validateMinLength("abc", 3, "code"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateMinLength throws when string is shorter than min")
    void validateMinLengthThrowsWhenTooShort() {
        assertThatThrownBy(() -> ValidationUtils.validateMinLength("ab", 5, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("validateMinLength accepts null (null is skipped)")
    void validateMinLengthAcceptsNull() {
        assertThatCode(() -> ValidationUtils.validateMinLength(null, 5, "code"))
                .doesNotThrowAnyException();
    }

    // ── validateRange (int) ──────────────────────────────────────────────────

    @Test
    @DisplayName("validateRange accepts value within range")
    void validateRangeIntAcceptsWithin() {
        assertThatCode(() -> ValidationUtils.validateRange(5, 1, 10, "count"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateRange accepts value at inclusive bounds")
    void validateRangeIntAcceptsAtBounds() {
        assertThatCode(() -> ValidationUtils.validateRange(1, 1, 10, "count"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ValidationUtils.validateRange(10, 1, 10, "count"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateRange throws for value below min")
    void validateRangeIntThrowsBelowMin() {
        assertThatThrownBy(() -> ValidationUtils.validateRange(0, 1, 10, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validateRange throws for value above max")
    void validateRangeIntThrowsAboveMax() {
        assertThatThrownBy(() -> ValidationUtils.validateRange(11, 1, 10, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    // ── validatePattern ──────────────────────────────────────────────────────

    @Test
    @DisplayName("validatePattern accepts value matching pattern")
    void validatePatternAcceptsMatch() {
        Pattern emailPattern = Pattern.compile("[a-z]+@[a-z]+\\.com");
        assertThatCode(() -> ValidationUtils.validatePattern("user@domain.com", emailPattern, "email"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePattern throws for value not matching pattern")
    void validatePatternThrowsForNonMatch() {
        Pattern alphanumeric = Pattern.compile("[a-zA-Z0-9]+");
        assertThatThrownBy(() -> ValidationUtils.validatePattern("invalid-value!", alphanumeric, "code"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @Test
    @DisplayName("validatePattern accepts null (null is skipped)")
    void validatePatternAcceptsNull() {
        Pattern alphanumeric = Pattern.compile("[a-zA-Z0-9]+");
        assertThatCode(() -> ValidationUtils.validatePattern(null, alphanumeric, "code"))
                .doesNotThrowAnyException();
    }

    // ── validateNotEmpty (Collection) ────────────────────────────────────────

    @Test
    @DisplayName("validateNotEmpty accepts non-empty collection")
    void validateNotEmptyAcceptsNonEmpty() {
        assertThatCode(() -> ValidationUtils.validateNotEmpty(List.of("item"), "items"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateNotEmpty throws for null collection")
    void validateNotEmptyThrowsForNull() {
        assertThatThrownBy(() -> ValidationUtils.validateNotEmpty(null, "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    @Test
    @DisplayName("validateNotEmpty throws for empty collection")
    void validateNotEmptyThrowsForEmpty() {
        assertThatThrownBy(() -> ValidationUtils.validateNotEmpty(Collections.emptyList(), "items"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items");
    }

    // ── validatePositive ─────────────────────────────────────────────────────

    @Test
    @DisplayName("validatePositive(int) accepts positive value")
    void validatePositiveIntAccepts() {
        assertThatCode(() -> ValidationUtils.validatePositive(1, "count"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePositive(int) throws for zero")
    void validatePositiveIntThrowsForZero() {
        assertThatThrownBy(() -> ValidationUtils.validatePositive(0, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validatePositive(int) throws for negative")
    void validatePositiveIntThrowsForNegative() {
        assertThatThrownBy(() -> ValidationUtils.validatePositive(-5, "count"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    @DisplayName("validatePositive(long) accepts positive value")
    void validatePositiveLongAccepts() {
        assertThatCode(() -> ValidationUtils.validatePositive(100L, "size"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePositive(long) throws for zero")
    void validatePositiveLongThrowsForZero() {
        assertThatThrownBy(() -> ValidationUtils.validatePositive(0L, "size"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
