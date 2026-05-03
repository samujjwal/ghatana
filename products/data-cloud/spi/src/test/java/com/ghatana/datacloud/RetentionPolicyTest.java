package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RetentionPolicy}.
 */
@DisplayName("RetentionPolicy")
class RetentionPolicyTest {

    @Test
    @DisplayName("keepFor creates time-based retention policy")
    void keepFor_createsTimeBasedRetentionPolicy() {
        RetentionPolicy policy = RetentionPolicy.keepFor(Duration.ofDays(90));

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.TIME_BASED);
        assertThat(policy.getRetentionDuration()).isEqualTo(Duration.ofDays(90));
        assertThat(policy.getDeleteAfter()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    @DisplayName("keepFor throws for null duration")
    void keepFor_throwsForNullDuration() {
        assertThatThrownBy(() -> RetentionPolicy.keepFor(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("duration");
    }

    @Test
    @DisplayName("keepFor throws for zero duration")
    void keepFor_throwsForZeroDuration() {
        assertThatThrownBy(() -> RetentionPolicy.keepFor(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("keepFor throws for negative duration")
    void keepFor_throwsForNegativeDuration() {
        assertThatThrownBy(() -> RetentionPolicy.keepFor(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("keepThenArchive creates retention with archive")
    void keepThenArchive_createsRetentionWithArchive() {
        RetentionPolicy policy = RetentionPolicy.keepThenArchive(Duration.ofDays(30), Duration.ofDays(365));

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.TIME_BASED);
        assertThat(policy.getArchiveAfter()).isEqualTo(Duration.ofDays(30));
        assertThat(policy.getDeleteAfter()).isEqualTo(Duration.ofDays(395));
    }

    @Test
    @DisplayName("keepLastN creates count-based retention policy")
    void keepLastN_createsCountBasedRetentionPolicy() {
        RetentionPolicy policy = RetentionPolicy.keepLastN(1_000_000);

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.COUNT_BASED);
        assertThat(policy.getMaxRecordCount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("keepLastN throws for zero count")
    void keepLastN_throwsForZeroCount() {
        assertThatThrownBy(() -> RetentionPolicy.keepLastN(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("keepLastN throws for negative count")
    void keepLastN_throwsForNegativeCount() {
        assertThatThrownBy(() -> RetentionPolicy.keepLastN(-100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("keepUpToSize creates size-based retention policy")
    void keepUpToSize_createsSizeBasedRetentionPolicy() {
        RetentionPolicy policy = RetentionPolicy.keepUpToSize(1_000_000_000L);

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.SIZE_BASED);
        assertThat(policy.getMaxSizeBytes()).isEqualTo(1_000_000_000L);
    }

    @Test
    @DisplayName("keepUpToSize throws for zero size")
    void keepUpToSize_throwsForZeroSize() {
        assertThatThrownBy(() -> RetentionPolicy.keepUpToSize(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("keepUpToSize throws for negative size")
    void keepUpToSize_throwsForNegativeSize() {
        assertThatThrownBy(() -> RetentionPolicy.keepUpToSize(-100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
    }

    @Test
    @DisplayName("keepForever creates retention with no automatic deletion")
    void keepForever_createsRetentionWithNoAutomaticDeletion() {
        RetentionPolicy policy = RetentionPolicy.keepForever();

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.NONE);
        assertThat(policy.getEnabled()).isFalse();
    }

    @Test
    @DisplayName("tiered creates tiered retention policy")
    void tiered_createsTieredRetentionPolicy() {
        RetentionPolicy policy = RetentionPolicy.tiered(7, 30, 90, 180);

        assertThat(policy.getStrategy()).isEqualTo(RetentionPolicy.RetentionStrategy.TIME_BASED);
        assertThat(policy.getHotDuration()).isEqualTo(Duration.ofDays(7));
        assertThat(policy.getWarmDuration()).isEqualTo(Duration.ofDays(30));
        assertThat(policy.getColdDuration()).isEqualTo(Duration.ofDays(90));
        assertThat(policy.getArchiveAfter()).isEqualTo(Duration.ofDays(127));
        assertThat(policy.getDeleteAfter()).isEqualTo(Duration.ofDays(307));
    }

    @Test
    @DisplayName("isTiered returns true when tiers are configured")
    void isTiered_returnsTrueWhenTiersConfigured() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .hotDuration(Duration.ofDays(7))
                .warmDuration(Duration.ofDays(30))
                .build();

        assertThat(policy.isTiered()).isTrue();
    }

    @Test
    @DisplayName("isTiered returns false when no tiers configured")
    void isTiered_returnsFalseWhenNoTiersConfigured() {
        RetentionPolicy policy = RetentionPolicy.keepFor(Duration.ofDays(90));

        assertThat(policy.isTiered()).isFalse();
    }

    @Test
    @DisplayName("getTotalDuration returns deleteAfter when set")
    void getTotalDuration_returnsDeleteAfterWhenSet() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .deleteAfter(Duration.ofDays(365))
                .build();

        assertThat(policy.getTotalDuration()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    @DisplayName("getTotalDuration returns retentionDuration when deleteAfter not set")
    void getTotalDuration_returnsRetentionDurationWhenDeleteAfterNotSet() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .retentionDuration(Duration.ofDays(90))
                .build();

        assertThat(policy.getTotalDuration()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    @DisplayName("getTotalDuration returns ZERO when neither set")
    void getTotalDuration_returnsZeroWhenNeitherSet() {
        RetentionPolicy policy = RetentionPolicy.builder().build();

        assertThat(policy.getTotalDuration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("RetentionStrategy enum contains all expected strategies")
    void retentionStrategyEnum_containsAllExpectedStrategies() {
        RetentionPolicy.RetentionStrategy[] strategies = RetentionPolicy.RetentionStrategy.values();
        assertThat(strategies).contains(
                RetentionPolicy.RetentionStrategy.TIME_BASED,
                RetentionPolicy.RetentionStrategy.COUNT_BASED,
                RetentionPolicy.RetentionStrategy.SIZE_BASED,
                RetentionPolicy.RetentionStrategy.NONE
        );
    }
}
