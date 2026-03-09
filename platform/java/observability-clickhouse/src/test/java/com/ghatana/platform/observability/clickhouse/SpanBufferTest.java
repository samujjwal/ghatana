package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.observability.trace.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SpanBuffer}.
 *
 * Covers: size-trigger, time-trigger, empty flush, null guard,
 * and a concurrent stress test verifying that external synchronization
 * (as applied in {@link ClickHouseTraceStorage}) prevents data loss.
 */
class SpanBufferTest {

    private static final int MAX_SIZE = 5;
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(2);

    private SpanBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new SpanBuffer(MAX_SIZE, FLUSH_INTERVAL);
    }

    // -------------------------------------------------------------------------
    // Basic state after construction
    // -------------------------------------------------------------------------

    @Test
    void newBuffer_isEmpty() {
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.size()).isZero();
    }

    @Test
    void newBuffer_shouldNotFlush() {
        assertThat(buffer.shouldFlush()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Null guard
    // -------------------------------------------------------------------------

    @Test
    void add_nullSpan_throwsIllegalArgument() {
        assertThatThrownBy(() -> buffer.add(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // Size-based flush trigger
    // -------------------------------------------------------------------------

    @Test
    void shouldFlush_whenSizeReachesMax_returnsTrue() {
        for (int i = 0; i < MAX_SIZE; i++) {
            assertThat(buffer.shouldFlush()).isFalse();
            buffer.add(makeSpan("s" + i));
        }
        assertThat(buffer.shouldFlush()).isTrue();
    }

    @Test
    void shouldFlush_belowMaxSize_returnsFalse() {
        buffer.add(makeSpan("x"));
        assertThat(buffer.shouldFlush()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Time-based flush trigger
    // -------------------------------------------------------------------------

    @Test
    void shouldFlush_afterIntervalElapsed_returnsTrueEvenIfEmpty() throws InterruptedException {
        SpanBuffer shortBuffer = new SpanBuffer(1000, Duration.ofMillis(50));
        shortBuffer.add(makeSpan("t1"));
        Thread.sleep(60);
        assertThat(shortBuffer.shouldFlush()).isTrue();
    }

    // -------------------------------------------------------------------------
    // flush() behaviour
    // -------------------------------------------------------------------------

    @Test
    void flush_returnsAllBufferedSpans() {
        buffer.add(makeSpan("a"));
        buffer.add(makeSpan("b"));
        buffer.add(makeSpan("c"));

        List<SpanData> flushed = buffer.flush();

        assertThat(flushed).hasSize(3);
        assertThat(buffer.isEmpty()).isTrue();
        assertThat(buffer.size()).isZero();
    }

    @Test
    void flush_emptyBuffer_returnsEmptyList() {
        List<SpanData> flushed = buffer.flush();
        assertThat(flushed).isEmpty();
    }

    @Test
    void flush_resetsShouldFlush() {
        for (int i = 0; i < MAX_SIZE; i++) buffer.add(makeSpan("s" + i));
        assertThat(buffer.shouldFlush()).isTrue();

        buffer.flush();
        assertThat(buffer.shouldFlush()).isFalse();
    }

    @Test
    void flush_returnedList_isIndependentCopy() {
        buffer.add(makeSpan("orig"));
        List<SpanData> flushed = buffer.flush();

        buffer.add(makeSpan("new"));
        assertThat(flushed).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Concurrent stress test — verifies external synchronized(this) is sufficient
    // -------------------------------------------------------------------------

    @Test
    void concurrentAdd_withExternalSync_noDataLoss() throws InterruptedException {
        final int threads = 8;
        final int spansPerThread = 500;
        final Object lock = new Object();
        SpanBuffer shared = new SpanBuffer(10_000, Duration.ofHours(1));

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < spansPerThread; i++) {
                        synchronized (lock) {
                            shared.add(makeSpan("t" + tid + "-s" + i));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        exec.shutdown();

        synchronized (lock) {
            List<SpanData> all = shared.flush();
            assertThat(all).hasSize(threads * spansPerThread);
        }
    }

    @Test
    void concurrentAddAndFlush_withExternalSync_totalSpanCountConsistent() throws InterruptedException {
        final int addThreads = 4;
        final int spansPerThread = 200;
        final Object lock = new Object();
        SpanBuffer shared = new SpanBuffer(50, Duration.ofHours(1));

        AtomicInteger flushedTotal = new AtomicInteger(0);
        ExecutorService exec = Executors.newFixedThreadPool(addThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(addThreads + 1);

        for (int t = 0; t < addThreads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < spansPerThread; i++) {
                        synchronized (lock) {
                            shared.add(makeSpan("t" + tid + "-" + i));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        exec.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < spansPerThread * addThreads / 50; i++) {
                    Thread.sleep(1);
                    synchronized (lock) {
                        flushedTotal.addAndGet(shared.flush().size());
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        done.await(15, TimeUnit.SECONDS);
        exec.shutdown();

        synchronized (lock) {
            flushedTotal.addAndGet(shared.flush().size());
        }
        assertThat(flushedTotal.get()).isEqualTo(addThreads * spansPerThread);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private SpanData makeSpan(String traceId) {
        return SpanData.builder()
                .withServiceName("test-service")
                .withTraceId(traceId)
                .withSpanId(traceId + "-span")
                .withOperationName("test-op")
                .withStartTime(Instant.now())
                .withEndTime(Instant.now().plusMillis(10))
                .build();
    }
}
