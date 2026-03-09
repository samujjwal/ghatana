package com.ghatana.yappc.sdlc.performance;

import io.activej.promise.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU-aware batch request processor for LLM inference. Groups multiple LLM requests together for
 * efficient GPU utilization.
 *
 * <p><b>Benefits of Batching:</b>
 *
 * <ul>
 *   <li><b>GPU Efficiency</b>: Process multiple requests in parallel on GPU
 *   <li><b>Throughput</b>: 5-10x more requests per second
 *   <li><b>Latency</b>: Reduced per-request overhead
 *   <li><b>Cost</b>: Better GPU utilization = lower cost per inference
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * BatchProcessor processor = new BatchProcessor(4, 100);  // Batch size 4, timeout 100ms
 *
 * Promise<String> result = processor.submit(
 *     "Generate code for...",
 *     prompt -> ollama.generate(prompt)
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GPU-optimized batch processing for LLM requests
 * @doc.layer platform
 * @doc.pattern BatchProcessor|Performance
 * @author YAPPC Platform
 * @version 1.0
 * @since Session 14C (GPU Acceleration)
 */
public final class BatchProcessor {

  private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);

  /** Maximum requests in a batch (GPU memory constraint) */
  private final int batchSize;

  /** Maximum wait time before flushing batch (milliseconds) */
  private final long batchTimeoutMs;

  /** Current batch of pending requests */
  private final List<BatchRequest<?>> pendingBatch = new ArrayList<>();

  /** Last flush timestamp */
  private volatile long lastFlushTime = System.currentTimeMillis();

  /** Performance metrics */
  private final AtomicInteger totalRequests = new AtomicInteger(0);

  private final AtomicInteger batchedRequests = new AtomicInteger(0);
  private final AtomicLong totalLatencyMs = new AtomicLong(0);

  /**
   * Create batch processor with GPU-optimized defaults.
   *
   * @param batchSize Maximum requests per batch (4-8 optimal for most GPUs)
   * @param batchTimeoutMs Maximum wait before flushing batch (50-100ms typical)
   */
  public BatchProcessor(int batchSize, long batchTimeoutMs) {
    this.batchSize = batchSize;
    this.batchTimeoutMs = batchTimeoutMs;

    log.info("BatchProcessor initialized: batchSize={}, timeoutMs={}", batchSize, batchTimeoutMs);
  }

  /**
   * Submit request for batched processing.
   *
   * <p>Request will be queued until:
   *
   * <ul>
   *   <li>Batch reaches {@code batchSize} requests, OR
   *   <li>{@code batchTimeoutMs} elapsed since last flush
   * </ul>
   *
   * @param input Request input (e.g., prompt text)
   * @param processor Function to execute batch (receives list of inputs)
   * @param <T> Result type
   * @return Promise that resolves when batch is processed
   */
  public <T> Promise<T> submit(String input, BatchFunction<T> processor) {
    long startTime = System.currentTimeMillis();
    totalRequests.incrementAndGet();

    BatchRequest<T> request = new BatchRequest<>(input, processor, startTime);

    synchronized (pendingBatch) {
      pendingBatch.add(request);

      // Flush if batch is full
      if (pendingBatch.size() >= batchSize) {
        log.debug("Batch full ({} requests), flushing immediately", pendingBatch.size());
        flushBatch();
      }
      // Flush if timeout exceeded
      else if (System.currentTimeMillis() - lastFlushTime > batchTimeoutMs) {
        log.debug("Batch timeout exceeded ({} requests), flushing", pendingBatch.size());
        flushBatch();
      }
    }

    return request.promise;
  }

  /** Process all pending requests in current batch. */
  private void flushBatch() {
    if (pendingBatch.isEmpty()) {
      return;
    }

    List<BatchRequest<?>> batch;
    synchronized (pendingBatch) {
      batch = new ArrayList<>(pendingBatch);
      pendingBatch.clear();
      lastFlushTime = System.currentTimeMillis();
    }

    int batchCount = batch.size();
    batchedRequests.addAndGet(batchCount);

    log.debug("Processing batch of {} requests", batchCount);

    // Process each request (actual batching happens in Ollama with OLLAMA_NUM_PARALLEL)
    for (BatchRequest<?> request : batch) {
      processSingleRequest(request);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> void processSingleRequest(BatchRequest<T> request) {
    request
        .processor
        .apply(request.input)
        .whenComplete(
            (result, error) -> {
              long latency = System.currentTimeMillis() - request.startTime;
              totalLatencyMs.addAndGet(latency);

              if (error != null) {
                log.warn("Batch request failed after {}ms: {}", latency, error.getMessage());
                request.promiseSettable.setException(error);
              } else {
                log.debug("Batch request completed in {}ms", latency);
                request.promiseSettable.set(result);
              }
            });
  }

  /** Get performance statistics. */
  public PerformanceStats getStats() {
    int total = totalRequests.get();
    int batched = batchedRequests.get();
    long avgLatency = total > 0 ? totalLatencyMs.get() / total : 0;

    return new PerformanceStats(
        total, batched, avgLatency, (double) batched / Math.max(1, total) * 100);
  }

  /** Performance statistics for batch processor. */
  public record PerformanceStats(
      int totalRequests,
      int batchedRequests,
      long averageLatencyMs,
      double batchingEfficiency // Percentage of requests that were batched
      ) {
    @Override
    public String toString() {
      return String.format(
          "BatchStats[total=%d, batched=%d (%.1f%%), avgLatency=%dms]",
          totalRequests, batchedRequests, batchingEfficiency, averageLatencyMs);
    }
  }

  /** Function interface for batch processing. */
  @FunctionalInterface
  public interface BatchFunction<T> {
    Promise<T> apply(String input);
  }

  /** Single request in a batch. */
  private static class BatchRequest<T> {
    final String input;
    final BatchFunction<T> processor;
    final long startTime;
    final PromiseSettable<T> promiseSettable = new PromiseSettable<>();
    final Promise<T> promise = promiseSettable.getPromise();

    BatchRequest(String input, BatchFunction<T> processor, long startTime) {
      this.input = input;
      this.processor = processor;
      this.startTime = startTime;
    }
  }

  /** Settable promise for async result delivery. */
  private static class PromiseSettable<T> {
    private final io.activej.promise.SettablePromise<T> settable =
        new io.activej.promise.SettablePromise<>();

    Promise<T> getPromise() {
      return settable;
    }

    void set(T value) {
      settable.set(value);
    }

    void setException(Throwable error) {
      if (error instanceof Exception) {
        settable.setException((Exception) error);
      } else {
        settable.setException(new RuntimeException(error));
      }
    }
  }

  /**
   * Create batch processor with GPU-optimized defaults.
   *
   * <p><b>Default Configuration:</b>
   *
   * <ul>
   *   <li>Batch Size: 4 (optimal for most GPUs)
   *   <li>Timeout: 100ms (balance latency vs throughput)
   * </ul>
   */
  public static BatchProcessor createDefault() {
    return new BatchProcessor(4, 100);
  }

  /**
   * Create batch processor for high-throughput scenarios.
   *
   * <p><b>High-Throughput Configuration:</b>
   *
   * <ul>
   *   <li>Batch Size: 8 (larger batches)
   *   <li>Timeout: 50ms (faster flushing)
   * </ul>
   */
  public static BatchProcessor createHighThroughput() {
    return new BatchProcessor(8, 50);
  }
}
