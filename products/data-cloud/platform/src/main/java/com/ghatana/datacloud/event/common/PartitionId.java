package com.ghatana.datacloud.event.common;

/**
 * Immutable partition identifier within a stream.
 *
 * <p><b>Purpose</b><br>
 * Represents a partition within an EventStream. Partition IDs are:
 * - Zero-based integer indices
 * - Immutable (partitions don't change identity)
 * - Stream-scoped (same ID can exist in different streams)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PartitionId partition = PartitionId.of(3);
 * int index = partition.value();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable partition identifier
 * @doc.layer common
 * @doc.pattern Value Object
 */
public record PartitionId(int value) implements Comparable<PartitionId> {

    /**
     * First partition (index 0).
     */
    public static final PartitionId FIRST = new PartitionId(0);

    /**
     * Broadcast marker (all partitions).
     */
    public static final PartitionId ALL = new PartitionId(-1);

    public PartitionId {
        if (value < -1) {
            throw new IllegalArgumentException("Partition ID must be >= -1, got: " + value);
        }
    }

    /**
     * Create partition ID from index.
     *
     * @param index partition index
     * @return PartitionId instance
     */
    public static PartitionId of(int index) {
        if (index == 0) return FIRST;
        if (index == -1) return ALL;
        return new PartitionId(index);
    }

    /**
     * Check if this is a broadcast marker (all partitions).
     *
     * @return true if ALL
     */
    public boolean isBroadcast() {
        return value == -1;
    }

    /**
     * Check if this partition ID is within the given partition count.
     *
     * @param partitionCount total number of partitions
     * @return true if valid for the partition count
     */
    public boolean isValidFor(int partitionCount) {
        if (isBroadcast()) return true;
        return value >= 0 && value < partitionCount;
    }

    @Override
    public int compareTo(PartitionId other) {
        return Integer.compare(this.value, other.value);
    }

    @Override
    public String toString() {
        if (this == ALL) return "PartitionId[ALL]";
        return "PartitionId[" + value + "]";
    }
}
