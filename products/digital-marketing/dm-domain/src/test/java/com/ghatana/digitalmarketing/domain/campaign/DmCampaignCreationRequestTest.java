package com.ghatana.digitalmarketing.domain.campaign;

import com.ghatana.digitalmarketing.domain.campaign.DmCampaignCreationRequest;
import com.ghatana.digitalmarketing.domain.campaign.DmCampaignCreationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmCampaignCreationRequest domain entity")
class DmCampaignCreationRequestTest {

    private DmCampaignCreationRequest valid() {
        return DmCampaignCreationRequest.builder()
            .id("req-1").tenantId("t1").workspaceId("ws1").connectorId("conn-1")
            .campaignName("Test Campaign").dailyBudgetMicros(1_000_000L)
            .targetLocationCode("US").bidStrategy("MANUAL_CPC")
            .adGroupConfig(Map.of()).status(DmCampaignCreationStatus.PENDING)
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid request")
    void shouldBuildValid() {
        DmCampaignCreationRequest r = valid();
        assertThat(r.getId()).isEqualTo("req-1");
        assertThat(r.getStatus()).isEqualTo(DmCampaignCreationStatus.PENDING);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("").tenantId("t").connectorId("c")
                .campaignName("n").status(DmCampaignCreationStatus.PENDING)
                .adGroupConfig(Map.of()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank campaignName")
    void shouldRejectBlankCampaignName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("t").connectorId("c")
                .campaignName("").status(DmCampaignCreationStatus.PENDING)
                .adGroupConfig(Map.of()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("t").connectorId("c")
                .campaignName("n").status(null)
                .adGroupConfig(Map.of()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("markSubmitted transitions from PENDING")
    void shouldMarkSubmitted() {
        DmCampaignCreationRequest submitted = valid().markSubmitted("ext-123");
        assertThat(submitted.getStatus()).isEqualTo(DmCampaignCreationStatus.SUBMITTED);
        assertThat(submitted.getExternalCampaignId()).isEqualTo("ext-123");
    }

    @Test @DisplayName("markSubmitted rejects non-PENDING state")
    void shouldRejectSubmitWhenNotPending() {
        DmCampaignCreationRequest submitted = valid().markSubmitted("ext");
        assertThatIllegalStateException().isThrownBy(() -> submitted.markSubmitted("ext2"));
    }

    @Test @DisplayName("markFailed from PENDING")
    void shouldMarkFailed() {
        DmCampaignCreationRequest failed = valid().markFailed("oops");
        assertThat(failed.getStatus()).isEqualTo(DmCampaignCreationStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("oops");
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmCampaignCreationRequest a = valid();
        DmCampaignCreationRequest b = valid();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test @DisplayName("toString contains id")
    void shouldContainId() {
        assertThat(valid().toString()).contains("req-1");
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id(null).tenantId("t").connectorId("c")
                .campaignName("n").adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("").connectorId("c")
                .campaignName("n").adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("null tenantId throws")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId(null).connectorId("c")
                .campaignName("n").adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("null connectorId throws")
    void shouldRejectNullConnectorId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("t").connectorId(null)
                .campaignName("n").adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank connectorId throws")
    void shouldRejectBlankConnectorId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("t").connectorId("")
                .campaignName("n").adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("null campaignName throws")
    void shouldRejectNullCampaignName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignCreationRequest.builder().id("x").tenantId("t").connectorId("c")
                .campaignName(null).adGroupConfig(java.util.Map.of())
                .status(DmCampaignCreationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }
}
