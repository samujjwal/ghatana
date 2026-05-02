package com.ghatana.digitalmarketing.contracts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DmCorrelationId}.
 */
@DisplayName("DmCorrelationId")
class DmCorrelationIdTest {

    @Test
    @DisplayName("generate() produces unique non-blank values")
    void shouldGenerateUniqueValues() {
        DmCorrelationId a = DmCorrelationId.generate();
        DmCorrelationId b = DmCorrelationId.generate();
        assertThat(a.getValue()).isNotBlank();
        assertThat(b.getValue()).isNotBlank();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("of() accepts existing correlation ID string")
    void shouldWrapExistingId() {
        DmCorrelationId id = DmCorrelationId.of("550e8400-e29b-41d4-a716-446655440000");
        assertThat(id.getValue()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("of() rejects null")
    void shouldRejectNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmCorrelationId.of(null));
    }

    @Test
    @DisplayName("of() rejects blank")
    void shouldRejectBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmCorrelationId.of("   "));
    }

    @Test
    @DisplayName("equals and hashCode are value-based")
    void shouldHaveValueBasedEquality() {
        DmCorrelationId a = DmCorrelationId.of("corr-id-1");
        DmCorrelationId b = DmCorrelationId.of("corr-id-1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
