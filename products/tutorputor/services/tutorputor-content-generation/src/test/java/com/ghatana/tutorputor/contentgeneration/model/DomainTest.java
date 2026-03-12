package com.ghatana.tutorputor.explorer.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Unit tests for Domain enum
 * @doc.layer test
 */
class DomainTest {

    @Test
    @DisplayName("Should contain all expected domain values")
    void shouldContainAllDomainValues() {
        // Then
        assertThat(Domain.values()).hasSize(8);
        assertThat(Domain.values()).contains(
            Domain.PHYSICS,
            Domain.CHEMISTRY,
            Domain.BIOLOGY,
            Domain.MATHEMATICS,
            Domain.ECONOMICS,
            Domain.CS_DISCRETE,
            Domain.MEDICINE,
            Domain.ENGINEERING
        );
    }

    @Test
    @DisplayName("Should convert Domain to string correctly")
    void shouldConvertToString() {
        assertThat(Domain.PHYSICS.toString()).isEqualTo("PHYSICS");
        assertThat(Domain.CHEMISTRY.toString()).isEqualTo("CHEMISTRY");
        assertThat(Domain.BIOLOGY.toString()).isEqualTo("BIOLOGY");
    }

    @Test
    @DisplayName("Should retrieve Domain by name")
    void shouldRetrieveByName() {
        assertThat(Domain.valueOf("PHYSICS")).isEqualTo(Domain.PHYSICS);
        assertThat(Domain.valueOf("CS_DISCRETE")).isEqualTo(Domain.CS_DISCRETE);
    }
}
