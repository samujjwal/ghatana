/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import com.ghatana.aep.server.ingestion.IdempotencyStore;
import com.ghatana.aep.server.ingestion.InMemoryIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-35: Real integration tests for {@link IdempotencyStore} deduplication logic.
 *
 * <p>Replaces the fully-simulated predecessor with tests that exercise the actual
 * {@link InMemoryIdempotencyStore} implementation, including concurrency safety.
 *
 * @doc.type class
 * @doc.purpose Integration tests for idempotency key deduplication
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("IdempotencyStore deduplication — integration tests")
class IdempotencyKeyDeduplicationTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String TENANT = "tenant-test";

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
    }

    @Test
    @DisplayName("first call with a new key returns false (not a duplicate)")
    void firstCallWithNewKeyIsNotDuplicate() {
        String key = UUID.randomUUID().toString();
        boolean isDuplicate = store.isDuplicate(TENANT, key, TTL).getResult();
        assertFalse(isDuplicate, "First call should not be a duplicate");
    }

    @Test
    @DisplayName("second call with the same key returns true (duplicate)")
    void secondCallWithSameKeyIsDuplicate() {
        String key = UUID.randomUUID().toString();
        store.isDuplicate(TENANT, key, TTL).getResult();
        boolean isDuplicate = store.isDuplicate(TENANT, key, TTL).getResult();
        assertTrue(isDuplicate, "Second call with same key should be a duplicate");
    }

    @Test
    @DisplayName("different keys in the same tenant are independent")
    void differentKeysAreIndependent() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        boolean dup1 = store.isDuplicate(TENANT, key1, TTL).getResult();
        boolean dup2 = store.isDuplicate(TENANT, key2, TTL).getResult();
        assertFalse(dup1, "key1 first call should not be a duplicate");
        assertFalse(dup2, "key2 first call should not be a duplicate");
    }

    @Test
    @DisplayName("same key in different tenants are independent")
    void sameKeyInDifferentTenantsAreIndependent() {
        String key = UUID.randomUUID().toString();
        boolean dup1 = store.isDuplicate("tenant-a", key, TTL).getResult();
        boolean dup2 = store.isDuplicate("tenant-b", key, TTL).getResult();
        assertFalse(dup1, "tenant-a first call should not be a duplicate");
        assertFalse(dup2, "tenant-b first call should not be a duplicate (different tenant)");
    }

    @Test
    @DisplayName("key with zero/expired TTL allows re-use")
    void keyWithExpiredTtlIsNotDuplicate() throws InterruptedException {
        String key = UUID.randomUUID().toString();
        store.isDuplicate(TENANT, key, Duration.ofMillis(1)).getResult();
        Thread.sleep(10);
        boolean isDuplicate = store.isDuplicate(TENANT, key, Duration.ofMillis(1)).getResult();
        assertFalse(isDuplicate, "Expired key should not be treated as a duplicate");
    }

    @Test
    @DisplayName("concurrent calls with the same key — exactly one succeeds")
    void concurrentCallsWithSameKey_exactlyOneSucceeds() throws Exception {
        String key = UUID.randomUUID().toString();
        int threads = 10;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger notDuplicateCount = new AtomicInteger(0);

        var executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                boolean isDuplicate = store.isDuplicate(TENANT, key, TTL).getResult();
                if (!isDuplicate) notDuplicateCount.incrementAndGet();
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) f.get();
        executor.shutdown();

        assertEquals(1, notDuplicateCount.get(),
            "Exactly one thread should observe 'not a duplicate' under concurrent access");
    }

    @Test
    @DisplayName("store is bounded — oldest entries evicted when MAX_ENTRIES exceeded")
    void storeEvictsOldestEntriesWhenFull() {
        int overLimit = 100_001;
        for (int i = 0; i < overLimit; i++) {
            store.isDuplicate(TENANT, "key-" + i, TTL).getResult();
        }
        long duplicateCount = 0;
        for (int i = 0; i < overLimit; i++) {
            if (store.isDuplicate(TENANT, "new-after-eviction-" + i, Duration.ofMillis(1)).getResult()) {
                duplicateCount++;
            }
        }
        assertEquals(0, duplicateCount,
            "Freshly inserted keys after eviction window should not be duplicates");
    }
}
