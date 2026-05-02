package com.ghatana.digitalmarketing.contracts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DmTenantId}.
 *
 * Verifies formatting constraints, equality semantics, and null/blank rejection.
 */
@DisplayName("DmTenantId")
class DmTenantIdTest {

    @Test
    @DisplayName("of() accepts valid non-blank value")
    void shouldCreateFromValidString() {
        DmTenantId id = DmTenantId.of("acme-corp");
        assertThat(id.getValue()).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("of() rejects null value")
    void shouldRejectNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmTenantId.of(null))
            .withMessageContaining("tenantId");
    }

    @Test
    @DisplayName("of() rejects blank value")
    void shouldRejectBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmTenantId.of("   "))
            .withMessageContaining("blank");
    }

    @Test
    @DisplayName("of() rejects empty string")
    void shouldRejectEmpty() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmTenantId.of(""))
            .withMessageContaining("blank");
    }

    @Test
    @DisplayName("equals and hashCode are value-based")
    void shouldHaveValueBasedEquality() {
        DmTenantId a = DmTenantId.of("tenant-1");
        DmTenantId b = DmTenantId.of("tenant-1");
        DmTenantId c = DmTenantId.of("tenant-2");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString includes value")
    void shouldIncludeValueInToString() {
        assertThat(DmTenantId.of("acme").toString()).contains("acme");
    }
}
