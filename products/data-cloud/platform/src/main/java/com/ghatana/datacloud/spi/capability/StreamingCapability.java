package com.ghatana.datacloud.spi.capability;

import io.activej.promise.Promise;

import java.util.function.Consumer;

/**
 * Capability interface for plugins that support streaming operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines streaming operations for real-time data:
 * <ul>
 * <li>Publishing events</li>
 * <li>Subscribing to streams</li>
 * <li>Consumer group management</li>
 * </ul>
 *
 * @param <T> the event type
 * @see com.ghatana.datacloud.spi.Plugin
 * @doc.type interface
 * @doc.purpose Streaming capability for plugins
 * @doc.layer spi
 * @doc.pattern Capability
 */
public interface StreamingCapability<T> {

    /**
     * Publishes an event to a stream.
     *
     * @param streamId the stream ID
     * @param event the event to publish
     * @return the offset of the published event
     */
    Promise<Long> publish(String streamId, T event);

    /**
     * Subscribes to a stream.
     *
     * @param streamId the stream ID
     * @param handler the event handler
     * @return subscription handle
     */
    Promise<Subscription> subscribe(String streamId, Consumer<T> handler);

    /**
     * Subscribes to a stream with a consumer group.
     *
     * @param streamId the stream ID
     * @param consumerGroup the consumer group ID
     * @param handler the event handler
     * @return subscription handle
     */
    Promise<Subscription> subscribe(String streamId, String consumerGroup, Consumer<T> handler);

    /**
     * Gets the latest offset for a stream.
     *
     * @param streamId the stream ID
     * @return the latest offset
     */
    Promise<Long> getLatestOffset(String streamId);

    /**
     * Gets the current offset for a consumer group.
     *
     * @param streamId the stream ID
     * @param consumerGroup the consumer group ID
     * @return the current offset
     */
    Promise<Long> getConsumerOffset(String streamId, String consumerGroup);

    /**
     * Commits the offset for a consumer group.
     *
     * @param streamId the stream ID
     * @param consumerGroup the consumer group ID
     * @param offset the offset to commit
     * @return empty promise on success
     */
    Promise<Void> commitOffset(String streamId, String consumerGroup, long offset);

    /**
     * Subscription handle for managing active subscriptions.
     */
    interface Subscription {

        /**
         * Gets the subscription ID.
         *
         * @return the ID
         */
        String getId();

        /**
         * Gets the stream ID.
         *
         * @return the stream ID
         */
        String getStreamId();

        /**
         * Cancels the subscription.
         *
         * @return empty promise on success
         */
        Promise<Void> cancel();

        /**
         * Checks if the subscription is active.
         *
         * @return true if active
         */
        boolean isActive();
    }
}
