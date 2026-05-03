package com.ghatana.digitalmarketing.domain.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmGoogleAdsCampaignLink")
class DmGoogleAdsCampaignLinkTest {

    @Test
    @DisplayName("builds valid campaign link")
    void buildsValidLink() {
        DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
            .id("link-1")
            .tenantId("tenant-1")
            .connectorId("conn-1")
            .internalCampaignId("campaign-1")
            .externalCampaignId("google-1")
            .createdAt(Instant.now())
            .build();

        assertThat(link.getId()).isEqualTo("link-1");
        assertThat(link.getInternalCampaignId()).isEqualTo("campaign-1");
        assertThat(link.getExternalCampaignId()).isEqualTo("google-1");
    }

    @Test
    @DisplayName("rejects blank required identifiers")
    void rejectsBlankIdentifiers() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            DmGoogleAdsCampaignLink.builder().id(" ").tenantId("t").connectorId("c")
                .internalCampaignId("i").externalCampaignId("e").createdAt(Instant.now()).build());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            DmGoogleAdsCampaignLink.builder().id("id").tenantId(" ").connectorId("c")
                .internalCampaignId("i").externalCampaignId("e").createdAt(Instant.now()).build());

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            DmGoogleAdsCampaignLink.builder().id("id").tenantId("t").connectorId(" ")
                .internalCampaignId("i").externalCampaignId("e").createdAt(Instant.now()).build());
    }

    @Test
    @DisplayName("rejects null createdAt")
    void rejectsNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmGoogleAdsCampaignLink.builder().id("id").tenantId("t").connectorId("c")
                .internalCampaignId("i").externalCampaignId("e").createdAt(null).build());
    }
}
