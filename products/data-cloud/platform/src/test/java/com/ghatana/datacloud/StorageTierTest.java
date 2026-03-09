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
    @DisplayName("HOT is higher than WARM, COOL, COLD")
    void hotIsHigherThanAll() {
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue();
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COOL)).isTrue();
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COLD)).isTrue();
    }

    @Test
    @DisplayName("COLD is lower than HOT, WARM, COOL")
    void coldIsLowerThanAll() {
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.HOT)).isTrue();
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.WARM)).isTrue();
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.COOL)).isTrue();
    }

    @Test
    @DisplayName("Same tier is neither higher nor lower")
    void sameTierNotHigherOrLower() {
        for (StorageTier tier : StorageTier.values()) {
            assertThat(tier.isHigherThan(tier)).isFalse();
            assertThat(tier.isLowerThan(tier)).isFalse();
        }
    }

    @Test
    @DisplayName("Ordering is transitive: HOT > WARM > COOL > COLD")
    void orderingIsTransitive() {
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue();
        assertThat(StorageTier.WARM.isHigherThan(StorageTier.COOL)).isTrue();
        assertThat(StorageTier.COOL.isHigherThan(StorageTier.COLD)).isTrue();
    }

    // ═══ Navigation ═══

    @Test
    @DisplayName("nextLowerTier traverses HOT→WARM→COOL→COLD→COLD")
    void nextLowerTier_fullTraversal() {
        assertThat(StorageTier.HOT.nextLowerTier()).isEqualTo(StorageTier.WARM);
        assertThat(StorageTier.WARM.nextLowerTier()).isEqualTo(StorageTier.COOL);
        assertThat(StorageTier.COOL.nextLowerTier()).isEqualTo(StorageTier.COLD);
        assertThat(StorageTier.COLD.nextLowerTier()).isEqualTo(StorageTier.COLD);
    }

    @Test
    @DisplayName("nextHigherTier traverses COLD→COOL→WARM→HOT→HOT")
    void nextHigherTier_fullTraversal() {
        assertThat(StorageTier.COLD.nextHigherTier()).isEqualTo(StorageTier.COOL);
        assertThat(StorageTier.COOL.nextHigherTier()).isEqualTo(StorageTier.WARM);
        assertThat(StorageTier.WARM.nextHigherTier()).isEqualTo(StorageTier.HOT);
        assertThat(StorageTier.HOT.nextHigherTier()).isEqualTo(StorageTier.HOT);
    }

    // ═══ Boundary methods ═══

    @Test
    @DisplayName("Only HOT is hottest")
    void onlyHotIsHottest() {
        assertThat(StorageTier.HOT.isHottest()).isTrue();
        assertThat(StorageTier.WARM.isHottest()).isFalse();
        assertThat(StorageTier.COOL.isHottest()).isFalse();
        assertThat(StorageTier.COLD.isHottest()).isFalse();
    }

    @Test
    @DisplayName("Only COLD is coldest")
    void onlyColdIsColdest() {
        assertThat(StorageTier.COLD.isColdest()).isTrue();
        assertThat(StorageTier.HOT.isColdest()).isFalse();
        assertThat(StorageTier.WARM.isColdest()).isFalse();
        assertThat(StorageTier.COOL.isColdest()).isFalse();
    }

    // ═══ Default tier ═══

    @Test
    @DisplayName("Default tier is WARM")
    void defaultTierIsWarm() {
        assertThat(StorageTier.defaultTier()).isEqualTo(StorageTier.WARM);
    }

    // ═══ Values completeness ═══

    @Test
    @DisplayName("Enum has exactly 4 tiers")
    void exactlyFourTiers() {
        assertThat(StorageTier.values()).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(StorageTier.class)
    @DisplayName("nextLowerTier never returns null")
    void nextLowerTier_neverNull(StorageTier tier) {
        assertThat(tier.nextLowerTier()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(StorageTier.class)
    @DisplayName("nextHigherTier never returns null")
    void nextHigherTier_neverNull(StorageTier tier) {
        assertThat(tier.nextHigherTier()).isNotNull();
    }

    // ═══ Asymmetry ═══

    @Test
    @DisplayName("isHigherThan and isLowerThan are asymmetric")
    void higherAndLowerAreAsymmetric() {
        assertThat(StorageTier.HOT.isHigherThan(StorageTier.COLD)).isTrue();
        assertThat(StorageTier.COLD.isHigherThan(StorageTier.HOT)).isFalse();
        assertThat(StorageTier.HOT.isLowerThan(StorageTier.COLD)).isFalse();
        assertThat(StorageTier.COLD.isLowerThan(StorageTier.HOT)).isTrue();
    }
}
