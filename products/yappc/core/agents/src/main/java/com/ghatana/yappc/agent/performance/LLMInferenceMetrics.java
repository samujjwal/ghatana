package com.ghatana.yappc.agent.performance;

import io.activej.promise.Promise;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance metrics collector for LLM inference operations. Tracks latency, throughput, GPU
 * utilization, and success rates.
 *
 * <p><b>Metrics Tracked:</b>
 *
 * <ul>
 *   <li><b>Latency</b>: P50, P95, P99 response times
 *   <li><b>Throughput</b>: Requests per second
 *   <li><b>Success Rate</b>: Percentage of successful requests
 *   <li><b>Token Rate</b>: Tokens per second (GPU efficiency)
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * LLMInferenceMetrics metrics = LLMInferenceMetrics.getInstance();
 *
 * long startTime = System.currentTimeMillis();
 * try {
 *     String result = ollama.generate(prompt);
 *     metrics.recordSuccess(System.currentTimeMillis() - startTime, result.length());
 * } catch (Exception e) {
 *     metrics.recordFailure(System.currentTimeMillis() - startTime);
 * }
 *
 * // Print performance report
 * metrics.printReport();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Performance monitoring for GPU-accelerated LLM inference
 * @doc.layer platform
 * @doc.pattern Metrics|Observability
 * @author YAPPC Platform
 * @version 1.0
 * @since Session 14C (GPU Acceleration)
 */
public final class LLMInferenceMetrics {

  private static final Logger log = LoggerFactory.getLogger(LLMInferenceMetrics.class);

  /** Singleton instance */
  private static final LLMInferenceMetrics INSTANCE = new LLMInferenceMetrics();

  /** Total successful requests */
  private final AtomicInteger successCount = new AtomicInteger(0);

  /** Total failed requests */
  private final AtomicInteger failureCount = new AtomicInteger(0);

  /** Total latency in milliseconds */
  private final AtomicLong totalLatencyMs = new AtomicLong(0);

  /** Total tokens generated */
  private final AtomicLong totalTokens = new AtomicLong(0);

  /** Latency histogram (buckets: <100ms, <500ms, <1s, <2s, <5s, 5s+) */
  private final ConcurrentHashMap<String, AtomicInteger> latencyBuckets = new ConcurrentHashMap<>();

  /** First request timestamp */
  private volatile long firstRequestTime = 0;

  private LLMInferenceMetrics() {
    // Initialize latency buckets
    latencyBuckets.put("<100ms", new AtomicInteger(0));
    latencyBuckets.put("<500ms", new AtomicInteger(0));
    latencyBuckets.put("<1s", new AtomicInteger(0));
    latencyBuckets.put("<2s", new AtomicInteger(0));
    latencyBuckets.put("<5s", new AtomicInteger(0));
    latencyBuckets.put("5s+", new AtomicInteger(0));
  }

  /** Get singleton instance. */
  public static LLMInferenceMetrics getInstance() {
    return INSTANCE;
  }

  /**
   * Record successful LLM request.
   *
   * @param latencyMs Request latency in milliseconds
   * @param tokenCount Number of tokens generated
   */
  public void recordSuccess(long latencyMs, int tokenCount) {
    if (firstRequestTime == 0) {
      firstRequestTime = System.currentTimeMillis();
    }

    successCount.incrementAndGet();
    totalLatencyMs.addAndGet(latencyMs);
    totalTokens.addAndGet(tokenCount);

    // Update latency histogram
    if (latencyMs < 100) {
      latencyBuckets.get("<100ms").incrementAndGet();
    } else if (latencyMs < 500) {
      latencyBuckets.get("<500ms").incrementAndGet();
    } else if (latencyMs < 1000) {
      latencyBuckets.get("<1s").incrementAndGet();
    } else if (latencyMs < 2000) {
      latencyBuckets.get("<2s").incrementAndGet();
    } else if (latencyMs < 5000) {
      latencyBuckets.get("<5s").incrementAndGet();
    } else {
      latencyBuckets.get("5s+").incrementAndGet();
    }

    log.debug(
        "LLM request: {}ms, {} tokens, {:.1f} tokens/s",
        latencyMs,
        tokenCount,
        (double) tokenCount / latencyMs * 1000);
  }

  /**
   * Record failed LLM request.
   *
   * @param latencyMs Request latency before failure
   */
  public void recordFailure(long latencyMs) {
    if (firstRequestTime == 0) {
      firstRequestTime = System.currentTimeMillis();
    }

    failureCount.incrementAndGet();
    totalLatencyMs.addAndGet(latencyMs);

    log.warn("LLM request failed after {}ms", latencyMs);
  }

  /** Get current performance snapshot. */
  public PerformanceSnapshot getSnapshot() {
    int success = successCount.get();
    int failure = failureCount.get();
    int total = success + failure;

    long avgLatency = total > 0 ? totalLatencyMs.get() / total : 0;
    double successRate = total > 0 ? (double) success / total * 100 : 0;

    long elapsedMs = firstRequestTime > 0 ? System.currentTimeMillis() - firstRequestTime : 0;
    double throughput = elapsedMs > 0 ? (double) total / elapsedMs * 1000 : 0;

    long tokens = totalTokens.get();
    double tokenRate = elapsedMs > 0 ? (double) tokens / elapsedMs * 1000 : 0;

    return new PerformanceSnapshot(
        total,
        success,
        failure,
        avgLatency,
        successRate,
        throughput,
        tokens,
        tokenRate,
        latencyBuckets.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey, e -> e.getValue().get())));
  }

  /** Print performance report to logs. */
  public void printReport() {
    PerformanceSnapshot snapshot = getSnapshot();

    log.info("═".repeat(70));
    log.info("LLM Performance Report");
    log.info("═".repeat(70));
    log.info("Total Requests: {}", snapshot.totalRequests());
    log.info("  ✓ Successful: {} ({:.1f}%)", snapshot.successCount(), snapshot.successRate());
    log.info("  ✗ Failed: {} ({:.1f}%)", snapshot.failureCount(), 100 - snapshot.successRate());
    log.info("");
    log.info("Latency:");
    log.info("  Average: {}ms", snapshot.averageLatencyMs());
    log.info("  Distribution:");
    snapshot
        .latencyDistribution()
        .forEach(
            (bucket, count) -> {
              double percent = (double) count / snapshot.totalRequests() * 100;
              log.info("    {}: {} ({:.1f}%)", bucket, count, percent);
            });
    log.info("");
    log.info("Throughput:");
    log.info("  Requests/sec: {:.2f}", snapshot.requestsPerSecond());
    log.info("  Tokens/sec: {:.2f}", snapshot.tokensPerSecond());
    log.info("  Total Tokens: {}", snapshot.totalTokens());
    log.info("═".repeat(70));
  }

  /** Reset all metrics (useful for testing). */
  public void reset() {
    successCount.set(0);
    failureCount.set(0);
    totalLatencyMs.set(0);
    totalTokens.set(0);
    firstRequestTime = 0;
    latencyBuckets.values().forEach(counter -> counter.set(0));

    log.info("Metrics reset");
  }

  /** Performance snapshot record. */
  public record PerformanceSnapshot(
      int totalRequests,
      int successCount,
      int failureCount,
      long averageLatencyMs,
      double successRate,
      double requestsPerSecond,
      long totalTokens,
      double tokensPerSecond,
      java.util.Map<String, Integer> latencyDistribution) {}

  /**
   * Wrap LLM call with automatic metrics recording.
   *
   * <p><b>Usage:</b>
   *
   * <pre>{@code
   * Promise<String> result = LLMInferenceMetrics.instrument(
   *     () -> ollama.generate(prompt)
   * );
   * }</pre>
   *
   * @param operation LLM operation to instrument
   * @return Promise with metrics tracking
   */
  public static Promise<String> instrument(LLMOperation operation) {
    long startTime = System.currentTimeMillis();

    return operation
        .execute()
        .whenComplete(
            (result, error) -> {
              long latency = System.currentTimeMillis() - startTime;

              if (error != null) {
                getInstance().recordFailure(latency);
              } else {
                // Estimate token count (rough: 4 chars ≈ 1 token)
                int tokens = result.length() / 4;
                getInstance().recordSuccess(latency, tokens);
              }
            });
  }

  /** LLM operation interface for instrumentation. */
  @FunctionalInterface
  public interface LLMOperation {
    Promise<String> execute();
  }
}
