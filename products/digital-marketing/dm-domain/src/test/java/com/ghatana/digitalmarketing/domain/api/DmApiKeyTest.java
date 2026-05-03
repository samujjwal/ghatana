package com.ghatana.digitalmarketing.domain.api;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for DmApiKey (DMOS-P1-016).
 *
 * @doc.type test
 * @doc.purpose Verify API key generation, hashing, rotation, revocation, and usage tracking
 * @doc.layer domain
 */
@DisplayName("DmApiKey")
class DmApiKeyTest {

    @Test
    @DisplayName("generate creates API key with raw key displayed once")
    void generate_createsApiKeyWithRawKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        String createdBy = "user-789";
        String rateLimitPlan = "default";

        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, createdBy, rateLimitPlan);

        assertThat(result.apiKey()).isNotNull();
        assertThat(result.rawKey()).isNotNull();
        assertThat(result.rawKey()).hasSize(64); // 32 bytes = 64 hex characters
        assertThat(result.apiKey().getKeyPrefix()).hasSize(8); // First 8 characters
        assertThat(result.apiKey().getKeyPrefix()).isEqualTo(result.rawKey().substring(0, 8));
        assertThat(result.apiKey().getTenantId()).isEqualTo(tenantId);
        assertThat(result.apiKey().getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(result.apiKey().getCreatedBy()).isEqualTo(createdBy);
        assertThat(result.apiKey().getRateLimitPlan()).isEqualTo(rateLimitPlan);
    }

    @Test
    @DisplayName("verify returns true for correct raw key")
    void verify_returnsTrueForCorrectRawKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        assertThat(result.apiKey().verify(result.rawKey())).isTrue();
    }

    @Test
    @DisplayName("verify returns false for incorrect raw key")
    void verify_returnsFalseForIncorrectRawKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        assertThat(result.apiKey().verify("incorrect-key")).isFalse();
    }

    @Test
    @DisplayName("rotate creates new API key with new raw key")
    void rotate_createsNewApiKeyWithNewRawKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw original = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        DmApiKey.ApiKeyWithRaw rotated = original.apiKey().rotate("user-789");

        assertThat(rotated.apiKey().getId()).isEqualTo(original.apiKey().getId());
        assertThat(rotated.apiKey().getKeyPrefix()).isNotEqualTo(original.apiKey().getKeyPrefix());
        assertThat(rotated.apiKey().getKeyHash()).isNotEqualTo(original.apiKey().getKeyHash());
        assertThat(rotated.rawKey()).isNotEqualTo(original.rawKey());
        assertThat(original.apiKey().verify(rotated.rawKey())).isFalse();
    }

    @Test
    @DisplayName("revoke marks API key as revoked")
    void revoke_marksApiKeyAsRevoked() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        assertThat(result.apiKey().isRevoked()).isFalse();

        DmApiKey revoked = result.apiKey().revoke("admin-123");

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.getRevokedBy()).isEqualTo("admin-123");
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("revoke throws when already revoked")
    void revoke_throwsWhenAlreadyRevoked() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        DmApiKey revoked = result.apiKey().revoke("admin-123");

        assertThatThrownBy(() -> revoked.revoke("admin-123"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already revoked");
    }

    @Test
    @DisplayName("recordUsage updates last used timestamp")
    void recordUsage_updatesLastUsedTimestamp() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        assertThat(result.apiKey().getLastUsedAt()).isNull();

        DmApiKey used = result.apiKey().recordUsage();

        assertThat(used.getLastUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("isValid returns true for non-revoked, non-expired key")
    void isValid_returnsTrueForNonRevokedNonExpiredKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        assertThat(result.apiKey().isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid returns false for revoked key")
    void isValid_returnsFalseForRevokedKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        DmApiKey revoked = result.apiKey().revoke("admin-123");

        assertThat(revoked.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for expired key")
    void isValid_returnsFalseForExpiredKey() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        DmApiKey expired = result.apiKey().toBuilder()
            .expiresAt(java.time.Instant.now().minusSeconds(60))
            .build();

        assertThat(expired.isValid()).isFalse();
    }

    @Test
    @DisplayName("toString redacts hash from logs")
    void toString_redactsHashFromLogs() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");
        DmWorkspaceId workspaceId = DmWorkspaceId.of("workspace-456");
        DmApiKey.ApiKeyWithRaw result = DmApiKey.generate(tenantId, workspaceId, "user-789", "default");

        String str = result.apiKey().toString();

        assertThat(str).doesNotContain(result.apiKey().getKeyHash());
        assertThat(str).contains("keyPrefix");
    }
}
