package com.ghatana.digitalmarketing.infra.research;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("InMemoryCompetitorResearchRepository")
class InMemoryCompetitorResearchRepositoryTest extends EventloopTestBase {

    private InMemoryCompetitorResearchRepository repository;

    private static final DmWorkspaceId WS_1 = DmWorkspaceId.of("ws-1");
    private static final DmWorkspaceId WS_2 = DmWorkspaceId.of("ws-2");

    @BeforeEach
    void setUp() {
        repository = new InMemoryCompetitorResearchRepository();
    }

    @Test
    @DisplayName("save returns the saved snapshot")
    void shouldReturnSavedSnapshot() {
        CompetitorResearchSnapshot snap = buildSnapshot(WS_1, "snap-1");
        CompetitorResearchSnapshot saved = runPromise(() -> repository.save(snap));
        assertThat(saved).isSameAs(snap);
    }

    @Test
    @DisplayName("findLatestByWorkspace returns saved snapshot")
    void shouldFindSavedSnapshot() {
        runPromise(() -> repository.save(buildSnapshot(WS_1, "snap-1")));

        Optional<CompetitorResearchSnapshot> found = runPromise(() -> repository.findLatestByWorkspace(WS_1));

        assertThat(found).isPresent();
        assertThat(found.get().getSnapshotId()).isEqualTo("snap-1");
    }

    @Test
    @DisplayName("findLatestByWorkspace returns empty when nothing saved")
    void shouldReturnEmptyWhenNothingSaved() {
        Optional<CompetitorResearchSnapshot> found = runPromise(() -> repository.findLatestByWorkspace(WS_1));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("workspace isolation: snapshot saved for ws-1 is not visible from ws-2")
    void shouldIsolateWorkspaces() {
        runPromise(() -> repository.save(buildSnapshot(WS_1, "snap-1")));

        Optional<CompetitorResearchSnapshot> found = runPromise(() -> repository.findLatestByWorkspace(WS_2));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("save overwrites previous snapshot for the same workspace (latest wins)")
    void shouldReplaceOldSnapshotWithLatest() {
        runPromise(() -> repository.save(buildSnapshot(WS_1, "snap-old")));
        runPromise(() -> repository.save(buildSnapshot(WS_1, "snap-new")));

        Optional<CompetitorResearchSnapshot> found = runPromise(() -> repository.findLatestByWorkspace(WS_1));

        assertThat(found).isPresent();
        assertThat(found.get().getSnapshotId()).isEqualTo("snap-new");
    }

    @Test
    @DisplayName("save rejects null snapshot")
    void shouldRejectNullSnapshot() {
        assertThatNullPointerException().isThrownBy(() -> repository.save(null));
    }

    @Test
    @DisplayName("findLatestByWorkspace rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatNullPointerException().isThrownBy(() -> repository.findLatestByWorkspace(null));
    }

    private static CompetitorResearchSnapshot buildSnapshot(DmWorkspaceId workspaceId, String snapshotId) {
        return CompetitorResearchSnapshot.builder()
            .snapshotId(snapshotId)
            .workspaceId(workspaceId)
            .competitorFindings(List.of())
            .keywordFindings(List.of())
            .opportunitySummary("Opportunity summary for " + snapshotId)
            .generatedAt(Instant.now())
            .generatedBy("research-agent")
            .build();
    }
}
