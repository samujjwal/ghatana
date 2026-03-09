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
    void cleanup() {
        // Reset manager state after each test
        EventloopManager.resetForTesting();
    }
    
    @Test
    void testGetCurrentEventloop_createsNewEventloop() {
        Eventloop eventloop = EventloopManager.getCurrentEventloop();
        
        assertThat(eventloop).isNotNull();
        assertThat(EventloopManager.hasEventloop()).isTrue();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
    }
    
    @Test
    void testGetCurrentEventloop_isIdempotent() {
        Eventloop first = EventloopManager.getCurrentEventloop();
        Eventloop second = EventloopManager.getCurrentEventloop();
        
        assertThat(first).isSameAs(second);
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
    }
    
    @Test
    void testCreate_createsNewEventloop() {
        Eventloop eventloop = EventloopManager.create();
        
        assertThat(eventloop).isNotNull();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
    }
    
    @Test
    void testCreate_withCustomThreadName() {
        Eventloop eventloop = EventloopManager.create("custom-loop");
        
        assertThat(eventloop).isNotNull();
        // Thread name is set internally, we can't directly verify it here
        // but we can verify the eventloop was created
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
    }
    
    @Test
    void testHasEventloop_returnsFalseInitially() {
        assertThat(EventloopManager.hasEventloop()).isFalse();
    }
    
    @Test
    void testHasEventloop_returnsTrueAfterCreation() {
        EventloopManager.getCurrentEventloop();
        
        assertThat(EventloopManager.hasEventloop()).isTrue();
    }
    
    @Test
    void testClearCurrentEventloop_removesEventloop() {
        EventloopManager.getCurrentEventloop();
        assertThat(EventloopManager.hasEventloop()).isTrue();
        
        EventloopManager.clearCurrentEventloop();
        
        assertThat(EventloopManager.hasEventloop()).isFalse();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
    }
    
    @Test
    void testGetEventloop_byThreadId() {
        long threadId = Thread.currentThread().getId();
        Eventloop created = EventloopManager.create();
        
        Eventloop retrieved = EventloopManager.getEventloop(threadId);
        
        assertThat(retrieved).isSameAs(created);
    }
    
    @Test
    void testGetEventloop_returnsNullForUnknownThread() {
        Eventloop eventloop = EventloopManager.getEventloop(999999L);
        
        assertThat(eventloop).isNull();
    }
    
    @Test
    void testMultipleThreads_createSeparateEventloops() throws Exception {
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Eventloop> eventloops = new ArrayList<>();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    eventloops.add(EventloopManager.getCurrentEventloop());
                    latch.countDown();
                });
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(eventloops).hasSize(threadCount);
        assertThat(eventloops).doesNotHaveDuplicates();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(threadCount);
    }
    
    @Test
    void testShutdownAll_stopsAllEventloops() {
        // Create multiple eventloops
        EventloopManager.create();
        EventloopManager.create();
        EventloopManager.create();
        
        assertThat(EventloopManager.getActiveCount()).isEqualTo(3);
        
        boolean success = EventloopManager.shutdownAll(Duration.ofSeconds(5));
        
        assertThat(success).isTrue();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
    }
    
    @Test
    void testShutdownAll_isIdempotent() {
        EventloopManager.create();
        
        boolean first = EventloopManager.shutdownAll(Duration.ofSeconds(5));
        boolean second = EventloopManager.shutdownAll(Duration.ofSeconds(5));
        
        assertThat(first).isTrue();
        assertThat(second).isFalse(); // Already shut down
    }
    
    @Test
    void testCreate_afterShutdown_throwsException() {
        EventloopManager.shutdownAll(Duration.ofSeconds(1));
        
        assertThatThrownBy(() -> EventloopManager.create())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("shutting down");
    }
    
    @Test
    void testGetCurrentEventloop_afterShutdown_throwsException() {
        EventloopManager.shutdownAll(Duration.ofSeconds(1));
        
        assertThatThrownBy(() -> EventloopManager.getCurrentEventloop())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("shutting down");
    }
    
    @Test
    void testGetActiveCount_tracksCorrectly() {
        assertThat(EventloopManager.getActiveCount()).isEqualTo(0);
        
        EventloopManager.create();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
        
        EventloopManager.create();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(2);
        
        EventloopManager.clearCurrentEventloop();
        assertThat(EventloopManager.getActiveCount()).isEqualTo(1);
    }
    
    @Test
    void testConcurrentAccess_threadSafe() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        try {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        Eventloop eventloop = EventloopManager.getCurrentEventloop();
                        if (eventloop != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown(); // Start all threads
            assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(EventloopManager.getActiveCount()).isEqualTo(threadCount);
    }
}
