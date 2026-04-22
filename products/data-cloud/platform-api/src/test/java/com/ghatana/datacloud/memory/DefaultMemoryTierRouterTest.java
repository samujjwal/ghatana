package com.ghatana.datacloud.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultMemoryTierRouter [GH-90000]")
class DefaultMemoryTierRouterTest {

    @Test
    @DisplayName("applies custom policies during construction [GH-90000]")
    void appliesCustomPoliciesDuringConstruction() { // GH-90000
        TierPolicy hotPolicy = TierPolicy.defaultFor(MemoryTier.HOT) // GH-90000
                .toBuilder() // GH-90000
                .maxRecords(42) // GH-90000
                .build(); // GH-90000

        DefaultMemoryTierRouter<com.ghatana.datacloud.EntityRecord> router =
                new DefaultMemoryTierRouter<>(Map.of(MemoryTier.HOT, hotPolicy)); // GH-90000

        assertThat(router.getPolicy(MemoryTier.HOT).getMaxRecords()).isEqualTo(42); // GH-90000
    }
}
