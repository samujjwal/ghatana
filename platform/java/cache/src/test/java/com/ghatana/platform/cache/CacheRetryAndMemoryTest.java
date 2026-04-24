package com.ghatana.platform.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for cache retry-on-backend-failure behaviour and memory growth baselines.
 *
 * <p>Covers:
 * <ul>
 *   <li>Backend error on {@code get} is absorbed (fail-open) — callers receive empty.</li>
 *   <li>Backend error on {@code put} is absorbed — callers see no exception.</li>
 *   <li>Backend error on {@code invalidate} is absorbed.</li>
 *   <li>A retry wrapper on top of {@link DistributedCacheService} can recover from
 *       transient backend failures before succeeding.</li>
 *   <li>Heap growth from repeated cache operations stays within an acceptable bound.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Cache retry-on-backend-failure and memory-growth baseline tests
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Cache Retry and Memory Baseline")
@Tag("unit")
class CacheRetryAndMemoryTest {

    private static final String TENANT = "test-tenant";

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Backend that throws on the first {@code failCount} getValue calls, then returns {@code value}. */
    private static DistributedCacheService.CacheBackend flakyGetBackend(int failCount, String value) {
        AtomicInteger attempts = new AtomicInteger(0);
        return new StubCacheBackend() {
            @Override
            public String getValue(String key) {
                int attempt = attempts.incrementAndGet();
                if (attempt <= failCount) {
                    throw new RuntimeException("Transient backend error (attempt " + attempt + ")");
                }
                return value;
            }

            @Override
            public Object deserialize(String serialized, Class<?> type) {
                return serialized;
            }
        };
    }

    /** Backend that always throws on getValue. */
    private static DistributedCacheService.CacheBackend alwaysFailingBackend() {
        return new StubCacheBackend() {
            @Override
            public String getValue(String key) {
                throw new RuntimeException("Permanent backend failure");
            }
        };
    }

    /**
     * Retry wrapper for {@link DistributedCacheService#get}: recreates the
     * service against a potentially-recovering backend.
     */
    private static <T> Optional<T> getWithRetry(
            int maxAttempts,
            DistributedCacheService.CacheBackend backend,
            String key,
            Class<T> type) {
        RuntimeException last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return new DistributedCacheService(backend, TENANT).get(key, type);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last != null ? last : new RuntimeException("Unknown error");
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Fail-open error absorption")
    class FailOpenAbsorption {

        private DistributedCacheService cache;

        @BeforeEach
        void setUp() {
            cache = new DistributedCacheService(alwaysFailingBackend(), TENANT);
        }

        @Test
        @DisplayName("get() returns empty when backend throws — never propagates to caller")
        void getAbsorbsBackendError() {
            assertThatCode(() -> {
                Optional<String> result = cache.get("any-key", String.class);
                assertThat(result).isEmpty();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("put() absorbs backend serialization error — never propagates to caller")
        void putAbsorbsBackendError() {
            DistributedCacheService cacheWithBadSerializer = new DistributedCacheService(
                    new StubCacheBackend() {
                        @Override
                        public String serialize(Object value) {
                            throw new RuntimeException("Serialization failed");
                        }
                    }, TENANT);

            assertThatCode(() -> cacheWithBadSerializer.put("k", "v", 60))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("invalidate() absorbs backend delete error — never propagates to caller")
        void invalidateAbsorbsBackendError() {
            DistributedCacheService cacheWithBadDelete = new DistributedCacheService(
                    new StubCacheBackend() {
                        @Override
                        public void deleteKey(String key) {
                            throw new RuntimeException("Delete failed");
                        }
                    }, TENANT);

            assertThatCode(() -> cacheWithBadDelete.invalidate("some-key"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Retry on transient failures")
    class RetryOnTransientFailures {

        @Test
        @DisplayName("recovers from one transient getValue failure and returns value")
        void recoversFromOneGetFailure() {
            DistributedCacheService.CacheBackend backend = flakyGetBackend(1, "cached-value");

            Optional<String> result = getWithRetry(3, backend, "key", String.class);

            assertThat(result).isPresent().hasValue("cached-value");
        }

        @Test
        @DisplayName("recovers from two transient getValue failures before returning value")
        void recoversFromTwoGetFailures() {
            DistributedCacheService.CacheBackend backend = flakyGetBackend(2, "recovered");

            Optional<String> result = getWithRetry(3, backend, "key", String.class);

            assertThat(result).isPresent().hasValue("recovered");
        }

        @Test
        @DisplayName("after recovery returns correct deserialized value")
        void recoveredValueIsCorrectlyDeserialized() {
            DistributedCacheService.CacheBackend backend = flakyGetBackend(1, "hello");

            Optional<String> result = getWithRetry(3, backend, "greeting", String.class);

            assertThat(result).hasValue("hello");
        }
    }

    @Nested
    @DisplayName("Memory growth baseline")
    class MemoryGrowthBaseline {

        @Test
        @DisplayName("1000 get operations on in-memory backend grow heap by less than 20 MB")
        void repeatedGetOperationsLimitedHeapGrowth() {
            DistributedCacheService.CacheBackend backend = new StubCacheBackend() {
                @Override
                public String getValue(String key) { return "v"; }

                @Override
                public Object deserialize(String s, Class<?> t) { return s; }
            };
            DistributedCacheService cache = new DistributedCacheService(backend, TENANT);

            System.gc();
            long memBefore = usedHeapMb();

            IntStream.range(0, 1_000).forEach(i -> cache.get("key-" + i, String.class));

            System.gc();
            long memAfter = usedHeapMb();
            long deltaMb = memAfter - memBefore;

            assertThat(deltaMb)
                    .as("Heap growth from 1000 get() calls should be < 20 MB but was %d MB", deltaMb)
                    .isLessThan(20);
        }

        @Test
        @DisplayName("1000 put operations on in-memory backend grow heap by less than 20 MB")
        void repeatedPutOperationsLimitedHeapGrowth() {
            DistributedCacheService.CacheBackend backend = new StubCacheBackend();
            DistributedCacheService cache = new DistributedCacheService(backend, TENANT);

            System.gc();
            long memBefore = usedHeapMb();

            IntStream.range(0, 1_000).forEach(i -> cache.put("key-" + i, "value-" + i, 60));

            System.gc();
            long memAfter = usedHeapMb();
            long deltaMb = memAfter - memBefore;

            assertThat(deltaMb)
                    .as("Heap growth from 1000 put() calls should be < 20 MB but was %d MB", deltaMb)
                    .isLessThan(20);
        }

        @Test
        @DisplayName("1000 invalidate operations on in-memory backend grow heap by less than 10 MB")
        void repeatedInvalidateOperationsLimitedHeapGrowth() {
            DistributedCacheService.CacheBackend backend = new StubCacheBackend();
            DistributedCacheService cache = new DistributedCacheService(backend, TENANT);

            System.gc();
            long memBefore = usedHeapMb();

            IntStream.range(0, 1_000).forEach(i -> cache.invalidate("key-" + i));

            System.gc();
            long memAfter = usedHeapMb();
            long deltaMb = memAfter - memBefore;

            assertThat(deltaMb)
                    .as("Heap growth from 1000 invalidate() calls should be < 10 MB but was %d MB", deltaMb)
                    .isLessThan(10);
        }

        private long usedHeapMb() {
            Runtime rt = Runtime.getRuntime();
            return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        }
    }

    // ── Stub backend ──────────────────────────────────────────────────────────

    /**
     * No-op stub implementing all CacheBackend methods with silent defaults.
     * Override only the methods relevant to each test.
     */
    static class StubCacheBackend implements DistributedCacheService.CacheBackend {
        @Override public String getValue(String key) { return null; }
        @Override public void setValue(String key, String value, long ttlSeconds) {}
        @Override public void deleteKey(String key) {}
        @Override public int deletePattern(String pattern) { return 0; }
        @Override public long getKeyCount(String pattern) { return 0; }
        @Override public long getCacheSize(String pattern) { return 0; }
        @Override public <T> String serialize(T value) { return value == null ? null : value.toString(); }
        @Override public <T> T deserialize(String serialized, Class<T> type) { return null; }
    }
}
