package com.ghatana.datacloud.event.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Partition within an EventStream for parallel processing.
 * 
 * <p><b>Purpose</b><br>
 * Represents a single partition within an EventStream. Partitions enable:
 * <ul>
 *   <li><b>Parallelism</b>: Multiple consumers process different partitions</li>
 *   <li><b>Ordering</b>: Events within a partition are strictly ordered</li>
 *   <li><b>Scalability</b>: More partitions = more parallelism</li>
 * </ul>
 * 
 * <p><b>Offset Semantics</b>:
 * <ul>
 *   <li>Offsets start at 0 and increase monotonically</li>
 *   <li>Offsets are unique within a partition (not globally)</li>
 *   <li>earliestOffset: Lowest available offset (after compaction/retention)</li>
 *   <li>currentOffset: Next offset to be assigned</li>
 * </ul>
 * 
 * <p><b>Leader Election</b>:
 * <ul>
 *   <li>Each partition has a leader node responsible for writes</li>
 *   <li>Leader handles all produce requests for this partition</li>
 *   <li>Leader election happens on node failure</li>
 * </ul>
 * 
 * <p><b>Usage</b>:
 * <pre>{@code
 * // Partitions are typically created by EventStream
 * EventStream stream = EventStream.builder()
 *     .partitionCount(8)
 *     .build();
 * 
 * // Access partition info
 * Partition partition = stream.getPartitions().get(0);
 * long nextOffset = partition.getCurrentOffset();
 * long lag = partition.getCurrentOffset() - consumer.getCommittedOffset();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Partition within stream for parallel processing and ordering
 * @doc.layer domain
 * @doc.pattern Domain Entity, Partitioned Log
 * 
 * @see EventStream
 * @see Event
 * @see ConsumerGroup
 * 
 * @author EventCloud Team
 * @since 1.0.0
 */
@jakarta.persistence.Entity
@Table(name = "partitions", indexes = {
    @Index(name = "idx_partitions_stream", columnList = "stream_id"),
    @Index(name = "idx_partitions_stream_index", columnList = "stream_id, partition_index")
})
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Partition {

    // ==================== Identity ====================

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The stream this partition belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false)
    private EventStream stream;

    /**
     * Zero-based index of this partition within the stream.
     * 
     * <p>Range: [0, stream.partitionCount - 1]
     */
    @NotNull
    @Column(name = "partition_index", nullable = false)
    private Integer partitionIndex;

    // ==================== Offset Tracking ====================

    /**
     * Next offset to be assigned to a new event.
     * 
     * <p>Starts at 0. Incremented atomically on each append.
     * <p>High watermark: all events with offset < currentOffset exist.
     */
    @NotNull
    @Column(name = "current_offset", nullable = false)
    @Builder.Default
    private Long currentOffset = 0L;

    /**
     * Earliest available offset in this partition.
     * 
     * <p>Events with offset < earliestOffset have been:
     * <ul>
     *   <li>Deleted by retention policy</li>
     *   <li>Removed by log compaction</li>
     *   <li>Moved to cold storage (no longer queryable)</li>
     * </ul>
     */
    @NotNull
    @Column(name = "earliest_offset", nullable = false)
    @Builder.Default
    private Long earliestOffset = 0L;

    // ==================== Leader Election ====================

    /**
     * Node ID of the current leader for this partition.
     * 
     * <p>The leader handles all write operations.
     * <p>Null if no leader is assigned (partition unavailable).
     */
    @Column(name = "leader_node", length = 255)
    private String leaderNode;

    /**
     * Epoch number for leader election.
     * 
     * <p>Incremented on each leader change.
     * <p>Used to detect stale leaders and fence old writes.
     */
    @Column(name = "leader_epoch")
    @Builder.Default
    private Long leaderEpoch = 0L;

    // ==================== Timestamps ====================

    /**
     * When the last event was written to this partition.
     * 
     * <p>Null if no events have been written.
     */
    @Column(name = "last_write_at")
    private Instant lastWriteAt;

    /**
     * When this partition was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ==================== Lifecycle Callbacks ====================

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ==================== Offset Operations ====================

    /**
     * Gets the number of events in this partition.
     * 
     * @return count of events (currentOffset - earliestOffset)
     */
    public long getEventCount() {
        return currentOffset - earliestOffset;
    }

    /**
     * Checks if this partition has any events.
     * 
     * @return true if at least one event exists
     */
    public boolean hasEvents() {
        return currentOffset > earliestOffset;
    }

    /**
     * Advances the current offset after appending an event.
     * 
     * <p><b>Thread Safety</b>: This should be called within a transaction
     * with proper locking to ensure atomic offset assignment.
     * 
     * @return the offset assigned to the new event
     */
    public long assignNextOffset() {
        long assigned = currentOffset;
        currentOffset++;
        lastWriteAt = Instant.now();
        return assigned;
    }

    /**
     * Advances the earliest offset after retention cleanup.
     * 
     * @param newEarliest the new earliest offset
     * @throws IllegalArgumentException if newEarliest > currentOffset
     */
    public void advanceEarliestOffset(long newEarliest) {
        if (newEarliest > currentOffset) {
            throw new IllegalArgumentException(
                "Earliest offset cannot exceed current offset: " + newEarliest + " > " + currentOffset);
        }
        if (newEarliest > earliestOffset) {
            earliestOffset = newEarliest;
        }
    }

    /**
     * Checks if an offset is valid (within available range).
     * 
     * @param offset the offset to check
     * @return true if offset is available
     */
    public boolean isValidOffset(long offset) {
        return offset >= earliestOffset && offset < currentOffset;
    }

    // ==================== Leader Operations ====================

    /**
     * Assigns a new leader to this partition.
     * 
     * @param nodeId the new leader node ID
     */
    public void assignLeader(String nodeId) {
        this.leaderNode = nodeId;
        this.leaderEpoch++;
    }

    /**
     * Removes the leader from this partition.
     * 
     * <p>Partition becomes unavailable for writes until new leader elected.
     */
    public void revokeLeader() {
        this.leaderNode = null;
    }

    /**
     * Checks if this partition has an active leader.
     * 
     * @return true if a leader is assigned
     */
    public boolean hasLeader() {
        return leaderNode != null && !leaderNode.isEmpty();
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Partition partition = (Partition) o;
        return id != null && Objects.equals(id, partition.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Partition{" +
                "id=" + id +
                ", partitionIndex=" + partitionIndex +
                ", currentOffset=" + currentOffset +
                ", earliestOffset=" + earliestOffset +
                ", leaderNode='" + leaderNode + '\'' +
                ", leaderEpoch=" + leaderEpoch +
                '}';
    }
}
