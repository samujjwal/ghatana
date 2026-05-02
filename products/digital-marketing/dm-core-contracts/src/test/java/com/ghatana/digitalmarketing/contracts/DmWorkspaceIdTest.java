package com.ghatana.digitalmarketing.contracts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DmWorkspaceId}.
 */
@DisplayName("DmWorkspaceId")
class DmWorkspaceIdTest {

    @Test
    @DisplayName("of() accepts valid non-blank value")
    void shouldCreateFromValidString() {
        DmWorkspaceId id = DmWorkspaceId.of("ws-abc-123");
        assertThat(id.getValue()).isEqualTo("ws-abc-123");
    }

    @Test
    @DisplayName("of() rejects null")
    void shouldRejectNull() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmWorkspaceId.of(null))
            .withMessageContaining("workspaceId");
    }

    @Test
    @DisplayName("of() rejects blank")
    void shouldRejectBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmWorkspaceId.of("  "));
    }

    @Test
    @DisplayName("equals and hashCode are value-based")
    void shouldHaveValueBasedEquality() {
        DmWorkspaceId a = DmWorkspaceId.of("ws-1");
        DmWorkspaceId b = DmWorkspaceId.of("ws-1");
        DmWorkspaceId c = DmWorkspaceId.of("ws-2");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }
}
