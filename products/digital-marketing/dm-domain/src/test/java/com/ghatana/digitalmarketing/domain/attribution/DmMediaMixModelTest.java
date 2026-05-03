package com.ghatana.digitalmarketing.domain.attribution;

import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModel.DmChannelContribution;
import com.ghatana.digitalmarketing.domain.attribution.DmMediaMixModelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmMediaMixModel domain entity")
class DmMediaMixModelTest {

    private DmMediaMixModel valid() {
        Instant now = Instant.now();
        return DmMediaMixModel.builder()
            .id("mmm-1").tenantId("t1").workspaceId("ws1")
            .modelName("Q1 2026 Model")
            .channelIds(List.of("google", "meta"))
            .contributions(List.of(
                new DmChannelContribution("google", 0.6, 2.5),
                new DmChannelContribution("meta", 0.4, 1.8)))
            .dataFrom(now.minusSeconds(86400 * 90)).dataTo(now)
            .status(DmMediaMixModelStatus.READY)
            .rSquared(0.92).createdAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmMediaMixModel m = valid();
        assertThat(m.getId()).isEqualTo("mmm-1");
        assertThat(m.getStatus()).isEqualTo(DmMediaMixModelStatus.READY);
        assertThat(m.getContributions()).hasSize(2);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id("").tenantId("t").modelName("m")
                .channelIds(List.of()).contributions(List.of())
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("DmChannelContribution rejects contributionFraction below 0")
    void shouldRejectNegativeFraction() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DmChannelContribution("ch1", -0.1, 1.0));
    }

    @Test @DisplayName("DmChannelContribution rejects contributionFraction above 1")
    void shouldRejectFractionAboveOne() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DmChannelContribution("ch1", 1.1, 1.0));
    }

    @Test @DisplayName("channelIds list is immutable")
    void shouldHaveImmutableChannelIds() {
        assertThat(valid().getChannelIds()).isUnmodifiable();
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

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmMediaMixModel m = valid();
        assertThat(m.getTenantId()).isEqualTo("t1");
        assertThat(m.getWorkspaceId()).isEqualTo("ws1");
        assertThat(m.getModelName()).isEqualTo("Q1 2026 Model");
        assertThat(m.getChannelIds()).hasSize(2);
        assertThat(m.getRSquared()).isEqualTo(0.92);
        assertThat(m.getDataFrom()).isNotNull();
        assertThat(m.getDataTo()).isNotNull();
        assertThat(m.getCreatedAt()).isNotNull();
        assertThat(m.toString()).contains("mmm-1");
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId("t").modelName("m")
                .status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId("t").modelName("m")
                .status(DmMediaMixModelStatus.PENDING).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null id")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id(null).tenantId("t").modelName("m")
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null tenantId")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId(null).modelName("m")
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank tenantId")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId("").modelName("m")
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null modelName")
    void shouldRejectNullModelName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId("t").modelName(null)
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank modelName")
    void shouldRejectBlankModelName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmMediaMixModel.builder().id("x").tenantId("t").modelName("")
                .status(DmMediaMixModelStatus.PENDING).createdAt(Instant.now()).build());
    }
}
