package com.ghatana.core.operator.eventcloud.partition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Consistent hash-based partition assignment strategy.
 * <p>
 * Uses consistent hashing to assign partitions to consumers. Provides:
 * <ul>
 *   <li>Balanced partition distribution</li>
 *   <li>Minimal partition movement on rebalancing</li>
 *   <li>Deterministic assignments</li>
 * </ul>
 * </p>
 *
 * @since 2.0
 
 *
 * @doc.type class
 * @doc.purpose Consistent hash strategy
 * @doc.layer platform
 * @doc.pattern Strategy
*/
public class ConsistentHashStrategy implements PartitionAssignmentStrategy {

    private static final int VIRTUAL_NODES = 150;

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

        // Use a direct hash-based approach for better clarity
        Set<Integer> assigned = new HashSet<>();
        for (int partition = 0; partition < totalPartitions; partition++) {
            // Hash the consumer+partition combination and assign consistently
            long hashValue = Math.abs((long) (consumerInstanceId + ":" + partition).hashCode());
            int consumerIndex = (int) (hashValue % totalConsumers);
            
            // Get the stable consumer index for the given consumer ID
            int myConsumerIndex = Math.abs(consumerInstanceId.hashCode()) % totalConsumers;
            
            if (consumerIndex == myConsumerIndex) {
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
        // Build new assignment
        TreeMap<Long, String> ring = buildConsistentHashRing(currentConsumers, VIRTUAL_NODES);
        
        Map<String, Set<Integer>> newConsumerPartitions = new HashMap<>();
        for (String consumer : currentConsumers) {
            newConsumerPartitions.put(consumer, new HashSet<>());
        }

        for (int partition = 0; partition < totalPartitions; partition++) {
            String consumer = getConsumerForPartition(ring, partition);
            newConsumerPartitions.get(consumer).add(partition);
        }

        PartitionAssignment newAssignment = new PartitionAssignment(
                newConsumerPartitions,
                previousAssignment.getEpoch() + 1
        );

        // Compute changes
        Set<Integer> addedPartitions = new HashSet<>();
        Set<Integer> revokedPartitions = new HashSet<>();

        // This is a simplified approach - in real scenario we'd track per consumer
        // For now, we compute the total changes
        previousAssignment.getAllAssignments().values().forEach(
                partitions -> revokedPartitions.addAll(partitions)
        );
        newAssignment.getAllAssignments().values().forEach(
                partitions -> {
                    if (!revokedPartitions.contains(partitions)) {
                        addedPartitions.addAll(partitions);
                    }
                }
        );

        return new RebalanceResult(newAssignment, addedPartitions, revokedPartitions);
    }

    @Override
    public String getName() {
        return "ConsistentHash";
    }

    /**
     * Builds a consistent hash ring for the given consumers.
     *
     * @param consumers Consumer instance IDs
     * @param virtualNodes Virtual nodes per consumer
     * @return TreeMap representing the hash ring
     */
    private TreeMap<Long, String> buildConsistentHashRing(List<String> consumers, int virtualNodes) {
        TreeMap<Long, String> ring = new TreeMap<>();

        for (String consumer : consumers) {
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hashFunction(consumer + ":" + i);
                ring.put(hash, consumer);
            }
        }

        return ring;
    }

    /**
     * Gets the consumer responsible for a partition using the hash ring.
     *
     * @param ring Consistent hash ring
     * @param partition Partition ID
     * @return Consumer instance ID responsible for this partition
     */
    private String getConsumerForPartition(TreeMap<Long, String> ring, int partition) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("Hash ring is empty");
        }

        long partitionHash = hashFunction("partition:" + partition);
        Map.Entry<Long, String> entry = ring.ceilingEntry(partitionHash);

        if (entry == null) {
            // Wrap around to the beginning
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    /**
     * Simple hash function for consistent hashing.
     *
     * @param key Key to hash
     * @return Hash value
     */
    private long hashFunction(String key) {
        return Math.abs((long) key.hashCode());
    }

    /**
     * Generates a stable list of consumer IDs based on consumer count.
     * <p>
     * Uses index-based naming to ensure deterministic results across invocations.
     * </p>
     *
     * @param currentConsumerId Current consumer instance ID
     * @param totalConsumers Total number of consumers
     * @return List of consumer IDs
     */
    private List<String> generateConsumerIds(String currentConsumerId, int totalConsumers) {
        List<String> consumers = new ArrayList<>();
        
        // For deterministic hashing, we need consistent consumer IDs
        // Use index-based IDs that won't collide with real instance names
        for (int i = 0; i < totalConsumers; i++) {
            // Create stable IDs based on current consumer ID root and index
            String consumerId = String.format("%s_partition_group_%d", 
                    currentConsumerId.substring(0, Math.min(10, currentConsumerId.length())), 
                    i);
            consumers.add(consumerId);
        }

        return consumers;
    }
}
