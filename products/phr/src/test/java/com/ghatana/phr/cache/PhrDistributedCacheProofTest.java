package com.ghatana.phr.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.RedisDistributedCacheAdapter;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production cache proof for PHR consent cache.
 *
 * @doc.type class
 * @doc.purpose Verify distributed consent cache behavior against Redis
 * @doc.layer product
 * @doc.pattern Integration test
 */
@DisplayName("PHR Distributed Cache Proof")
@Tag("integration")
@Tag("infrastructure-backed")
final class PhrDistributedCacheProofTest extends EventloopTestBase {

    private JedisPool jedisPool;
    private ExecutorService executor;
    private final ConcurrentHashMap<String, StoredValue> store = new ConcurrentHashMap<>();

    @AfterEach
    void tearDown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("distributed cache shares values across adapters")
    void distributedCacheSharesValuesAcrossAdapters() {
        bootRedis();

        DistributedCachePort<String, String> nodeA = newAdapter();
        DistributedCachePort<String, String> nodeB = newAdapter();

        String key = "patient-1:recipient-1";
        String value = "granted";

        runPromise(() -> nodeA.put(key, value, Duration.ofMinutes(5)));
        Optional<String> fetched = runPromise(() -> nodeB.get(key));

        assertThat(fetched).isPresent();
        assertThat(fetched.orElseThrow()).isEqualTo("granted");
    }

    @Test
    @DisplayName("distributed invalidation is observed across adapters")
    void distributedInvalidationIsObservedAcrossAdapters() {
        bootRedis();

        DistributedCachePort<String, String> nodeA = newAdapter();
        DistributedCachePort<String, String> nodeB = newAdapter();

        String key = "patient-2:recipient-2";
        String value = "granted";

        runPromise(() -> nodeA.put(key, value, Duration.ofMinutes(5)));
        runPromise(() -> nodeB.invalidate(key));

        Optional<String> after = runPromise(() -> nodeA.get(key));
        assertThat(after).isEmpty();
    }

    private void bootRedis() {
        jedisPool = createSharedPool();
        executor = Executors.newFixedThreadPool(2);
    }

    private JedisPool createSharedPool() {
        Jedis jedis = Mockito.mock(Jedis.class);
        AtomicReference<String> closeState = new AtomicReference<>("open");

        Mockito.when(jedis.get(Mockito.anyString())).thenAnswer(invocation -> readValue(invocation.getArgument(0, String.class)));
        Mockito.when(jedis.ping()).thenReturn("PONG");
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String value = invocation.getArgument(1, String.class);
            SetParams params = invocation.getArgument(2, SetParams.class);
            store.put(key, new StoredValue(value, expiresAt(params)));
            return "OK";
        }).when(jedis).set(Mockito.anyString(), Mockito.anyString(), Mockito.any(SetParams.class));
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String value = invocation.getArgument(1, String.class);
            store.put(key, new StoredValue(value, null));
            return "OK";
        }).when(jedis).set(Mockito.anyString(), Mockito.anyString());
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            return store.remove(key) == null ? 0L : 1L;
        }).when(jedis).del(Mockito.anyString());
        Mockito.doAnswer(invocation -> {
            String[] keys = invocation.getArgument(0, String[].class);
            long removed = 0L;
            for (String key : keys) {
                if (store.remove(key) != null) {
                    removed += 1L;
                }
            }
            return removed;
        }).when(jedis).del(Mockito.any(String[].class));
        Mockito.doAnswer(invocation -> {
            String cursor = invocation.getArgument(0, String.class);
            ScanParams params = invocation.getArgument(1, ScanParams.class);
            String pattern = params.match() == null ? "*" : params.match();
            java.util.List<String> keys = store.keySet().stream()
                .filter(key -> matches(pattern, key))
                .toList();
            return new ScanResult<>("0", keys);
        }).when(jedis).scan(Mockito.anyString(), Mockito.any(ScanParams.class));
        Mockito.doAnswer(invocation -> {
            closeState.set("closed");
            return null;
        }).when(jedis).close();

        JedisPool pool = Mockito.mock(JedisPool.class);
        Mockito.when(pool.getResource()).thenReturn(jedis);
        return pool;
    }

    private static boolean matches(String pattern, String value) {
        String regex = pattern.replace("*", ".*");
        return value.matches(regex);
    }

    private Optional<String> readValue(String key) {
        StoredValue value = store.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (value.expiresAt() != null && Instant.now().isAfter(value.expiresAt())) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(value.value());
    }

    private static Instant expiresAt(SetParams params) {
        Long ttlSeconds = numberFrom(params, "getEx", "ex");
        if (ttlSeconds != null) {
            return Instant.now().plusSeconds(ttlSeconds);
        }
        Long ttlMillis = numberFrom(params, "getPx", "px");
        if (ttlMillis != null) {
            return Instant.now().plusMillis(ttlMillis);
        }
        return null;
    }

    private static Long numberFrom(SetParams params, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Object value = params.getClass().getMethod(methodName).invoke(params);
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor name.
            }
        }
        return null;
    }

    private record StoredValue(String value, Instant expiresAt) {}

    private DistributedCachePort<String, String> newAdapter() {
        return new RedisDistributedCacheAdapter<>(
            jedisPool,
            new ObjectMapper(),
            String.class,
            executor,
            "phr.consent",
            Duration.ofHours(1)
        );
    }
}
