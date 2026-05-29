package com.ghatana.digitalmarketing.domain.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmGoogleAdsCredential domain entity")
class DmGoogleAdsCredentialTest {

    private DmGoogleAdsCredential valid() {
        return DmGoogleAdsCredential.builder()
            .id("cred-1")
            .tenantId("tenant-1")
            .connectorId("conn-1")
            .accessToken("access")
            .refreshToken("refresh")
            .expiresAt(Instant.now().plusSeconds(3600))
            .scopes(List.of("scope1"))
            .createdAt(Instant.now())
            .build();
    }

    @Test @DisplayName("builder creates valid credential")
    void shouldBuildValid() {
        DmGoogleAdsCredential c = valid();
        assertThat(c.getId()).isEqualTo("cred-1");
        assertThat(c.getTenantId()).isEqualTo("tenant-1");
        assertThat(c.isExpired()).isFalse();
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmGoogleAdsCredential.builder().id("").tenantId("t").connectorId("c")
                .accessToken("a").refreshToken("r").createdAt(Instant.now()).scopes(List.of()).build());
    }

    @Test @DisplayName("builder rejects blank tenantId")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmGoogleAdsCredential.builder().id("x").tenantId("").connectorId("c")
                .accessToken("a").refreshToken("r").createdAt(Instant.now()).scopes(List.of()).build());
    }

    @Test @DisplayName("builder rejects blank connectorId")
    void shouldRejectBlankConnectorId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmGoogleAdsCredential.builder().id("x").tenantId("t").connectorId("")
                .accessToken("a").refreshToken("r").createdAt(Instant.now()).scopes(List.of()).build());
    }

    @Test @DisplayName("builder rejects null accessToken")
    void shouldRejectNullAccessToken() {
        assertThatNullPointerException().isThrownBy(() ->
            DmGoogleAdsCredential.builder().id("x").tenantId("t").connectorId("c")
                .accessToken(null).refreshToken("r").createdAt(Instant.now()).scopes(List.of()).build());
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmGoogleAdsCredential.builder().id("x").tenantId("t").connectorId("c")
                .accessToken("a").refreshToken("r").createdAt(null).scopes(List.of()).build());
    }

    @Test @DisplayName("isExpired returns true when expiresAt is in the past")
    void shouldBeExpiredWhenPast() {
        DmGoogleAdsCredential expired = DmGoogleAdsCredential.builder()
            .id("c1").tenantId("t1").connectorId("c1").accessToken("a").refreshToken("r")
            .expiresAt(Instant.now().minusSeconds(1))
            .scopes(List.of()).createdAt(Instant.now()).build();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test @DisplayName("refresh returns updated credential")
    void shouldRefreshToken() {
        DmGoogleAdsCredential c = valid();
        Instant newExpiry = Instant.now().plusSeconds(7200);
        DmGoogleAdsCredential refreshed = c.refresh("newToken", newExpiry);
        assertThat(refreshed.getAccessToken()).isEqualTo("newToken");
        assertThat(refreshed.getExpiresAt()).isEqualTo(newExpiry);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmGoogleAdsCredential a = valid();
        DmGoogleAdsCredential b = valid();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test @DisplayName("toString contains id")
    void shouldContainId() {
        assertThat(valid().toString()).contains("cred-1");
    }
}
