package com.ghatana.datacloud.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultMemoryTierRouter")
class DefaultMemoryTierRouterTest {

    @Test
    @DisplayName("applies custom policies during construction")
    void appliesCustomPoliciesDuringConstruction() { 
        TierPolicy hotPolicy = TierPolicy.defaultFor(MemoryTier.HOT) 
                .toBuilder() 
                .maxRecords(42) 
                .build(); 

        DefaultMemoryTierRouter<com.ghatana.datacloud.EntityRecord> router =
                new DefaultMemoryTierRouter<>(Map.of(MemoryTier.HOT, hotPolicy)); 

        assertThat(router.getPolicy(MemoryTier.HOT).getMaxRecords()).isEqualTo(42); 
    }
}
