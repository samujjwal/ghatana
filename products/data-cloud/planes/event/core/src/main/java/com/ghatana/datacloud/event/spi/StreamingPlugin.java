package com.ghatana.datacloud.event.spi;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import com.ghatana.datacloud.event.model.Event;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.function.Consumer;

/**
 * SPI for pluggable streaming backends.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time event delivery to consumers. Implementations must ensure:
 * <ul>
 *   <li><b>Low Latency</b>: &lt;10ms p99 delivery</li>
 *   <li><b>Ordering</b>: Events ordered within partition</li>
 *   <li><b>At-least-once</b>: Delivery guarantees with consumer ack</li>
 *   <li><b>Backpressure</b>: Handle slow consumers gracefully</li>
 * </ul>
 *
 * <p><b>Implementations</b><br>
 * <ul>
 *   <li><b>KafkaStreamingPlugin</b>: High-throughput distributed streaming</li>
 *   <li><b>PostgreSQLStreamingPlugin</b>: LISTEN/NOTIFY for single-node</li>
 *   <li><b>WebSocketStreamingPlugin</b>: Browser-based real-time</li>
 *   <li><b>RedisStreamingPlugin</b>: Redis Streams for caching layer</li>
 * </ul>
 *
 * <p><b>Subscription Models</b><br>
 * <pre>
 * Single Consumer → One subscriber per partition
 * Consumer Group  → Multiple subscribers sharing partitions
 * Broadcast       → All subscribers receive all events
 * </pre>
 *
 * @see com.ghatana.platform.plugin.Plugin
 * @see Subscription
 * @doc.type interface
 * @doc.purpose SPI for real-time event streaming
 * @doc.layer spi
 * @doc.pattern Plugin, Observer, Pub/Sub
 */
public interface StreamingPlugin extends Plugin {

    // ==================== Subscribe Operations ====================

    /**
     * Subscribe to event stream.
     *
     * <p>Events are delivered to the consumer callback in order.
     * Use the returned Subscription to manage the subscription lifecycle.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param partitionId partition to subscribe (null or ALL for all partitions)
     * @param startOffset starting offset (LATEST, EARLIEST, or specific)
     * @param consumer event consumer callback
     * @return Promise with subscription handle
     */
    Promise<Subscription> subscribe(
        String tenantId,
        String streamName,
        PartitionId partitionId,
        Offset startOffset,
        Consumer<Event> consumer
    );

    /**
     * Subscribe with consumer group for coordinated consumption.
     *
     * <p>Partitions are automatically assigned and rebalanced among
     * group members. Offsets are committed to storage.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param consumerGroup consumer group name
     * @param consumer event consumer callback
     * @return Promise with subscription handle
     */
    Promise<Subscription> subscribeWithGroup(
        String tenantId,
        String streamName,
        String consumerGroup,
        Consumer<Event> consumer
    );

    /**
     * Subscribe with batch delivery for efficiency.
     *
     * <p>Events are buffered and delivered in batches. Use for
     * high-throughput scenarios where per-event overhead matters.</p>
     *
     * @param tenantId tenant for isolation
     * @param streamName stream name
     * @param options subscription options (batch size, timeout, etc.)
     * @param batchConsumer batch consumer callback
     * @return Promise with subscription handle
     */
    Promise<Subscription> subscribeBatch(
        String tenantId,
        String streamName,
        SubscriptionOptions options,
        Consumer<java.util.List<Event>> batchConsumer
    );

    // ==================== Publish Operations ====================

    /**
     * Publish event to subscribers (after storage).
     *
     * <p>This is typically called by the EventCloud core after
     * successful storage, not directly by users.</p>
     *
     * @param event event to publish
     * @return Promise completing when published
     */
    Promise<Void> publish(Event event);

    /**
     * Publish batch of events to subscribers.
     *
     * @param events events to publish
     * @return Promise completing when all published
     */
    Promise<Void> publishBatch(java.util.List<Event> events);

    // ==================== Capabilities ====================

    /**
     * Get streaming plugin capabilities.
     *
     * @return capabilities descriptor
     */
    Capabilities capabilities();

    /**
     * Streaming plugin capabilities.
     */
    interface Capabilities {
        /**
         * Supports consumer-side backpressure.
         */
        boolean supportsBackpressure();

        /**
         * Supports dynamic partition rebalancing.
         */
        boolean supportsPartitionRebalancing();

        /**
         * Supports consumer groups for coordination.
         */
        boolean supportsConsumerGroups();

        /**
         * Supports exactly-once delivery semantics.
         */
        boolean supportsExactlyOnce();

        /**
         * Maximum subscribers per stream.
         */
        int maxSubscribers();

        /**
         * Maximum batch size for batch subscriptions.
         */
        int maxBatchSize();
    }

    // ==================== Subscription Handle ====================

    /**
     * Handle for managing active subscription.
     */
    interface Subscription extends AutoCloseable {

        /**
         * Pause event delivery.
         *
         * <p>Events are buffered but not delivered until resume.</p>
         *
         * @return Promise completing when paused
         */
        Promise<Void> pause();

        /**
         * Resume event delivery after pause.
         *
         * @return Promise completing when resumed
         */
        Promise<Void> resume();

        /**
         * Check if subscription is active.
         *
         * @return true if receiving events
         */
        boolean isActive();

        /**
         * Check if subscription is paused.
         *
         * @return true if paused
         */
        boolean isPaused();

        /**
         * Get current offset positions for all assigned partitions.
         *
         * @return Promise with partition to offset mapping
         */
        Promise<Map<PartitionId, Offset>> getCurrentOffsets();

        /**
         * Commit offsets (for consumer group).
         *
         * <p>Commits the given offsets as processed. On restart,
         * consumption resumes from committed offsets.</p>
         *
         * @param offsets partition to offset mapping
         * @return Promise completing when committed
         */
        Promise<Void> commitOffsets(Map<PartitionId, Offset> offsets);

        /**
         * Get assigned partitions (for consumer group).
         *
         * @return Promise with list of assigned partition IDs
         */
        Promise<java.util.List<PartitionId>> getAssignedPartitions();

        /**
         * Cancel subscription and release resources.
         */
        @Override
        void close();
    }

    // ==================== Subscription Options ====================

    /**
     * Options for subscription configuration.
     */
    record SubscriptionOptions(
        PartitionId partitionId,
        Offset startOffset,
        String consumerGroup,
        int batchSize,
        java.time.Duration batchTimeout,
        boolean autoCommit,
        java.time.Duration autoCommitInterval
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private PartitionId partitionId = PartitionId.ALL;
            private Offset startOffset = Offset.LATEST;
            private String consumerGroup;
            private int batchSize = 100;
            private java.time.Duration batchTimeout = java.time.Duration.ofMillis(100);
            private boolean autoCommit = false;
            private java.time.Duration autoCommitInterval = java.time.Duration.ofSeconds(5);

            private Builder() {}

            public Builder partitionId(PartitionId partitionId) {
                this.partitionId = partitionId;
                return this;
            }

            public Builder startOffset(Offset startOffset) {
                this.startOffset = startOffset;
                return this;
            }

            public Builder consumerGroup(String consumerGroup) {
                this.consumerGroup = consumerGroup;
                return this;
            }

            public Builder batchSize(int batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public Builder batchTimeout(java.time.Duration batchTimeout) {
                this.batchTimeout = batchTimeout;
                return this;
            }

            public Builder autoCommit(boolean autoCommit) {
                this.autoCommit = autoCommit;
                return this;
            }

            public Builder autoCommitInterval(java.time.Duration autoCommitInterval) {
                this.autoCommitInterval = autoCommitInterval;
                return this;
            }

            public SubscriptionOptions build() {
                return new SubscriptionOptions(
                    partitionId, startOffset, consumerGroup, batchSize,
                    batchTimeout, autoCommit, autoCommitInterval
                );
            }
        }
    }
}
