package com.ghatana.virtualorg.cache;

import com.ghatana.virtualorg.v1.AgentStateProto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis-backed cache for agent state with TTL-based expiration.
 *
 * <p><b>Purpose</b><br>
 * Adapter implementing distributed agent state storage using Redis for
 * cross-instance state sharing and automatic expiration.
 *
 * <p><b>Architecture Role</b><br>
 * Cache adapter wrapping Jedis (Redis client). Provides:
 * - Distributed agent state across instances
 * - TTL-based automatic expiration (default 1 hour)
 * - Atomic state updates
 * - Metadata storage (role, task, last active timestamp)
 * - Future: Pub/Sub for state change notifications
 *
 * <p><b>Redis Data Structures</b><br>
 * <pre>
 * Key: virtualorg:agent:{agentId}:state
 * Type: String
 * Value: AgentStateProto enum name (IDLE, BUSY, etc.)
 * TTL: 3600 seconds (1 hour)
 *
 * Key: virtualorg:agent:{agentId}:metadata
 * Type: Hash
 * Fields: role, current_task_id, last_active_at, capabilities
 * TTL: 3600 seconds (1 hour)
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RedisAgentStateCache cache = new RedisAgentStateCache(
 *     "localhost",
 *     6379,
 *     password
 * );
 * 
 * // Set agent state
 * cache.setState("agent-123", AgentStateProto.BUSY);
 * 
 * // Get agent state
 * AgentStateProto state = cache.getState("agent-123");
 * 
 * // Set metadata
 * cache.setMetadata("agent-123", Map.of("role", "engineer"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Redis-backed distributed agent state cache adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisAgentStateCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAgentStateCache.class);

    private static final String STATE_KEY_PREFIX = "virtualorg:agent:";
    private static final String STATE_KEY_SUFFIX = ":state";
    private static final String METADATA_KEY_SUFFIX = ":metadata";
    private static final int DEFAULT_TTL_SECONDS = 3600; // 1 hour

    private final JedisPool jedisPool;

    public RedisAgentStateCache(@NotNull String host, int port, @Nullable String password) {
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

        log.info("Initialized RedisAgentStateCache: host={}, port={}", host, port);
    }

    /**
     * Sets the agent state.
     *
     * @param agentId the agent ID
     * @param state   the agent state
     */
    public void setState(@NotNull String agentId, @NotNull AgentStateProto state) {
        String stateKey = getStateKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(stateKey, DEFAULT_TTL_SECONDS, state.name());

            log.debug("Set agent state: agentId={}, state={}", agentId, state);
        }
    }

    /**
     * Gets the agent state.
     *
     * @param agentId the agent ID
     * @return the agent state, or null if not found
     */
    @Nullable
    public AgentStateProto getState(@NotNull String agentId) {
        String stateKey = getStateKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            String stateStr = jedis.get(stateKey);

            if (stateStr == null) {
                return null;
            }

            return AgentStateProto.valueOf(stateStr);

        } catch (IllegalArgumentException e) {
            log.error("Invalid agent state in Redis: agentId={}", agentId, e);
            return null;
        }
    }

    /**
     * Sets agent metadata.
     *
     * @param agentId  the agent ID
     * @param metadata the metadata map
     */
    public void setMetadata(@NotNull String agentId, @NotNull Map<String, String> metadata) {
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            if (!metadata.isEmpty()) {
                jedis.hset(metadataKey, metadata);
                jedis.expire(metadataKey, DEFAULT_TTL_SECONDS);

                log.debug("Set agent metadata: agentId={}, fields={}", agentId, metadata.size());
            }
        }
    }

    /**
     * Gets agent metadata.
     *
     * @param agentId the agent ID
     * @return the metadata map
     */
    @NotNull
    public Map<String, String> getMetadata(@NotNull String agentId) {
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(metadataKey);
        }
    }

    /**
     * Gets a specific metadata field.
     *
     * @param agentId the agent ID
     * @param field   the field name
     * @return the field value, or null if not found
     */
    @Nullable
    public String getMetadataField(@NotNull String agentId, @NotNull String field) {
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(metadataKey, field);
        }
    }

    /**
     * Sets a specific metadata field.
     *
     * @param agentId the agent ID
     * @param field   the field name
     * @param value   the field value
     */
    public void setMetadataField(@NotNull String agentId, @NotNull String field, @NotNull String value) {
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(metadataKey, field, value);
            jedis.expire(metadataKey, DEFAULT_TTL_SECONDS);
        }
    }

    /**
     * Removes agent from cache.
     *
     * @param agentId the agent ID
     */
    public void remove(@NotNull String agentId) {
        String stateKey = getStateKey(agentId);
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(stateKey, metadataKey);

            log.info("Removed agent from cache: agentId={}", agentId);
        }
    }

    /**
     * Gets all active agent IDs.
     *
     * @return set of agent IDs
     */
    @NotNull
    public Set<String> getActiveAgentIds() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pattern = STATE_KEY_PREFIX + "*" + STATE_KEY_SUFFIX;

            return jedis.keys(pattern).stream()
                    .map(key -> extractAgentIdFromKey(key, STATE_KEY_SUFFIX))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Refreshes TTL for an agent (keeps it alive).
     *
     * @param agentId the agent ID
     */
    public void refreshTTL(@NotNull String agentId) {
        String stateKey = getStateKey(agentId);
        String metadataKey = getMetadataKey(agentId);

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.expire(stateKey, DEFAULT_TTL_SECONDS);
            jedis.expire(metadataKey, DEFAULT_TTL_SECONDS);
        }
    }

    /**
     * Closes the Redis connection pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            log.info("RedisAgentStateCache closed");
        }
    }

    // =============================
    // Helper methods
    // =============================

    private String getStateKey(String agentId) {
        return STATE_KEY_PREFIX + agentId + STATE_KEY_SUFFIX;
    }

    private String getMetadataKey(String agentId) {
        return STATE_KEY_PREFIX + agentId + METADATA_KEY_SUFFIX;
    }

    private String extractAgentIdFromKey(String key, String suffix) {
        int start = STATE_KEY_PREFIX.length();
        int end = key.indexOf(suffix);
        return key.substring(start, end);
    }
}
