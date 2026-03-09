package com.ghatana.datacloud.event.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration options for tailing an EventStream.
 *
 * <p><b>Purpose</b><br>
 * Defines how to tail (subscribe to) events from a stream:
 * - Start position (offset, time, or LATEST)
 * - Batch size for fetching
 * - Timeout for polling
 * - Consumer group for coordination
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TailOptions options = TailOptions.builder()
 *     .fromOffset(Offset.LATEST)
 *     .batchSize(100)
 *     .pollTimeout(Duration.ofSeconds(1))
 *     .consumerGroup("my-service")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for stream tailing
 * @doc.layer common
 * @doc.pattern Builder, Value Object
 */
public final class TailOptions {

    /**
     * Start mode for tailing.
     */
    public enum StartMode {
        /**
         * Start from specific offset.
         */
        FROM_OFFSET,
        /**
         * Start from specific timestamp.
         */
        FROM_TIME,
        /**
         * Start from latest (new events only).
         */
        FROM_LATEST,
        /**
         * Start from earliest (all events).
         */
        FROM_EARLIEST,
        /**
         * Resume from committed offset (consumer group).
         */
        FROM_COMMITTED
    }

    private final StartMode startMode;
    private final Offset startOffset;
    private final Instant startTime;
    private final int batchSize;
    private final Duration pollTimeout;
    private final String consumerGroup;
    private final boolean autoCommit;
    private final Duration autoCommitInterval;

    private TailOptions(Builder builder) {
        this.startMode = builder.startMode;
        this.startOffset = builder.startOffset;
        this.startTime = builder.startTime;
        this.batchSize = builder.batchSize;
        this.pollTimeout = builder.pollTimeout;
        this.consumerGroup = builder.consumerGroup;
        this.autoCommit = builder.autoCommit;
        this.autoCommitInterval = builder.autoCommitInterval;
    }

    // ==================== Getters ====================

    public StartMode startMode() {
        return startMode;
    }

    public Optional<Offset> startOffset() {
        return Optional.ofNullable(startOffset);
    }

    public Optional<Instant> startTime() {
        return Optional.ofNullable(startTime);
    }

    public int batchSize() {
        return batchSize;
    }

    public Duration pollTimeout() {
        return pollTimeout;
    }

    public Optional<String> consumerGroup() {
        return Optional.ofNullable(consumerGroup);
    }

    public boolean autoCommit() {
        return autoCommit;
    }

    public Duration autoCommitInterval() {
        return autoCommitInterval;
    }

    // ==================== Factory Methods ====================

    /**
     * Create options starting from latest.
     *
     * @return TailOptions for latest
     */
    public static TailOptions fromLatest() {
        return builder().fromLatest().build();
    }

    /**
     * Create options starting from earliest.
     *
     * @return TailOptions for earliest
     */
    public static TailOptions fromEarliest() {
        return builder().fromEarliest().build();
    }

    /**
     * Create options starting from specific offset.
     *
     * @param offset starting offset
     * @return TailOptions for offset
     */
    public static TailOptions fromOffset(Offset offset) {
        return builder().fromOffset(offset).build();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private StartMode startMode = StartMode.FROM_LATEST;
        private Offset startOffset;
        private Instant startTime;
        private int batchSize = 100;
        private Duration pollTimeout = Duration.ofSeconds(1);
        private String consumerGroup;
        private boolean autoCommit = false;
        private Duration autoCommitInterval = Duration.ofSeconds(5);

        private Builder() {}

        public Builder fromOffset(Offset offset) {
            Objects.requireNonNull(offset, "offset required");
            this.startMode = StartMode.FROM_OFFSET;
            this.startOffset = offset;
            return this;
        }

        public Builder fromTime(Instant time) {
            Objects.requireNonNull(time, "time required");
            this.startMode = StartMode.FROM_TIME;
            this.startTime = time;
            return this;
        }

        public Builder fromLatest() {
            this.startMode = StartMode.FROM_LATEST;
            return this;
        }

        public Builder fromEarliest() {
            this.startMode = StartMode.FROM_EARLIEST;
            return this;
        }

        public Builder fromCommitted() {
            this.startMode = StartMode.FROM_COMMITTED;
            return this;
        }

        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be > 0");
            }
            this.batchSize = batchSize;
            return this;
        }

        public Builder pollTimeout(Duration pollTimeout) {
            Objects.requireNonNull(pollTimeout, "pollTimeout required");
            this.pollTimeout = pollTimeout;
            return this;
        }

        public Builder consumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
            if (consumerGroup != null && startMode == StartMode.FROM_LATEST) {
                // Default to resuming from committed when consumer group is set
                this.startMode = StartMode.FROM_COMMITTED;
            }
            return this;
        }

        public Builder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public Builder autoCommitInterval(Duration interval) {
            Objects.requireNonNull(interval, "interval required");
            this.autoCommitInterval = interval;
            return this;
        }

        public TailOptions build() {
            // Validate: FROM_COMMITTED requires consumer group
            if (startMode == StartMode.FROM_COMMITTED && consumerGroup == null) {
                throw new IllegalStateException("FROM_COMMITTED requires consumerGroup");
            }
            return new TailOptions(this);
        }
    }

    @Override
    public String toString() {
        return "TailOptions[" +
            "startMode=" + startMode +
            ", batchSize=" + batchSize +
            ", consumerGroup=" + consumerGroup +
            "]";
    }
}
