package com.ghatana.core.operator.eventcloud.partition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Round-robin partition assignment strategy.
 * <p>
 * Assigns partitions to consumers in a round-robin fashion. Provides:
 * <ul>
 *   <li>Simple, predictable partition distribution</li>
 *   <li>Balanced load across consumers</li>
 *   <li>Deterministic assignments based on consumer order</li>
 * </ul>
 * </p>
 *
 * @since 2.0
 
 *
 * @doc.type class
 * @doc.purpose Round robin strategy
 * @doc.layer platform
 * @doc.pattern Strategy
*/
public class RoundRobinStrategy implements PartitionAssignmentStrategy {

    @Override
    public boolean shouldAssignPartition(
            int partition,
            String consumerInstanceId,
            int totalConsumers,
            int totalPartitions
    ) {
        if (totalConsumers <= 0 || totalPartitions <= 0) {
            return false;
        }

        Set<Integer> assigned = getAssignedPartitions(consumerInstanceId, totalConsumers, totalPartitions);
        return assigned.contains(partition);
    }

    @Override
    public Set<Integer> getAssignedPartitions(
            String consumerInstanceId,
            int totalConsumers,
            int totalPartitions
    ) {
        if (totalConsumers <= 0 || totalPartitions <= 0) {
            return Set.of();
        }

        Set<Integer> assigned = new HashSet<>();
        
        // Round-robin: assign partition i to consumer (i % totalConsumers)
        int consumerIndex = calculateConsumerIndex(consumerInstanceId, totalConsumers);
        
        for (int partition = 0; partition < totalPartitions; partition++) {
            int assignedConsumerIndex = partition % totalConsumers;
            if (assignedConsumerIndex == consumerIndex) {
                assigned.add(partition);
            }
        }

        return assigned;
    }

    @Override
    public RebalanceResult rebalance(
            PartitionAssignment previousAssignment,
            List<String> currentConsumers,
            int totalPartitions
    ) {
        // Build new assignment using round-robin
        Map<String, Set<Integer>> newConsumerPartitions = new HashMap<>();
        for (String consumer : currentConsumers) {
            newConsumerPartitions.put(consumer, new HashSet<>());
        }

        // Assign partitions in round-robin order
        for (int partition = 0; partition < totalPartitions; partition++) {
            int consumerIndex = partition % currentConsumers.size();
            String consumer = currentConsumers.get(consumerIndex);
            newConsumerPartitions.get(consumer).add(partition);
        }

        PartitionAssignment newAssignment = new PartitionAssignment(
                newConsumerPartitions,
                previousAssignment.getEpoch() + 1
        );

        // Compute changes for all consumers
        Map<String, Set<Integer>> previousAssignments = previousAssignment.getAllAssignments();
        
        Set<Integer> totalAdded = new HashSet<>();
        Set<Integer> totalRevoked = new HashSet<>();

        for (String consumer : currentConsumers) {
            Set<Integer> newPartitions = newConsumerPartitions.getOrDefault(consumer, Set.of());
            Set<Integer> oldPartitions = previousAssignments.getOrDefault(consumer, Set.of());

            // Find added partitions
            newPartitions.stream()
                    .filter(p -> !oldPartitions.contains(p))
                    .forEach(totalAdded::add);

            // Find revoked partitions
            oldPartitions.stream()
                    .filter(p -> !newPartitions.contains(p))
                    .forEach(totalRevoked::add);
        }

        return new RebalanceResult(newAssignment, totalAdded, totalRevoked);
    }

    @Override
    public String getName() {
        return "RoundRobin";
    }

    /**
     * Calculates the consumer index based on the consumer instance ID.
     * <p>
     * Uses a stable hash to ensure consistent ordering across invocations.
     * </p>
     *
     * @param consumerInstanceId Consumer instance ID
     * @param totalConsumers Total number of consumers
     * @return Consumer index (0-based)
     */
    private int calculateConsumerIndex(String consumerInstanceId, int totalConsumers) {
        // Use stable hash to get consistent index
        return Math.abs(consumerInstanceId.hashCode()) % totalConsumers;
    }
}
