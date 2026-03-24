package com.ghatana.platform.cache;

import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WriteThroughDistributedCache}.
 *
 * <p>Uses two {@link InMemoryCacheAdapter} instances as L1 and L2 to avoid Redis
 * dependency in unit tests — the composition and routing logic is what's under test.</p>
 *
 * @doc.type class
 * @doc.purpose Unit tests for WriteThroughDistributedCache L1/L2 composition
 * @doc.layer platform
 * @doc.pattern TestClass
 */
@DisplayName("WriteThroughDistributedCache Tests")
class WriteThroughDistributedCacheTest {

    private InMemoryCacheAdapter<String, String> l1;
    private InMemoryCacheAdapter<String, String> l2;
    private WriteThroughDistributedCache<String, String> cache;

    @BeforeEach
    void setUp() {
        l1 = new InMemoryCacheAdapter<>(100, Duration.ofMinutes(1));
        l2 = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5));
        cache = new WriteThroughDistributedCache<>(l1, l2);
    }

    @Test
    @DisplayName("get returns empty when both L1 and L2 miss")
    void getBothMiss() throws Exception {
        assertThat(cache.get("key").get()).isEmpty();
    }

    @Test
    @DisplayName("put writes to both L1 and L2")
    void putWritesToBothLayers() throws Exception {
        cache.put("key", "value").get();
        assertThat(l1.get("key").get()).contains("value");
        assertThat(l2.get("key").get()).contains("value");
    }

    @Test
    @DisplayName("get hits L1 without touching L2")
    void getHitsL1First() throws Exception {
        // Populate L1 directly but skip L2
        l1.put("key", "l1-value").get();
        // L2 has different value to confirm we did NOT hit L2
        l2.put("key", "l2-value").get();

        assertThat(cache.get("key").get()).contains("l1-value");
    }

    @Test
    @DisplayName("L1 miss populates L1 from L2")
    void l1MissPopulatesFromL2() throws Exception {
        l2.put("key", "l2-value").get();
        // L1 is empty
        assertThat(cache.get("key").get()).contains("l2-value");
        // L1 is now populated
        assertThat(l1.get("key").get()).contains("l2-value");
    }

    @Test
    @DisplayName("invalidate removes from both layers")
    void invalidateRemovesFromBothLayers() throws Exception {
        cache.put("key", "value").get();
        cache.invalidate("key").get();
        assertThat(l1.get("key").get()).isEmpty();
        assertThat(l2.get("key").get()).isEmpty();
    }

    @Test
    @DisplayName("invalidateAll clears both layers")
    void invalidateAllClearsBothLayers() throws Exception {
        cache.put("key1", "v1").get();
        cache.put("key2", "v2").get();
        cache.invalidateAll().get();
        assertThat(cache.get("key1").get()).isEmpty();
        assertThat(cache.get("key2").get()).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on full miss and writes to both layers")
    void getOrLoadInvokesLoaderOnFullMiss() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("computed");
        }).get();
        assertThat(value).isEqualTo("computed");
        assertThat(loadCount.get()).isEqualTo(1);
        assertThat(l1.get("key").get()).contains("computed");
        assertThat(l2.get("key").get()).contains("computed");
    }

    @Test
    @DisplayName("getOrLoad returns L1 hit without invoking loader")
    void getOrLoadReturnsL1HitWithoutLoadCall() throws Exception {
        l1.put("key", "cached").get();
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("computed");
        }).get();
        assertThat(value).isEqualTo("cached");
        assertThat(loadCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("isHealthy delegates to L2")
    void isHealthyDelegatesToL2() {
        assertThat(cache.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("put with explicit TTL stores in both layers")
    void putWithTtlStoredInBothLayers() throws Exception {
        cache.put("key", "value", Duration.ofSeconds(30)).get();
        assertThat(l1.get("key").get()).contains("value");
        assertThat(l2.get("key").get()).contains("value");
    }
}
