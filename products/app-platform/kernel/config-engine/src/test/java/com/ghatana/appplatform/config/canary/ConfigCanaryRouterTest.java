package com.ghatana.appplatform.config.canary;

import com.ghatana.appplatform.config.domain.ConfigHierarchyLevel;
import com.ghatana.appplatform.config.domain.ConfigValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ConfigCanaryRouter}.
 *
 * <p>Tests cover the deterministic bucketing logic, percentage boundaries,
 * and edge cases (0%, 100%, bad percentage).
 *
 * @doc.type class
 * @doc.purpose Unit tests for ConfigCanaryRouter bucketing and routing logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ConfigCanaryRouter Tests")
class ConfigCanaryRouterTest {

    private final ConfigCanaryRouter router =
        new ConfigCanaryRouter(mock(com.ghatana.appplatform.config.port.ConfigStore.class));

    @Test
    @DisplayName("0% canary always returns stable value")
    void zeroPercentAlwaysStable() {
        ConfigValue result = router.resolve("payments", "max_limit",
            "tenant-A", "session-123", "\"10000\"", "\"50000\"", 0);

        assertThat(result.value()).isEqualTo("\"10000\"");
        assertThat(result.key()).isEqualTo("max_limit");
        assertThat(result.resolvedFromLevel()).isEqualTo(ConfigHierarchyLevel.TENANT);
    }

    @Test
    @DisplayName("100% canary always returns canary value")
    void hundredPercentAlwaysCanary() {
        ConfigValue result = router.resolve("payments", "max_limit",
            "tenant-A", "session-123", "\"10000\"", "\"50000\"", 100);

        assertThat(result.value()).isEqualTo("\"50000\"");
        assertThat(result.key()).isEqualTo("max_limit");
    }

    @Test
    @DisplayName("bucketing is deterministic — same input always yields same result")
    void bucketingIsDeterministic() {
        int first  = ConfigCanaryRouter.bucket("payments", "max_limit", "session-XYZ");
        int second = ConfigCanaryRouter.bucket("payments", "max_limit", "session-XYZ");
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("bucket is in range [0, 100)")
    void bucketInRange() {
        for (int i = 0; i < 200; i++) {
            int b = ConfigCanaryRouter.bucket("ns", "key", "session-" + i);
            assertThat(b).isBetween(0, 99);
        }
    }

    @Test
    @DisplayName("different bucket keys produce different buckets (spread check)")
    void bucketKeyDifferentiation() {
        long distinctBuckets = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> ConfigCanaryRouter.bucket("payments", "max_limit", "session-" + i))
            .distinct()
            .count();
        // With 100 samples we expect at least 30 distinct bucket values (uniform hash)
        assertThat(distinctBuckets).isGreaterThan(30);
    }

    @Test
    @DisplayName("session in canary bucket receives canary value")
    void sessionInCanaryBucketGetsCanaryValue() {
        // Find a bucketKey that lands below 50 (canary bucket) and one that lands ≥ 50 (stable)
        String canarySession = findBucketKey(0, 49);
        String stableSession = findBucketKey(50, 99);

        ConfigValue canaryResult = router.resolve("payments", "max_limit",
            "t1", canarySession, "\"STABLE\"", "\"CANARY\"", 50);
        assertThat(canaryResult.value()).isEqualTo("\"CANARY\"");

        ConfigValue stableResult = router.resolve("payments", "max_limit",
            "t1", stableSession, "\"STABLE\"", "\"CANARY\"", 50);
        assertThat(stableResult.value()).isEqualTo("\"STABLE\"");
    }

    @Test
    @DisplayName("negative percentage throws IllegalArgumentException")
    void negativePercentageThrows() {
        assertThatThrownBy(() ->
            router.resolve("ns", "key", "t1", "s1", "stable", "canary", -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("percentage > 100 throws IllegalArgumentException")
    void percentageOver100Throws() {
        assertThatThrownBy(() ->
            router.resolve("ns", "key", "t1", "s1", "stable", "canary", 101))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Finds a bucket key whose bucket value falls within [low, high]. */
    private static String findBucketKey(int low, int high) {
        for (int i = 0; i < 10_000; i++) {
            String key = "probe-" + i;
            int b = ConfigCanaryRouter.bucket("payments", "max_limit", key);
            if (b >= low && b <= high) return key;
        }
        throw new AssertionError("Could not find bucket key in range [" + low + "," + high + "]");
    }
}
