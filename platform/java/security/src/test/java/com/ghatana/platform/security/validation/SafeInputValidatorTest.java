package com.ghatana.platform.security.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SafeInputValidator")
class SafeInputValidatorTest {

    @Test
    @DisplayName("accepts safe identifiers and trims boundary whitespace")
    void acceptsSafeIdentifiers() {
        assertThat(SafeInputValidator.requireSafeIdentifier(" tenant:abc-123 ", "tenantId"))
            .isEqualTo("tenant:abc-123");
    }

    @Test
    @DisplayName("rejects blank, unsafe, and overlong identifiers")
    void rejectsUnsafeIdentifiers() {
        assertThatThrownBy(() -> SafeInputValidator.requireSafeIdentifier(" ", "tenantId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> SafeInputValidator.requireSafeIdentifier("../tenant<script>", "tenantId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("safe identifier");
        assertThatThrownBy(() -> SafeInputValidator.requireSafeIdentifier("a".repeat(129), "tenantId"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max length");
    }

    @Test
    @DisplayName("accepts safe product codes and rejects punctuation outside the common vocabulary")
    void validatesSafeCodes() {
        assertThat(SafeInputValidator.requireSafeCode("finance/settle_v1", "action"))
            .isEqualTo("finance/settle_v1");
        assertThatThrownBy(() -> SafeInputValidator.requireSafeCode("finance:settle", "action"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("safe code");
    }

    @Test
    @DisplayName("validates allowed values after trimming")
    void validatesAllowedValues() {
        assertThat(SafeInputValidator.requireAllowedValue(" USD ", "currency", Set.of("USD", "NPR")))
            .isEqualTo("USD");
        assertThatThrownBy(() -> SafeInputValidator.requireAllowedValue("EUR", "currency", Set.of("USD", "NPR")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    @DisplayName("validates finite numeric boundaries")
    void validatesNumericBoundaries() {
        assertThat(SafeInputValidator.requirePositiveFinite(1.25, "amount")).isEqualTo(1.25);
        assertThat(SafeInputValidator.requireNonNegativeFinite(0, "velocity")).isZero();
        assertThatThrownBy(() -> SafeInputValidator.requirePositiveFinite(Double.NaN, "amount"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive finite");
        assertThatThrownBy(() -> SafeInputValidator.requireNonNegativeFinite(-0.1, "velocity"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-negative finite");
    }
}
