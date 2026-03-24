package com.ghatana.platform.cache;

import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryCacheAdapter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for InMemoryCacheAdapter
 * @doc.layer platform
 * @doc.pattern TestClass
 */
@DisplayName("InMemoryCacheAdapter Tests")
class InMemoryCacheAdapterTest {

    private InMemoryCacheAdapter<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("get returns empty for absent key")
    void getReturnsEmptyForAbsentKey() throws Exception {
        Optional<String> result = cache.get("missing").get();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put and get round-trip succeeds")
    void putAndGetRoundTripSucceeds() throws Exception {
        cache.put("key1", "value1").get();
        Optional<String> result = cache.get("key1").get();
        assertThat(result).contains("value1");
    }

    @Test
    @DisplayName("invalidate removes key")
    void invalidateRemovesKey() throws Exception {
        cache.put("key2", "value2").get();
        cache.invalidate("key2").get();
        Optional<String> result = cache.get("key2").get();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("invalidateAll removes all keys")
    void invalidateAllRemovesAllKeys() throws Exception {
        cache.put("k1", "v1").get();
        cache.put("k2", "v2").get();
        cache.invalidateAll().get();
        assertThat(cache.get("k1").get()).isEmpty();
        assertThat(cache.get("k2").get()).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on miss")
    void getOrLoadInvokesLoaderOnMiss() throws Exception {
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("loaded");
        }).get();
        assertThat(value).isEqualTo("loaded");
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("getOrLoad does not invoke loader on hit")
    void getOrLoadDoesNotInvokeLoaderOnHit() throws Exception {
        cache.put("key", "cached").get();
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("loaded");
        }).get();
        assertThat(value).isEqualTo("cached");
        assertThat(loadCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("isHealthy returns true")
    void isHealthyReturnsTrue() {
        assertThat(cache.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("constructor rejects non-positive maximumSize")
    void constructorRejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximumSize");
    }

    @Test
    @DisplayName("constructor rejects zero TTL")
    void constructorRejectsZeroTtl() {
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTtl");
    }

    @Test
    @DisplayName("constructor rejects negative TTL")
    void constructorRejectsNegativeTtl() {
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultTtl");
    }

    @Test
    @DisplayName("put with explicit TTL stores value")
    void putWithExplicitTtlStoresValue() throws Exception {
        cache.put("k", "v", Duration.ofMinutes(1)).get();
        assertThat(cache.get("k").get()).contains("v");
    }

    @Test
    @DisplayName("second put overwrites first")
    void secondPutOverwritesFirst() throws Exception {
        cache.put("k", "first").get();
        cache.put("k", "second").get();
        assertThat(cache.get("k").get()).contains("second");
    }
}
