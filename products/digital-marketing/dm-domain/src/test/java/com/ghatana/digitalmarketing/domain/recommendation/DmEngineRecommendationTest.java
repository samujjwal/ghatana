package com.ghatana.digitalmarketing.domain.recommendation;

import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendation;
import com.ghatana.digitalmarketing.domain.recommendation.DmEngineRecommendationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmEngineRecommendation domain entity")
class DmEngineRecommendationTest {

    private DmEngineRecommendation valid() {
        return DmEngineRecommendation.builder()
            .id("rec-1").tenantId("t1").workspaceId("ws1")
            .recommendationType("BUDGET_INCREASE").rationale("Low impressions")
            .confidenceScore(0.85).status(DmEngineRecommendationStatus.ACTIVE)
            .supportingMetricKeys(List.of("impressions")).suggestedActions(List.of("increase budget"))
            .createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmEngineRecommendation r = valid();
        assertThat(r.getId()).isEqualTo("rec-1");
        assertThat(r.getConfidenceScore()).isEqualTo(0.85);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("").tenantId("t").recommendationType("t")
                .rationale("r").confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE)
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects confidence score below 0")
    void shouldRejectNegativeScore() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("t").recommendationType("t")
                .rationale("r").confidenceScore(-0.1).status(DmEngineRecommendationStatus.ACTIVE)
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects confidence score above 1")
    void shouldRejectScoreAboveOne() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("t").recommendationType("t")
                .rationale("r").confidenceScore(1.1).status(DmEngineRecommendationStatus.ACTIVE)
                .createdAt(Instant.now()).build());
    }

    @Test @DisplayName("isExpired returns false when no expiry")
    void shouldNotBeExpiredWithoutExpiry() {
        assertThat(valid().isExpired()).isFalse();
    }

    @Test @DisplayName("isExpired returns true when past expiry")
    void shouldBeExpiredWhenPast() {
        DmEngineRecommendation expired = DmEngineRecommendation.builder()
            .id("rec-2").tenantId("t1").recommendationType("t").rationale("r")
            .confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE)
            .expiresAt(Instant.now().minusSeconds(1)).createdAt(Instant.now()).build();
        assertThat(expired.isExpired()).isTrue();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id(null).tenantId("t").recommendationType("t")
                .confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("").recommendationType("t")
                .confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("blank recommendationType throws")
    void shouldRejectBlankRecommendationType() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("t").recommendationType("")
                .confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null status throws")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("t").recommendationType("t")
                .confidenceScore(0.5).status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null createdAt throws")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmEngineRecommendation.builder().id("x").tenantId("t").recommendationType("t")
                .confidenceScore(0.5).status(DmEngineRecommendationStatus.ACTIVE).createdAt(null).build());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmEngineRecommendation r = valid();
        assertThat(r.getTenantId()).isEqualTo("t1");
        assertThat(r.getRecommendationType()).isEqualTo("BUDGET_INCREASE");
        assertThat(r.getConfidenceScore()).isEqualTo(0.85);
        assertThat(r.getStatus()).isEqualTo(DmEngineRecommendationStatus.ACTIVE);
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.toString()).contains("rec-1");
    }
}
