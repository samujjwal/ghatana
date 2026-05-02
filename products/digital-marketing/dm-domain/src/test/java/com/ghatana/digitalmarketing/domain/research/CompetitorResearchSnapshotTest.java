package com.ghatana.digitalmarketing.domain.research;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompetitorResearchSnapshot")
class CompetitorResearchSnapshotTest {

    private static final DmWorkspaceId WS = DmWorkspaceId.of("ws-1");

    private CompetitorResearchSnapshot buildValid() {
        return CompetitorResearchSnapshot.builder()
            .snapshotId("snap-1")
            .workspaceId(WS)
            .competitorFindings(List.of(
                new CompetitorFinding("rival.com", "Has testimonials page", "Targets service intent", false, "user-provided")
            ))
            .keywordFindings(List.of(
                new KeywordFinding("plumber near me", KeywordIntent.TRANSACTIONAL, 0.9, "Search campaign", "High local search volume", "inferred-mvp")
            ))
            .opportunitySummary("Competitor gap identified in local SEO")
            .generatedAt(Instant.now())
            .generatedBy("owner-1")
            .build();
    }

    @Test
    @DisplayName("builds valid snapshot with all fields")
    void shouldBuildValidSnapshot() {
        CompetitorResearchSnapshot snap = buildValid();

        assertThat(snap.getSnapshotId()).isEqualTo("snap-1");
        assertThat(snap.getWorkspaceId()).isEqualTo(WS);
        assertThat(snap.getCompetitorFindings()).hasSize(1);
        assertThat(snap.getKeywordFindings()).hasSize(1);
        assertThat(snap.getOpportunitySummary()).isEqualTo("Competitor gap identified in local SEO");
        assertThat(snap.getGeneratedBy()).isEqualTo("owner-1");
    }

    @Test
    @DisplayName("defaults to empty findings lists when nulls passed")
    void shouldDefaultToEmptyLists() {
        CompetitorResearchSnapshot snap = CompetitorResearchSnapshot.builder()
            .snapshotId("snap-2")
            .workspaceId(WS)
            .opportunitySummary("No competitors found")
            .generatedAt(Instant.now())
            .generatedBy("owner-1")
            .build();

        assertThat(snap.getCompetitorFindings()).isEmpty();
        assertThat(snap.getKeywordFindings()).isEmpty();
    }

    @Test
    @DisplayName("rejects blank snapshotId")
    void shouldRejectBlankSnapshotId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> CompetitorResearchSnapshot.builder()
                .snapshotId(" ")
                .workspaceId(WS)
                .opportunitySummary("ok")
                .generatedAt(Instant.now())
                .generatedBy("owner-1")
                .build());
    }

    @Test
    @DisplayName("rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatThrownBy(() -> CompetitorResearchSnapshot.builder()
            .snapshotId("snap-3")
            .workspaceId(null)
            .opportunitySummary("ok")
            .generatedAt(Instant.now())
            .generatedBy("owner-1")
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("rejects blank opportunitySummary")
    void shouldRejectBlankOpportunitySummary() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> CompetitorResearchSnapshot.builder()
                .snapshotId("snap-4")
                .workspaceId(WS)
                .opportunitySummary("")
                .generatedAt(Instant.now())
                .generatedBy("owner-1")
                .build());
    }

    @Test
    @DisplayName("rejects blank generatedBy")
    void shouldRejectBlankGeneratedBy() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> CompetitorResearchSnapshot.builder()
                .snapshotId("snap-5")
                .workspaceId(WS)
                .opportunitySummary("ok")
                .generatedAt(Instant.now())
                .generatedBy(" ")
                .build());
    }

    @Test
    @DisplayName("rejects null generatedAt")
    void shouldRejectNullGeneratedAt() {
        assertThatThrownBy(() -> CompetitorResearchSnapshot.builder()
            .snapshotId("snap-6")
            .workspaceId(WS)
            .opportunitySummary("ok")
            .generatedAt(null)
            .generatedBy("owner-1")
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("KeywordFinding rejects out-of-range relevance score")
    void shouldRejectInvalidRelevanceScore() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                "test keyword", KeywordIntent.INFORMATIONAL, 1.5, "Search", "evidence", "src"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                "test keyword", KeywordIntent.INFORMATIONAL, -0.1, "Search", "evidence", "src"
            ));
    }

    @Test
    @DisplayName("KeywordFinding rejects blank fields")
    void shouldRejectBlankKeywordFindingFields() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                " ", KeywordIntent.TRANSACTIONAL, 0.5, "Search", "evidence", "src"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                "keyword", KeywordIntent.TRANSACTIONAL, 0.5, "", "evidence", "src"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                "keyword", KeywordIntent.TRANSACTIONAL, 0.5, "Search", " ", "src"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new KeywordFinding(
                "keyword", KeywordIntent.TRANSACTIONAL, 0.5, "Search", "evidence", ""
            ));
    }

    @Test
    @DisplayName("KeywordFinding rejects null intent")
    void shouldRejectNullKeywordIntent() {
        assertThatThrownBy(() -> new KeywordFinding(
            "keyword", null, 0.5, "Search", "evidence", "src"
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("CompetitorFinding rejects blank fields")
    void shouldRejectBlankCompetitorFindingFields() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorFinding(
                " ", "Has testimonials", "targets service", false, "user-provided"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorFinding(
                "rival.com", "", "targets service", false, "user-provided"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorFinding(
                "rival.com", "Has testimonials", " ", false, "user-provided"
            ));

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new CompetitorFinding(
                "rival.com", "Has testimonials", "targets service", false, ""
            ));
    }
}
