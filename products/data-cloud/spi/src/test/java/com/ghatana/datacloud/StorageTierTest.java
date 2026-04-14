/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StorageTier}.
 */
@DisplayName("StorageTier")
class StorageTierTest {

    @Nested
    @DisplayName("isHigherThan()")
    class IsHigherThan {

        @Test
        void hotIsHigherThanWarm() {
            assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue();
        }

        @Test
        void warmIsHigherThanCool() {
            assertThat(StorageTier.WARM.isHigherThan(StorageTier.COOL)).isTrue();
        }

        @Test
        void coldIsNotHigherThanWarm() {
            assertThat(StorageTier.COLD.isHigherThan(StorageTier.WARM)).isFalse();
        }

        @Test
        void sameIsNotHigher() {
            assertThat(StorageTier.WARM.isHigherThan(StorageTier.WARM)).isFalse();
        }
    }

    @Nested
    @DisplayName("isLowerThan()")
    class IsLowerThan {

        @Test
        void coldIsLowerThanWarm() {
            assertThat(StorageTier.COLD.isLowerThan(StorageTier.WARM)).isTrue();
        }

        @Test
        void hotIsNotLowerThanWarm() {
            assertThat(StorageTier.HOT.isLowerThan(StorageTier.WARM)).isFalse();
        }

        @Test
        void sameIsNotLower() {
            assertThat(StorageTier.COOL.isLowerThan(StorageTier.COOL)).isFalse();
        }
    }

    @Nested
    @DisplayName("nextLowerTier()")
    class NextLowerTier {

        @Test
        void hotNextIsWarm() {
            assertThat(StorageTier.HOT.nextLowerTier()).isEqualTo(StorageTier.WARM);
        }

        @Test
        void warmNextIsCool() {
            assertThat(StorageTier.WARM.nextLowerTier()).isEqualTo(StorageTier.COOL);
        }

        @Test
        void coolNextIsCold() {
            assertThat(StorageTier.COOL.nextLowerTier()).isEqualTo(StorageTier.COLD);
        }

        @Test
        void coldNextIsCold() {
            assertThat(StorageTier.COLD.nextLowerTier()).isEqualTo(StorageTier.COLD);
        }
    }

    @Nested
    @DisplayName("nextHigherTier()")
    class NextHigherTier {

        @Test
        void coldNextIsWarm() {
            assertThat(StorageTier.COLD.nextHigherTier()).isEqualTo(StorageTier.COOL);
        }

        @Test
        void coolNextIsWarm() {
            assertThat(StorageTier.COOL.nextHigherTier()).isEqualTo(StorageTier.WARM);
        }

        @Test
        void warmNextIsHot() {
            assertThat(StorageTier.WARM.nextHigherTier()).isEqualTo(StorageTier.HOT);
        }

        @Test
        void hotNextIsHot() {
            assertThat(StorageTier.HOT.nextHigherTier()).isEqualTo(StorageTier.HOT);
        }
    }

    @Test
    @DisplayName("defaultTier() returns WARM")
    void defaultTier() {
        assertThat(StorageTier.defaultTier()).isEqualTo(StorageTier.WARM);
    }

    @Nested
    @DisplayName("isColdest() / isHottest()")
    class TierChecks {

        @Test
        void coldIsColdest() {
            assertThat(StorageTier.COLD.isColdest()).isTrue();
        }

        @Test
        void warmIsNotColdest() {
            assertThat(StorageTier.WARM.isColdest()).isFalse();
        }

        @Test
        void hotIsHottest() {
            assertThat(StorageTier.HOT.isHottest()).isTrue();
        }

        @Test
        void warmIsNotHottest() {
            assertThat(StorageTier.WARM.isHottest()).isFalse();
        }
    }

    @Test
    @DisplayName("all four tiers present in values()")
    void allTiersPresent() {
        assertThat(StorageTier.values())
                .containsExactly(StorageTier.HOT, StorageTier.WARM, StorageTier.COOL, StorageTier.COLD);
    }

    @Test
    @DisplayName("valueOf() by name")
    void valueOf() {
        assertThat(StorageTier.valueOf("HOT")).isSameAs(StorageTier.HOT);
        assertThat(StorageTier.valueOf("COLD")).isSameAs(StorageTier.COLD);
    }
}
