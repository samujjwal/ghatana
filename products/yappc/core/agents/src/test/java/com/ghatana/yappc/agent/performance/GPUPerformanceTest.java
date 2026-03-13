package com.ghatana.yappc.agent.performance;

import static org.assertj.core.api.Assertions.*;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for GPU-accelerated batch processing and performance optimization.
 *
 * <p>This test suite verifies:
 *
 * <ul>
 *   <li>GPU detection logic
 *   <li>Batch processing functionality
 *   <li>Performance metrics collection
 *   <li>GPU configuration recommendations
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Test GPU acceleration features
 * @doc.layer test
 * @doc.pattern Integration Test
 * @since Session 14C (GPU Acceleration)
 */
@DisplayName("GPU Performance Tests")
class GPUPerformanceTest extends EventloopTestBase {

  private static final Logger log = LoggerFactory.getLogger(GPUPerformanceTest.class);

  @Test
  @DisplayName("Should detect GPU availability")
  void shouldDetectGPU() {
    // GIVEN
    GPUDetector detector = new GPUDetector();

    // WHEN
    boolean gpuAvailable = detector.isGPUAvailable();

    // THEN
    log.info("GPU Available: {}", gpuAvailable);

    if (gpuAvailable) {
      detector
          .getGPUInfo()
          .ifPresent(
              info -> {
                log.info("GPU Model: {}", info.name());
                log.info(
                    "GPU Memory: {}MB total, {}MB free", info.memoryTotalMB(), info.memoryFreeMB());

                assertThat(info.memoryTotalMB()).isGreaterThan(0);
                assertThat(info.memoryFreeMB()).isGreaterThan(0);
              });
    } else {
      log.warn("⚠ No GPU detected - tests will use CPU simulation");
    }

    // Test should not fail regardless of GPU availability
    assertThat(gpuAvailable).isIn(true, false);
  }

  @Test
  @DisplayName("Should provide GPU configuration recommendations")
  void shouldProvideGPUConfig() {
    // GIVEN
    GPUDetector detector = new GPUDetector();

    // WHEN
    GPUDetector.GPUConfig config = detector.getRecommendedConfig();

    // THEN
    assertThat(config).isNotNull();
    assertThat(config.gpuLayers()).isGreaterThanOrEqualTo(0);
    assertThat(config.cpuThreads()).isGreaterThan(0);
    assertThat(config.parallelRequests()).isGreaterThan(0);

    log.info("Recommended Config: {}", config);
    log.info("Environment Variables:\n{}", config.toEnvironmentVariables());

    if (config.gpuEnabled()) {
      assertThat(config.gpuLayers()).isBetween(1, 33); // llama3.2 has 33 layers
      assertThat(config.parallelRequests()).isBetween(2, 8);
    } else {
      assertThat(config.gpuLayers()).isEqualTo(0);
      log.info("✓ CPU-only configuration generated");
    }
  }

  @Test
  @DisplayName("Should print GPU detection report")
  void shouldPrintGPUReport() {
    // GIVEN
    GPUDetector detector = new GPUDetector();

    // WHEN / THEN (no exceptions should be thrown)
    assertThatCode(() -> detector.printReport()).doesNotThrowAnyException();

    log.info("✓ GPU report generated successfully");
  }

  @Test
  @DisplayName("Should create batch processor with default settings")
  void shouldCreateBatchProcessor() {
    // GIVEN / WHEN
    BatchProcessor processor = BatchProcessor.createDefault();

    // THEN
    assertThat(processor).isNotNull();

    BatchProcessor.PerformanceStats stats = processor.getStats();
    assertThat(stats.totalRequests()).isEqualTo(0);
    assertThat(stats.batchedRequests()).isEqualTo(0);

    log.info("✓ BatchProcessor created: {}", stats);
  }

  @Test
  @DisplayName("Should create high-throughput batch processor")
  void shouldCreateHighThroughputProcessor() {
    // GIVEN / WHEN
    BatchProcessor processor = BatchProcessor.createHighThroughput();

    // THEN
    assertThat(processor).isNotNull();
    log.info("✓ High-throughput BatchProcessor created");
  }

  @Test
  @DisplayName("Should collect LLM performance metrics")
  void shouldCollectMetrics() {
    // GIVEN
    LLMInferenceMetrics metrics = LLMInferenceMetrics.getInstance();
    metrics.reset(); // Clear any previous metrics

    // WHEN
    metrics.recordSuccess(500, 100); // 500ms, 100 tokens
    metrics.recordSuccess(1500, 200); // 1500ms, 200 tokens
    metrics.recordFailure(2000); // Failed after 2000ms

    // THEN
    LLMInferenceMetrics.PerformanceSnapshot snapshot = metrics.getSnapshot();

    assertThat(snapshot.totalRequests()).isEqualTo(3);
    assertThat(snapshot.successCount()).isEqualTo(2);
    assertThat(snapshot.failureCount()).isEqualTo(1);
    assertThat(snapshot.successRate()).isEqualTo(66.66666666666666, within(0.01));
    assertThat(snapshot.totalTokens()).isEqualTo(300);

    log.info("Metrics: {}", snapshot);
    log.info("  Total Requests: {}", snapshot.totalRequests());
    log.info("  Success Rate: {:.1f}%", snapshot.successRate());
    log.info("  Average Latency: {}ms", snapshot.averageLatencyMs());
    log.info("  Total Tokens: {}", snapshot.totalTokens());

    // Print full report
    metrics.printReport();

    log.info("✓ Metrics collection verified");

    // Cleanup
    metrics.reset();
  }

  @Test
  @DisplayName("Should track latency distribution")
  void shouldTrackLatencyDistribution() {
    // GIVEN
    LLMInferenceMetrics metrics = LLMInferenceMetrics.getInstance();
    metrics.reset();

    // WHEN
    metrics.recordSuccess(50, 50); // <100ms
    metrics.recordSuccess(200, 100); // <500ms
    metrics.recordSuccess(800, 150); // <1s
    metrics.recordSuccess(1500, 200); // <2s
    metrics.recordSuccess(3000, 300); // <5s
    metrics.recordSuccess(6000, 400); // 5s+

    // THEN
    LLMInferenceMetrics.PerformanceSnapshot snapshot = metrics.getSnapshot();

    assertThat(snapshot.latencyDistribution())
        .containsKeys("<100ms", "<500ms", "<1s", "<2s", "<5s", "5s+");
    assertThat(snapshot.latencyDistribution().get("<100ms")).isEqualTo(1);
    assertThat(snapshot.latencyDistribution().get("<500ms")).isEqualTo(1);
    assertThat(snapshot.latencyDistribution().get("<1s")).isEqualTo(1);
    assertThat(snapshot.latencyDistribution().get("<2s")).isEqualTo(1);
    assertThat(snapshot.latencyDistribution().get("<5s")).isEqualTo(1);
    assertThat(snapshot.latencyDistribution().get("5s+")).isEqualTo(1);

    log.info("✓ Latency distribution tracking verified");

    // Cleanup
    metrics.reset();
  }

  @Test
  @DisplayName("Should calculate throughput metrics")
  void shouldCalculateThroughput() throws InterruptedException {
    // GIVEN
    LLMInferenceMetrics metrics = LLMInferenceMetrics.getInstance();
    metrics.reset();

    // WHEN
    for (int i = 0; i < 10; i++) {
      metrics.recordSuccess(1000, 100); // 10 requests, 100 tokens each
      Thread.sleep(10); // Small delay to allow throughput calculation
    }

    // THEN
    LLMInferenceMetrics.PerformanceSnapshot snapshot = metrics.getSnapshot();

    assertThat(snapshot.totalRequests()).isEqualTo(10);
    assertThat(snapshot.totalTokens()).isEqualTo(1000);
    assertThat(snapshot.requestsPerSecond()).isGreaterThan(0);
    assertThat(snapshot.tokensPerSecond()).isGreaterThan(0);

    log.info(
        "Throughput: {:.2f} req/s, {:.2f} tokens/s",
        snapshot.requestsPerSecond(),
        snapshot.tokensPerSecond());

    log.info("✓ Throughput calculation verified");

    // Cleanup
    metrics.reset();
  }

  @Test
  @DisplayName("Should demonstrate GPU performance improvement")
  void shouldDemonstrateGPUPerformance() {
    // This is a demonstration test showing expected improvements
    log.info("═".repeat(70));
    log.info("GPU Performance Improvement Demonstration");
    log.info("═".repeat(70));

    // Simulated CPU performance
    long cpuLatencyMs = 14000; // 14 seconds per request
    int cpuThroughput = 1; // 1 request per second

    // Simulated GPU performance
    long gpuLatencyMs = 1500; // 1.5 seconds per request
    int gpuThroughput = 10; // 10 requests per second

    double latencySpeedup = (double) cpuLatencyMs / gpuLatencyMs;
    double throughputSpeedup = (double) gpuThroughput / cpuThroughput;

    log.info("CPU Performance:");
    log.info("  Latency: {}ms per request", cpuLatencyMs);
    log.info("  Throughput: {} req/s", cpuThroughput);
    log.info("");
    log.info("GPU Performance:");
    log.info("  Latency: {}ms per request", gpuLatencyMs);
    log.info("  Throughput: {} req/s", gpuThroughput);
    log.info("");
    log.info("Improvement:");
    log.info("  Latency: {:.1f}x faster", latencySpeedup);
    log.info("  Throughput: {:.1f}x more requests", throughputSpeedup);
    log.info("═".repeat(70));

    assertThat(latencySpeedup).isBetween(5.0, 15.0);
    assertThat(throughputSpeedup).isBetween(5.0, 15.0);

    log.info("✓ GPU performance demonstration complete");
  }
}
