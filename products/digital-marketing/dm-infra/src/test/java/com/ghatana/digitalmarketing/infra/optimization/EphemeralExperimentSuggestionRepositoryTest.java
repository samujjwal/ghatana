package com.ghatana.digitalmarketing.infra.optimization;

import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestionStatus;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentType;
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
 * @doc.purpose Unit tests for EphemeralExperimentSuggestionRepository
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EphemeralExperimentSuggestionRepository")
class EphemeralExperimentSuggestionRepositoryTest extends EventloopTestBase {

    private EphemeralExperimentSuggestionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EphemeralExperimentSuggestionRepository();
    }

    @Test
    @DisplayName("save persists and returns suggestion")
    void shouldSaveSuggestion() {
        ExperimentSuggestion suggestion = buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1");
        ExperimentSuggestion saved = runPromise(() -> repository.save(suggestion));
        assertThat(saved).isSameAs(suggestion);
    }

    @Test
    @DisplayName("findById returns saved suggestion by ID")
    void shouldFindByIdAfterSave() {
        ExperimentSuggestion suggestion = buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1");
        runPromise(() -> repository.save(suggestion));

        Optional<ExperimentSuggestion> found = runPromise(() -> repository.findById("exp-1"));

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo("exp-1");
    }

    @Test
    @DisplayName("findById returns empty for missing ID")
    void shouldReturnEmptyForMissingId() {
        Optional<ExperimentSuggestion> found = runPromise(() -> repository.findById("no-such-id"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByTenant returns suggestions for tenant")
    void shouldListByTenant() {
        runPromise(() -> repository.save(buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildSuggestion("exp-2", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildSuggestion("exp-3", "tenant-2", "ws-2", "camp-2")));

        List<ExperimentSuggestion> found = runPromise(() -> repository.listByTenant("tenant-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(s -> s.getTenantId().equals("tenant-1"));
    }

    @Test
    @DisplayName("listByTenant returns empty for tenant with no suggestions")
    void shouldReturnEmptyListForMissingTenant() {
        List<ExperimentSuggestion> found = runPromise(() -> repository.listByTenant("no-such-tenant"));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByWorkspace filters by tenant and workspace")
    void shouldListByWorkspace() {
        runPromise(() -> repository.save(buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildSuggestion("exp-2", "tenant-1", "ws-1", "camp-2")));
        runPromise(() -> repository.save(buildSuggestion("exp-3", "tenant-1", "ws-2", "camp-1")));

        List<ExperimentSuggestion> found = runPromise(() -> repository.listByWorkspace("tenant-1", "ws-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(s -> s.getWorkspaceId().equals("ws-1"));
    }

    @Test
    @DisplayName("listByWorkspace respects tenant isolation")
    void shouldIsolateTenantInListByWorkspace() {
        runPromise(() -> repository.save(buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1")));

        List<ExperimentSuggestion> found = runPromise(() -> repository.listByWorkspace("tenant-2", "ws-1"));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("listByCampaign filters by tenant and campaign")
    void shouldListByCampaign() {
        runPromise(() -> repository.save(buildSuggestion("exp-1", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildSuggestion("exp-2", "tenant-1", "ws-1", "camp-1")));
        runPromise(() -> repository.save(buildSuggestion("exp-3", "tenant-1", "ws-2", "camp-2")));

        List<ExperimentSuggestion> found = runPromise(() -> repository.listByCampaign("tenant-1", "camp-1"));

        assertThat(found).hasSize(2);
        assertThat(found).allMatch(s -> s.getCampaignId().equals("camp-1"));
    }

    @Test
    @DisplayName("listByStatus filters by tenant and status")
    void shouldListByStatus() {
        ExperimentSuggestion exp1 = ExperimentSuggestion.builder()
            .id("exp-1").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .experimentType(ExperimentType.CREATIVE_TEST).title("Test").description("Desc")
            .controlVariant(Map.of()).treatmentVariant(Map.of()).hypothesis("Hyp")
            .successMetric("ctr").minimumDetectableEffect(0.15)
            .status(ExperimentSuggestionStatus.PENDING).createdAt(Instant.now()).build();
        ExperimentSuggestion exp2 = ExperimentSuggestion.builder()
            .id("exp-2").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .experimentType(ExperimentType.CREATIVE_TEST).title("Test 2").description("Desc")
            .controlVariant(Map.of()).treatmentVariant(Map.of()).hypothesis("Hyp")
            .successMetric("ctr").minimumDetectableEffect(0.15)
            .status(ExperimentSuggestionStatus.APPROVED).createdAt(Instant.now()).build();

        runPromise(() -> repository.save(exp1));
        runPromise(() -> repository.save(exp2));

        List<ExperimentSuggestion> found = runPromise(
            () -> repository.listByStatus("tenant-1", ExperimentSuggestionStatus.PENDING)
        );

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getStatus()).isEqualTo(ExperimentSuggestionStatus.PENDING);
    }

    @Test
    @DisplayName("update modifies existing suggestion")
    void shouldUpdateExistingSuggestion() {
        ExperimentSuggestion original = ExperimentSuggestion.builder()
            .id("exp-1").tenantId("tenant-1").workspaceId("ws-1").campaignId("camp-1")
            .experimentType(ExperimentType.CREATIVE_TEST).title("Test").description("Desc")
            .controlVariant(Map.of()).treatmentVariant(Map.of()).hypothesis("Hyp")
            .successMetric("ctr").minimumDetectableEffect(0.15)
            .status(ExperimentSuggestionStatus.PENDING).createdAt(Instant.now()).build();
        runPromise(() -> repository.save(original));

        ExperimentSuggestion updated_modified = original.toBuilder().status(ExperimentSuggestionStatus.APPROVED).build();
        ExperimentSuggestion updated = runPromise(() -> repository.update(updated_modified));

        assertThat(updated.getStatus()).isEqualTo(ExperimentSuggestionStatus.APPROVED);

        Optional<ExperimentSuggestion> retrieved = runPromise(() -> repository.findById("exp-1"));
        assertThat(retrieved.get().getStatus()).isEqualTo(ExperimentSuggestionStatus.APPROVED);
    }

    @Test
    @DisplayName("update throws for non-existent suggestion")
    void shouldThrowOnUpdateNonExistent() {
        ExperimentSuggestion nonExistent = buildSuggestion("no-such-id", "tenant-1", "ws-1", "camp-1");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> repository.update(nonExistent)));
    }

    @Test
    @DisplayName("save rejects null suggestion")
    void shouldRejectNullSave() {
        assertThatNullPointerException()
            .isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("update rejects null suggestion")
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
            .isThrownBy(() -> repository.listByStatus(null, ExperimentSuggestionStatus.PENDING));
        assertThatNullPointerException()
            .isThrownBy(() -> repository.listByStatus("tenant-1", null));
    }

    private static ExperimentSuggestion buildSuggestion(String id, String tenantId, String workspaceId, String campaignId) {
        return ExperimentSuggestion.builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .campaignId(campaignId)
            .experimentType(ExperimentType.CREATIVE_TEST)
            .title("Test")
            .description("Test description")
            .controlVariant(Map.of())
            .treatmentVariant(Map.of())
            .hypothesis("Test hypothesis")
            .successMetric("ctr")
            .minimumDetectableEffect(0.15)
            .status(ExperimentSuggestionStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }
}
