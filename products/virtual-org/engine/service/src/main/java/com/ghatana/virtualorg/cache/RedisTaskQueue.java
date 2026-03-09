package com.ghatana.virtualorg.cache;

import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.virtualorg.v1.TaskProto;
import com.google.protobuf.InvalidProtocolBufferException;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Optional;

/**
 * Redis-backed distributed task queue.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing distributed task queue using Redis lists for
 * cross-instance task distribution by agent role.
 *
 * <p><b>Architecture Role</b><br>
 * Cache adapter wrapping Jedis (Redis client). Provides:
 * - Distributed task queue across multiple instances
 * - Per-role queues (separate lists per AgentRoleProto)
 * - Persistent storage (Redis durability)
 * - Atomic LPUSH/RPOP operations
 *
 * <p><b>Redis Data Structure</b><br>
 * <pre>
 * Key: virtualorg:queue:{role}
 * Type: List
 * Value: Serialized TaskProto (protobuf bytes)
 * Operations: LPUSH (enqueue), RPOP (dequeue)
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisTaskQueue queue = new RedisTaskQueue(
 *     "localhost",
 *     6379,
 *     password,
 *     1000,  // max queue size
 *     eventloop
 * );
 * 
 * // Enqueue task
 * queue.enqueue(AgentRoleProto.SENIOR_ENGINEER, task).getResult();
 * 
 * // Dequeue task
 * Optional<TaskProto> task = queue.dequeue(AgentRoleProto.SENIOR_ENGINEER).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Redis-backed distributed task queue adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskQueue.class);
    private static final String QUEUE_PREFIX = "virtualorg:queue:";

    private final JedisPool jedisPool;
    private final int maxQueueSize;
    private final Eventloop eventloop;

    public RedisTaskQueue(@NotNull String host, int port, String password, int maxQueueSize, @NotNull Eventloop eventloop) {
        this.maxQueueSize = maxQueueSize;
        this.eventloop = eventloop;

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);

        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }

        log.info("Initialized RedisTaskQueue: host={}, port={}, maxSize={}", host, port, maxQueueSize);
    }

    /**
     * Enqueues a task for a specific role.
     *
     * @param task the task to enqueue
     * @param role the required agent role
     * @return promise that completes when task is enqueued
     */
    @NotNull
    public Promise<Void> enqueue(@NotNull TaskProto task, @NotNull AgentRoleProto role) {
        return Promise.ofBlocking(eventloop, () -> {
            String queueKey = getQueueKey(role);

            try (Jedis jedis = jedisPool.getResource()) {
                // Check queue size
                long queueSize = jedis.llen(queueKey);
                if (queueSize >= maxQueueSize) {
                    throw new IllegalStateException("Queue full for role: " + role);
                }

                // Serialize task to protobuf bytes
                byte[] taskBytes = task.toByteArray();

                // Push to tail of list (FIFO)
                jedis.rpush(queueKey.getBytes(), taskBytes);

                log.info("Enqueued task: taskId={}, role={}, queueSize={}",
                        task.getTaskId(), role, queueSize + 1);
            }

            return null;
        });
    }

    /**
     * Dequeues the next task for a specific role.
     *
     * @param role the agent role
     * @return optional task, or empty if queue is empty
     */
    @NotNull
    public Optional<TaskProto> dequeue(@NotNull AgentRoleProto role) {
        String queueKey = getQueueKey(role);

        try (Jedis jedis = jedisPool.getResource()) {
            // Pop from head of list (FIFO)
            byte[] taskBytes = jedis.lpop(queueKey.getBytes());

            if (taskBytes == null) {
                return Optional.empty();
            }

            // Deserialize protobuf
            TaskProto task = TaskProto.parseFrom(taskBytes);

            long remainingSize = jedis.llen(queueKey);

            log.info("Dequeued task: taskId={}, role={}, remainingInQueue={}",
                    task.getTaskId(), role, remainingSize);

            return Optional.of(task);

        } catch (InvalidProtocolBufferException e) {
            log.error("Failed to deserialize task from Redis", e);
            return Optional.empty();
        }
    }

    /**
     * Gets the current queue size for a role.
     *
     * @param role the agent role
     * @return queue size
     */
    public int getQueueSize(@NotNull AgentRoleProto role) {
        String queueKey = getQueueKey(role);

        try (Jedis jedis = jedisPool.getResource()) {
            return (int) jedis.llen(queueKey);
        }
    }

    /**
     * Clears all queues.
     */
    public void clearAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            // Find all queue keys
            var keys = jedis.keys(QUEUE_PREFIX + "*");

            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
                log.info("Cleared {} Redis queues", keys.size());
            }
        }
    }

    /**
     * Gets total number of queued tasks across all roles.
     *
     * @return total queue size
     */
    public int getTotalQueueSize() {
        try (Jedis jedis = jedisPool.getResource()) {
            var keys = jedis.keys(QUEUE_PREFIX + "*");

            return keys.stream()
                    .mapToInt(key -> (int) jedis.llen(key))
                    .sum();
        }
    }

    /**
     * Closes the Redis connection pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            log.info("RedisTaskQueue closed");
        }
    }

    // =============================
    // Helper methods
    // =============================

    private String getQueueKey(AgentRoleProto role) {
        return QUEUE_PREFIX + role.name().toLowerCase();
    }
}
