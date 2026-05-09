package com.ghatana.digitalmarketing.infra.optimization;

import com.ghatana.digitalmarketing.domain.optimization.NextBestActionRecommendation;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionStatus;
import com.ghatana.digitalmarketing.domain.optimization.NextBestActionType;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryNextBestActionRecommendationRepository
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("InMemoryNextBestActionRecommendationRepository")
class InMemoryNextBestActionRecommendationRepositoryTest extends EventloopTestBase {

    private InMemoryNextBestActionRecommendationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryNextBestActionRecommendationRepository();
    }

    @Test
    @DisplayName("save persists and returns recommendation")
    void shouldSaveRecommendation() {
        NextBestActionRecommendation recommendation = buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1");
        NextBestActionRecommendation saved = runPromise(() -> repository.save(recommendation));
        assertThat(saved).isSameAs(recommendation);
    }

    @Test
    @DisplayName("findById returns saved recommendation by ID")
    void shouldFindByIdAfterSave() {
        NextBestActionRecommendation recommendation = buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1");
        runPromise(() -> repository.save(recommendation));

        Optional<NextBestActionRecommendation> found = runPromise(() -> repository.findById("nba-1"));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("nba-1");
    }

    @Test
    @DisplayName("findById returns empty for missing ID")
    void shouldReturnEmptyForMissingId() {
        Optional<NextBestActionRecommendation> found = runPromise(() -> repository.findById("no-such-id"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByTenant returns recommendations for tenant")
    void shouldListByTenant() {
        runPromise(() -> repository.save(buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildRecommendation("nba-2", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildRecommendation("nba-3", "tenant-2", "ws-2", "camp-2")));

        List<NextBestActionRecommendation> found = runPromise(() -> repository.listByTenant("tenant-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(r -> r.getTenantId().equals("tenant-1"));
    }

    @Test
    @DisplayName("listByTenant returns empty for tenant with no recommendations")
    void shouldReturnEmptyListForMissingTenant() {
        List<NextBestActionRecommendation> found = runPromise(() -> repository.listByTenant("no-such-tenant"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByWorkspace filters by tenant and workspace")
    void shouldListByWorkspace() {
        runPromise(() -> repository.save(buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildRecommendation("nba-2", "tenant-1", "ws-1", "camp-2")));
        runPromise(() -> repository.save(buildRecommendation("nba-3", "tenant-1", "ws-2", "camp-1")));

        List<NextBestActionRecommendation> found = runPromise(() -> repository.listByWorkspace("tenant-1", "ws-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(r -> r.getWorkspaceId().equals("ws-1"));
    }

    @Test
    @DisplayName("listByWorkspace respects tenant isolation")
    void shouldIsolateTenantInListByWorkspace() {
        runPromise(() -> repository.save(buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1")));

        List<NextBestActionRecommendation> found = runPromise(() -> repository.listByWorkspace("tenant-2", "ws-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByCampaign filters by tenant and campaign")
    void shouldListByCampaign() {
        runPromise(() -> repository.save(buildRecommendation("nba-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildRecommendation("nba-2", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildRecommendation("nba-3", "tenant-1", "ws-2", "camp-2")));

        List<NextBestActionRecommendation> found = runPromise(() -> repository.listByCampaign("tenant-1", "camp-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(r -> r.getCampaignId().equals("camp-1"));
    }

    @Test
    @DisplayName("listByStatus filters by tenant and status")
    void shouldListByStatus() {
        NextBestActionRecommendation nba1 = NextBestActionRecommendation.builder()
            .id("nba-1").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .actionType(NextBestActionType.INCREASE_BUDGET).title("NBA1").description("Desc")
            .parameters(Map.of()).confidenceScore(0.8).status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now()).build();
        NextBestActionRecommendation nba2 = NextBestActionRecommendation.builder()
            .id("nba-2").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .actionType(NextBestActionType.ADJUST_TARGETING).title("NBA2").description("Desc")
            .parameters(Map.of()).confidenceScore(0.8).status(NextBestActionStatus.APPROVED)
            .createdAt(Instant.now()).build();

        runPromise(() -> repository.save(nba1));
        runPromise(() -> repository.save(nba2));

        List<NextBestActionRecommendation> found = runPromise(
            () -> repository.listByStatus("tenant-1", NextBestActionStatus.PENDING)
        );

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo(NextBestActionStatus.PENDING);
    }

    @Test
    @DisplayName("update modifies existing recommendation")
    void shouldUpdateExistingRecommendation() {
        NextBestActionRecommendation original = NextBestActionRecommendation.builder()
            .id("nba-1").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .actionType(NextBestActionType.ADJUST_TARGETING).title("NBA").description("Desc")
            .parameters(Map.of()).confidenceScore(0.8).status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now()).build();
        runPromise(() -> repository.save(original));

        NextBestActionRecommendation updated_modified = original.toBuilder().status(NextBestActionStatus.APPROVED).build();
        NextBestActionRecommendation updated = runPromise(() -> repository.update(updated_modified));

        assertThat(updated.getStatus()).isEqualTo(NextBestActionStatus.APPROVED);

        Optional<NextBestActionRecommendation> retrieved = runPromise(() -> repository.findById("nba-1"));
        assertThat(retrieved.get().getStatus()).isEqualTo(NextBestActionStatus.APPROVED);
    }

    @Test
    @DisplayName("update throws for non-existent recommendation")
    void shouldThrowOnUpdateNonExistent() {
        NextBestActionRecommendation nonExistent = buildRecommendation("no-such-id", "tenant-1", "ws-1", "camp-1");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> repository.update(nonExistent)));
    }

    @Test
    @DisplayName("save rejects null recommendation")
    void shouldRejectNullSave() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("update rejects null recommendation")
    void shouldRejectNullUpdate() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.update(null));
    }

    @Test
    @DisplayName("findById rejects null ID")
    void shouldRejectNullFindById() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.findById(null));
    }

    @Test
    @DisplayName("listByTenant rejects null tenantId")
    void shouldRejectNullListByTenant() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByTenant(null));
    }

    @Test
    @DisplayName("listByWorkspace rejects null arguments")
    void shouldRejectNullListByWorkspace() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByWorkspace(null, "ws-1"));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByWorkspace("tenant-1", null));
    }

    @Test
    @DisplayName("listByCampaign rejects null arguments")
    void shouldRejectNullListByCampaign() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByCampaign(null, "camp-1"));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByCampaign("tenant-1", null));
    }

    @Test
    @DisplayName("listByStatus rejects null arguments")
    void shouldRejectNullListByStatus() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByStatus(null, NextBestActionStatus.PENDING));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByStatus("tenant-1", null));
    }

    private static NextBestActionRecommendation buildRecommendation(String id, String tenantId, String workspaceId, String campaignId) {
        return NextBestActionRecommendation.builder()
            .id(id).tenantId(tenantId).workspaceId(workspaceId).campaignId(campaignId)
            .actionType(NextBestActionType.INCREASE_BUDGET).title("Test NBA").description("Test")
            .parameters(Map.of()).confidenceScore(0.8).status(NextBestActionStatus.PENDING)
            .createdAt(Instant.now()).build();
    }
}
