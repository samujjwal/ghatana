package com.ghatana.digitalmarketing.domain.narrative;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmNarrativeReview domain entity")
class DmNarrativeReviewTest {

    private DmNarrativeReview valid() {
        Instant now = Instant.now();
        return DmNarrativeReview.builder()
            .id("nr-1").tenantId("t1").workspaceId("ws1")
            .periodType(DmNarrativePeriodType.WEEKLY)
            .periodStart(now.minusSeconds(86400 * 7)).periodEnd(now)
            .narrativeText("Good week.").keyInsights("High CTR observed")
            .recommendations("Increase budget")
            .status(DmNarrativeReviewStatus.READY)
            .generatedAt(now).createdAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmNarrativeReview r = valid();
        assertThat(r.getId()).isEqualTo("nr-1");
        assertThat(r.getStatus()).isEqualTo(DmNarrativeReviewStatus.READY);
        assertThat(r.getPeriodType()).isEqualTo(DmNarrativePeriodType.WEEKLY);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmNarrativeReview.builder().id("").tenantId("t")
                .periodType(DmNarrativePeriodType.MONTHLY)
                .narrativeText("text").keyInsights("insights").recommendations("recs")
                .status(DmNarrativeReviewStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("keyInsights is accessible")
    void shouldAccessKeyInsights() {
        assertThat(valid().getKeyInsights()).isEqualTo("High CTR observed");
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() {
        assertThat(valid()).isNotEqualTo(null);
    }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() {
        assertThat(valid()).isNotEqualTo("x");
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmNarrativeReview r = valid();
        assertThat(r.getTenantId()).isEqualTo("t1");
        assertThat(r.getWorkspaceId()).isEqualTo("ws1");
        assertThat(r.getNarrativeText()).isEqualTo("Good week.");
        assertThat(r.getRecommendations()).isEqualTo("Increase budget");
        assertThat(r.getGeneratedAt()).isNotNull();
        assertThat(r.getCreatedAt()).isNotNull();
        assertThat(r.toString()).contains("nr-1");
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmNarrativeReview.builder().id("x").tenantId("t")
                .periodType(DmNarrativePeriodType.MONTHLY)
                .narrativeText("t").keyInsights("i").recommendations("r")
                .status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null periodType")
    void shouldRejectNullPeriodType() {
        assertThatNullPointerException().isThrownBy(() ->
            DmNarrativeReview.builder().id("x").tenantId("t")
                .periodType(null)
                .narrativeText("t").keyInsights("i").recommendations("r")
                .status(DmNarrativeReviewStatus.PENDING).createdAt(Instant.now()).build());
    }
}

