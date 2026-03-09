package com.ghatana.core.operator.eventcloud.partition;

import java.util.Set;
import java.util.Objects;

/**
 * Result of a partition rebalancing operation.
 *
 * @since 2.0
 
 *
 * @doc.type class
 * @doc.purpose Rebalance result
 * @doc.layer platform
 * @doc.pattern Component
*/
public class RebalanceResult {

    private final PartitionAssignment newAssignment;
    private final Set<Integer> addedPartitions;
    private final Set<Integer> revokedPartitions;

    /**
     * Creates a new rebalance result.
     *
     * @param newAssignment New partition assignment
     * @param addedPartitions Partitions newly assigned to this consumer
     * @param revokedPartitions Partitions revoked from this consumer
     */
    public RebalanceResult(
            PartitionAssignment newAssignment,
            Set<Integer> addedPartitions,
            Set<Integer> revokedPartitions
    ) {
        this.newAssignment = Objects.requireNonNull(newAssignment, "New assignment must not be null");
        this.addedPartitions = Objects.requireNonNull(addedPartitions, "Added partitions must not be null");
        this.revokedPartitions = Objects.requireNonNull(revokedPartitions, "Revoked partitions must not be null");
    }

    /**
     * Gets the new partition assignment after rebalancing.
     *
     * @return New assignment
     */
    public PartitionAssignment getNewAssignment() {
        return newAssignment;
    }

    /**
     * Gets the partitions newly assigned to this consumer.
     *
     * @return Set of added partition IDs
     */
    public Set<Integer> getAddedPartitions() {
        return Set.copyOf(addedPartitions);
    }

    /**
     * Gets the partitions revoked from this consumer.
     *
     * @return Set of revoked partition IDs
     */
    public Set<Integer> getRevokedPartitions() {
        return Set.copyOf(revokedPartitions);
    }

    /**
     * Checks if rebalancing caused any changes.
     *
     * @return true if any partitions were added or revoked
     */
    public boolean hasChanges() {
        return !addedPartitions.isEmpty() || !revokedPartitions.isEmpty();
    }

    @Override
    public String toString() {
        return "RebalanceResult{" +
                "newAssignment=" + newAssignment +
                ", addedPartitions=" + addedPartitions +
                ", revokedPartitions=" + revokedPartitions +
                '}';
    }
}
