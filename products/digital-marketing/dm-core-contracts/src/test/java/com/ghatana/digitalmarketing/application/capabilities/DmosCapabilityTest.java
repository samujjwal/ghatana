package com.ghatana.digitalmarketing.application.capabilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmosCapability")
class DmosCapabilityTest {

    @Test
    @DisplayName("exports expected canonical capability keys")
    void exportsCanonicalCapabilityKeys() {
        Set<String> keys = Arrays.stream(DmosCapability.values())
            .map(DmosCapability::getKey)
            .filter(key -> key != null)
            .collect(Collectors.toSet());

        assertThat(keys).contains(
            "dmos.advanced_channels",
            "dmos.agency",
            "dmos.ai_optimization",
            "dmos.budget",
            "dmos.campaigns",
            "dmos.localization",
            "dmos.market_research",
            "dmos.reporting",
            "dmos.self_marketing",
            "dmos.strategy"
        );
        assertThat(DmosCapability.NONE.getKey()).isNull();
    }

    @Test
    @DisplayName("isDefined validates known and unknown keys")
    void validatesKnownAndUnknownKeys() {
        assertThat(DmosCapability.isDefined("dmos.campaigns")).isTrue();
        assertThat(DmosCapability.isDefined("dmos.ai_optimization")).isTrue();

        assertThat(DmosCapability.isDefined("unknown.capability")).isFalse();
        assertThat(DmosCapability.isDefined(""))
            .isFalse();
        assertThat(DmosCapability.isDefined("   "))
            .isFalse();
        assertThat(DmosCapability.isDefined(null))
            .isFalse();
    }
}
