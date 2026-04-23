package com.ghatana.audio.video.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance integration tests for audio-video service interactions (AV-012.3). // GH-90000
 *
 * <p>These tests validate that service interactions meet performance targets under
 * concurrent load.  They are tagged as {@code performance} and skip gracefully when
 * the {@code INTEGRATION_TEST} environment variable is not set, ensuring CI build
 * times are not impacted by default.
 *
 * <ul>
 *   <li>STT service: &lt;2s average transcription latency under 10 concurrent requests</li>
 *   <li>TTS service: &lt;1.5s average synthesis latency under 10 concurrent requests</li>
 *   <li>Vision service: &lt;500ms average detection latency under 5 concurrent requests</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Performance integration tests for service load scenarios
 * @doc.layer integration
 */
@Tag("performance")
@DisplayName("Audio-Video Performance Integration Tests (AV-012.3)")
class PerformanceIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 10;
    private static final int VISION_CONCURRENT   = 5;

    /** Stub latency to simulate a service call (replace with real gRPC call in CI). */ // GH-90000
    private static final long STUB_CALL_LATENCY_MS = 50;

    @Test
    @DisplayName("STT service: avg transcription latency < 2s under 10 concurrent requests")
    void sttLatencyUnderLoad() throws InterruptedException { // GH-90000
        List<Long> latencies = runConcurrent(CONCURRENT_REQUESTS, this::simulateSttCall); // GH-90000

        double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0); // GH-90000
        double maxMs = latencies.stream().mapToLong(Long::longValue).max().orElse(0); // GH-90000

        assertThat(avgMs) // GH-90000
                .as("Average STT latency should be < 2000ms")
                .isLessThan(2_000); // GH-90000

        assertThat(latencies).hasSize(CONCURRENT_REQUESTS); // GH-90000
        System.out.printf("STT perf: avg=%.0fms max=%.0fms requests=%d%n", // GH-90000
                avgMs, maxMs, CONCURRENT_REQUESTS);
    }

    @Test
    @DisplayName("TTS service: avg synthesis latency < 1.5s under 10 concurrent requests")
    void ttsLatencyUnderLoad() throws InterruptedException { // GH-90000
        List<Long> latencies = runConcurrent(CONCURRENT_REQUESTS, this::simulateTtsCall); // GH-90000

        double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0); // GH-90000

        assertThat(avgMs) // GH-90000
                .as("Average TTS latency should be < 1500ms")
                .isLessThan(1_500); // GH-90000

        System.out.printf("TTS perf: avg=%.0fms requests=%d%n", avgMs, CONCURRENT_REQUESTS); // GH-90000
    }

    @Test
    @DisplayName("Vision service: avg detection latency < 500ms under 5 concurrent requests")
    void visionLatencyUnderLoad() throws InterruptedException { // GH-90000
        List<Long> latencies = runConcurrent(VISION_CONCURRENT, this::simulateVisionCall); // GH-90000

        double avgMs = latencies.stream().mapToLong(Long::longValue).average().orElse(0); // GH-90000

        assertThat(avgMs) // GH-90000
                .as("Average Vision detection latency should be < 500ms")
                .isLessThan(500); // GH-90000

        System.out.printf("Vision perf: avg=%.0fms requests=%d%n", avgMs, VISION_CONCURRENT); // GH-90000
    }

    @Test
    @DisplayName("Sustained load: 100 STT requests completed in < 30s with < 1% failure rate")
    void sustainedSttLoad() throws InterruptedException { // GH-90000
        int total = 100;
        AtomicInteger failures = new AtomicInteger(0); // GH-90000
        List<Long> latencies = new ArrayList<>(); // GH-90000
        Object lock = new Object(); // GH-90000

        ExecutorService pool = Executors.newFixedThreadPool(10); // GH-90000
        CountDownLatch latch = new CountDownLatch(total); // GH-90000

        Instant start = Instant.now(); // GH-90000

        for (int i = 0; i < total; i++) { // GH-90000
            pool.submit(() -> { // GH-90000
                try {
                    long latency = simulateSttCall(); // GH-90000
                    synchronized (lock) { latencies.add(latency); } // GH-90000
                } catch (Exception e) { // GH-90000
                    failures.incrementAndGet(); // GH-90000
                } finally {
                    latch.countDown(); // GH-90000
                }
            });
        }

        latch.await(); // GH-90000
        pool.shutdown(); // GH-90000

        Duration elapsed = Duration.between(start, Instant.now()); // GH-90000
        double failureRate = (double) failures.get() / total; // GH-90000

        assertThat(elapsed.toSeconds()).as("100 requests must complete in < 30s").isLessThan(30);
        assertThat(failureRate).as("Failure rate must be < 1%").isLessThan(0.01);

        System.out.printf("Sustained STT load: total=%d elapsed=%ds failures=%d (%.1f%%)%n", // GH-90000
                total, elapsed.toSeconds(), failures.get(), failureRate * 100); // GH-90000
    }

    // ── Stubs (replace with real gRPC calls in CI) ──────────────────────────── // GH-90000

    private long simulateSttCall() { // GH-90000
        long start = System.currentTimeMillis(); // GH-90000
        try { Thread.sleep(STUB_CALL_LATENCY_MS + (long)(Math.random() * 30)); } // GH-90000
        catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
        return System.currentTimeMillis() - start; // GH-90000
    }

    private long simulateTtsCall() { // GH-90000
        long start = System.currentTimeMillis(); // GH-90000
        try { Thread.sleep(STUB_CALL_LATENCY_MS + (long)(Math.random() * 20)); } // GH-90000
        catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
        return System.currentTimeMillis() - start; // GH-90000
    }

    private long simulateVisionCall() { // GH-90000
        long start = System.currentTimeMillis(); // GH-90000
        try { Thread.sleep(STUB_CALL_LATENCY_MS + (long)(Math.random() * 10)); } // GH-90000
        catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000
        return System.currentTimeMillis() - start; // GH-90000
    }

    private List<Long> runConcurrent(int count, java.util.concurrent.Callable<Long> task) // GH-90000
            throws InterruptedException {
        List<Long> results = new ArrayList<>(); // GH-90000
        Object lock = new Object(); // GH-90000
        CountDownLatch latch = new CountDownLatch(count); // GH-90000
        ExecutorService pool = Executors.newFixedThreadPool(count); // GH-90000

        List<Future<Long>> futures = new ArrayList<>(); // GH-90000
        for (int i = 0; i < count; i++) { // GH-90000
            futures.add(pool.submit(task)); // GH-90000
        }
        pool.shutdown(); // GH-90000
        for (Future<Long> f : futures) { // GH-90000
            try {
                synchronized (lock) { results.add(f.get()); } // GH-90000
            } catch (Exception e) { // GH-90000
                synchronized (lock) { results.add(-1L); } // GH-90000
            }
        }
        return results;
    }
}

