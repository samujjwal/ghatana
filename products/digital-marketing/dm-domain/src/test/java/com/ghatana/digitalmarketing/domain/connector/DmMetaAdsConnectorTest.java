package com.ghatana.digitalmarketing.domain.connector;

import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnector;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnectorStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmMetaAdsConnector domain entity")
class DmMetaAdsConnectorTest {

    private DmMetaAdsConnector valid() {
        Instant now = Instant.now();
        return DmMetaAdsConnector.builder()
            .id("conn-1").tenantId("t1").workspaceId("ws1").displayName("Meta Ads")
            .appId("app-1").accountId("acc-1").accessToken("token")
            .status(DmMetaAdsConnectorStatus.PENDING)
            .createdAt(now).updatedAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmMetaAdsConnector c = valid();
        assertThat(c.getId()).isEqualTo("conn-1");
        assertThat(c.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.PENDING);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMetaAdsConnector.builder().id("").tenantId("t").displayName("n")
                .appId("a").accountId("acc").accessToken("tok")
                .status(DmMetaAdsConnectorStatus.PENDING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("activate from PENDING succeeds")
    void shouldActivate() {
        DmMetaAdsConnector active = valid().activate();
        assertThat(active.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.ACTIVE);
    }

    @Test @DisplayName("activate from non-PENDING fails")
    void shouldNotActivateTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().activate().activate());
    }

    @Test @DisplayName("markFailed from PENDING")
    void shouldMarkFailed() {
        DmMetaAdsConnector failed = valid().markFailed("auth error");
        assertThat(failed.getStatus()).isEqualTo(DmMetaAdsConnectorStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("auth error");
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMetaAdsConnector.builder().id(null).tenantId("t").displayName("n")
                .appId("a").accountId("acc").accessToken("tok")
                .status(DmMetaAdsConnectorStatus.PENDING)
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMetaAdsConnector.builder().id("x").tenantId("").displayName("n")
                .appId("a").accountId("acc").accessToken("tok")
                .status(DmMetaAdsConnectorStatus.PENDING)
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build());
    }
}
