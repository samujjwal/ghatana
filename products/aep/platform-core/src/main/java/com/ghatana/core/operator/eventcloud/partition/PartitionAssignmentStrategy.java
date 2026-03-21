package com.ghatana.core.operator.eventcloud.partition;

import java.util.List;
import java.util.Set;

/**
 * Strategy interface for assigning partitions to consumer instances.
 * <p>
 * Implementations define how partitions are distributed across multiple
 * operators instances for parallel consumption and load balancing.
 * </p>
 *
 * @since 2.0
 
 *
 * @doc.type interface
 * @doc.purpose Partition assignment strategy
 * @doc.layer platform
 * @doc.pattern Strategy
*/
public interface PartitionAssignmentStrategy {

    /**
     * Calculates which partitions should be assigned to a specific consumer.
     *
     * @param partition Partition identifier
     * @param consumerInstanceId Current consumer instance ID
     * @param totalConsumers Total number of consumer instances
     * @param totalPartitions Total number of partitions
     * @return true if partition should be assigned to this consumer, false otherwise
     */
    boolean shouldAssignPartition(
            int partition,
            String consumerInstanceId,
            int totalConsumers,
            int totalPartitions
    );

    /**
     * Gets all assigned partitions for a specific consumer instance.
     *
     * @param consumerInstanceId Consumer instance ID
     * @param totalConsumers Total number of consumer instances
     * @param totalPartitions Total number of partitions
     * @return Set of assigned partition IDs (0-indexed)
     */
    Set<Integer> getAssignedPartitions(
            String consumerInstanceId,
            int totalConsumers,
            int totalPartitions
    );

    /**
     * Handles rebalancing when consumer instances join or leave.
     *
     * @param previousAssignment Previous partition assignments
     * @param currentConsumers Current active consumer instances
     * @param totalPartitions Total number of partitions
     * @return New partition assignment for all consumers
     */
    RebalanceResult rebalance(
            PartitionAssignment previousAssignment,
            List<String> currentConsumers,
            int totalPartitions
    );

    /**
     * Gets the strategy name for logging/monitoring.
     *
     * @return Strategy name
     */
    String getName();
}
