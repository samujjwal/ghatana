package com.ghatana.core.operator.eventcloud.partition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks offsets for each partition in EventCloud consumption.
 * <p>
 * Maintains per-partition offsets for:
 * <ul>
 *   <li>Last consumed offset (what we've read)</li>
 *   <li>Last committed offset (durably persisted)</li>
 *   <li>In-flight offsets (being processed)</li>
 * </ul>
 * </p>
 * <p>
 * Thread-safe for concurrent partition tracking.
 * </p>
 *
 * @since 2.0
 
 *
 * @doc.type class
 * @doc.purpose Offset tracker
 * @doc.layer platform
 * @doc.pattern Component
*/
public class OffsetTracker {

    private final Map<Integer, PartitionOffsets> partitionOffsets = new ConcurrentHashMap<>();
    private final String consumerId;

    /**
     * Creates a new offset tracker.
     *
     * @param consumerId Consumer instance ID
     */
    public OffsetTracker(String consumerId) {
        this.consumerId = Objects.requireNonNull(consumerId, "Consumer ID must not be null");
    }

    /**
     * Updates the last consumed offset for a partition.
     *
     * @param partition Partition ID
     * @param offset Offset to set
     */
    public void updateConsumedOffset(int partition, long offset) {
        partitionOffsets
                .computeIfAbsent(partition, p -> new PartitionOffsets(partition))
                .updateConsumedOffset(offset);
    }

    /**
     * Updates the last committed offset for a partition.
     *
     * @param partition Partition ID
     * @param offset Offset to commit
     */
    public void commitOffset(int partition, long offset) {
        partitionOffsets
                .computeIfAbsent(partition, p -> new PartitionOffsets(partition))
                .commitOffset(offset);
    }

    /**
     * Gets the last committed offset for a partition.
     *
     * @param partition Partition ID
     * @return Committed offset, or -1 if not committed
     */
    public long getCommittedOffset(int partition) {
        PartitionOffsets offsets = partitionOffsets.get(partition);
        return offsets != null ? offsets.getCommittedOffset() : -1;
    }

    /**
     * Gets the last consumed offset for a partition.
     *
     * @param partition Partition ID
     * @return Consumed offset, or -1 if not consumed
     */
    public long getConsumedOffset(int partition) {
        PartitionOffsets offsets = partitionOffsets.get(partition);
        return offsets != null ? offsets.getConsumedOffset() : -1;
    }

    /**
     * Gets the lag for a partition (uncommitted records).
     *
     * @param partition Partition ID
     * @return Number of uncommitted records
     */
    public long getPartitionLag(int partition) {
        PartitionOffsets offsets = partitionOffsets.get(partition);
        if (offsets == null) {
            return 0;
        }
        long consumed = offsets.getConsumedOffset();
        long committed = offsets.getCommittedOffset();
        return consumed > committed ? (consumed - committed) : 0;
    }

    /**
     * Gets the total lag across all partitions.
     *
     * @return Total uncommitted records
     */
    public long getTotalLag() {
        return partitionOffsets.values().stream()
                .mapToLong(po -> {
                    long consumed = po.getConsumedOffset();
                    long committed = po.getCommittedOffset();
                    return consumed > committed ? (consumed - committed) : 0;
                })
                .sum();
    }

    /**
     * Gets all tracked partition offsets.
     *
     * @return Map of partition ID to offset info
     */
    public Map<Integer, PartitionOffsetSnapshot> getSnapshot() {
        Map<Integer, PartitionOffsetSnapshot> snapshot = new HashMap<>();
        partitionOffsets.forEach((partition, offsets) ->
                snapshot.put(partition, offsets.getSnapshot())
        );
        return snapshot;
    }

    /**
     * Gets tracked partitions.
     *
     * @return Set of partition IDs being tracked
     */
    public java.util.Set<Integer> getTrackedPartitions() {
        return java.util.Set.copyOf(partitionOffsets.keySet());
    }

    /**
     * Clears tracking for a partition (when revoked).
     *
     * @param partition Partition ID
     */
    public void clearPartition(int partition) {
        partitionOffsets.remove(partition);
    }

    /**
     * Gets consumer ID.
     *
     * @return Consumer instance ID
     */
    public String getConsumerId() {
        return consumerId;
    }

    /**
     * Per-partition offset tracking.
     */
    private static class PartitionOffsets {
        private final int partition;
        private final AtomicLong consumedOffset = new AtomicLong(-1);
        private final AtomicLong committedOffset = new AtomicLong(-1);

        PartitionOffsets(int partition) {
            this.partition = partition;
        }

        void updateConsumedOffset(long offset) {
            consumedOffset.set(offset);
        }

        void commitOffset(long offset) {
            committedOffset.set(offset);
        }

        long getConsumedOffset() {
            return consumedOffset.get();
        }

        long getCommittedOffset() {
            return committedOffset.get();
        }

        PartitionOffsetSnapshot getSnapshot() {
            return new PartitionOffsetSnapshot(
                    partition,
                    consumedOffset.get(),
                    committedOffset.get()
            );
        }
    }

    /**
     * Immutable snapshot of partition offset state.
     */
    public static class PartitionOffsetSnapshot {
        private final int partition;
        private final long consumedOffset;
        private final long committedOffset;

        public PartitionOffsetSnapshot(int partition, long consumedOffset, long committedOffset) {
            this.partition = partition;
            this.consumedOffset = consumedOffset;
            this.committedOffset = committedOffset;
        }

        public int getPartition() {
            return partition;
        }

        public long getConsumedOffset() {
            return consumedOffset;
        }

        public long getCommittedOffset() {
            return committedOffset;
        }

        public long getLag() {
            return consumedOffset > committedOffset ? (consumedOffset - committedOffset) : 0;
        }

        @Override
        public String toString() {
            return "PartitionOffsetSnapshot{" +
                    "partition=" + partition +
                    ", consumedOffset=" + consumedOffset +
                    ", committedOffset=" + committedOffset +
                    ", lag=" + getLag() +
                    '}';
        }
    }
}
