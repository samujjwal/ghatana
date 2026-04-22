package com.ghatana.datacloud;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the {@link StorageTier} enum.
 *
 * <p>Covers ordering, navigation, boundary methods, and default tier.
 */
class StorageTierTest {

    // ═══ Ordering ═══

    @Test
    @DisplayName("HOT is higher than WARM, COOL, COLD [GH-90000]")
    void hotIsHigherThanAll() { // GH-90000
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue(); // GH-90000
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COOL)).isTrue(); // GH-90000
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COLD)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("COLD is lower than HOT, WARM, COOL [GH-90000]")
    void coldIsLowerThanAll() { // GH-90000
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.HOT)).isTrue(); // GH-90000
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.WARM)).isTrue(); // GH-90000
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.COOL)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Same tier is neither higher nor lower [GH-90000]")
    void sameTierNotHigherOrLower() { // GH-90000
        for (StorageTier tier : StorageTier.values()) { // GH-90000
            assertThat(tier.isHigherThan(tier)).isFalse(); // GH-90000
            assertThat(tier.isLowerThan(tier)).isFalse(); // GH-90000
        }
    }

    @Test
    @DisplayName("Ordering is transitive: HOT > WARM > COOL > COLD [GH-90000]")
    void orderingIsTransitive() { // GH-90000
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue(); // GH-90000
        assertThat(StorageTier.WARM.isHigherThan(StorageTier.COOL)).isTrue(); // GH-90000
        assertThat(StorageTier.COOL.isHigherThan(StorageTier.COLD)).isTrue(); // GH-90000
    }

    // ═══ Navigation ═══

    @Test
    @DisplayName("nextLowerTier traverses HOT→WARM→COOL→COLD→COLD [GH-90000]")
    void nextLowerTier_fullTraversal() { // GH-90000
        assertThat(StorageTier.HOT.nextLowerTier()).isEqualTo(StorageTier.WARM); // GH-90000
        assertThat(StorageTier.WARM.nextLowerTier()).isEqualTo(StorageTier.COOL); // GH-90000
        assertThat(StorageTier.COOL.nextLowerTier()).isEqualTo(StorageTier.COLD); // GH-90000
        assertThat(StorageTier.COLD.nextLowerTier()).isEqualTo(StorageTier.COLD); // GH-90000
    }

    @Test
    @DisplayName("nextHigherTier traverses COLD→COOL→WARM→HOT→HOT [GH-90000]")
    void nextHigherTier_fullTraversal() { // GH-90000
        assertThat(StorageTier.COLD.nextHigherTier()).isEqualTo(StorageTier.COOL); // GH-90000
        assertThat(StorageTier.COOL.nextHigherTier()).isEqualTo(StorageTier.WARM); // GH-90000
        assertThat(StorageTier.WARM.nextHigherTier()).isEqualTo(StorageTier.HOT); // GH-90000
        assertThat(StorageTier.HOT.nextHigherTier()).isEqualTo(StorageTier.HOT); // GH-90000
    }

    // ═══ Boundary methods ═══

    @Test
    @DisplayName("Only HOT is hottest [GH-90000]")
    void onlyHotIsHottest() { // GH-90000
        assertThat(StorageTier.HOT.isHottest()).isTrue(); // GH-90000
        assertThat(StorageTier.WARM.isHottest()).isFalse(); // GH-90000
        assertThat(StorageTier.COOL.isHottest()).isFalse(); // GH-90000
        assertThat(StorageTier.COLD.isHottest()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Only COLD is coldest [GH-90000]")
    void onlyColdIsColdest() { // GH-90000
        assertThat(StorageTier.COLD.isColdest()).isTrue(); // GH-90000
        assertThat(StorageTier.HOT.isColdest()).isFalse(); // GH-90000
        assertThat(StorageTier.WARM.isColdest()).isFalse(); // GH-90000
        assertThat(StorageTier.COOL.isColdest()).isFalse(); // GH-90000
    }

    // ═══ Default tier ═══

    @Test
    @DisplayName("Default tier is WARM [GH-90000]")
    void defaultTierIsWarm() { // GH-90000
        assertThat(StorageTier.defaultTier()).isEqualTo(StorageTier.WARM); // GH-90000
    }

    // ═══ Values completeness ═══

    @Test
    @DisplayName("Enum has exactly 4 tiers [GH-90000]")
    void exactlyFourTiers() { // GH-90000
        assertThat(StorageTier.values()).hasSize(4); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(StorageTier.class) // GH-90000
    @DisplayName("nextLowerTier never returns null [GH-90000]")
    void nextLowerTier_neverNull(StorageTier tier) { // GH-90000
        assertThat(tier.nextLowerTier()).isNotNull(); // GH-90000
    }

    @ParameterizedTest
    @EnumSource(StorageTier.class) // GH-90000
    @DisplayName("nextHigherTier never returns null [GH-90000]")
    void nextHigherTier_neverNull(StorageTier tier) { // GH-90000
        assertThat(tier.nextHigherTier()).isNotNull(); // GH-90000
    }

    // ═══ Asymmetry ═══

    @Test
    @DisplayName("isHigherThan and isLowerThan are asymmetric [GH-90000]")
    void higherAndLowerAreAsymmetric() { // GH-90000
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COLD)).isTrue(); // GH-90000
        assertThat(StorageTier.COLD.isHigherThan(StorageTier.HOT)).isFalse(); // GH-90000
        assertThat(StorageTier.HOT.isLowerThan(StorageTier.COLD)).isFalse(); // GH-90000
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.HOT)).isTrue(); // GH-90000
    }
}
