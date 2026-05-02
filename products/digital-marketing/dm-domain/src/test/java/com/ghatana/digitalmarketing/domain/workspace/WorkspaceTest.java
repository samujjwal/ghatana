package com.ghatana.digitalmarketing.domain.workspace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("Workspace domain entity")
class WorkspaceTest {

    private Workspace activeWorkspace() {
        Instant now = Instant.now();
        return Workspace.builder()
            .id(DmWorkspaceId.of("ws-1"))
            .tenantId(DmTenantId.of("tenant-1"))
            .name("Workspace A")
            .description("desc")
            .status(WorkspaceStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("user-1")
            .build();
    }

    @Test
    @DisplayName("builder rejects blank name")
    void shouldRejectBlankName() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Workspace.builder()
                .id(DmWorkspaceId.of("ws-1"))
                .tenantId(DmTenantId.of("tenant-1"))
                .name(" ")
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy("user-1")
                .build());
    }

    @Test
    @DisplayName("suspend and reactivate enforce valid transitions")
    void shouldSuspendAndReactivate() {
        Workspace suspended = activeWorkspace().suspend();
        Workspace reactivated = suspended.reactivate();

        assertThat(suspended.getStatus()).isEqualTo(WorkspaceStatus.SUSPENDED);
        assertThat(reactivated.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);

        assertThatIllegalStateException().isThrownBy(() -> activeWorkspace().reactivate());
        assertThatIllegalStateException().isThrownBy(() -> suspended.suspend());
    }

    @Test
    @DisplayName("archive is terminal and cannot archive twice")
    void shouldArchiveOnce() {
        Workspace archived = activeWorkspace().archive();
        assertThat(archived.getStatus()).isEqualTo(WorkspaceStatus.ARCHIVED);

        assertThatIllegalStateException().isThrownBy(archived::archive);
    }

    @Test
    @DisplayName("exposes fields and supports toBuilder roundtrip")
    void shouldExposeFieldsAndRoundTrip() {
        Workspace w = activeWorkspace();
        Workspace copy = w.toBuilder().build();

        assertThat(w.getId()).isEqualTo(DmWorkspaceId.of("ws-1"));
        assertThat(w.getTenantId()).isEqualTo(DmTenantId.of("tenant-1"));
        assertThat(w.getName()).isEqualTo("Workspace A");
        assertThat(w.getDescription()).isEqualTo("desc");
        assertThat(w.getStatus()).isEqualTo(WorkspaceStatus.ACTIVE);
        assertThat(w.getCreatedAt()).isNotNull();
        assertThat(w.getUpdatedAt()).isNotNull();
        assertThat(w.getCreatedBy()).isEqualTo("user-1");
        assertThat(w).isEqualTo(copy);
        assertThat(w.hashCode()).isEqualTo(copy.hashCode());
        assertThat(w.toString()).contains("ws-1");
        // self-reference
        assertThat(w).isEqualTo(w);
        // null and wrong type
        assertThat(w).isNotEqualTo(null);
        assertThat(w).isNotEqualTo("string");
        // different id: not equal
        Workspace other = w.toBuilder().id(DmWorkspaceId.of("ws-different")).build();
        assertThat(w).isNotEqualTo(other);
        assertThat(w.hashCode()).isNotEqualTo(other.hashCode());
    }
}
