package com.ghatana.datacloud.event.model;

import com.ghatana.datacloud.EntityRecord;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Consumer group for coordinated event consumption.
 *
 * <p>
 * <b>Purpose</b><br>
 * A ConsumerGroup coordinates multiple consumers reading from the same stream,
 * ensuring:
 * <ul>
 * <li><b>Parallelism</b>: Multiple consumers process partitions in
 * parallel</li>
 * <li><b>Load Balancing</b>: Partitions distributed across consumers</li>
 * <li><b>Fault Tolerance</b>: Automatic rebalancing on consumer failure</li>
 * <li><b>Exactly-Once</b>: Committed offsets enable replay/recovery</li>
 * </ul>
 *
 * <p>
 * <b>Inheritance</b><br>
 * <pre>
 * DataRecord (core)
 *   └── EntityRecord (core) - mutable, versioned
 *         └── ConsumerGroup - offset tracking and coordination
 * </pre>
 *
 * <p>
 * <b>Offset Management</b>:
 * <ul>
 * <li>Each partition has an independent committed offset</li>
 * <li>Consumers commit offsets after successful processing</li>
 * <li>On restart, consumers resume from committed offset</li>
 * <li>Uncommitted events are redelivered (at-least-once semantics)</li>
 * </ul>
 *
 * <p>
 * <b>Consumer Lifecycle</b>:
 * <pre>
 * 1. Consumer joins group
 * 2. Coordinator assigns partitions
 * 3. Consumer fetches from committed offset
 * 4. Consumer processes and commits
 * 5. On failure: partitions reassigned to surviving consumers
 * </pre>
 *
 * <p>
 * <b>Usage</b>:
 * <pre>{@code
 * ConsumerGroup group = ConsumerGroup.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("consumer-groups")
 *     .stream(orderStream)
 *     .groupName("order-processor")
 *     .build();
 *
 * // Commit offset after processing
 * group.commitOffset(partitionId, lastProcessedOffset + 1);
 *
 * // Get consumer lag
 * long lag = partition.getCurrentOffset() - group.getCommittedOffset(partitionId);
 * }</pre>
 *
 * @see EntityRecord
 * @see EventStream
 * @see Partition
 * @see Event
 * @doc.type class
 * @doc.purpose Consumer group for coordinated consumption with offset tracking
 * @doc.layer domain
 * @doc.pattern Domain Entity, Consumer Group Protocol
 */
@jakarta.persistence.Entity
@Table(name = "consumer_groups", indexes = {
    @Index(name = "idx_consumer_groups_tenant_stream", columnList = "tenant_id, stream_id"),
    @Index(name = "idx_consumer_groups_name", columnList = "tenant_id, group_name"),
    @Index(name = "idx_consumer_groups_collection", columnList = "tenant_id, collection_name")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumerGroup extends EntityRecord {

    // ==================== Identity ====================
    /**
     * The stream this group consumes from.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stream_id", nullable = false)
    private EventStream stream;

    /**
     * Unique name for this consumer group within tenant+stream.
     *
     * <p>
     * All consumers with the same group name share offset state.
     */
    @NotBlank
    @Column(name = "group_name", nullable = false, length = 255)
    private String groupName;

    // ==================== Offset Tracking ====================
    /**
     * Committed offsets per partition.
     *
     * <p>
     * Map of partitionIndex → committedOffset
     * <p>
     * The committed offset is the NEXT offset to be consumed.
     * <p>
     * Events with offset < committedOffset have been processed.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "committed_offsets", columnDefinition = "jsonb")
    @Builder.Default
    private Map<Integer, Long> committedOffsets = new HashMap<>();

    /**
     * When offsets were last committed.
     *
     * <p>
     * Null if no commits have been made.
     */
    @Column(name = "last_commit_at")
    private Instant lastCommitAt;

    // ==================== Group Metadata ====================
    /**
     * Current generation/epoch of the group.
     *
     * <p>
     * Incremented on each rebalance.
     * <p>
     * Used to fence stale consumers.
     */
    @Column(name = "generation")
    @Builder.Default
    private Long generation = 0L;

    /**
     * ID of the current group coordinator node.
     *
     * <p>
     * The coordinator handles:
     * <ul>
     * <li>Consumer heartbeats</li>
     * <li>Partition assignment</li>
     * <li>Rebalancing</li>
     * </ul>
     */
    @Column(name = "coordinator_node", length = 255)
    private String coordinatorNode;

    /**
     * Current group state.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 20)
    @Builder.Default
    private GroupState state = GroupState.EMPTY;

    // ==================== Status ====================
    /**
     * Whether this group is active.
     *
     * <p>
     * Inactive groups retain offset state but don't participate in consumption.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ==================== Enums ====================
    /**
     * Consumer group state machine.
     */
    public enum GroupState {
        /**
         * No active consumers.
         */
        EMPTY,
        /**
         * Consumers joining, partition assignment pending.
         */
        PREPARING_REBALANCE,
        /**
         * Partition assignment completed, syncing state.
         */
        COMPLETING_REBALANCE,
        /**
         * Stable state, consumers actively consuming.
         */
        STABLE,
        /**
         * Group is being deleted.
         */
        DEAD
    }

    // ==================== Lifecycle Callbacks ====================
    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    // ==================== Offset Operations ====================
    /**
     * Gets the committed offset for a partition.
     *
     * @param partitionIndex the partition index
     * @return committed offset, or 0 if never committed
     */
    public long getCommittedOffset(int partitionIndex) {
        return committedOffsets.getOrDefault(partitionIndex, 0L);
    }

    /**
     * Commits an offset for a partition.
     *
     * @param partitionIndex the partition index
     * @param offset the offset to commit (next to consume)
     */
    public void commitOffset(int partitionIndex, long offset) {
        if (committedOffsets == null) {
            committedOffsets = new HashMap<>();
        }
        committedOffsets.put(partitionIndex, offset);
        lastCommitAt = Instant.now();
    }

    /**
     * Commits offsets for multiple partitions.
     *
     * @param offsets map of partitionIndex → offset
     */
    public void commitOffsets(Map<Integer, Long> offsets) {
        if (committedOffsets == null) {
            committedOffsets = new HashMap<>();
        }
        committedOffsets.putAll(offsets);
        lastCommitAt = Instant.now();
    }

    /**
     * Resets offset for a partition to beginning.
     *
     * @param partitionIndex the partition index
     * @param earliestOffset the earliest available offset
     */
    public void resetToEarliest(int partitionIndex, long earliestOffset) {
        commitOffset(partitionIndex, earliestOffset);
    }

    /**
     * Resets offset for a partition to latest.
     *
     * @param partitionIndex the partition index
     * @param latestOffset the latest offset
     */
    public void resetToLatest(int partitionIndex, long latestOffset) {
        commitOffset(partitionIndex, latestOffset);
    }

    /**
     * Gets lag for a partition.
     *
     * @param partitionIndex the partition index
     * @param currentOffset the partition's current offset
     * @return number of events behind
     */
    public long getLag(int partitionIndex, long currentOffset) {
        return currentOffset - getCommittedOffset(partitionIndex);
    }

    /**
     * Gets committed offsets map, ensuring it's never null.
     *
     * @return committed offsets map (empty if not set)
     */
    public Map<Integer, Long> getCommittedOffsets() {
        if (committedOffsets == null) {
            committedOffsets = new HashMap<>();
        }
        return committedOffsets;
    }

    // ==================== Group Operations ====================
    /**
     * Triggers a rebalance (new epoch).
     */
    public void triggerRebalance() {
        this.generation++;
        this.state = GroupState.PREPARING_REBALANCE;
    }

    /**
     * Completes rebalance and enters stable state.
     */
    public void completeRebalance() {
        this.state = GroupState.STABLE;
    }

    /**
     * Marks group as empty (no active consumers).
     */
    public void markEmpty() {
        this.state = GroupState.EMPTY;
        this.coordinatorNode = null;
    }

    /**
     * Deactivates this group.
     */
    public void deactivate() {
        this.active = false;
        this.state = GroupState.DEAD;
    }

    // ==================== Object Methods ====================
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConsumerGroup that = (ConsumerGroup) o;
        if (getId() != null && that.getId() != null) {
            return Objects.equals(getId(), that.getId());
        }
        return Objects.equals(getTenantId(), that.getTenantId())
                && Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return getId() != null
                ? Objects.hash(getId())
                : Objects.hash(getTenantId(), groupName);
    }

    @Override
    public String toString() {
        return "ConsumerGroup{"
                + "id=" + getId()
                + ", tenantId='" + getTenantId() + '\''
                + ", groupName='" + groupName + '\''
                + ", state=" + state
                + ", generation=" + generation
                + ", active=" + active
                + '}';
    }
}
