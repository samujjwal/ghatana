package com.ghatana.digitalmarketing.infra.googleads;

import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmGoogleAdsCampaignLinkInMemoryRepository")
class DmGoogleAdsCampaignLinkInMemoryRepositoryTest extends EventloopTestBase {

    private DmGoogleAdsCampaignLinkInMemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DmGoogleAdsCampaignLinkInMemoryRepository();
    }

    @Test
    @DisplayName("save and findByInternalCampaignId")
    void saveAndFind() {
        DmGoogleAdsCampaignLink link = DmGoogleAdsCampaignLink.builder()
            .id("link-1")
            .tenantId("tenant-a")
            .connectorId("conn-1")
            .internalCampaignId("campaign-1")
            .externalCampaignId("ga-100")
            .createdAt(Instant.now())
            .build();

        DmGoogleAdsCampaignLink saved = runPromise(() -> repository.save(link));
        assertThat(saved).isSameAs(link);

        Optional<DmGoogleAdsCampaignLink> found = runPromise(() -> repository.findByInternalCampaignId("campaign-1"));
        assertThat(found).isPresent();
        assertThat(found.get().getExternalCampaignId()).isEqualTo("ga-100");

        Optional<DmGoogleAdsCampaignLink> missing = runPromise(() -> repository.findByInternalCampaignId("campaign-missing"));
        assertThat(missing).isEmpty();
    }
}
