package com.ghatana.core.activej.eventloop;

import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for EventloopManager.
 */
class EventloopManagerTest {

    @AfterEach
    void cleanup() { // GH-90000
        // Reset manager state after each test
        EventloopManager.resetForTesting(); // GH-90000
    }

    @Test
    void testGetCurrentEventloop_createsNewEventloop() { // GH-90000
        Eventloop eventloop = EventloopManager.getCurrentEventloop(); // GH-90000

        assertThat(eventloop).isNotNull(); // GH-90000
        assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void testGetCurrentEventloop_isIdempotent() { // GH-90000
        Eventloop first = EventloopManager.getCurrentEventloop(); // GH-90000
        Eventloop second = EventloopManager.getCurrentEventloop(); // GH-90000

        assertThat(first).isSameAs(second); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void testCreate_createsNewEventloop() { // GH-90000
        Eventloop eventloop = EventloopManager.create(); // GH-90000

        assertThat(eventloop).isNotNull(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void testCreate_withCustomThreadName() { // GH-90000
        Eventloop eventloop = EventloopManager.create("custom-loop [GH-90000]");

        assertThat(eventloop).isNotNull(); // GH-90000
        // Thread name is set internally, we can't directly verify it here
        // but we can verify the eventloop was created
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void testHasEventloop_returnsFalseInitially() { // GH-90000
        assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000
    }

    @Test
    void testHasEventloop_returnsTrueAfterCreation() { // GH-90000
        EventloopManager.getCurrentEventloop(); // GH-90000

        assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000
    }

    @Test
    void testClearCurrentEventloop_removesEventloop() { // GH-90000
        EventloopManager.getCurrentEventloop(); // GH-90000
        assertThat(EventloopManager.hasEventloop()).isTrue(); // GH-90000

        EventloopManager.clearCurrentEventloop(); // GH-90000

        assertThat(EventloopManager.hasEventloop()).isFalse(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void testGetEventloop_byThreadId() { // GH-90000
        long threadId = Thread.currentThread().getId(); // GH-90000
        Eventloop created = EventloopManager.create(); // GH-90000

        Eventloop retrieved = EventloopManager.getEventloop(threadId); // GH-90000

        assertThat(retrieved).isSameAs(created); // GH-90000
    }

    @Test
    void testGetEventloop_returnsNullForUnknownThread() { // GH-90000
        Eventloop eventloop = EventloopManager.getEventloop(999999L); // GH-90000

        assertThat(eventloop).isNull(); // GH-90000
    }

    @Test
    void testMultipleThreads_createSeparateEventloops() throws Exception { // GH-90000
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
        List<Eventloop> eventloops = new ArrayList<>(); // GH-90000

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
        try {
            for (int i = 0; i < threadCount; i++) { // GH-90000
                executor.submit(() -> { // GH-90000
                    eventloops.add(EventloopManager.getCurrentEventloop()); // GH-90000
                    latch.countDown(); // GH-90000
                });
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); // GH-90000
        } finally {
            executor.shutdownNow(); // GH-90000
        }
        assertThat(eventloops).hasSize(threadCount); // GH-90000
        assertThat(eventloops).doesNotHaveDuplicates(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(threadCount); // GH-90000
    }

    @Test
    void testShutdownAll_stopsAllEventloops() { // GH-90000
        // Create multiple eventloops
        EventloopManager.create(); // GH-90000
        EventloopManager.create(); // GH-90000
        EventloopManager.create(); // GH-90000

        assertThat(EventloopManager.getActiveCount()).isEqualTo(3); // GH-90000

        boolean success = EventloopManager.shutdownAll(Duration.ofSeconds(5)); // GH-90000

        assertThat(success).isTrue(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000
    }

    @Test
    void testShutdownAll_isIdempotent() { // GH-90000
        EventloopManager.create(); // GH-90000

        boolean first = EventloopManager.shutdownAll(Duration.ofSeconds(5)); // GH-90000
        boolean second = EventloopManager.shutdownAll(Duration.ofSeconds(5)); // GH-90000

        assertThat(first).isTrue(); // GH-90000
        assertThat(second).isFalse(); // Already shut down // GH-90000
    }

    @Test
    void testCreate_afterShutdown_throwsException() { // GH-90000
        EventloopManager.shutdownAll(Duration.ofSeconds(1)); // GH-90000

        assertThatThrownBy(() -> EventloopManager.create()) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("shutting down [GH-90000]");
    }

    @Test
    void testGetCurrentEventloop_afterShutdown_throwsException() { // GH-90000
        EventloopManager.shutdownAll(Duration.ofSeconds(1)); // GH-90000

        assertThatThrownBy(() -> EventloopManager.getCurrentEventloop()) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("shutting down [GH-90000]");
    }

    @Test
    void testGetActiveCount_tracksCorrectly() { // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0); // GH-90000

        EventloopManager.create(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000

        EventloopManager.create(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(2); // GH-90000

        EventloopManager.clearCurrentEventloop(); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1); // GH-90000
    }

    @Test
    void testConcurrentAccess_threadSafe() throws Exception { // GH-90000
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1); // GH-90000
        CountDownLatch doneLatch = new CountDownLatch(threadCount); // GH-90000
        AtomicInteger successCount = new AtomicInteger(0); // GH-90000
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount); // GH-90000
        try {
            for (int i = 0; i < threadCount; i++) { // GH-90000
                executor.submit(() -> { // GH-90000
                    try {
                        startLatch.await(); // Wait for all threads to be ready // GH-90000
                        Eventloop eventloop = EventloopManager.getCurrentEventloop(); // GH-90000
                        if (eventloop != null) { // GH-90000
                            successCount.incrementAndGet(); // GH-90000
                        }
                    } catch (Exception e) { // GH-90000
                        // Ignore
                    } finally {
                        doneLatch.countDown(); // GH-90000
                    }
                });
            }

            startLatch.countDown(); // Start all threads // GH-90000
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        } finally {
            executor.shutdownNow(); // GH-90000
        }

        assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        assertThat(EventloopManager.getActiveCount()).isEqualTo(threadCount); // GH-90000
    }
}
