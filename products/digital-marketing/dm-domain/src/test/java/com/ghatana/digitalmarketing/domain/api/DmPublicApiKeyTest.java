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

    private DmPublicApiKey valid() {
        return DmPublicApiKey.builder()
            .id("key-1").tenantId("t1").displayName("My Key")
            .keyHash("sha256-abc").scopes(List.of("read", "write"))
            .status(DmPublicApiKeyStatus.ACTIVE).createdAt(Instant.now()).build();
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
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank displayName")
    void shouldRejectBlankDisplayName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("t").displayName("")
                .keyHash("h").scopes(List.of()).status(DmPublicApiKeyStatus.ACTIVE)
                .createdAt(Instant.now()).build());
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
            .expiresAt(Instant.now().minusSeconds(1)).createdAt(Instant.now()).build();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test @DisplayName("scopes list is immutable")
    void shouldHaveImmutableScopes() {
        assertThat(valid().getScopes()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id(null).tenantId("t").keyHash("h")
                .status(DmPublicApiKeyStatus.ACTIVE).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmPublicApiKey.builder().id("x").tenantId("").keyHash("h")
                .status(DmPublicApiKeyStatus.ACTIVE).createdAt(java.time.Instant.now()).build());
    }
}
