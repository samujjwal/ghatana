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
    void setUp() { // GH-90000
        cache = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5)); // GH-90000
    }

    @Test
    @DisplayName("get returns empty for absent key")
    void getReturnsEmptyForAbsentKey() { // GH-90000
        Optional<String> result = runPromise(() -> cache.get("missing"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("put and get round-trip succeeds")
    void putAndGetRoundTripSucceeds() { // GH-90000
        runPromise(() -> cache.put("key1", "value1")); // GH-90000
        Optional<String> result = runPromise(() -> cache.get("key1"));
        assertThat(result).contains("value1");
    }

    @Test
    @DisplayName("invalidate removes key")
    void invalidateRemovesKey() { // GH-90000
        runPromise(() -> cache.put("key2", "value2")); // GH-90000
        runPromise(() -> cache.invalidate("key2"));
        Optional<String> result = runPromise(() -> cache.get("key2"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("invalidateAll removes all keys")
    void invalidateAllRemovesAllKeys() { // GH-90000
        runPromise(() -> cache.put("k1", "v1")); // GH-90000
        runPromise(() -> cache.put("k2", "v2")); // GH-90000
        runPromise(() -> cache.invalidateAll()); // GH-90000
        assertThat(runPromise(() -> cache.get("k1"))).isEmpty();
        assertThat(runPromise(() -> cache.get("k2"))).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on miss")
    void getOrLoadInvokesLoaderOnMiss() { // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("loaded");
        }));
        assertThat(value).isEqualTo("loaded");
        assertThat(loadCount.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("getOrLoad does not invoke loader on hit")
    void getOrLoadDoesNotInvokeLoaderOnHit() { // GH-90000
        runPromise(() -> cache.put("key", "cached")); // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("loaded");
        }));
        assertThat(value).isEqualTo("cached");
        assertThat(loadCount.get()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("isHealthy returns true")
    void isHealthyReturnsTrue() { // GH-90000
        assertThat(cache.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects non-positive maximumSize")
    void constructorRejectsNonPositiveMaxSize() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(0, Duration.ofMinutes(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("maximumSize");
    }

    @Test
    @DisplayName("constructor rejects zero TTL")
    void constructorRejectsZeroTtl() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ZERO)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("defaultTtl");
    }

    @Test
    @DisplayName("constructor rejects negative TTL")
    void constructorRejectsNegativeTtl() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ofSeconds(-1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("defaultTtl");
    }

    @Test
    @DisplayName("put with explicit TTL stores value")
    void putWithExplicitTtlStoresValue() { // GH-90000
        runPromise(() -> cache.put("k", "v", Duration.ofMinutes(1))); // GH-90000
        assertThat(runPromise(() -> cache.get("k"))).contains("v");
    }

    @Test
    @DisplayName("second put overwrites first")
    void secondPutOverwritesFirst() { // GH-90000
        runPromise(() -> cache.put("k", "first")); // GH-90000
        runPromise(() -> cache.put("k", "second")); // GH-90000
        assertThat(runPromise(() -> cache.get("k"))).contains("second");
    }
}
