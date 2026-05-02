package com.ghatana.digitalmarketing.contracts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DmIdempotencyKey}.
 */
@DisplayName("DmIdempotencyKey")
class DmIdempotencyKeyTest {

    @Test
    @DisplayName("generate() produces a non-blank value")
    void shouldGenerateNonBlankKey() {
        DmIdempotencyKey key = DmIdempotencyKey.generate();
        assertThat(key.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("forCommand() is deterministic for same inputs")
    void shouldBeDeterministicForCommand() {
        DmIdempotencyKey a = DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-1");
        DmIdempotencyKey b = DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("forCommand() produces different keys for different command types")
    void shouldDifferByCommandType() {
        DmIdempotencyKey launch = DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-1");
        DmIdempotencyKey pause = DmIdempotencyKey.forCommand("PauseCampaign", "campaign-1");
        assertThat(launch).isNotEqualTo(pause);
    }

    @Test
    @DisplayName("forCommand() produces different keys for different entity IDs")
    void shouldDifferByEntityId() {
        DmIdempotencyKey a = DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-1");
        DmIdempotencyKey b = DmIdempotencyKey.forCommand("LaunchCampaign", "campaign-2");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("forCommand() rejects null commandType")
    void shouldRejectNullCommandType() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmIdempotencyKey.forCommand(null, "entity-1"));
    }

    @Test
    @DisplayName("forCommand() rejects null entityId")
    void shouldRejectNullEntityId() {
        assertThatNullPointerException()
            .isThrownBy(() -> DmIdempotencyKey.forCommand("LaunchCampaign", null));
    }

    @Test
    @DisplayName("of() rejects blank value")
    void shouldRejectBlank() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> DmIdempotencyKey.of("  "));
    }
}
