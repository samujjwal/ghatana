package com.ghatana.digitalmarketing.domain.api;

import com.ghatana.digitalmarketing.domain.api.DmPublicApiKey;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKeyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmPublicApiKey domain entity")
class DmPublicApiKeyTest {

    private static final Instant NOW = Instant.parse("2026-05-02T18:00:00Z");

    private DmPublicApiKey valid() {
        return DmPublicApiKey.builder()
            .id("key-1").tenantId("t1").displayName("My Key")
            .keyHash("sha256-abc").scopes(List.of("read", "write"))
            .status(DmPublicApiKeyStatus.ACTIVE).createdAt(NOW).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmPublicApiKey k = valid();
        assertThat(k.getId()).isEqualTo("key-1");
        assertThat(k.getStatus()).isEqualTo(DmPublicApiKeyStatus.ACTIVE);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id("").tenantId("t").displayName("n")
                .keyHash("h").scopes(List.of()).status(DmPublicApiKeyStatus.ACTIVE)
                .createdAt(NOW).build());
    }

    @Test @DisplayName("builder rejects blank displayName")
    void shouldRejectBlankDisplayName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("t").displayName("")
                .keyHash("h").scopes(List.of()).status(DmPublicApiKeyStatus.ACTIVE)
                .createdAt(NOW).build());
    }

    @Test @DisplayName("revoke transitions from ACTIVE")
    void shouldRevoke() {
        DmPublicApiKey revoked = valid().revoke();
        assertThat(revoked.getStatus()).isEqualTo(DmPublicApiKeyStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isNotNull();
    }

    @Test @DisplayName("revoke rejects already revoked")
    void shouldNotRevokeTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().revoke().revoke());
    }

    @Test @DisplayName("isExpired returns false for unexpired key")
    void shouldNotBeExpired() {
        assertThat(valid().isExpired()).isFalse();
    }

    @Test @DisplayName("isExpired returns true for past expiry")
    void shouldBeExpired() {
        DmPublicApiKey expired = DmPublicApiKey.builder()
            .id("k2").tenantId("t").displayName("n").keyHash("h").scopes(List.of())
            .status(DmPublicApiKeyStatus.ACTIVE)
            .expiresAt(Instant.now().minusSeconds(1)).createdAt(NOW).build();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test @DisplayName("recordUsage sets last used time")
    void shouldRecordUsage() {
        DmPublicApiKey used = valid().recordUsage();

        assertThat(used.getLastUsedAt()).isNotNull();
        assertThat(used.getLastUsedAt()).isAfterOrEqualTo(NOW);
        assertThat(used.getScopes()).containsExactly("read", "write");
    }

    @Test @DisplayName("toBuilder copies and overrides state")
    void shouldCopyWithToBuilder() {
        Instant expiresAt = NOW.plusSeconds(3600);
        DmPublicApiKey original = DmPublicApiKey.builder()
            .id("key-7").tenantId("tenant-7").displayName("Partner Key")
            .keyHash("sha256-xyz").scopes(List.of("metrics"))
            .status(DmPublicApiKeyStatus.ACTIVE)
            .expiresAt(expiresAt).lastUsedAt(NOW.plusSeconds(60)).createdAt(NOW).build();

        DmPublicApiKey copy = original.toBuilder().displayName("Renamed Key").build();

        assertThat(copy.getId()).isEqualTo("key-7");
        assertThat(copy.getTenantId()).isEqualTo("tenant-7");
        assertThat(copy.getDisplayName()).isEqualTo("Renamed Key");
        assertThat(copy.getKeyHash()).isEqualTo("sha256-xyz");
        assertThat(copy.getScopes()).containsExactly("metrics");
        assertThat(copy.getStatus()).isEqualTo(DmPublicApiKeyStatus.ACTIVE);
        assertThat(copy.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(copy.getLastUsedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(copy.getCreatedAt()).isEqualTo(NOW);
        assertThat(copy.getRevokedAt()).isNull();
    }

    @Test @DisplayName("scopes list is immutable")
    void shouldHaveImmutableScopes() {
        assertThat(valid().getScopes()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmPublicApiKey left = valid();
        DmPublicApiKey sameId = valid();
        DmPublicApiKey differentId = DmPublicApiKey.builder()
            .id("key-9").tenantId("t1").displayName("Other")
            .keyHash("sha256-other").status(DmPublicApiKeyStatus.ACTIVE)
            .createdAt(NOW).build();

        assertThat(left)
            .isEqualTo(sameId)
            .hasSameHashCodeAs(sameId)
            .isNotEqualTo(differentId)
            .isNotEqualTo("key-1");
        assertThat(left.toString()).contains("key-1", "ACTIVE");
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id(null).tenantId("t").keyHash("h")
                .status(DmPublicApiKeyStatus.ACTIVE).createdAt(NOW).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("").keyHash("h")
                .status(DmPublicApiKeyStatus.ACTIVE).createdAt(NOW).build());
    }

    @Test @DisplayName("builder requires key hash")
    void shouldRequireKeyHash() {
        assertThatNullPointerException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("t").displayName("name")
                .keyHash(null).status(DmPublicApiKeyStatus.ACTIVE).createdAt(NOW).build())
            .withMessage("keyHash must not be null");
    }

    @Test @DisplayName("builder requires status and createdAt")
    void shouldRequireStatusAndCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("t").displayName("name")
                .keyHash("hash").createdAt(NOW).build())
            .withMessage("status must not be null");

        assertThatNullPointerException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("t").displayName("name")
                .keyHash("hash").status(DmPublicApiKeyStatus.ACTIVE).build())
            .withMessage("createdAt must not be null");
    }
}
