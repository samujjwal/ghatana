package com.ghatana.datacloud.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for the {@link MemoryTier} enum.
 *
 * <p>Covers salience routing, promotion/demotion, comparators,
 * TTL defaults, eviction priority, and boundary conditions.
 */
class MemoryTierTest {

    // ═══ Salience-based tier routing ═══

    @ParameterizedTest
    @CsvSource({ // GH-90000
        "0.0,  ARCHIVE",
        "0.1,  ARCHIVE",
        "0.19, ARCHIVE",
        "0.2,  COLD",
        "0.35, COLD",
        "0.49, COLD",
        "0.5,  WARM",
        "0.65, WARM",
        "0.79, WARM",
        "0.8,  HOT",
        "0.9,  HOT",
        "1.0,  HOT"
    })
    @DisplayName("fromSalienceScore maps score to correct tier [GH-90000]")
    void fromSalienceScore_mapsCorrectly(double score, MemoryTier expected) { // GH-90000
        assertThat(MemoryTier.fromSalienceScore(score)).isEqualTo(expected); // GH-90000
    }

    @Test
    @DisplayName("fromSalienceScore rejects negative score [GH-90000]")
    void fromSalienceScore_rejectsNegative() { // GH-90000
        assertThatThrownBy(() -> MemoryTier.fromSalienceScore(-0.1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("between 0.0 and 1.0 [GH-90000]");
    }

    @Test
    @DisplayName("fromSalienceScore rejects score > 1.0 [GH-90000]")
    void fromSalienceScore_rejectsAboveOne() { // GH-90000
        assertThatThrownBy(() -> MemoryTier.fromSalienceScore(1.01)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ═══ acceptsSalience ═══

    @Test
    @DisplayName("HOT accepts [0.8, 1.0) [GH-90000]")
    void hotAcceptsSalience() { // GH-90000
        assertThat(MemoryTier.HOT.acceptsSalience(0.8)).isTrue(); // GH-90000
        assertThat(MemoryTier.HOT.acceptsSalience(0.9)).isTrue(); // GH-90000
        assertThat(MemoryTier.HOT.acceptsSalience(0.79)).isFalse(); // GH-90000
        assertThat(MemoryTier.HOT.acceptsSalience(1.0)).isFalse(); // exclusive upper bound // GH-90000
    }

    @Test
    @DisplayName("ARCHIVE accepts [0.0, 0.2) [GH-90000]")
    void archiveAcceptsSalience() { // GH-90000
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.0)).isTrue(); // GH-90000
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.1)).isTrue(); // GH-90000
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.2)).isFalse(); // GH-90000
    }

    // ═══ Promotion / Demotion ═══

    @Test
    @DisplayName("Demotion chain: HOT→WARM→COLD→ARCHIVE→ARCHIVE [GH-90000]")
    void demotionChain() { // GH-90000
        assertThat(MemoryTier.HOT.demote()).isEqualTo(MemoryTier.WARM); // GH-90000
        assertThat(MemoryTier.WARM.demote()).isEqualTo(MemoryTier.COLD); // GH-90000
        assertThat(MemoryTier.COLD.demote()).isEqualTo(MemoryTier.ARCHIVE); // GH-90000
        assertThat(MemoryTier.ARCHIVE.demote()).isEqualTo(MemoryTier.ARCHIVE); // GH-90000
    }

    @Test
    @DisplayName("Promotion chain: ARCHIVE→COLD→WARM→HOT→HOT [GH-90000]")
    void promotionChain() { // GH-90000
        assertThat(MemoryTier.ARCHIVE.promote()).isEqualTo(MemoryTier.COLD); // GH-90000
        assertThat(MemoryTier.COLD.promote()).isEqualTo(MemoryTier.WARM); // GH-90000
        assertThat(MemoryTier.WARM.promote()).isEqualTo(MemoryTier.HOT); // GH-90000
        assertThat(MemoryTier.HOT.promote()).isEqualTo(MemoryTier.HOT); // GH-90000
    }

    // ═══ shouldPromote / shouldDemote ═══

    @Test
    @DisplayName("shouldPromote returns true when salience exceeds max threshold [GH-90000]")
    void shouldPromote_exceedsMaxThreshold() { // GH-90000
        assertThat(MemoryTier.WARM.shouldPromote(0.85)).isTrue(); // 0.85 >= WARM.max(0.8) // GH-90000
        assertThat(MemoryTier.COLD.shouldPromote(0.6)).isTrue();  // 0.6 >= COLD.max(0.5) // GH-90000
        assertThat(MemoryTier.ARCHIVE.shouldPromote(0.3)).isTrue(); // 0.3 >= ARCHIVE.max(0.2) // GH-90000
    }

    @Test
    @DisplayName("shouldPromote returns false when salience within tier range [GH-90000]")
    void shouldPromote_withinRange() { // GH-90000
        assertThat(MemoryTier.WARM.shouldPromote(0.6)).isFalse(); // GH-90000
        assertThat(MemoryTier.COLD.shouldPromote(0.3)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("shouldPromote returns false for HOT (already highest) [GH-90000]")
    void shouldPromote_falseForHot() { // GH-90000
        assertThat(MemoryTier.HOT.shouldPromote(1.0)).isFalse(); // GH-90000
        assertThat(MemoryTier.HOT.shouldPromote(0.99)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("shouldDemote returns true when salience below min threshold [GH-90000]")
    void shouldDemote_belowMinThreshold() { // GH-90000
        assertThat(MemoryTier.HOT.shouldDemote(0.7)).isTrue(); // 0.7 < HOT.min(0.8) // GH-90000
        assertThat(MemoryTier.WARM.shouldDemote(0.4)).isTrue(); // 0.4 < WARM.min(0.5) // GH-90000
        assertThat(MemoryTier.COLD.shouldDemote(0.1)).isTrue(); // 0.1 < COLD.min(0.2) // GH-90000
    }

    @Test
    @DisplayName("shouldDemote returns false for ARCHIVE (already lowest) [GH-90000]")
    void shouldDemote_falseForArchive() { // GH-90000
        assertThat(MemoryTier.ARCHIVE.shouldDemote(0.0)).isFalse(); // GH-90000
    }

    // ═══ Priority comparison ═══

    @Test
    @DisplayName("HOT has highest priority [GH-90000]")
    void hotHasHighestPriority() { // GH-90000
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.WARM)).isTrue(); // GH-90000
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.COLD)).isTrue(); // GH-90000
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.ARCHIVE)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("ARCHIVE has lowest priority [GH-90000]")
    void archiveHasLowestPriority() { // GH-90000
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.HOT)).isFalse(); // GH-90000
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.WARM)).isFalse(); // GH-90000
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.COLD)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Same tier is not higher priority than itself [GH-90000]")
    void sameTierNotHigherPriority() { // GH-90000
        for (MemoryTier tier : MemoryTier.values()) { // GH-90000
            assertThat(tier.isHigherPriorityThan(tier)).isFalse(); // GH-90000
        }
    }

    // ═══ Comparators ═══

    @Test
    @DisplayName("BY_EVICTION_PRIORITY sorts HOT first, ARCHIVE last [GH-90000]")
    void byEvictionPriority_order() { // GH-90000
        List<MemoryTier> sorted = Arrays.stream(MemoryTier.values()) // GH-90000
                .sorted(MemoryTier.BY_EVICTION_PRIORITY) // GH-90000
                .toList(); // GH-90000
        assertThat(sorted).containsExactly( // GH-90000
                MemoryTier.HOT, MemoryTier.WARM, MemoryTier.COLD, MemoryTier.ARCHIVE);
    }

    @Test
    @DisplayName("BY_SALIENCE_ASC sorts ARCHIVE first, HOT last [GH-90000]")
    void bySalienceAsc_order() { // GH-90000
        List<MemoryTier> sorted = Arrays.stream(MemoryTier.values()) // GH-90000
                .sorted(MemoryTier.BY_SALIENCE_ASC) // GH-90000
                .toList(); // GH-90000
        assertThat(sorted).containsExactly( // GH-90000
                MemoryTier.ARCHIVE, MemoryTier.COLD, MemoryTier.WARM, MemoryTier.HOT);
    }

    // ═══ Default TTL and properties ═══

    @ParameterizedTest
    @EnumSource(MemoryTier.class) // GH-90000
    @DisplayName("Every tier has non-null positive defaultTtl [GH-90000]")
    void everyTierHasPositiveTtl(MemoryTier tier) { // GH-90000
        assertThat(tier.getDefaultTtl()).isNotNull(); // GH-90000
        assertThat(tier.getDefaultTtl()).isPositive(); // GH-90000
    }

    @Test
    @DisplayName("HOT has shortest TTL, ARCHIVE has longest [GH-90000]")
    void ttlOrdering() { // GH-90000
        assertThat(MemoryTier.HOT.getDefaultTtl()).isLessThan(MemoryTier.WARM.getDefaultTtl()); // GH-90000
        assertThat(MemoryTier.WARM.getDefaultTtl()).isLessThan(MemoryTier.COLD.getDefaultTtl()); // GH-90000
        assertThat(MemoryTier.COLD.getDefaultTtl()).isLessThan(MemoryTier.ARCHIVE.getDefaultTtl()); // GH-90000
    }

    @Test
    @DisplayName("Storage prefixes are unique across tiers [GH-90000]")
    void storagePrefixesAreUnique() { // GH-90000
        List<String> prefixes = Arrays.stream(MemoryTier.values()) // GH-90000
                .map(MemoryTier::getStoragePrefix) // GH-90000
                .toList(); // GH-90000
        assertThat(prefixes).doesNotHaveDuplicates(); // GH-90000
    }

    // ═══ Enum completeness ═══

    @Test
    @DisplayName("Exactly 4 memory tiers exist [GH-90000]")
    void exactlyFourTiers() { // GH-90000
        assertThat(MemoryTier.values()).hasSize(4); // GH-90000
    }

    @Test
    @DisplayName("Salience thresholds cover [0.0, 1.0] without gaps [GH-90000]")
    void salienceThresholdsCoverEntireRange() { // GH-90000
        // Every value from 0.0 to 1.0 in increments of 0.01 should map to some tier
        for (int i = 0; i <= 100; i++) { // GH-90000
            final double score = i / 100.0;
            assertThatCode(() -> MemoryTier.fromSalienceScore(score)) // GH-90000
                    .as("salience %.2f should map to a tier", score) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    @Test
    @DisplayName("toString contains tier name and priority [GH-90000]")
    void toStringContainsInfo() { // GH-90000
        String s = MemoryTier.HOT.toString(); // GH-90000
        assertThat(s).contains("HOT [GH-90000]").contains("1 [GH-90000]");
    }
}
