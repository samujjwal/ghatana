package com.ghatana.digitalmarketing.domain.suppression;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("SuppressionEntry domain entity")
class SuppressionEntryTest {

    private SuppressionEntry activeEntry() {
        Instant now = Instant.now();
        return SuppressionEntry.builder()
            .id("sup-1")
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .email("a@example.com")
            .reason("unsubscribe")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects blank id and email")
    void shouldRejectBlankFields() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> SuppressionEntry.builder()
                .id(" ")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .email("a@example.com")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());

        assertThatIllegalArgumentException()
            .isThrownBy(() -> SuppressionEntry.builder()
                .id("sup-1")
                .workspaceId(DmWorkspaceId.of("ws-1"))
                .email(" ")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("deactivate marks entry inactive")
    void shouldDeactivate() {
        SuppressionEntry entry = activeEntry();
        SuppressionEntry deactivated = entry.deactivate();

        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.getUpdatedAt()).isAfterOrEqualTo(entry.getUpdatedAt());
    }

    @Test
    @DisplayName("deactivate throws when already inactive")
    void shouldRejectDeactivateWhenInactive() {
        SuppressionEntry inactive = activeEntry().toBuilder().active(false).build();

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(inactive::deactivate);
    }
}
