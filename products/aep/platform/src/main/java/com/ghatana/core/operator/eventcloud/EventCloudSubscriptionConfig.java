package com.ghatana.core.operator.eventcloud;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Configuration for EventCloud subscription.
 * <p>
 * Defines how an operator should subscribe to and consume events from EventCloud.
 * </p>
 *
 * @since 2.0
 */
public final class EventCloudSubscriptionConfig {

    private final String tenantId;
    private final String eventTypePattern;
    private final Instant startAt;
    private final int maxBatchSize;
    private final Duration pollTimeout;
    private final boolean autoCommit;
    private final int prefetchCount;
    private final ReconnectionStrategy reconnectionStrategy;
    private final int alertThreshold;
    private final String consumerGroup;

    private EventCloudSubscriptionConfig(Builder builder) {
        this.tenantId = builder.tenantId;
        this.eventTypePattern = builder.eventTypePattern;
        this.startAt = builder.startAt;
        this.maxBatchSize = builder.maxBatchSize;
        this.pollTimeout = builder.pollTimeout;
        this.autoCommit = builder.autoCommit;
        this.prefetchCount = builder.prefetchCount;
        this.reconnectionStrategy = builder.reconnectionStrategy;
        this.alertThreshold = builder.alertThreshold;
        this.consumerGroup = builder.consumerGroup;
    }

    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * Creates a subscription configuration for all events of a tenant.
     *
     * @param tenantId The tenant identifier
     * @return Configuration for all events
     */
    public static EventCloudSubscriptionConfig allEvents(String tenantId) {
        return builder(tenantId)
                .eventTypePattern("*")
                .build();
    }

    /**
     * Creates a subscription configuration for specific event types.
     *
     * @param tenantId The tenant identifier
     * @param eventTypePattern Event type pattern (supports wildcards)
     * @return Configuration for filtered events
     */
    public static EventCloudSubscriptionConfig forEventType(String tenantId, String eventTypePattern) {
        return builder(tenantId)
                .eventTypePattern(eventTypePattern)
                .build();
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getEventTypePattern() {
        return eventTypePattern;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public ReconnectionStrategy getReconnectionStrategy() {
        return reconnectionStrategy;
    }

    public int getAlertThreshold() {
        return alertThreshold;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    @Override
    public String toString() {
        return "EventCloudSubscriptionConfig{" +
                "tenant='" + tenantId + '\'' +
                ", pattern='" + eventTypePattern + '\'' +
                ", startAt=" + startAt +
                ", batchSize=" + maxBatchSize +
                ", consumerGroup='" + consumerGroup + '\'' +
                '}';
    }

    public static final class Builder {
        private final String tenantId;
        private String eventTypePattern = "*";
        private Instant startAt = Instant.now();
        private int maxBatchSize = 100;
        private Duration pollTimeout = Duration.ofSeconds(5);
        private boolean autoCommit = true;
        private int prefetchCount = 10;
        private ReconnectionStrategy reconnectionStrategy = ReconnectionStrategy.defaultStrategy();
        private int alertThreshold = 5;
        private String consumerGroup = "default";

        private Builder(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        }

        /**
         * Sets the event type pattern to filter events.
         * <p>
         * Supports wildcards:
         * <ul>
         *   <li>"*" - All events</li>
         *   <li>"user.*" - All events starting with "user."</li>
         *   <li>"*.created" - All events ending with ".created"</li>
         * </ul>
         *
         * @param eventTypePattern The event type pattern
         * @return This builder
         */
        public Builder eventTypePattern(String eventTypePattern) {
            this.eventTypePattern = Objects.requireNonNull(eventTypePattern, "Event type pattern must not be null");
            return this;
        }

        /**
         * Sets the starting point for event consumption.
         * <p>
         * Events before this timestamp will be skipped.
         * </p>
         *
         * @param startAt The starting timestamp
         * @return This builder
         */
        public Builder startAt(Instant startAt) {
            this.startAt = Objects.requireNonNull(startAt, "Start timestamp must not be null");
            return this;
        }

        /**
         * Sets the maximum batch size for event consumption.
         * <p>
         * EventCloud will return up to this many events per poll.
         * </p>
         *
         * @param maxBatchSize Maximum batch size (must be > 0)
         * @return This builder
         */
        public Builder maxBatchSize(int maxBatchSize) {
            if (maxBatchSize <= 0) {
                throw new IllegalArgumentException("Max batch size must be > 0");
            }
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        /**
         * Sets the poll timeout duration.
         * <p>
         * How long to wait for new events before returning empty batch.
         * </p>
         *
         * @param pollTimeout Poll timeout duration
         * @return This builder
         */
        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = Objects.requireNonNull(pollTimeout, "Poll timeout must not be null");
            return this;
        }

        /**
         * Sets whether to auto-commit offsets after processing.
         *
         * @param autoCommit True to auto-commit, false for manual commit
         * @return This builder
         */
        public Builder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        /**
         * Sets the prefetch count for event buffering.
         * <p>
         * Number of batches to prefetch ahead of consumption.
         * </p>
         *
         * @param prefetchCount Prefetch count (must be >= 0)
         * @return This builder
         */
        public Builder prefetchCount(int prefetchCount) {
            if (prefetchCount < 0) {
                throw new IllegalArgumentException("Prefetch count must be >= 0");
            }
            this.prefetchCount = prefetchCount;
            return this;
        }

        /**
         * Sets the reconnection strategy for handling connection failures.
         * <p>
         * Defines how the operator should retry connections after failures.
         * </p>
         *
         * @param reconnectionStrategy Reconnection strategy
         * @return This builder
         */
        public Builder reconnectionStrategy(ReconnectionStrategy reconnectionStrategy) {
            this.reconnectionStrategy = Objects.requireNonNull(reconnectionStrategy, "Reconnection strategy must not be null");
            return this;
        }

        /**
         * Sets the alert threshold for consecutive failures.
         * <p>
         * When consecutive failures reach this threshold, alerts are emitted.
         * </p>
         *
         * @param alertThreshold Alert threshold (must be > 0)
         * @return This builder
         */
        public Builder alertThreshold(int alertThreshold) {
            if (alertThreshold <= 0) {
                throw new IllegalArgumentException("Alert threshold must be > 0");
            }
            this.alertThreshold = alertThreshold;
            return this;
        }

        /**
         * Sets the consumer group for the subscription.
         * <p>
         * Events will be distributed to instances with the same consumer group.
         * </p>
         *
         * @param consumerGroup The consumer group ID
         * @return This builder
         */
        public Builder consumerGroup(String consumerGroup) {
            this.consumerGroup = Objects.requireNonNull(consumerGroup, "Consumer group must not be null");
            return this;
        }

        public EventCloudSubscriptionConfig build() {
            return new EventCloudSubscriptionConfig(this);
        }
    }
}
