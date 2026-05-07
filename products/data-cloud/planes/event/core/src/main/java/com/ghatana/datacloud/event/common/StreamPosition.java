package com.ghatana.datacloud.event.common;

import java.util.Objects;

/**
 * Immutable position within an EventStream (partition + offset).
 *
 * <p><b>Purpose</b><br>
 * Represents a precise position within a stream, combining:
 * - PartitionId: Which partition
 * - Offset: Position within that partition
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StreamPosition pos = StreamPosition.of(PartitionId.of(3), Offset.of(42L));
 * StreamPosition next = pos.nextOffset();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable stream position (partition + offset)
 * @doc.layer common
 * @doc.pattern Value Object
 */
public record StreamPosition(PartitionId partitionId, Offset offset) implements Comparable<StreamPosition> {

    public StreamPosition {
        Objects.requireNonNull(partitionId, "partitionId required");
        Objects.requireNonNull(offset, "offset required");
    }

    /**
     * Create stream position from partition and offset.
     *
     * @param partitionId partition identifier
     * @param offset offset within partition
     * @return StreamPosition instance
     */
    public static StreamPosition of(PartitionId partitionId, Offset offset) {
        return new StreamPosition(partitionId, offset);
    }

    /**
     * Create stream position from raw values.
     *
     * @param partition partition index
     * @param offset offset value
     * @return StreamPosition instance
     */
    public static StreamPosition of(int partition, long offset) {
        return new StreamPosition(PartitionId.of(partition), Offset.of(offset));
    }

    /**
     * Create position at start of partition.
     *
     * @param partitionId partition identifier
     * @return position at offset 0
     */
    public static StreamPosition startOf(PartitionId partitionId) {
        return new StreamPosition(partitionId, Offset.FIRST);
    }

    /**
     * Create position at latest of partition.
     *
     * @param partitionId partition identifier
     * @return position at LATEST
     */
    public static StreamPosition latestOf(PartitionId partitionId) {
        return new StreamPosition(partitionId, Offset.LATEST);
    }

    /**
     * Get next position (same partition, next offset).
     *
     * @return position with incremented offset
     */
    public StreamPosition nextOffset() {
        return new StreamPosition(partitionId, offset.next());
    }

    /**
     * Check if this position is before another in the same partition.
     *
     * @param other position to compare
     * @return true if this is before other
     * @throws IllegalArgumentException if partitions differ
     */
    public boolean isBefore(StreamPosition other) {
        Objects.requireNonNull(other, "other position required");
        if (!this.partitionId.equals(other.partitionId)) {
            throw new IllegalArgumentException(
                "Cannot compare positions in different partitions: " + this.partitionId + " vs " + other.partitionId
            );
        }
        return this.offset.isBefore(other.offset);
    }

    /**
     * Check if this position is after another in the same partition.
     *
     * @param other position to compare
     * @return true if this is after other
     * @throws IllegalArgumentException if partitions differ
     */
    public boolean isAfter(StreamPosition other) {
        Objects.requireNonNull(other, "other position required");
        if (!this.partitionId.equals(other.partitionId)) {
            throw new IllegalArgumentException(
                "Cannot compare positions in different partitions: " + this.partitionId + " vs " + other.partitionId
            );
        }
        return this.offset.isAfter(other.offset);
    }

    @Override
    public int compareTo(StreamPosition other) {
        int partitionCompare = this.partitionId.compareTo(other.partitionId);
        if (partitionCompare != 0) return partitionCompare;
        return this.offset.compareTo(other.offset);
    }

    @Override
    public String toString() {
        return "StreamPosition[partition=" + partitionId.value() + ", offset=" + offset.value() + "]";
    }
}
