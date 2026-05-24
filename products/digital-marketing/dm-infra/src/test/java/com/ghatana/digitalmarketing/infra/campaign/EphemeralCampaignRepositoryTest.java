package com.ghatana.digitalmarketing.infra.campaign;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("EphemeralCampaignRepository")
class EphemeralCampaignRepositoryTest extends EventloopTestBase {

    private EphemeralCampaignRepository repository;

    private static final DmWorkspaceId WS_1 = DmWorkspaceId.of("ws-1");
    private static final DmWorkspaceId WS_2 = DmWorkspaceId.of("ws-2");

    @BeforeEach
    void setUp() {
        repository = new EphemeralCampaignRepository();
    }

    @Test
    @DisplayName("save returns the saved campaign")
    void shouldReturnSavedCampaign() {
        Campaign c = buildCampaign(WS_1, "c-1");
        Campaign saved = runPromise(() -> repository.save(c));
        assertThat(saved).isSameAs(c);
    }

    @Test
    @DisplayName("findById returns campaign within same workspace")
    void shouldFindSavedCampaign() {
        Campaign c = buildCampaign(WS_1, "c-1");
        runPromise(() -> repository.save(c));

        Optional<Campaign> found = runPromise(() -> repository.findById(WS_1, "c-1"));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("c-1");
    }

    @Test
    @DisplayName("findById returns empty for missing campaign")
    void shouldReturnEmptyForMissing() {
        Optional<Campaign> found = runPromise(() -> repository.findById(WS_1, "no-such-id"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("workspace isolation: campaign saved in ws-1 is not visible from ws-2")
    void shouldIsolateWorkspaces() {
        runPromise(() -> repository.save(buildCampaign(WS_1, "c-1")));

        Optional<Campaign> found = runPromise(() -> repository.findById(WS_2, "c-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save overwrites an existing campaign with the same id")
    void shouldOverwriteExistingCampaign() {
        Campaign original = buildCampaign(WS_1, "c-1");
        Campaign updated  = buildCampaign(WS_1, "c-1");

        runPromise(() -> repository.save(original));
        runPromise(() -> repository.save(updated));

        Optional<Campaign> found = runPromise(() -> repository.findById(WS_1, "c-1"));
        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("save rejects null campaign")
    void shouldRejectNullCampaign() {
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findById rejects null arguments")
    void shouldRejectNullArgs() {
        assertThatNullPointerException().isThrownBy(() -> repository.findById(null, "c-1"));
        assertThatNullPointerException().isThrownBy(() -> repository.findById(WS_1, null));
    }

    private static Campaign buildCampaign(DmWorkspaceId workspaceId, String campaignId) {
        Instant now = Instant.now();
        return Campaign.builder()
            .id(campaignId)
            .workspaceId(workspaceId)
            .name("Campaign " + UUID.randomUUID())
            .status(CampaignStatus.DRAFT)
            .type(CampaignType.EMAIL)
            .createdAt(now)
            .updatedAt(now)
            .createdBy("test-user")
            .build();
    }
}
