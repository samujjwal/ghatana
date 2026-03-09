package com.ghatana.stt.core.metrics;

import com.ghatana.stt.core.api.EngineMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricsCollector.
 *
 * @doc.type test
 * @doc.purpose Test real-time metrics collection
 * @doc.layer core
 */
@DisplayName("MetricsCollector Tests")
class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    @DisplayName("Should start with zero metrics")
    void shouldStartWithZeroMetrics() {
        // WHEN
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.realTimeFactor()).isEqualTo(0.0f);
        assertThat(metrics.activeSessions()).isEqualTo(0);
        assertThat(metrics.totalTranscriptions()).isEqualTo(0);
        assertThat(metrics.averageConfidence()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Should record transcription metrics")
    void shouldRecordTranscription() {
        // GIVEN
        long audioDurationMs = 1000; // 1 second
        long processingTimeMs = 250;  // 250ms
        float confidence = 0.95f;

        // WHEN
        collector.recordTranscription(audioDurationMs, processingTimeMs, confidence);
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.totalTranscriptions()).isEqualTo(1);
        assertThat(metrics.realTimeFactor()).isEqualTo(0.25f); // 250ms / 1000ms
        assertThat(metrics.averageConfidence()).isCloseTo(0.95f, within(0.01f));
        assertThat(metrics.averageLatencyMs()).isEqualTo(250);
    }

    @Test
    @DisplayName("Should calculate average confidence correctly")
    void shouldCalculateAverageConfidence() {
        // WHEN
        collector.recordTranscription(1000, 100, 0.9f);
        collector.recordTranscription(1000, 100, 0.8f);
        collector.recordTranscription(1000, 100, 1.0f);

        EngineMetrics metrics = collector.getMetrics();

        // THEN
        float expectedAvg = (0.9f + 0.8f + 1.0f) / 3;
        assertThat(metrics.averageConfidence()).isCloseTo(expectedAvg, within(0.01f));
    }

    @Test
    @DisplayName("Should calculate real-time factor correctly")
    void shouldCalculateRealTimeFactor() {
        // GIVEN - Process 2 seconds of audio in 1 second (2x faster than real-time)
        collector.recordTranscription(2000, 1000, 0.9f);

        // WHEN
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.realTimeFactor()).isEqualTo(0.5f);
    }

    @Test
    @DisplayName("Should track active sessions")
    void shouldTrackActiveSessions() {
        // WHEN
        collector.recordSessionStart();
        collector.recordSessionStart();
        EngineMetrics metrics1 = collector.getMetrics();

        collector.recordSessionEnd();
        EngineMetrics metrics2 = collector.getMetrics();

        // THEN
        assertThat(metrics1.activeSessions()).isEqualTo(2);
        assertThat(metrics2.activeSessions()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle wake word detections")
    void shouldHandleWakeWordDetections() {
        // WHEN
        collector.recordWakeWordDetection();
        collector.recordWakeWordDetection();
        collector.recordWakeWordDetection();

        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.wakeWordAccuracy()).isGreaterThan(0.0f);
    }

    @Test
    @DisplayName("Should calculate average latency")
    void shouldCalculateAverageLatency() {
        // WHEN
        collector.recordTranscription(1000, 100, 0.9f);
        collector.recordTranscription(1000, 200, 0.9f);
        collector.recordTranscription(1000, 300, 0.9f);

        EngineMetrics metrics = collector.getMetrics();

        // THEN
        float expectedAvg = (100 + 200 + 300) / 3f;
        assertThat(metrics.averageLatencyMs()).isEqualTo((long) expectedAvg);
    }

    @Test
    @DisplayName("Should reset metrics")
    void shouldResetMetrics() {
        // GIVEN
        collector.recordTranscription(1000, 100, 0.9f);
        collector.recordSessionStart();

        // WHEN
        collector.reset();
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.totalTranscriptions()).isEqualTo(0);
        assertThat(metrics.realTimeFactor()).isEqualTo(0.0f);
        assertThat(metrics.averageConfidence()).isEqualTo(0.0f);
        // Active sessions should NOT be reset (they represent current state)
    }

    @Test
    @DisplayName("Should handle zero audio duration")
    void shouldHandleZeroAudioDuration() {
        // WHEN
        collector.recordTranscription(0, 100, 0.9f);
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.realTimeFactor()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Should handle large numbers of transcriptions")
    void shouldHandleLargeNumbers() {
        // WHEN
        for (int i = 0; i < 10000; i++) {
            collector.recordTranscription(1000, 250, 0.95f);
        }

        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.totalTranscriptions()).isEqualTo(10000);
        assertThat(metrics.realTimeFactor()).isCloseTo(0.25f, within(0.01f));
        assertThat(metrics.averageConfidence()).isCloseTo(0.95f, within(0.01f));
    }

    @Test
    @DisplayName("Should generate detailed report")
    void shouldGenerateDetailedReport() {
        // GIVEN
        collector.recordTranscription(1000, 250, 0.95f);
        collector.recordSessionStart();

        // WHEN
        String report = collector.getDetailedReport();

        // THEN
        assertThat(report).contains("STT Engine Metrics");
        assertThat(report).contains("Total Transcriptions: 1");
        assertThat(report).contains("Real-Time Factor: 0.250");
        assertThat(report).contains("Active Sessions: 1");
        assertThat(report).contains("Average Confidence: 0.950");
        assertThat(report).contains("Average Latency: 250");
    }

    @Test
    @DisplayName("Should track memory usage")
    void shouldTrackMemoryUsage() {
        // WHEN
        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.memoryUsageBytes()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle concurrent operations")
    void shouldHandleConcurrentOperations() throws InterruptedException {
        // WHEN
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    collector.recordTranscription(1000, 250, 0.9f);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        EngineMetrics metrics = collector.getMetrics();

        // THEN
        assertThat(metrics.totalTranscriptions()).isEqualTo(1000);
    }

    private org.assertj.core.data.Offset<Float> within(float offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}

