package com.ghatana.datacloud.memory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
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
    @CsvSource({
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
    @DisplayName("fromSalienceScore maps score to correct tier")
    void fromSalienceScore_mapsCorrectly(double score, MemoryTier expected) {
        assertThat(MemoryTier.fromSalienceScore(score)).isEqualTo(expected);
    }

    @Test
    @DisplayName("fromSalienceScore rejects negative score")
    void fromSalienceScore_rejectsNegative() {
        assertThatThrownBy(() -> MemoryTier.fromSalienceScore(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    @Test
    @DisplayName("fromSalienceScore rejects score > 1.0")
    void fromSalienceScore_rejectsAboveOne() {
        assertThatThrownBy(() -> MemoryTier.fromSalienceScore(1.01))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ═══ acceptsSalience ═══

    @Test
    @DisplayName("HOT accepts [0.8, 1.0)")
    void hotAcceptsSalience() {
        assertThat(MemoryTier.HOT.acceptsSalience(0.8)).isTrue();
        assertThat(MemoryTier.HOT.acceptsSalience(0.9)).isTrue();
        assertThat(MemoryTier.HOT.acceptsSalience(0.79)).isFalse();
        assertThat(MemoryTier.HOT.acceptsSalience(1.0)).isFalse(); // exclusive upper bound
    }

    @Test
    @DisplayName("ARCHIVE accepts [0.0, 0.2)")
    void archiveAcceptsSalience() {
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.0)).isTrue();
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.1)).isTrue();
        assertThat(MemoryTier.ARCHIVE.acceptsSalience(0.2)).isFalse();
    }

    // ═══ Promotion / Demotion ═══

    @Test
    @DisplayName("Demotion chain: HOT→WARM→COLD→ARCHIVE→ARCHIVE")
    void demotionChain() {
        assertThat(MemoryTier.HOT.demote()).isEqualTo(MemoryTier.WARM);
        assertThat(MemoryTier.WARM.demote()).isEqualTo(MemoryTier.COLD);
        assertThat(MemoryTier.COLD.demote()).isEqualTo(MemoryTier.ARCHIVE);
        assertThat(MemoryTier.ARCHIVE.demote()).isEqualTo(MemoryTier.ARCHIVE);
    }

    @Test
    @DisplayName("Promotion chain: ARCHIVE→COLD→WARM→HOT→HOT")
    void promotionChain() {
        assertThat(MemoryTier.ARCHIVE.promote()).isEqualTo(MemoryTier.COLD);
        assertThat(MemoryTier.COLD.promote()).isEqualTo(MemoryTier.WARM);
        assertThat(MemoryTier.WARM.promote()).isEqualTo(MemoryTier.HOT);
        assertThat(MemoryTier.HOT.promote()).isEqualTo(MemoryTier.HOT);
    }

    // ═══ shouldPromote / shouldDemote ═══

    @Test
    @DisplayName("shouldPromote returns true when salience exceeds max threshold")
    void shouldPromote_exceedsMaxThreshold() {
        assertThat(MemoryTier.WARM.shouldPromote(0.85)).isTrue(); // 0.85 >= WARM.max(0.8)
        assertThat(MemoryTier.COLD.shouldPromote(0.6)).isTrue();  // 0.6 >= COLD.max(0.5)
        assertThat(MemoryTier.ARCHIVE.shouldPromote(0.3)).isTrue(); // 0.3 >= ARCHIVE.max(0.2)
    }

    @Test
    @DisplayName("shouldPromote returns false when salience within tier range")
    void shouldPromote_withinRange() {
        assertThat(MemoryTier.WARM.shouldPromote(0.6)).isFalse();
        assertThat(MemoryTier.COLD.shouldPromote(0.3)).isFalse();
    }

    @Test
    @DisplayName("shouldPromote returns false for HOT (already highest)")
    void shouldPromote_falseForHot() {
        assertThat(MemoryTier.HOT.shouldPromote(1.0)).isFalse();
        assertThat(MemoryTier.HOT.shouldPromote(0.99)).isFalse();
    }

    @Test
    @DisplayName("shouldDemote returns true when salience below min threshold")
    void shouldDemote_belowMinThreshold() {
        assertThat(MemoryTier.HOT.shouldDemote(0.7)).isTrue(); // 0.7 < HOT.min(0.8)
        assertThat(MemoryTier.WARM.shouldDemote(0.4)).isTrue(); // 0.4 < WARM.min(0.5)
        assertThat(MemoryTier.COLD.shouldDemote(0.1)).isTrue(); // 0.1 < COLD.min(0.2)
    }

    @Test
    @DisplayName("shouldDemote returns false for ARCHIVE (already lowest)")
    void shouldDemote_falseForArchive() {
        assertThat(MemoryTier.ARCHIVE.shouldDemote(0.0)).isFalse();
    }

    // ═══ Priority comparison ═══

    @Test
    @DisplayName("HOT has highest priority")
    void hotHasHighestPriority() {
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.WARM)).isTrue();
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.COLD)).isTrue();
        assertThat(MemoryTier.HOT.isHigherPriorityThan(MemoryTier.ARCHIVE)).isTrue();
    }

    @Test
    @DisplayName("ARCHIVE has lowest priority")
    void archiveHasLowestPriority() {
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.HOT)).isFalse();
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.WARM)).isFalse();
        assertThat(MemoryTier.ARCHIVE.isHigherPriorityThan(MemoryTier.COLD)).isFalse();
    }

    @Test
    @DisplayName("Same tier is not higher priority than itself")
    void sameTierNotHigherPriority() {
        for (MemoryTier tier : MemoryTier.values()) {
            assertThat(tier.isHigherPriorityThan(tier)).isFalse();
        }
    }

    // ═══ Comparators ═══

    @Test
    @DisplayName("BY_EVICTION_PRIORITY sorts HOT first, ARCHIVE last")
    void byEvictionPriority_order() {
        List<MemoryTier> sorted = Arrays.stream(MemoryTier.values())
                .sorted(MemoryTier.BY_EVICTION_PRIORITY)
                .toList();
        assertThat(sorted).containsExactly(
                MemoryTier.HOT, MemoryTier.WARM, MemoryTier.COLD, MemoryTier.ARCHIVE);
    }

    @Test
    @DisplayName("BY_SALIENCE_ASC sorts ARCHIVE first, HOT last")
    void bySalienceAsc_order() {
        List<MemoryTier> sorted = Arrays.stream(MemoryTier.values())
                .sorted(MemoryTier.BY_SALIENCE_ASC)
                .toList();
        assertThat(sorted).containsExactly(
                MemoryTier.ARCHIVE, MemoryTier.COLD, MemoryTier.WARM, MemoryTier.HOT);
    }

    // ═══ Default TTL and properties ═══

    @ParameterizedTest
    @EnumSource(MemoryTier.class)
    @DisplayName("Every tier has non-null positive defaultTtl")
    void everyTierHasPositiveTtl(MemoryTier tier) {
        assertThat(tier.getDefaultTtl()).isNotNull();
        assertThat(tier.getDefaultTtl()).isPositive();
    }

    @Test
    @DisplayName("HOT has shortest TTL, ARCHIVE has longest")
    void ttlOrdering() {
        assertThat(MemoryTier.HOT.getDefaultTtl()).isLessThan(MemoryTier.WARM.getDefaultTtl());
        assertThat(MemoryTier.WARM.getDefaultTtl()).isLessThan(MemoryTier.COLD.getDefaultTtl());
        assertThat(MemoryTier.COLD.getDefaultTtl()).isLessThan(MemoryTier.ARCHIVE.getDefaultTtl());
    }

    @Test
    @DisplayName("Storage prefixes are unique across tiers")
    void storagePrefixesAreUnique() {
        List<String> prefixes = Arrays.stream(MemoryTier.values())
                .map(MemoryTier::getStoragePrefix)
                .toList();
        assertThat(prefixes).doesNotHaveDuplicates();
    }

    // ═══ Enum completeness ═══

    @Test
    @DisplayName("Exactly 4 memory tiers exist")
    void exactlyFourTiers() {
        assertThat(MemoryTier.values()).hasSize(4);
    }

    @Test
    @DisplayName("Salience thresholds cover [0.0, 1.0] without gaps")
    void salienceThresholdsCoverEntireRange() {
        // Every value from 0.0 to 1.0 in increments of 0.01 should map to some tier
        for (int i = 0; i <= 100; i++) {
            final double score = i / 100.0;
            assertThatCode(() -> MemoryTier.fromSalienceScore(score))
                    .as("salience %.2f should map to a tier", score)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("toString contains tier name and priority")
    void toStringContainsInfo() {
        String s = MemoryTier.HOT.toString();
        assertThat(s).contains("HOT").contains("1");
    }
}
