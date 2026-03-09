package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.DataRecord;
import io.activej.promise.Promise;

import java.util.function.Consumer;

/**
 * Extension interface for plugins supporting streaming/tailing.
 *
 * <p>
 * <b>Purpose</b><br>
 * Optional capability for storage plugins that support real-time streaming of
 * records. Primarily used for EVENT and TIMESERIES record types.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * if (plugin instanceof StreamingCapability streaming) {
 *     Subscription sub = streaming.subscribe(
 *         "tenantId",
 *         "events",
 *         SubscriptionOptions.builder()
 *             .fromOffset(100L)
 *             .partitions(List.of(0, 1))
 *             .build(),
 *         record -> processRecord(record)
 *     ).getResult();
 *
 *     // Later...
 *     sub.unsubscribe();
 * }
 * }</pre>
 *
 * @param <R> Record type
 * @see StoragePlugin
 * @doc.type interface
 * @doc.purpose Streaming capability
 * @doc.layer core
 * @doc.pattern Capability Interface
 */
public interface StreamingCapability<R extends DataRecord> {

    /**
     * Subscribes to a stream of records.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param options Subscription options
     * @param consumer Consumer for received records
     * @return Promise with subscription handle
     */
    Promise<Subscription> subscribe(
            String tenantId,
            String collectionName,
            SubscriptionOptions options,
            Consumer<R> consumer
    );

    /**
     * Tails a collection starting from a specific position.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param options Tailing options
     * @param consumer Consumer for received records
     * @return Promise with subscription handle
     */
    Promise<Subscription> tail(
            String tenantId,
            String collectionName,
            TailOptions options,
            Consumer<R> consumer
    );

    /**
     * Subscription handle for managing active subscriptions.
     */
    interface Subscription {

        /**
         * Gets the subscription ID.
         */
        String getId();

        /**
         * Checks if subscription is active.
         */
        boolean isActive();

        /**
         * Pauses the subscription.
         */
        Promise<Void> pause();

        /**
         * Resumes a paused subscription.
         */
        Promise<Void> resume();

        /**
         * Unsubscribes and closes the subscription.
         */
        Promise<Void> unsubscribe();

        /**
         * Gets current position/offset.
         */
        Promise<Long> getCurrentOffset();
    }

    /**
     * Options for subscription.
     */
    record SubscriptionOptions(
            Long fromOffset,
            java.time.Instant fromTimestamp,
            java.util.List<Integer> partitions,
            String streamName,
            Integer batchSize,
            java.time.Duration pollInterval
            ) {

        public static SubscriptionOptionsBuilder builder() {
            return new SubscriptionOptionsBuilder();
        }

        public static class SubscriptionOptionsBuilder {

            private Long fromOffset;
            private java.time.Instant fromTimestamp;
            private java.util.List<Integer> partitions;
            private String streamName;
            private Integer batchSize = 100;
            private java.time.Duration pollInterval = java.time.Duration.ofMillis(100);

            public SubscriptionOptionsBuilder fromOffset(Long offset) {
                this.fromOffset = offset;
                return this;
            }

            public SubscriptionOptionsBuilder fromTimestamp(java.time.Instant timestamp) {
                this.fromTimestamp = timestamp;
                return this;
            }

            public SubscriptionOptionsBuilder partitions(java.util.List<Integer> partitions) {
                this.partitions = partitions;
                return this;
            }

            public SubscriptionOptionsBuilder streamName(String streamName) {
                this.streamName = streamName;
                return this;
            }

            public SubscriptionOptionsBuilder batchSize(Integer batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public SubscriptionOptionsBuilder pollInterval(java.time.Duration pollInterval) {
                this.pollInterval = pollInterval;
                return this;
            }

            public SubscriptionOptions build() {
                return new SubscriptionOptions(fromOffset, fromTimestamp, partitions,
                        streamName, batchSize, pollInterval);
            }
        }
    }

    /**
     * Options for tailing.
     */
    record TailOptions(
            boolean fromBeginning,
            boolean fromEnd,
            Long fromOffset,
            java.time.Instant fromTimestamp,
            boolean follow,
            Integer batchSize
            ) {

        public static TailOptionsBuilder builder() {
            return new TailOptionsBuilder();
        }

        public static class TailOptionsBuilder {

            private boolean fromBeginning = false;
            private boolean fromEnd = true;
            private Long fromOffset;
            private java.time.Instant fromTimestamp;
            private boolean follow = true;
            private Integer batchSize = 100;

            public TailOptionsBuilder fromBeginning() {
                this.fromBeginning = true;
                this.fromEnd = false;
                return this;
            }

            public TailOptionsBuilder fromEnd() {
                this.fromEnd = true;
                this.fromBeginning = false;
                return this;
            }

            public TailOptionsBuilder fromOffset(Long offset) {
                this.fromOffset = offset;
                this.fromBeginning = false;
                this.fromEnd = false;
                return this;
            }

            public TailOptionsBuilder fromTimestamp(java.time.Instant timestamp) {
                this.fromTimestamp = timestamp;
                this.fromBeginning = false;
                this.fromEnd = false;
                return this;
            }

            public TailOptionsBuilder follow(boolean follow) {
                this.follow = follow;
                return this;
            }

            public TailOptionsBuilder batchSize(Integer batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public TailOptions build() {
                return new TailOptions(fromBeginning, fromEnd, fromOffset,
                        fromTimestamp, follow, batchSize);
            }
        }
    }
}
