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
@DisplayName("InMemoryCacheAdapter Tests [GH-90000]")
class InMemoryCacheAdapterTest extends EventloopTestBase {

    private InMemoryCacheAdapter<String, String> cache;

    @BeforeEach
    void setUp() { // GH-90000
        cache = new InMemoryCacheAdapter<>(1_000, Duration.ofMinutes(5)); // GH-90000
    }

    @Test
    @DisplayName("get returns empty for absent key [GH-90000]")
    void getReturnsEmptyForAbsentKey() { // GH-90000
        Optional<String> result = runPromise(() -> cache.get("missing [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("put and get round-trip succeeds [GH-90000]")
    void putAndGetRoundTripSucceeds() { // GH-90000
        runPromise(() -> cache.put("key1", "value1")); // GH-90000
        Optional<String> result = runPromise(() -> cache.get("key1 [GH-90000]"));
        assertThat(result).contains("value1 [GH-90000]");
    }

    @Test
    @DisplayName("invalidate removes key [GH-90000]")
    void invalidateRemovesKey() { // GH-90000
        runPromise(() -> cache.put("key2", "value2")); // GH-90000
        runPromise(() -> cache.invalidate("key2 [GH-90000]"));
        Optional<String> result = runPromise(() -> cache.get("key2 [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("invalidateAll removes all keys [GH-90000]")
    void invalidateAllRemovesAllKeys() { // GH-90000
        runPromise(() -> cache.put("k1", "v1")); // GH-90000
        runPromise(() -> cache.put("k2", "v2")); // GH-90000
        runPromise(() -> cache.invalidateAll()); // GH-90000
        assertThat(runPromise(() -> cache.get("k1 [GH-90000]"))).isEmpty();
        assertThat(runPromise(() -> cache.get("k2 [GH-90000]"))).isEmpty();
    }

    @Test
    @DisplayName("getOrLoad invokes loader on miss [GH-90000]")
    void getOrLoadInvokesLoaderOnMiss() { // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("loaded [GH-90000]");
        }));
        assertThat(value).isEqualTo("loaded [GH-90000]");
        assertThat(loadCount.get()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("getOrLoad does not invoke loader on hit [GH-90000]")
    void getOrLoadDoesNotInvokeLoaderOnHit() { // GH-90000
        runPromise(() -> cache.put("key", "cached")); // GH-90000
        AtomicInteger loadCount = new AtomicInteger(0); // GH-90000
        String value = runPromise(() -> cache.getOrLoad("key", k -> { // GH-90000
            loadCount.incrementAndGet(); // GH-90000
            return Promise.of("loaded [GH-90000]");
        }));
        assertThat(value).isEqualTo("cached [GH-90000]");
        assertThat(loadCount.get()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("isHealthy returns true [GH-90000]")
    void isHealthyReturnsTrue() { // GH-90000
        assertThat(cache.isHealthy()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("constructor rejects non-positive maximumSize [GH-90000]")
    void constructorRejectsNonPositiveMaxSize() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(0, Duration.ofMinutes(1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("maximumSize [GH-90000]");
    }

    @Test
    @DisplayName("constructor rejects zero TTL [GH-90000]")
    void constructorRejectsZeroTtl() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ZERO)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("defaultTtl [GH-90000]");
    }

    @Test
    @DisplayName("constructor rejects negative TTL [GH-90000]")
    void constructorRejectsNegativeTtl() { // GH-90000
        assertThatThrownBy(() -> new InMemoryCacheAdapter<>(100, Duration.ofSeconds(-1))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("defaultTtl [GH-90000]");
    }

    @Test
    @DisplayName("put with explicit TTL stores value [GH-90000]")
    void putWithExplicitTtlStoresValue() { // GH-90000
        runPromise(() -> cache.put("k", "v", Duration.ofMinutes(1))); // GH-90000
        assertThat(runPromise(() -> cache.get("k [GH-90000]"))).contains("v [GH-90000]");
    }

    @Test
    @DisplayName("second put overwrites first [GH-90000]")
    void secondPutOverwritesFirst() { // GH-90000
        runPromise(() -> cache.put("k", "first")); // GH-90000
        runPromise(() -> cache.put("k", "second")); // GH-90000
        assertThat(runPromise(() -> cache.get("k [GH-90000]"))).contains("second [GH-90000]");
    }
}
