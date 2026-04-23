/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
        void hotIsHigherThanWarm() { // GH-90000
            assertThat(StorageTier.HOT.isHigherThan(StorageTier.WARM)).isTrue(); // GH-90000
        }

        @Test
        void warmIsHigherThanCool() { // GH-90000
            assertThat(StorageTier.WARM.isHigherThan(StorageTier.COOL)).isTrue(); // GH-90000
        }

        @Test
        void coldIsNotHigherThanWarm() { // GH-90000
            assertThat(StorageTier.COLD.isHigherThan(StorageTier.WARM)).isFalse(); // GH-90000
        }

        @Test
        void sameIsNotHigher() { // GH-90000
            assertThat(StorageTier.WARM.isHigherThan(StorageTier.WARM)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("isLowerThan()")
    class IsLowerThan {

        @Test
        void coldIsLowerThanWarm() { // GH-90000
            assertThat(StorageTier.COLD.isLowerThan(StorageTier.WARM)).isTrue(); // GH-90000
        }

        @Test
        void hotIsNotLowerThanWarm() { // GH-90000
            assertThat(StorageTier.HOT.isLowerThan(StorageTier.WARM)).isFalse(); // GH-90000
        }

        @Test
        void sameIsNotLower() { // GH-90000
            assertThat(StorageTier.COOL.isLowerThan(StorageTier.COOL)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("nextLowerTier()")
    class NextLowerTier {

        @Test
        void hotNextIsWarm() { // GH-90000
            assertThat(StorageTier.HOT.nextLowerTier()).isEqualTo(StorageTier.WARM); // GH-90000
        }

        @Test
        void warmNextIsCool() { // GH-90000
            assertThat(StorageTier.WARM.nextLowerTier()).isEqualTo(StorageTier.COOL); // GH-90000
        }

        @Test
        void coolNextIsCold() { // GH-90000
            assertThat(StorageTier.COOL.nextLowerTier()).isEqualTo(StorageTier.COLD); // GH-90000
        }

        @Test
        void coldNextIsCold() { // GH-90000
            assertThat(StorageTier.COLD.nextLowerTier()).isEqualTo(StorageTier.COLD); // GH-90000
        }
    }

    @Nested
    @DisplayName("nextHigherTier()")
    class NextHigherTier {

        @Test
        void coldNextIsWarm() { // GH-90000
            assertThat(StorageTier.COLD.nextHigherTier()).isEqualTo(StorageTier.COOL); // GH-90000
        }

        @Test
        void coolNextIsWarm() { // GH-90000
            assertThat(StorageTier.COOL.nextHigherTier()).isEqualTo(StorageTier.WARM); // GH-90000
        }

        @Test
        void warmNextIsHot() { // GH-90000
            assertThat(StorageTier.WARM.nextHigherTier()).isEqualTo(StorageTier.HOT); // GH-90000
        }

        @Test
        void hotNextIsHot() { // GH-90000
            assertThat(StorageTier.HOT.nextHigherTier()).isEqualTo(StorageTier.HOT); // GH-90000
        }
    }

    @Test
    @DisplayName("defaultTier() returns WARM")
    void defaultTier() { // GH-90000
        assertThat(StorageTier.defaultTier()).isEqualTo(StorageTier.WARM); // GH-90000
    }

    @Nested
    @DisplayName("isColdest() / isHottest()")
    class TierChecks {

        @Test
        void coldIsColdest() { // GH-90000
            assertThat(StorageTier.COLD.isColdest()).isTrue(); // GH-90000
        }

        @Test
        void warmIsNotColdest() { // GH-90000
            assertThat(StorageTier.WARM.isColdest()).isFalse(); // GH-90000
        }

        @Test
        void hotIsHottest() { // GH-90000
            assertThat(StorageTier.HOT.isHottest()).isTrue(); // GH-90000
        }

        @Test
        void warmIsNotHottest() { // GH-90000
            assertThat(StorageTier.WARM.isHottest()).isFalse(); // GH-90000
        }
    }

    @Test
    @DisplayName("all four tiers present in values()")
    void allTiersPresent() { // GH-90000
        assertThat(StorageTier.values()) // GH-90000
                .containsExactly(StorageTier.HOT, StorageTier.WARM, StorageTier.COOL, StorageTier.COLD); // GH-90000
    }

    @Test
    @DisplayName("valueOf() by name")
    void valueOf() { // GH-90000
        assertThat(StorageTier.valueOf("HOT")).isSameAs(StorageTier.HOT);
        assertThat(StorageTier.valueOf("COLD")).isSameAs(StorageTier.COLD);
    }
}
