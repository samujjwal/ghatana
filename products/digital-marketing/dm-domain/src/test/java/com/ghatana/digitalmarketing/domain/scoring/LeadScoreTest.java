package com.ghatana.digitalmarketing.domain.scoring;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("LeadScore domain entity")
class LeadScoreTest {

    private static final DmWorkspaceId WORKSPACE = DmWorkspaceId.of("ws-1");
    private static final Instant NOW = Instant.now();

    private static LeadScore.Builder validBuilder() {
        return LeadScore.builder()
                .scoreId("score-1")
                .workspaceId(WORKSPACE)
                .score(72)
                .grade(LeadGrade.B)
                .dimensions(List.of(
                        new ScoreDimension("fit", 30, "Industry match"),
                        new ScoreDimension("urgency", 20, "Active search intent")
                ))
                .confidence(0.85)
                .requiresHumanReview(false)
                .recommendedNextAction("Send proposal")
                .modelVersion("v1.0")
                .scoredAt(NOW)
                .scoredBy("system");
    }

    @Test
    @DisplayName("valid build produces correct field values")
    void shouldBuildValidLeadScore() {
        LeadScore ls = validBuilder().build();
        assertThat(ls.getScoreId()).isEqualTo("score-1");
        assertThat(ls.getWorkspaceId()).isEqualTo(WORKSPACE);
        assertThat(ls.getScore()).isEqualTo(72);
        assertThat(ls.getGrade()).isEqualTo(LeadGrade.B);
        assertThat(ls.getDimensions()).hasSize(2);
        assertThat(ls.getConfidence()).isEqualTo(0.85);
        assertThat(ls.isRequiresHumanReview()).isFalse();
        assertThat(ls.getRecommendedNextAction()).isEqualTo("Send proposal");
        assertThat(ls.getModelVersion()).isEqualTo("v1.0");
        assertThat(ls.getScoredAt()).isEqualTo(NOW);
        assertThat(ls.getScoredBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("null dimensions defaults to empty list")
    void shouldDefaultNullDimensionsToEmptyList() {
        LeadScore ls = validBuilder().dimensions(null).build();
        assertThat(ls.getDimensions()).isEmpty();
    }

    @Test
    @DisplayName("requires human review when confidence is low")
    void shouldSupportRequiresHumanReview() {
        LeadScore ls = validBuilder().confidence(0.4).requiresHumanReview(true).build();
        assertThat(ls.isRequiresHumanReview()).isTrue();
        assertThat(ls.getConfidence()).isEqualTo(0.4);
    }

    @Test
    @DisplayName("rejects blank scoreId")
    void shouldRejectBlankScoreId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().scoreId("  ").build());
    }

    @Test
    @DisplayName("rejects null workspaceId")
    void shouldRejectNullWorkspaceId() {
        assertThatNullPointerException()
                .isThrownBy(() -> validBuilder().workspaceId(null).build());
    }

    @Test
    @DisplayName("rejects score below 0")
    void shouldRejectNegativeScore() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().score(-1).build());
    }

    @Test
    @DisplayName("rejects score above 100")
    void shouldRejectScoreAbove100() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().score(101).build());
    }

    @Test
    @DisplayName("accepts boundary score values 0 and 100")
    void shouldAcceptBoundaryScores() {
        assertThat(validBuilder().score(0).grade(LeadGrade.D).build().getScore()).isZero();
        assertThat(validBuilder().score(100).grade(LeadGrade.A).build().getScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("rejects null grade")
    void shouldRejectNullGrade() {
        assertThatNullPointerException()
                .isThrownBy(() -> validBuilder().grade(null).build());
    }

    @Test
    @DisplayName("rejects confidence below 0.0")
    void shouldRejectNegativeConfidence() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().confidence(-0.01).build());
    }

    @Test
    @DisplayName("rejects confidence above 1.0")
    void shouldRejectConfidenceAbove1() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().confidence(1.01).build());
    }

    @Test
    @DisplayName("rejects blank recommendedNextAction")
    void shouldRejectBlankRecommendedNextAction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().recommendedNextAction("").build());
    }

    @Test
    @DisplayName("rejects blank modelVersion")
    void shouldRejectBlankModelVersion() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().modelVersion("  ").build());
    }

    @Test
    @DisplayName("rejects null scoredAt")
    void shouldRejectNullScoredAt() {
        assertThatNullPointerException()
                .isThrownBy(() -> validBuilder().scoredAt(null).build());
    }

    @Test
    @DisplayName("rejects blank scoredBy")
    void shouldRejectBlankScoredBy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().scoredBy("").build());
    }

    @Test
    @DisplayName("ScoreDimension rejects blank dimension name")
    void scoreDimensionShouldRejectBlankDimension() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreDimension("", 10, "rationale"));
    }

    @Test
    @DisplayName("ScoreDimension rejects negative points")
    void scoreDimensionShouldRejectNegativePoints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreDimension("fit", -1, "rationale"));
    }

    @Test
    @DisplayName("ScoreDimension rejects points above 100")
    void scoreDimensionShouldRejectPointsAbove100() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreDimension("fit", 101, "rationale"));
    }

    @Test
    @DisplayName("ScoreDimension rejects blank rationale")
    void scoreDimensionShouldRejectBlankRationale() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ScoreDimension("fit", 10, "   "));
    }

    @Test
    @DisplayName("ScoreDimension rejects null dimension name")
    void scoreDimensionShouldRejectNullDimension() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ScoreDimension(null, 10, "rationale"));
    }

    @Test
    @DisplayName("ScoreDimension rejects null rationale")
    void scoreDimensionShouldRejectNullRationale() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ScoreDimension("fit", 10, null));
    }
}
