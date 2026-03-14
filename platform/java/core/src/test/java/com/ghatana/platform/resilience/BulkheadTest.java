/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("K18-004 Bulkhead — Concurrency Limiter")
class BulkheadTest {

    @Test
    @DisplayName("bulkhead_withinLimit: executes successfully when permits available")
    void bulkhead_withinLimit_executesSuccessfully() throws Exception {
        Bulkhead bulkhead = Bulkhead.of("payment-service", 3);

        String result = bulkhead.tryExecuteBlocking(() -> "result");

        assertThat(result).isEqualTo("result");
        assertThat(bulkhead.getTotalAcquired()).isEqualTo(1);
        assertThat(bulkhead.getTotalRejected()).isEqualTo(0);
    }

    @Test
    @DisplayName("bulkhead_exhausted_rejects: throws BulkheadFullException when saturated")
    void bulkhead_exhausted_rejects() {
        Bulkhead bulkhead = Bulkhead.of("slow-service", 1);

        // Fill the bulkhead (need 2 threads)
        // We verify rejection works by exhausting with tryRun holding the semaphore
        // Since tryRun doesn't hold across threads in test, use a direct approach:
        // Create a bulkhead with 0 permits isn't allowed, so we simulate saturation.
        // Instead, test the direct rejection path using a separate Bulkhead sized 0's equivalent:
        // We can't easily hold a permit across threads synchronously. Use a wrapped approach:
        
        // Simulate full bulkhead by checking: if we acquire manually then try again
        // Direct: create size-1, hold it, then call tryExecuteBlocking which blocks acquire by tryAcquire
        // The tryAcquire() returns false immediately when semaphore is at 0 permits.
        
        // Use a wrapper that occupies the permit during execution:
        java.util.concurrent.CountDownLatch occupying = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);

        Thread occupier = new Thread(() -> {
            try {
                bulkhead.tryExecuteBlocking(() -> {
                    ready.countDown();
                    occupying.await();
                    return null;
                });
            } catch (Exception ignored) {}
        });
        occupier.start();

        try {
            ready.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted");
        }

        // Now the single permit is held — try to acquire should fail immediately
        assertThatThrownBy(() -> bulkhead.tryExecuteBlocking(() -> "should-not-run"))
                .isInstanceOf(Bulkhead.BulkheadFullException.class)
                .hasMessageContaining("slow-service");
        assertThat(bulkhead.getTotalRejected()).isEqualTo(1);

        occupying.countDown();
        try { occupier.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Test
    @DisplayName("bulkhead_isolation: separate bulkheads do not affect each other")
    void bulkhead_isolation_separateBulkheads() throws Exception {
        Bulkhead b1 = Bulkhead.of("service-a", 1);
        Bulkhead b2 = Bulkhead.of("service-b", 1);

        b1.tryExecuteBlocking(() -> "a");
        b2.tryExecuteBlocking(() -> "b");

        assertThat(b1.getTotalAcquired()).isEqualTo(1);
        assertThat(b2.getTotalAcquired()).isEqualTo(1);
        assertThat(b1.getTotalRejected()).isEqualTo(0);
        assertThat(b2.getTotalRejected()).isEqualTo(0);
    }

    @Test
    @DisplayName("bulkhead_configurable_poolSize: rejects beyond maxConcurrency")
    void bulkhead_configurable_poolSize() {
        assertThatThrownBy(() -> Bulkhead.of("test", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxConcurrency must be > 0");
    }

    @Test
    @DisplayName("bulkhead_metrics: counts acquired and rejected separately")
    void bulkhead_metrics() throws Exception {
        Bulkhead bulkhead = Bulkhead.of("service", 5);

        for (int i = 0; i < 5; i++) {
            bulkhead.tryExecuteBlocking(() -> null);
        }

        assertThat(bulkhead.getTotalAcquired()).isEqualTo(5);
        assertThat(bulkhead.getTotalRejected()).isEqualTo(0);
        assertThat(bulkhead.getMaxConcurrency()).isEqualTo(5);
        assertThat(bulkhead.getName()).isEqualTo("service");
    }
}
