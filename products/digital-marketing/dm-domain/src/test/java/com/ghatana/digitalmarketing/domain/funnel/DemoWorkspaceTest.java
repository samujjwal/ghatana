package com.ghatana.digitalmarketing.domain.funnel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DemoWorkspace domain model.
 *
 * @doc.type test
 * @doc.purpose Validates DemoWorkspace lifecycle and validation (P3-001)
 * @doc.layer product
 */
@DisplayName("DemoWorkspace Tests")
class DemoWorkspaceTest {

    @Test
    @DisplayName("Should build valid DemoWorkspace")
    void shouldBuildValidDemoWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .templateConfig(Map.of("theme", "dark", "locale", "en-US"))
            .createdAt(Instant.now())
            .build();

        assertThat(workspace.getId()).isEqualTo("workspace-123");
        assertThat(workspace.getTenantId()).isEqualTo("tenant-456");
        assertThat(workspace.getWorkspaceId()).isEqualTo("ws-789");
        assertThat(workspace.getLeadId()).isEqualTo("lead-101");
        assertThat(workspace.getTemplateId()).isEqualTo("template-202");
        assertThat(workspace.getStatus()).isEqualTo(DemoWorkspaceStatus.PROVISIONED);
        assertThat(workspace.getTemplateConfig()).hasSize(2);
    }

    @Test
    @DisplayName("Should activate workspace from PROVISIONED status")
    void shouldActivateWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build();

        DemoWorkspace activated = workspace.activate();

        assertThat(activated.getStatus()).isEqualTo(DemoWorkspaceStatus.ACTIVE);
        assertThat(activated.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw when activating non-PROVISIONED workspace")
    void shouldThrowWhenActivatingNonProvisioned() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .build();

        assertThatThrownBy(() -> workspace.activate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot activate workspace in status");
    }

    @Test
    @DisplayName("Should deactivate workspace from ACTIVE status")
    void shouldDeactivateWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .build();

        DemoWorkspace deactivated = workspace.deactivate("User requested");

        assertThat(deactivated.getStatus()).isEqualTo(DemoWorkspaceStatus.DEACTIVATED);
        assertThat(deactivated.getDeactivationReason()).isEqualTo("User requested");
    }

    @Test
    @DisplayName("Should throw when deactivating non-ACTIVE workspace")
    void shouldThrowWhenDeactivatingNonActive() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> workspace.deactivate("User requested"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot deactivate workspace in status");
    }

    @Test
    @DisplayName("Should expire workspace from ACTIVE status")
    void shouldExpireWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .build();

        DemoWorkspace expired = workspace.expire();

        assertThat(expired.getStatus()).isEqualTo(DemoWorkspaceStatus.EXPIRED);
    }

    @Test
    @DisplayName("Should throw when expiring non-ACTIVE workspace")
    void shouldThrowWhenExpiringNonActive() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build();

        assertThatThrownBy(() -> workspace.expire())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot expire workspace in status");
    }

    @Test
    @DisplayName("Should return true for active workspace")
    void shouldReturnTrueForActiveWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .build();

        assertThat(workspace.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-active workspace")
    void shouldReturnFalseForNonActiveWorkspace() {
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build();

        assertThat(workspace.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should return true for expired workspace")
    void shouldReturnTrueForExpiredWorkspace() {
        Instant past = Instant.now().minusSeconds(3600);
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .expiresAt(past)
            .build();

        assertThat(workspace.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Should return false for non-expired workspace")
    void shouldReturnFalseForNonExpiredWorkspace() {
        Instant future = Instant.now().plusSeconds(3600);
        DemoWorkspace workspace = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .activatedAt(Instant.now())
            .expiresAt(future)
            .build();

        assertThat(workspace.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Should throw when id is blank")
    void shouldThrowWhenIdIsBlank() {
        assertThatThrownBy(() -> DemoWorkspace.builder()
            .id("")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("id must not be blank");
    }

    @Test
    @DisplayName("Should throw when tenantId is blank")
    void shouldThrowWhenTenantIdIsBlank() {
        assertThatThrownBy(() -> DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    @DisplayName("Should throw when workspaceId is blank")
    void shouldThrowWhenWorkspaceIdIsBlank() {
        assertThatThrownBy(() -> DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workspaceId must not be blank");
    }

    @Test
    @DisplayName("Should throw when leadId is blank")
    void shouldThrowWhenLeadIdIsBlank() {
        assertThatThrownBy(() -> DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("leadId must not be blank");
    }

    @Test
    @DisplayName("Should support toBuilder pattern")
    void shouldSupportToBuilderPattern() {
        DemoWorkspace original = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .templateConfig(Map.of("theme", "dark"))
            .createdAt(Instant.now())
            .build();

        DemoWorkspace modified = original.toBuilder()
            .status(DemoWorkspaceStatus.ACTIVE)
            .activatedAt(Instant.now())
            .build();

        assertThat(modified.getId()).isEqualTo(original.getId());
        assertThat(modified.getStatus()).isEqualTo(DemoWorkspaceStatus.ACTIVE);
        assertThat(modified.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should implement equals and hashCode based on id")
    void shouldImplementEqualsAndHashCode() {
        DemoWorkspace workspace1 = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-456")
            .workspaceId("ws-789")
            .leadId("lead-101")
            .templateId("template-202")
            .status(DemoWorkspaceStatus.PROVISIONED)
            .createdAt(Instant.now())
            .build();

        DemoWorkspace workspace2 = DemoWorkspace.builder()
            .id("workspace-123")
            .tenantId("tenant-999")
            .workspaceId("ws-999")
            .leadId("lead-999")
            .templateId("template-999")
            .status(DemoWorkspaceStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();

        assertThat(workspace1).isEqualTo(workspace2);
        assertThat(workspace1.hashCode()).isEqualTo(workspace2.hashCode());
    }
}
