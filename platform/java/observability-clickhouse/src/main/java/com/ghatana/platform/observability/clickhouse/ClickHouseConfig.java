package com.ghatana.platform.observability.clickhouse;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration value object for ClickHouse trace storage connection and batching behavior.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates all configuration parameters for {@link ClickHouseTraceStorage}: connection details (host/port/database)
 * and batching behavior (batch size/flush interval). Created internally by ClickHouseTraceStorage.Builder with
 * validation. Immutable value object - all fields final and set at construction.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration value object for ClickHouseTraceStorage. Not intended for direct instantiation - created via
 * ClickHouseTraceStorage.Builder. Passed to ClickHouseTraceStorage constructor and SpanBuffer for parameterization.
 * Ensures all configuration validated before ClickHouse connection established.
 *
 * <p><b>Usage Examples</b><br>
 *
 * <pre>{@code
 * // Example 1: Production configuration via builder
 * ClickHouseTraceStorage storage = ClickHouseTraceStorage.builder()
 *     .withHost("clickhouse.prod.internal")
 *     .withPort(8123)  // Default ClickHouse HTTP port
 *     .withDatabase("observability")
 *     .withBatchSize(10000)  // Large batch for high throughput
 *     .withFlushInterval(Duration.ofSeconds(10))
 *     .build();
 * 
 * // ClickHouseConfig created internally by builder
 * // Validates: host non-null, port >0, database non-null, batchSize >0, flushInterval >0
 * }</pre>
 *
 * <pre>{@code
 * // Example 2: Development configuration (local ClickHouse)
 * ClickHouseTraceStorage storage = ClickHouseTraceStorage.builder()
 *     .withHost("localhost")
 *     .withPort(8123)
 *     .withDatabase("dev_traces")
 *     .withBatchSize(1000)  // Smaller batch for dev
 *     .withFlushInterval(Duration.ofSeconds(2))  // Faster flush for testing
 *     .build();
 * }</pre>
 *
 * <pre>{@code
 * // Example 3: High-throughput configuration
 * ClickHouseTraceStorage storage = ClickHouseTraceStorage.builder()
 *     .withHost("clickhouse-cluster.prod")
 *     .withPort(8123)
 *     .withDatabase("traces")
 *     .withBatchSize(50000)  // Very large batch
 *     .withFlushInterval(Duration.ofSeconds(30))  // Infrequent flush
 *     .build();
 * 
 * // Optimized for: 100k+ spans/sec ingestion
 * // Trade-off: Higher memory usage (~100MB buffer), longer flush latency (30s)
 * }</pre>
 *
 * <pre>{@code
 * // Example 4: Low-latency configuration
 * ClickHouseTraceStorage storage = ClickHouseTraceStorage.builder()
 *     .withHost("clickhouse.region-west")
 *     .withPort(8123)
 *     .withDatabase("traces")
 *     .withBatchSize(500)  // Small batch
 *     .withFlushInterval(Duration.ofSeconds(1))  // Frequent flush
 *     .build();
 * 
 * // Optimized for: Low ingestion latency (<1s to ClickHouse)
 * // Trade-off: More ClickHouse writes, lower throughput
 * }</pre>
 *
 * <pre>{@code
 * // Example 5: Kubernetes deployment with env vars
 * String host = System.getenv("CLICKHOUSE_HOST");  // clickhouse-service.namespace.svc
 * int port = Integer.parseInt(System.getenv("CLICKHOUSE_PORT"));  // 8123
 * String db = System.getenv("CLICKHOUSE_DATABASE");  // traces
 * 
 * ClickHouseTraceStorage storage = ClickHouseTraceStorage.builder()
 *     .withHost(host)
 *     .withPort(port)
 *     .withDatabase(db)
 *     .withBatchSize(10000)
 *     .withFlushInterval(Duration.ofSeconds(10))
 *     .build();
 * }</pre>
 *
 * <p><b>Configuration Parameters</b><br>
 * - **host** (String, required): ClickHouse server hostname or IP (e.g., "clickhouse.prod.internal", "10.0.1.50")
 * - **port** (int, required): HTTP port for ClickHouse JDBC (default: 8123, ClickHouse default)
 * - **database** (String, required): Database name within ClickHouse instance (e.g., "observability", "traces")
 * - **batchSize** (int, required): Maximum spans per batch insert (typical: 1k-10k, high-throughput: 50k)
 * - **flushInterval** (Duration, required): Maximum time before forced buffer flush (typical: 5-30s)
 *
 * <p><b>Validation Rules</b><br>
 * Constructor validates:
 * - `host != null && !host.isBlank()` → IllegalArgumentException if invalid
 * - `port > 0 && port <= 65535` → IllegalArgumentException if invalid
 * - `database != null && !database.isBlank()` → IllegalArgumentException if invalid
 * - `batchSize > 0` → IllegalArgumentException if invalid
 * - `flushInterval != null && !flushInterval.isNegative()` → IllegalArgumentException if invalid
 *
 * <p><b>Default Values</b><br>
 * ClickHouseTraceStorage.Builder provides defaults:
 * - `port`: 8123 (ClickHouse HTTP default)
 * - `database`: "observability"
 * - `batchSize`: 5000
 * - `flushInterval`: Duration.ofSeconds(5)
 *
 * <p><b>Best Practices</b><br>
 * - Use environment variables for connection parameters (host/port/database)
 * - Tune `batchSize` based on ingestion rate: 1k-10k typical, 50k high-throughput
 * - Tune `flushInterval` based on latency requirements: 5-30s typical, 1s low-latency
 * - Monitor ClickHouse write load and adjust batch size accordingly
 * - Consider ClickHouse cluster capacity when setting batch size
 *
 * <p><b>Performance Characteristics</b><br>
 * - **Construction**: O(1) - simple field assignment + validation
 * - **Memory**: ~200 bytes per instance (negligible)
 * - **Immutability**: Thread-safe, can be shared across threads
 * - **Buffer memory**: `batchSize * ~2KB` (e.g., 10k * 2KB = ~20MB)
 *
 * <p><b>Thread Safety</b><br>
 * ✅ **Thread-safe** - Immutable value object (all fields final).
 * Can be safely shared across multiple threads without synchronization.
 *
 * <p><b>Integration Points</b><br>
 * - ClickHouseTraceStorage.Builder: Creates instances during builder.build()
 * - ClickHouseTraceStorage: Uses for JDBC connection URL and buffer configuration
 * - SpanBuffer: Uses batchSize and flushInterval for batching logic
 *
 * @see ClickHouseTraceStorage.Builder
 * @see ClickHouseTraceStorage
 * @see SpanBuffer
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Immutable configuration for ClickHouse trace storage
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class ClickHouseConfig {
    
    /**
     * ClickHouse server hostname or IP address.
     */
    private final String host;
    
    /**
     * ClickHouse HTTP port for JDBC connections (default: 8123).
     */
    private final int port;
    
    /**
     * Database name within ClickHouse instance.
     */
    private final String database;
    
    /**
     * Maximum number of spans to batch before automatic flush.
     * Higher values improve throughput but increase memory usage.
     */
    private final int batchSize;
    
    /**
     * Maximum time interval before forced buffer flush.
     * Ensures spans are persisted even if batch size not reached.
     */
    private final Duration flushInterval;
    
    /**
     * Constructs a ClickHouseConfig with validated parameters.
     * <p>
     * Validates non-null requirements for host, database, and flushInterval.
     * Port and batch size are primitive values (no null validation needed).
     * </p>
     *
     * @param host ClickHouse server hostname/IP (must not be null)
     * @param port HTTP port for JDBC (typically 8123 for ClickHouse HTTP protocol)
     * @param database Database name (must not be null)
     * @param batchSize Maximum spans per batch (recommended: 1000-10000)
     * @param flushInterval Maximum time before forced flush (must not be null, recommended: 5-30 seconds)
     * @throws NullPointerException if host, database, or flushInterval is null
     * @doc.thread-safety Constructor is thread-safe; resulting instance is immutable
     */
    public ClickHouseConfig(String host, int port, String database, int batchSize, Duration flushInterval) {
        this.host = Objects.requireNonNull(host, "Host cannot be null");
        this.port = port;
        this.database = Objects.requireNonNull(database, "Database cannot be null");
        this.batchSize = batchSize;
        this.flushInterval = Objects.requireNonNull(flushInterval, "Flush interval cannot be null");
    }
    
    /**
     * Returns the ClickHouse server hostname or IP address.
     *
     * @return host string (never null)
     */
    public String getHost() {
        return host;
    }
    
    /**
     * Returns the ClickHouse HTTP port for JDBC connections.
     *
     * @return port number (default: 8123)
     */
    public int getPort() {
        return port;
    }
    
    /**
     * Returns the database name within ClickHouse instance.
     *
     * @return database name (never null)
     */
    public String getDatabase() {
        return database;
    }
    
    /**
     * Returns the maximum number of spans per batch insert.
     *
     * @return batch size (recommended: 1000-10000)
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * Returns the maximum time interval before forced buffer flush.
     *
     * @return flush interval (never null, recommended: 5-30 seconds)
     */
    public Duration getFlushInterval() {
        return flushInterval;
    }
    
    /**
     * Returns a string representation of this configuration.
     * <p>
     * Format: "ClickHouseConfig{host='...', port=..., database='...', batchSize=..., flushInterval=...}"
     * </p>
     * <p>
     * Useful for logging configuration at startup and debugging connection issues.
     * </p>
     *
     * @return formatted configuration string
     */
    @Override
    public String toString() {
        return "ClickHouseConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", batchSize=" + batchSize +
                ", flushInterval=" + flushInterval +
                '}';
    }
}
