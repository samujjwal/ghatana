package com.ghatana.digitalmarketing.domain.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("DmBudgetAlert domain entity")
class DmBudgetAlertTest {

    private DmBudgetAlert valid() {
        return DmBudgetAlert.builder()
            .id("alert-1").tenantId("t1").workspaceId("ws1").campaignId("c1")
            .totalBudgetMicros(10_000_000L).spentMicros(8_000_000L).pacingRatio(0.8)
            .level(DmBudgetAlertLevel.WARNING).message("80% spent")
            .acknowledged(false).firedAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmBudgetAlert a = valid();
        assertThat(a.getId()).isEqualTo("alert-1");
        assertThat(a.getLevel()).isEqualTo(DmBudgetAlertLevel.WARNING);
        assertThat(a.isAcknowledged()).isFalse();
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmBudgetAlert.builder().id("").tenantId("t").campaignId("c")
                .totalBudgetMicros(100).spentMicros(50).pacingRatio(0.5)
                .level(DmBudgetAlertLevel.INFO).message("m").acknowledged(false)
                .firedAt(Instant.now()).build());
    }

    @Test @DisplayName("acknowledge transitions unacknowledged alert")
    void shouldAcknowledge() {
        DmBudgetAlert acknowledged = valid().acknowledge();
        assertThat(acknowledged.isAcknowledged()).isTrue();
        assertThat(acknowledged.getAcknowledgedAt()).isNotNull();
    }

    @Test @DisplayName("acknowledge rejects already acknowledged")
    void shouldNotAcknowledgeTwice() {
        assertThatIllegalStateException().isThrownBy(() -> valid().acknowledge().acknowledge());
    }

    @Test @DisplayName("getRemainingMicros computes correctly")
    void shouldComputeRemaining() {
        assertThat(valid().getRemainingMicros()).isEqualTo(2_000_000L);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
    }
}
