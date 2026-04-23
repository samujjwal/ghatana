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
 * (as applied in {@link ClickHouseTraceStorage}) prevents data loss. // GH-90000
 */
class SpanBufferTest {

    private static final int MAX_SIZE = 5;
    private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(2); // GH-90000

    private SpanBuffer buffer;

    @BeforeEach
    void setUp() { // GH-90000
        buffer = new SpanBuffer(MAX_SIZE, FLUSH_INTERVAL); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Basic state after construction
    // -------------------------------------------------------------------------

    @Test
    void newBuffer_isEmpty() { // GH-90000
        assertThat(buffer.isEmpty()).isTrue(); // GH-90000
        assertThat(buffer.size()).isZero(); // GH-90000
    }

    @Test
    void newBuffer_shouldNotFlush() { // GH-90000
        assertThat(buffer.shouldFlush()).isFalse(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Null guard
    // -------------------------------------------------------------------------

    @Test
    void add_nullSpan_throwsIllegalArgument() { // GH-90000
        assertThatThrownBy(() -> buffer.add(null)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Size-based flush trigger
    // -------------------------------------------------------------------------

    @Test
    void shouldFlush_whenSizeReachesMax_returnsTrue() { // GH-90000
        for (int i = 0; i < MAX_SIZE; i++) { // GH-90000
            assertThat(buffer.shouldFlush()).isFalse(); // GH-90000
            buffer.add(makeSpan("s" + i)); // GH-90000
        }
        assertThat(buffer.shouldFlush()).isTrue(); // GH-90000
    }

    @Test
    void shouldFlush_belowMaxSize_returnsFalse() { // GH-90000
        buffer.add(makeSpan("x"));
        assertThat(buffer.shouldFlush()).isFalse(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Time-based flush trigger
    // -------------------------------------------------------------------------

    @Test
    void shouldFlush_afterIntervalElapsed_returnsTrueEvenIfEmpty() throws InterruptedException { // GH-90000
        SpanBuffer shortBuffer = new SpanBuffer(1000, Duration.ofMillis(50)); // GH-90000
        shortBuffer.add(makeSpan("t1"));
        Thread.sleep(60); // GH-90000
        assertThat(shortBuffer.shouldFlush()).isTrue(); // GH-90000
    }

    // -------------------------------------------------------------------------
    // flush() behaviour // GH-90000
    // -------------------------------------------------------------------------

    @Test
    void flush_returnsAllBufferedSpans() { // GH-90000
        buffer.add(makeSpan("a"));
        buffer.add(makeSpan("b"));
        buffer.add(makeSpan("c"));

        List<SpanData> flushed = buffer.flush(); // GH-90000

        assertThat(flushed).hasSize(3); // GH-90000
        assertThat(buffer.isEmpty()).isTrue(); // GH-90000
        assertThat(buffer.size()).isZero(); // GH-90000
    }

    @Test
    void flush_emptyBuffer_returnsEmptyList() { // GH-90000
        List<SpanData> flushed = buffer.flush(); // GH-90000
        assertThat(flushed).isEmpty(); // GH-90000
    }

    @Test
    void flush_resetsShouldFlush() { // GH-90000
        for (int i = 0; i < MAX_SIZE; i++) buffer.add(makeSpan("s" + i)); // GH-90000
        assertThat(buffer.shouldFlush()).isTrue(); // GH-90000

        buffer.flush(); // GH-90000
        assertThat(buffer.shouldFlush()).isFalse(); // GH-90000
    }

    @Test
    void flush_returnedList_isIndependentCopy() { // GH-90000
        buffer.add(makeSpan("orig"));
        List<SpanData> flushed = buffer.flush(); // GH-90000

        buffer.add(makeSpan("new"));
        assertThat(flushed).hasSize(1); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Concurrent stress test — verifies external synchronized(this) is sufficient // GH-90000
    // -------------------------------------------------------------------------

    @Test
    void concurrentAdd_withExternalSync_noDataLoss() throws InterruptedException { // GH-90000
        final int threads = 8;
        final int spansPerThread = 500;
        final Object lock = new Object(); // GH-90000
        SpanBuffer shared = new SpanBuffer(10_000, Duration.ofHours(1)); // GH-90000

        ExecutorService exec = Executors.newFixedThreadPool(threads); // GH-90000
        CountDownLatch start = new CountDownLatch(1); // GH-90000
        CountDownLatch done  = new CountDownLatch(threads); // GH-90000

        for (int t = 0; t < threads; t++) { // GH-90000
            final int tid = t;
            exec.submit(() -> { // GH-90000
                try {
                    start.await(); // GH-90000
                    for (int i = 0; i < spansPerThread; i++) { // GH-90000
                        synchronized (lock) { // GH-90000
                            shared.add(makeSpan("t" + tid + "-s" + i)); // GH-90000
                        }
                    }
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                } finally {
                    done.countDown(); // GH-90000
                }
            });
        }

        start.countDown(); // GH-90000
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue(); // GH-90000
        exec.shutdown(); // GH-90000

        synchronized (lock) { // GH-90000
            List<SpanData> all = shared.flush(); // GH-90000
            assertThat(all).hasSize(threads * spansPerThread); // GH-90000
        }
    }

    @Test
    void concurrentAddAndFlush_withExternalSync_totalSpanCountConsistent() throws InterruptedException { // GH-90000
        final int addThreads = 4;
        final int spansPerThread = 200;
        final Object lock = new Object(); // GH-90000
        SpanBuffer shared = new SpanBuffer(50, Duration.ofHours(1)); // GH-90000

        AtomicInteger flushedTotal = new AtomicInteger(0); // GH-90000
        ExecutorService exec = Executors.newFixedThreadPool(addThreads + 1); // GH-90000
        CountDownLatch start = new CountDownLatch(1); // GH-90000
        CountDownLatch done  = new CountDownLatch(addThreads + 1); // GH-90000

        for (int t = 0; t < addThreads; t++) { // GH-90000
            final int tid = t;
            exec.submit(() -> { // GH-90000
                try {
                    start.await(); // GH-90000
                    for (int i = 0; i < spansPerThread; i++) { // GH-90000
                        synchronized (lock) { // GH-90000
                            shared.add(makeSpan("t" + tid + "-" + i)); // GH-90000
                        }
                    }
                } catch (InterruptedException e) { // GH-90000
                    Thread.currentThread().interrupt(); // GH-90000
                } finally {
                    done.countDown(); // GH-90000
                }
            });
        }

        exec.submit(() -> { // GH-90000
            try {
                start.await(); // GH-90000
                for (int i = 0; i < spansPerThread * addThreads / 50; i++) { // GH-90000
                    Thread.sleep(1); // GH-90000
                    synchronized (lock) { // GH-90000
                        flushedTotal.addAndGet(shared.flush().size()); // GH-90000
                    }
                }
            } catch (InterruptedException e) { // GH-90000
                Thread.currentThread().interrupt(); // GH-90000
            } finally {
                done.countDown(); // GH-90000
            }
        });

        start.countDown(); // GH-90000
        done.await(15, TimeUnit.SECONDS); // GH-90000
        exec.shutdown(); // GH-90000

        synchronized (lock) { // GH-90000
            flushedTotal.addAndGet(shared.flush().size()); // GH-90000
        }
        assertThat(flushedTotal.get()).isEqualTo(addThreads * spansPerThread); // GH-90000
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private SpanData makeSpan(String traceId) { // GH-90000
        return SpanData.builder() // GH-90000
                .withServiceName("test-service")
                .withTraceId(traceId) // GH-90000
                .withSpanId(traceId + "-span") // GH-90000
                .withOperationName("test-op")
                .withStartTime(Instant.now()) // GH-90000
                .withEndTime(Instant.now().plusMillis(10)) // GH-90000
                .build(); // GH-90000
    }
}
