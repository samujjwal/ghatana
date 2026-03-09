package com.ghatana.core.operator.eventcloud.partition;

import java.util.Map;
import java.util.Set;
import java.util.Objects;

/**
 * Represents partition assignment for one or more consumer instances.
 *
 * @since 2.0
 
 *
 * @doc.type class
 * @doc.purpose Partition assignment
 * @doc.layer platform
 * @doc.pattern Component
*/
public class PartitionAssignment {

    private final Map<String, Set<Integer>> consumerPartitions;
    private final int epoch;

    /**
     * Creates a new partition assignment.
     *
     * @param consumerPartitions Map of consumer instance ID to assigned partitions
     * @param epoch Assignment epoch/version
     */
    public PartitionAssignment(Map<String, Set<Integer>> consumerPartitions, int epoch) {
        this.consumerPartitions = Objects.requireNonNull(consumerPartitions, "Consumer partitions must not be null");
        this.epoch = epoch;
    }

    /**
     * Gets the assigned partitions for a specific consumer.
     *
     * @param consumerId Consumer instance ID
     * @return Set of assigned partition IDs, or empty set if none assigned
     */
    public Set<Integer> getPartitionsFor(String consumerId) {
        return consumerPartitions.getOrDefault(consumerId, Set.of());
    }

    /**
     * Gets all assigned partitions for all consumers.
     *
     * @return Map of consumer ID to assigned partitions
     */
    public Map<String, Set<Integer>> getAllAssignments() {
        return Map.copyOf(consumerPartitions);
    }

    /**
     * Gets the assignment epoch/version.
     *
     * @return Epoch number
     */
    public int getEpoch() {
        return epoch;
    }

    /**
     * Checks if a partition is assigned to any consumer.
     *
     * @param partition Partition ID
     * @return true if assigned, false otherwise
     */
    public boolean isAssigned(int partition) {
        return consumerPartitions.values().stream()
                .anyMatch(partitions -> partitions.contains(partition));
    }

    /**
     * Gets the total number of assigned partitions across all consumers.
     *
     * @return Total assigned partition count
     */
    public int getTotalAssignedPartitions() {
        return consumerPartitions.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Override
    public String toString() {
        return "PartitionAssignment{" +
                "consumerPartitions=" + consumerPartitions +
                ", epoch=" + epoch +
                '}';
    }
}
