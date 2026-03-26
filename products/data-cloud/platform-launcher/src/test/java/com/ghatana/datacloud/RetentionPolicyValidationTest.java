/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Validates input guards on {@link RetentionPolicy} factory methods.
 *
 * <p>Focuses on PDC-003: zero/negative/null inputs that were previously accepted
 * silently and could cause silent data-retention misconfiguration leading to
 * unbounded storage growth or immediate deletion of all records.
 *
 * @doc.type test
 * @doc.purpose Boundary and validation tests for RetentionPolicy factory methods (PDC-003)
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("RetentionPolicy Validation Tests")
class RetentionPolicyValidationTest {

    // =========================================================================
    // keepFor(Duration)
    // =========================================================================

    @Nested
    @DisplayName("keepFor(Duration)")
    class KeepForTests {

        @Test
        @DisplayName("Should create policy for a valid positive duration")
        void shouldAcceptPositiveDuration() {
            RetentionPolicy policy = RetentionPolicy.keepFor(Duration.ofDays(90));

            assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.TIME_BASED);
            assertThat(policy.getRetentionDuration()).isEqualTo(Duration.ofDays(90));
            assertThat(policy.getDeleteAfter()).isEqualTo(Duration.ofDays(90));
        }

        @Test
        @DisplayName("Should reject null duration")
        void shouldRejectNullDuration() {
            assertThatNullPointerException()
                    .isThrownBy(() -> RetentionPolicy.keepFor(null))
                    .withMessageContaining("duration must not be null");
        }

        @Test
        @DisplayName("Should reject zero duration")
        void shouldRejectZeroDuration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RetentionPolicy.keepFor(Duration.ZERO))
                    .withMessageContaining("duration must be positive");
        }

        @Test
        @DisplayName("Should reject negative duration")
        void shouldRejectNegativeDuration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RetentionPolicy.keepFor(Duration.ofDays(-1)))
                    .withMessageContaining("duration must be positive");
        }
    }

    // =========================================================================
    // keepLastN(long)
    // =========================================================================

    @Nested
    @DisplayName("keepLastN(long)")
    class KeepLastNTests {

        @Test
        @DisplayName("Should create COUNT_BASED policy for positive count")
        void shouldAcceptPositiveCount() {
            RetentionPolicy policy = RetentionPolicy.keepLastN(1_000_000L);

            assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.COUNT_BASED);
            assertThat(policy.getMaxRecordCount()).isEqualTo(1_000_000L);
        }

        @ParameterizedTest(name = "maxCount={0} should be rejected")
        @ValueSource(longs = {0L, -1L, -1_000L, Long.MIN_VALUE})
        @DisplayName("Should reject zero or negative maxCount")
        void shouldRejectNonPositiveCount(long invalidCount) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RetentionPolicy.keepLastN(invalidCount))
                    .withMessageContaining("maxCount must be > 0");
        }
    }

    // =========================================================================
    // keepUpToSize(long)
    // =========================================================================

    @Nested
    @DisplayName("keepUpToSize(long)")
    class KeepUpToSizeTests {

        @Test
        @DisplayName("Should create SIZE_BASED policy for positive byte count")
        void shouldAcceptPositiveSize() {
            long oneGb = 1024L * 1024 * 1024;
            RetentionPolicy policy = RetentionPolicy.keepUpToSize(oneGb);

            assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.SIZE_BASED);
            assertThat(policy.getMaxSizeBytes()).isEqualTo(oneGb);
        }

        @ParameterizedTest(name = "maxSizeBytes={0} should be rejected")
        @ValueSource(longs = {0L, -1L, -1_000L, Long.MIN_VALUE})
        @DisplayName("Should reject zero or negative maxSizeBytes")
        void shouldRejectNonPositiveSize(long invalidSize) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> RetentionPolicy.keepUpToSize(invalidSize))
                    .withMessageContaining("maxSizeBytes must be > 0");
        }
    }

    // =========================================================================
    // keepForever / NONE strategy
    // =========================================================================

    @Nested
    @DisplayName("keepForever()")
    class KeepForeverTests {

        @Test
        @DisplayName("Should produce a disabled NONE-strategy policy with zero total duration")
        void keepForeverShouldDisableRetention() {
            RetentionPolicy policy = RetentionPolicy.keepForever();

            assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.NONE);
            assertThat(policy.getEnabled()).isFalse();
            assertThat(policy.getTotalDuration()).isEqualTo(Duration.ZERO);
        }
    }

    // =========================================================================
    // tiered(int, int, int, int)
    // =========================================================================

    @Nested
    @DisplayName("tiered(int,int,int,int)")
    class TieredTests {

        @Test
        @DisplayName("Should build TIME_BASED tiered policy with correct durations")
        void shouldBuildTieredPolicy() {
            RetentionPolicy policy = RetentionPolicy.tiered(7, 30, 90, 365);

            assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.TIME_BASED);
            assertThat(policy.isTiered()).isTrue();
            assertThat(policy.getHotDuration()).isEqualTo(Duration.ofDays(7));
            assertThat(policy.getWarmDuration()).isEqualTo(Duration.ofDays(30));
            assertThat(policy.getColdDuration()).isEqualTo(Duration.ofDays(90));
            // total = hot + warm + cold + archive = 7 + 30 + 90 + 365 = 492 days
            assertThat(policy.getTotalDuration()).isEqualTo(Duration.ofDays(492));
        }
    }

    // =========================================================================
    // getTotalDuration fallback
    // =========================================================================

    @Nested
    @DisplayName("getTotalDuration() edge cases")
    class GetTotalDurationTests {

        @Test
        @DisplayName("Should prefer deleteAfter over retentionDuration")
        void shouldPreferDeleteAfter() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .strategy(RetentionPolicy.RetentionStrategy.TIME_BASED)
                    .retentionDuration(Duration.ofDays(30))
                    .deleteAfter(Duration.ofDays(60))
                    .build();

            assertThat(policy.getTotalDuration()).isEqualTo(Duration.ofDays(60));
        }

        @Test
        @DisplayName("Should fall back to retentionDuration when deleteAfter is null")
        void shouldFallBackToRetentionDuration() {
            RetentionPolicy policy = RetentionPolicy.builder()
                    .strategy(RetentionPolicy.RetentionStrategy.TIME_BASED)
                    .retentionDuration(Duration.ofDays(30))
                    .build();

            assertThat(policy.getTotalDuration()).isEqualTo(Duration.ofDays(30));
        }

        @Test
        @DisplayName("Should return Duration.ZERO when neither deleteAfter nor retentionDuration is set")
        void shouldReturnZeroWhenNeitherSet() {
            RetentionPolicy policy = RetentionPolicy.keepForever();

            assertThat(policy.getTotalDuration()).isEqualTo(Duration.ZERO);
        }
    }
}
