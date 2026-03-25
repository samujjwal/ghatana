package com.ghatana.platform.cache;

import com.ghatana.platform.testing.activej.EventloopTestBase;
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
class InMemoryCacheAdapterTest extends EventloopTestBase {

    private InMemoryCacheAdapter<String, String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("get returns empty for absent key")
    void getReturnsEmptyForAbsentKey() {
        Optional<String> result = runPromise(() -> cache.get("missing"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put and get round-trip succeeds")
    void putAndGetRoundTripSucceeds() {
        runPromise(() -> cache.put("key1", "value1"));
        Optional<String> result = runPromise(() -> cache.get("key1"));
        assertThat(result).contains("value1");
    }

    @Test
    @DisplayName("invalidate removes key")
    void invalidateRemovesKey() {
        runPromise(() -> cache.put("key2", "value2"));
        runPromise(() -> cache.invalidate("key2"));
        Optional<String> result = runPromise(() -> cache.get("key2"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("invalidateAll removes all keys")
    void invalidateAllRemovesAllKeys() {
        runPromise(() -> cache.put("k1", "v1"));
        runPromise(() -> cache.put("k2", "v2"));
        runPromise(() -> cache.invalidateAll());
        assertThat(runPromise(() -> cache.get("k1"))).isEmpty();
        assertThat(runPromise(() -> cache.get("k2"))).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on miss")
    void getOrLoadInvokesLoaderOnMiss() {
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = runPromise(() -> cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("loaded");
        }));
        assertThat(value).isEqualTo("loaded");
        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("getOrLoad does not invoke loader on hit")
    void getOrLoadDoesNotInvokeLoaderOnHit() {
        runPromise(() -> cache.put("key", "cached"));
        AtomicInteger loadCount = new AtomicInteger(0);
        String value = runPromise(() -> cache.getOrLoad("key", k -> {
            loadCount.incrementAndGet();
            return Promise.of("loaded");
        }));
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
    void putWithExplicitTtlStoresValue() {
        runPromise(() -> cache.put("k", "v", Duration.ofMinutes(1)));
        assertThat(runPromise(() -> cache.get("k"))).contains("v");
    }

    @Test
    @DisplayName("second put overwrites first")
    void secondPutOverwritesFirst() {
        runPromise(() -> cache.put("k", "first"));
        runPromise(() -> cache.put("k", "second"));
        assertThat(runPromise(() -> cache.get("k"))).contains("second");
    }
}
