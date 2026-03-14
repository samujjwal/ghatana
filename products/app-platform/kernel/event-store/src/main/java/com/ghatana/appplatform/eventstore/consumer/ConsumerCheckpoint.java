package com.ghatana.appplatform.eventstore.consumer;

/**
 * Port for persisting consumer offset checkpoints for crash recovery.
 *
 * <p>Implementations must be idempotent: saving the same offset twice is safe.
 *
 * @doc.type interface
 * @doc.purpose Consumer offset checkpoint store port (STORY-K05-011)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ConsumerCheckpoint {

    /**
     * Save the last committed offset for a consumer group on a specific partition.
     *
     * @param groupId   Kafka consumer group identifier
     * @param topic     Kafka topic name
     * @param partition Kafka partition number
     * @param offset    Last successfully committed offset
     */
    void save(String groupId, String topic, int partition, long offset);

    /**
     * Load the last committed offset. Returns -1 if no checkpoint exists.
     *
     * @param groupId   Kafka consumer group identifier
     * @param topic     Kafka topic name
     * @param partition Kafka partition number
     * @return last saved offset or -1 if absent
     */
    long load(String groupId, String topic, int partition);
}
