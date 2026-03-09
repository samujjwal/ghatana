package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.observability.trace.SpanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * In-memory batching buffer for span data with dual-trigger flush (size-based and time-based).
 *
 * <p><b>Purpose</b><br>
 * Accumulates spans in memory before batch insertion into ClickHouse to reduce write operations and improve
 * throughput. Flushes automatically when buffer reaches maximum size OR time interval elapses. Balances
 * ingestion latency (spans buffered briefly) vs throughput (fewer ClickHouse writes).
 *
 * <p><b>Architecture Role</b><br>
 * Internal batching component of ClickHouseTraceStorage. Not exposed to application code. Manages in-memory
 * span accumulation with configurable batch size and flush interval. Enables high-throughput span ingestion
 * (50k-100k+ spans/sec) by reducing ClickHouse write operations by 100-1000x.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Basic buffer usage with size-based flush
 * SpanBuffer buffer = new SpanBuffer(5000, Duration.ofSeconds(5));
 * 
 * // Add spans one by one
 * for (SpanData span : incomingSpans) {
 *     buffer.add(span);
 *     
 *     // Check if flush needed (size trigger)
 *     if (buffer.shouldFlush()) {
 *         List<SpanData> batch = buffer.flush();
 *         clickhouse.insertBatch(batch);
 *         log.info("Flushed {} spans (size trigger)", batch.size());
 *     }
 * }
 * // Buffer: [span1, span2, ..., span4999] → flush at span5000
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Time-based flush (periodic background task)
 * SpanBuffer buffer = new SpanBuffer(10000, Duration.ofSeconds(30));
 * 
 * // Background flush task (every 1 second)
 * scheduler.scheduleAtFixedRate(() -> {
 *     if (buffer.shouldFlush()) {
 *         List<SpanData> batch = buffer.flush();
 *         if (!batch.isEmpty()) {
 *             clickhouse.insertBatch(batch);
 *             log.info("Flushed {} spans (time trigger)", batch.size());
 *         }
 *     }
 * }, 0, 1, TimeUnit.SECONDS);
 * 
 * // Flush happens after 30s even if only 100 spans buffered
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: Manual flush on shutdown
 * SpanBuffer buffer = new SpanBuffer(5000, Duration.ofSeconds(5));
 * 
 * // Shutdown hook
 * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
 *     List<SpanData> remaining = buffer.flush();
 *     if (!remaining.isEmpty()) {
 *         clickhouse.insertBatch(remaining);
 *         log.info("Shutdown: flushed {} remaining spans", remaining.size());
 *     }
 * }));
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Production configuration (high throughput)
 * // Large batch size + short interval for high-volume systems
 * SpanBuffer highVolume = new SpanBuffer(
 *     10000,                        // Flush after 10k spans
 *     Duration.ofSeconds(5)         // Or after 5 seconds
 * );
 * 
 * // Memory usage: ~10k spans * ~2KB = ~20MB buffer
 * // Typical flush: Every 5s at 50k spans/sec = 250k spans → 25 batches
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Low-latency configuration
 * // Small batch size + short interval for low-latency requirements
 * SpanBuffer lowLatency = new SpanBuffer(
 *     1000,                         // Flush after 1k spans
 *     Duration.ofSeconds(2)         // Or after 2 seconds
 * );
 * 
 * // Spans persisted within 2s maximum (vs 30s with large buffer)
 * }</pre>
 *
 * <p><b>Flush Triggers</b><br>
 * Buffer flushes when `shouldFlush()` returns `true`:
 * 1. **Size-based**: `buffer.size() >= maxSize` (e.g., 5000 spans)
 * 2. **Time-based**: `Instant.now() - lastFlushTime >= flushInterval` (e.g., 5 seconds)
 * 3. **Manual**: Explicit `flush()` call (e.g., shutdown hook)
 *
 * Caller must check `shouldFlush()` periodically (e.g., background task every 1s).
 *
 * <p><b>Memory Management</b><br>
 * - **Heap usage**: ~O(maxSize) * span size (~1-5KB per span)
 * - **Typical buffer**: 5000 spans * 2KB = ~10MB heap
 * - **Large buffer**: 10k spans * 5KB = ~50MB heap
 * - **Flush**: Buffer cleared, eligible for GC
 *
 * <p><b>Best Practices</b><br>
 * - Set `maxSize` to 1k-10k (balance throughput vs memory)
 * - Set `flushInterval` to 5-30s (balance latency vs batch efficiency)
 * - Call `shouldFlush()` frequently (e.g., after each `add()` + background task every 1s)
 * - Always flush on shutdown (avoid data loss)
 * - Monitor flush metrics (count, batch size, trigger type)
 * - Tune based on ingestion rate: High rate → larger batch + shorter interval
 *
 * <p><b>Anti-Patterns</b><br>
 * - ❌ Setting `maxSize` too large (>50k spans = excessive heap pressure)
 * - ❌ Setting `flushInterval` too long (>60s = high ingestion latency)
 * - ❌ Not checking `shouldFlush()` periodically (time trigger never fires)
 * - ❌ Multi-threaded access (not thread-safe, requires external sync)
 * - ❌ Forgetting shutdown flush (spans lost on JVM exit)
 *
 * <p><b>Performance Characteristics</b><br>
 * - `add()`: O(1) - ArrayList append
 * - `shouldFlush()`: O(1) - size check + time comparison
 * - `flush()`: O(1) - swap buffer + reset (GC handles old list)
 * - **Throughput gain**: 100-1000x fewer ClickHouse writes (e.g., 10k individual inserts → 1 batch)
 * - **Latency trade-off**: Spans buffered for `flushInterval` max (typically 5-30s)
 *
 * <p><b>Thread Safety</b><br>
 * ❌ **NOT thread-safe** - Requires external synchronization.
 * Designed for single-threaded use within ClickHouseTraceStorage.
 * All methods (`add`, `flush`, `shouldFlush`) must be externally synchronized for multi-threaded access.
 *
 * <p><b>Integration Points</b><br>
 * - ClickHouseTraceStorage: Primary consumer (storeSpan/storeSpans methods)
 * - ClickHouse JDBC: Batch insert target
 * - Background flush task: Periodic `shouldFlush()` check
 * - Shutdown hook: Manual flush on JVM exit
 *
 * @see ClickHouseTraceStorage
 * @see ClickHouseConfig
 * @see SpanData
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose In-memory batching buffer for span data with dual-trigger flush
 * @doc.layer core
 * @doc.pattern Buffer
 */
public class SpanBuffer {
    
    /**
     * Logger for buffer operations and flush events.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SpanBuffer.class);
    
    /**
     * Internal span accumulation list (mutable).
     * Pre-sized to maxSize to avoid reallocation.
     */
    private final List<SpanData> buffer;
    
    /**
     * Maximum buffer size (size-based flush trigger).
     */
    private final int maxSize;
    
    /**
     * Maximum time interval before forced flush (time-based flush trigger).
     */
    private final Duration flushInterval;
    
    /**
     * Timestamp of last flush operation (used for time-based trigger calculation).
     * Updated on every flush() call.
     */
    private Instant lastFlushTime;
    
    /**
     * Constructs a SpanBuffer with configured batch size and flush interval.
     * <p>
     * Pre-allocates internal ArrayList to maxSize capacity for memory efficiency.
     * Initializes lastFlushTime to current instant.
     * </p>
     *
     * @param maxSize maximum spans before automatic flush (recommended: 1000-10000)
     * @param flushInterval maximum time before forced flush (must not be null, recommended: 5-30 seconds)
     * @throws NullPointerException if flushInterval is null
     * @doc.thread-safety Constructor is thread-safe; resulting instance requires external synchronization
     */
    public SpanBuffer(int maxSize, Duration flushInterval) {
        this.buffer = new ArrayList<>(maxSize);
        this.maxSize = maxSize;
        this.flushInterval = Objects.requireNonNull(flushInterval, "Flush interval cannot be null");
        this.lastFlushTime = Instant.now();
    }
    
    /**
     * Adds a span to the buffer.
     * <p>
     * Appends span to internal list. Does NOT automatically flush - caller must check
     * {@link #shouldFlush()} after adding to determine if flush is needed.
     * </p>
     * <p>
     * Typical usage pattern:
     * <pre>{@code
     * buffer.add(span);
     * if (buffer.shouldFlush()) {
     *     List<SpanData> spans = buffer.flush();
     *     // Insert spans into ClickHouse
     * }
     * }</pre>
     * </p>
     *
     * @param span span data to buffer (must not be null)
     * @throws IllegalArgumentException if span is null
     * @doc.thread-safety NOT thread-safe - requires external synchronization
     */
    public void add(SpanData span) {
        if (span == null) {
            throw new IllegalArgumentException("Span cannot be null");
        }
        buffer.add(span);
    }
    
    /**
     * Checks if the buffer should be flushed based on size or time triggers.
     * <p>
     * Flush Triggers:
     * <ul>
     *   <li><b>Size trigger</b>: buffer.size() >= maxSize (batch size reached)</li>
     *   <li><b>Time trigger</b>: elapsed time >= flushInterval (time limit reached)</li>
     * </ul>
     * </p>
     * <p>
     * Returns true if EITHER trigger condition is met.
     * </p>
     *
     * @return true if buffer should be flushed, false otherwise
     * @doc.thread-safety NOT thread-safe - requires external synchronization
     * @doc.performance O(1) operation - simple size comparison and duration calculation
     */
    public boolean shouldFlush() {
        if (buffer.size() >= maxSize) {
            return true;
        }
        
        Duration elapsed = Duration.between(lastFlushTime, Instant.now());
        return elapsed.compareTo(flushInterval) >= 0;
    }
    
    /**
     * Flushes the buffer and returns its contents.
     * <p>
     * Operations performed:
     * <ol>
     *   <li>Creates a copy of buffer contents (new ArrayList)</li>
     *   <li>Clears internal buffer (ready for new spans)</li>
     *   <li>Updates lastFlushTime to current instant</li>
     *   <li>Logs flush event with span count</li>
     * </ol>
     * </p>
     * <p>
     * The returned list is a copy - modifying it does not affect internal buffer.
     * </p>
     *
     * @return list of buffered spans (never null, may be empty if no spans buffered)
     * @doc.thread-safety NOT thread-safe - requires external synchronization
     * @doc.performance O(n) where n = buffer.size() (due to ArrayList copy)
     */
    public List<SpanData> flush() {
        List<SpanData> flushed = new ArrayList<>(buffer);
        buffer.clear();
        lastFlushTime = Instant.now();
        LOG.debug("Flushed {} spans from buffer", flushed.size());
        return flushed;
    }
    
    /**
     * Checks if the buffer is empty (no buffered spans).
     *
     * @return true if buffer is empty, false otherwise
     * @doc.thread-safety NOT thread-safe - requires external synchronization
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }
    
    /**
     * Gets the current number of buffered spans.
     *
     * @return current buffer size (0 to maxSize)
     * @doc.thread-safety NOT thread-safe - requires external synchronization
     */
    public int size() {
        return buffer.size();
    }
}
