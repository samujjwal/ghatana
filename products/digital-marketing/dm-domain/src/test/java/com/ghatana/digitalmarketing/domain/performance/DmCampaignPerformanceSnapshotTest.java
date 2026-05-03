package com.ghatana.digitalmarketing.domain.performance;

import com.ghatana.digitalmarketing.domain.performance.DmCampaignPerformanceSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmCampaignPerformanceSnapshot domain entity")
class DmCampaignPerformanceSnapshotTest {

    private DmCampaignPerformanceSnapshot valid() {
        Instant now = Instant.now();
        return DmCampaignPerformanceSnapshot.builder()
            .id("snap-1").tenantId("t1").externalCampaignId("ext-1")
            .impressions(1000).clicks(50).conversions(5).costMicros(100_000L)
            .ctr(0.05).cpc(2000L).conversionRate(0.1)
            .periodStart(now.minusSeconds(86400)).periodEnd(now)
            .capturedAt(now).build();
    }

    @Test @DisplayName("builder creates valid snapshot")
    void shouldBuildValid() {
        DmCampaignPerformanceSnapshot s = valid();
        assertThat(s.getId()).isEqualTo("snap-1");
        assertThat(s.getClicks()).isEqualTo(50);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignPerformanceSnapshot.builder().id("").tenantId("t1")
                .externalCampaignId("e").capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank tenantId")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCampaignPerformanceSnapshot.builder().id("x").tenantId("")
                .externalCampaignId("e").capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null externalCampaignId")
    void shouldRejectNullExternalId() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCampaignPerformanceSnapshot.builder().id("x").tenantId("t")
                .externalCampaignId(null).capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null capturedAt")
    void shouldRejectNullCapturedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCampaignPerformanceSnapshot.builder().id("x").tenantId("t")
                .externalCampaignId("e").capturedAt(null).build());
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("toString contains id")
    void shouldContainId() {
        assertThat(valid().toString()).contains("snap-1");
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmCampaignPerformanceSnapshot s = valid();
        assertThat(s.getTenantId()).isEqualTo("t1");
        assertThat(s.getExternalCampaignId()).isEqualTo("ext-1");
        assertThat(s.getImpressions()).isEqualTo(1000);
        assertThat(s.getConversions()).isEqualTo(5);
        assertThat(s.getCostMicros()).isEqualTo(100_000L);
        assertThat(s.getCtr()).isEqualTo(0.05);
        assertThat(s.getCpc()).isEqualTo(2000L);
        assertThat(s.getConversionRate()).isEqualTo(0.1);
        assertThat(s.getPeriodStart()).isNotNull();
        assertThat(s.getPeriodEnd()).isNotNull();
        assertThat(s.getCapturedAt()).isNotNull();
    }
}
