package com.ghatana.platform.cache;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
@DisplayName("WriteThroughDistributedCache Tests [GH-90000]")
class WriteThroughDistributedCacheTest extends EventloopTestBase {

    private InMemoryCacheAdapter<String, String> l1;
    private InMemoryCacheAdapter<String, String> l2;
    private WriteThroughDistributedCache<String, String> cache;

    @BeforeEach
    void setUp() { // GH-90000
        l1 = new InMemoryCacheAdapter<>(100, Duration.ofMinutes(1)); // GH-90000
        l2 = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5)); // GH-90000
        cache = new WriteThroughDistributedCache<>(l1, l2); // GH-90000
    }

    @Test
    @DisplayName("get returns empty when both L1 and L2 miss [GH-90000]")
    void getBothMiss() { // GH-90000
        assertThat(runPromise(() -> cache.get("key [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("put writes to both L1 and L2 [GH-90000]")
    void putWritesToBothLayers() { // GH-90000
        runPromise(() -> cache.put("key", "value")); // GH-90000
        assertThat(runPromise(() -> l1.get("key [GH-90000]"))).contains("value [GH-90000]");
        assertThat(runPromise(() -> l2.get("key [GH-90000]"))).contains("value [GH-90000]");
    }

    @Test
    @DisplayName("get hits L1 without touching L2 [GH-90000]")
    void getHitsL1First() { // GH-90000
        // Populate L1 directly but skip L2
        runPromise(() -> l1.put("key", "l1-value")); // GH-90000
        // L2 has different value to confirm we did NOT hit L2
        runPromise(() -> l2.put("key", "l2-value")); // GH-90000

        assertThat(runPromise(() -> cache.get("key [GH-90000]"))).contains("l1-value [GH-90000]");
    }

    @Test
    @DisplayName("L1 miss populates L1 from L2 [GH-90000]")
    void l1MissPopulatesFromL2() { // GH-90000
        runPromise(() -> l2.put("key", "l2-value")); // GH-90000
        // L1 is empty
        assertThat(runPromise(() -> cache.get("key [GH-90000]"))).contains("l2-value [GH-90000]");
        // L1 is now populated
        assertThat(runPromise(() -> l1.get("key [GH-90000]"))).contains("l2-value [GH-90000]");
    }

    @Test
    @DisplayName("invalidate removes from both layers [GH-90000]")
    void invalidateRemovesFromBothLayers() { // GH-90000
        runPromise(() -> cache.put("key", "value")); // GH-90000
        runPromise(() -> cache.invalidate("key [GH-90000]"));
        assertThat(runPromise(() -> l1.get("key [GH-90000]"))).isEmpty();
        assertThat(runPromise(() -> l2.get("key [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("invalidateAll clears both layers [GH-90000]")
    void invalidateAllClearsBothLayers() { // GH-90000
        runPromise(() -> cache.put("key1", "v1")); // GH-90000
        runPromise(() -> cache.put("key2", "v2")); // GH-90000
        runPromise(() -> cache.invalidateAll()); // GH-90000
        assertThat(runPromise(() -> cache.get("key1 [GH-90000]"))).isEmpty();
        assertThat(runPromise(() -> cache.get("key2 [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on full miss and writes to both layers [GH-90000]")
    void getOrLoadInvokesLoaderOnFullMiss() { // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("computed [GH-90000]");
        }));
        assertThat(value).isEqualTo("computed [GH-90000]");
        assertThat(loadCount.get()).isEqualTo(1); // GH-90000
        assertThat(runPromise(() -> l1.get("key [GH-90000]"))).contains("computed [GH-90000]");
        assertThat(runPromise(() -> l2.get("key [GH-90000]"))).contains("computed [GH-90000]");
    }

    @Test
    @DisplayName("getOrLoad returns L1 hit without invoking loader [GH-90000]")
    void getOrLoadReturnsL1HitWithoutLoadCall() { // GH-90000
        runPromise(() -> l1.put("key", "cached")); // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("computed [GH-90000]");
        }));
        assertThat(value).isEqualTo("cached [GH-90000]");
        assertThat(loadCount.get()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("isHealthy delegates to L2 [GH-90000]")
    void isHealthyDelegatesToL2() { // GH-90000
        assertThat(cache.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("put with explicit TTL stores in both layers [GH-90000]")
    void putWithTtlStoredInBothLayers() { // GH-90000
        runPromise(() -> cache.put("key", "value", Duration.ofSeconds(30))); // GH-90000
        assertThat(runPromise(() -> l1.get("key [GH-90000]"))).contains("value [GH-90000]");
        assertThat(runPromise(() -> l2.get("key [GH-90000]"))).contains("value [GH-90000]");
    }
}
