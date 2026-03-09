package com.ghatana.datacloud;

import lombok.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for EVENT record type collections.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides event-specific configuration including:
 * <ul>
 * <li>Partitioning strategy (hash, range, time-based)</li>
 * <li>Ordering guarantees (per-partition, global)</li>
 * <li>Deduplication settings</li>
 * <li>Replay and tailing options</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Simple event config with time-based partitioning
 * EventConfig config = EventConfig.timePartitioned(Duration.ofDays(1))
 *     .withDeduplication(Duration.ofMinutes(5));
 *
 * // Hash-partitioned for ordering by key
 * EventConfig orderEvents = EventConfig.hashPartitioned("orderId", 16)
 *     .withStrictOrdering()
 *     .withIdempotency("eventId", Duration.ofHours(1));
 *
 * // High-throughput config
 * EventConfig highThroughput = EventConfig.builder()
 *     .partitionCount(32)
 *     .partitionStrategy(PartitionStrategy.ROUND_ROBIN)
 *     .batchSize(1000)
 *     .compressPayload(true)
 *     .build();
 * }</pre>
 *
 * @see Collection
 * @see EventRecord
 * @doc.type class
 * @doc.purpose Event collection configuration
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Number of partitions for the event stream.
     */
    @Builder.Default
    private Integer partitionCount = 1;

    /**
     * Partitioning strategy.
     */
    @Builder.Default
    private PartitionStrategy partitionStrategy = PartitionStrategy.HASH;

    /**
     * Field to use for hash partitioning.
     */
    private String partitionKeyField;

    /**
     * Time bucket size for time-based partitioning.
     */
    private Duration partitionTimeBucket;

    /**
     * Ordering guarantee level.
     */
    @Builder.Default
    private OrderingGuarantee ordering = OrderingGuarantee.PER_PARTITION;

    /**
     * Whether to enable deduplication.
     */
    @Builder.Default
    private Boolean deduplicationEnabled = false;

    /**
     * Field to use for deduplication (usually idempotencyKey).
     */
    private String deduplicationKeyField;

    /**
     * Time window for deduplication.
     */
    private Duration deduplicationWindow;

    /**
     * Whether to compress event payloads.
     */
    @Builder.Default
    private Boolean compressPayload = false;

    /**
     * Compression codec.
     */
    @Builder.Default
    private String compressionCodec = "lz4";

    /**
     * Batch size for writes.
     */
    @Builder.Default
    private Integer batchSize = 100;

    /**
     * Maximum time to wait for batch to fill.
     */
    @Builder.Default
    private Duration batchLingerTime = Duration.ofMillis(10);

    /**
     * Whether to enable tailing (real-time subscription).
     */
    @Builder.Default
    private Boolean tailingEnabled = true;

    /**
     * Maximum tailing lag before disconnect.
     */
    private Duration maxTailingLag;

    /**
     * Whether replay from beginning is allowed.
     */
    @Builder.Default
    private Boolean replayEnabled = true;

    /**
     * Additional configuration properties.
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Partitioning strategies.
     */
    public enum PartitionStrategy {
        /**
         * Hash-based partitioning on a key field.
         */
        HASH,
        /**
         * Round-robin distribution.
         */
        ROUND_ROBIN,
        /**
         * Time-based partitioning (hourly, daily, etc.).
         */
        TIME_BASED,
        /**
         * Range-based partitioning on a field.
         */
        RANGE,
        /**
         * Single partition (no partitioning).
         */
        NONE
    }

    /**
     * Ordering guarantee levels.
     */
    public enum OrderingGuarantee {
        /**
         * No ordering guarantee.
         */
        NONE,
        /**
         * Ordered within each partition.
         */
        PER_PARTITION,
        /**
         * Globally ordered (requires single partition or coordination).
         */
        GLOBAL
    }

    // ==================== Factory Methods ====================
    /**
     * Creates a hash-partitioned event config.
     *
     * @param keyField Field to partition on
     * @param partitions Number of partitions
     * @return EventConfig
     */
    public static EventConfig hashPartitioned(String keyField, int partitions) {
        return builder()
                .partitionStrategy(PartitionStrategy.HASH)
                .partitionKeyField(keyField)
                .partitionCount(partitions)
                .ordering(OrderingGuarantee.PER_PARTITION)
                .build();
    }

    /**
     * Creates a time-partitioned event config.
     *
     * @param timeBucket Time bucket size
     * @return EventConfig
     */
    public static EventConfig timePartitioned(Duration timeBucket) {
        return builder()
                .partitionStrategy(PartitionStrategy.TIME_BASED)
                .partitionTimeBucket(timeBucket)
                .ordering(OrderingGuarantee.PER_PARTITION)
                .build();
    }

    /**
     * Creates a single-partition config (for strict ordering).
     *
     * @return EventConfig
     */
    public static EventConfig singlePartition() {
        return builder()
                .partitionStrategy(PartitionStrategy.NONE)
                .partitionCount(1)
                .ordering(OrderingGuarantee.GLOBAL)
                .build();
    }

    /**
     * Creates a high-throughput config.
     *
     * @param partitions Number of partitions
     * @return EventConfig
     */
    public static EventConfig highThroughput(int partitions) {
        return builder()
                .partitionStrategy(PartitionStrategy.ROUND_ROBIN)
                .partitionCount(partitions)
                .ordering(OrderingGuarantee.NONE)
                .compressPayload(true)
                .batchSize(1000)
                .batchLingerTime(Duration.ofMillis(50))
                .build();
    }

    // ==================== Fluent Configuration ====================
    /**
     * Enables deduplication.
     *
     * @param window Deduplication time window
     * @return this
     */
    public EventConfig withDeduplication(Duration window) {
        this.deduplicationEnabled = true;
        this.deduplicationKeyField = "idempotencyKey";
        this.deduplicationWindow = window;
        return this;
    }

    /**
     * Enables deduplication with custom key.
     *
     * @param keyField Field to use for deduplication
     * @param window Deduplication time window
     * @return this
     */
    public EventConfig withIdempotency(String keyField, Duration window) {
        this.deduplicationEnabled = true;
        this.deduplicationKeyField = keyField;
        this.deduplicationWindow = window;
        return this;
    }

    /**
     * Enables strict per-partition ordering.
     *
     * @return this
     */
    public EventConfig withStrictOrdering() {
        this.ordering = OrderingGuarantee.PER_PARTITION;
        return this;
    }

    /**
     * Enables global ordering (single partition).
     *
     * @return this
     */
    public EventConfig withGlobalOrdering() {
        this.ordering = OrderingGuarantee.GLOBAL;
        this.partitionCount = 1;
        this.partitionStrategy = PartitionStrategy.NONE;
        return this;
    }

    /**
     * Enables compression.
     *
     * @return this
     */
    public EventConfig withCompression() {
        this.compressPayload = true;
        return this;
    }

    /**
     * Enables compression with codec.
     *
     * @param codec Compression codec
     * @return this
     */
    public EventConfig withCompression(String codec) {
        this.compressPayload = true;
        this.compressionCodec = codec;
        return this;
    }
}
