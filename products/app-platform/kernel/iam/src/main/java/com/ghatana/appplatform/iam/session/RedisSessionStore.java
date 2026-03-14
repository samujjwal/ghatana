package com.ghatana.appplatform.iam.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.Tuple;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Redis-backed session store with concurrent-session enforcement (STORY-K01-006, K01-007).
 *
 * <h3>Session data</h3>
 * <pre>session:{sessionId} → tenantId|principalId|expiresAt(epoch-s)</pre>
 *
 * <h3>Concurrent-session index (K01-007)</h3>
 * <pre>sessions:{principalId} → Sorted Set where score = creation epoch-s</pre>
 * When the number of live sessions for a principal exceeds {@code maxConcurrentSessions},
 * the oldest session(s) are evicted (ZPOPMIN + DEL).
 *
 * @doc.type class
 * @doc.purpose Redis-backed distributed session store adapter (K01-006, K01-007)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    /** Default maximum concurrent sessions per principal (K01-007 policy). */
    public static final int DEFAULT_MAX_CONCURRENT_SESSIONS = 5;

    private static final String SESSION_PREFIX  = "session:";
    private static final String SESSIONS_PREFIX = "sessions:";

    private final JedisPool jedisPool;
    private final int maxConcurrentSessions;

    /** Creates a store with the default concurrent-session limit (5). */
    public RedisSessionStore(JedisPool jedisPool) {
        this(jedisPool, DEFAULT_MAX_CONCURRENT_SESSIONS);
    }

    /**
     * @param jedisPool              Redis connection pool
     * @param maxConcurrentSessions  maximum live sessions per principal; oldest evicted on overflow
     */
    public RedisSessionStore(JedisPool jedisPool, int maxConcurrentSessions) {
        this.jedisPool = jedisPool;
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    // ──────────────────────────────────────────────────────────────────────
    // SessionStore implementation
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void put(String sessionId, String tenantId, String principalId, int ttlSeconds) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        String value = tenantId + "|" + principalId + "|" + expiresAt.getEpochSecond();

        try (Jedis jedis = jedisPool.getResource()) {
            // Store session payload
            jedis.set(key(sessionId), value, new SetParams().ex(ttlSeconds));

            // Track session in the per-principal sorted set (score = creation timestamp)
            String indexKey = sessionsKey(principalId);
            jedis.zadd(indexKey, (double) now.getEpochSecond(), sessionId);

            // Keep TTL on the index: max(session TTL + buffer) — prevents orphan index keys
            jedis.expire(indexKey, ttlSeconds + 60L);

            // Evict oldest sessions when limit is exceeded
            long count = jedis.zcard(indexKey);
            if (count > maxConcurrentSessions) {
                long toEvict = count - maxConcurrentSessions;
                List<Tuple> oldest = jedis.zpopmin(indexKey, toEvict);
                for (Tuple t : oldest) {
                    String evictedId = t.getElement();
                    jedis.del(key(evictedId));
                    log.info("Evicted oldest session {} for principal={} (limit={})",
                             evictedId, principalId, maxConcurrentSessions);
                }
            }
        }
    }

    @Override
    public Optional<SessionEntry> get(String sessionId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(key(sessionId));
            if (value == null) return Optional.empty();
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3) return Optional.empty();
            return Optional.of(new SessionEntry(
                sessionId,
                parts[0],
                parts[1],
                Instant.ofEpochSecond(Long.parseLong(parts[2]))
            ));
        }
    }

    @Override
    public void invalidate(String sessionId) {
        // Look up principalId first so we can clean the sessions index
        Optional<SessionEntry> entry = get(sessionId);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(key(sessionId));
            entry.ifPresent(e -> jedis.zrem(sessionsKey(e.principalId()), sessionId));
        }
    }

    @Override
    public void refresh(String sessionId, int ttlSeconds) {
        Optional<SessionEntry> existing = get(sessionId);
        if (existing.isEmpty()) return;
        SessionEntry entry = existing.get();
        put(entry.sessionId(), entry.tenantId(), entry.principalId(), ttlSeconds);
    }

    @Override
    public void invalidateAllForUser(String principalId) {
        String indexKey = sessionsKey(principalId);
        try (Jedis jedis = jedisPool.getResource()) {
            // Retrieve all session IDs for this principal
            List<String> sessionIds = jedis.zrange(indexKey, 0, -1);
            if (!sessionIds.isEmpty()) {
                // Build keys array: session:{id} for each
                String[] keysToDelete = sessionIds.stream()
                    .map(this::key)
                    .toArray(String[]::new);
                jedis.del(keysToDelete);
            }
            jedis.del(indexKey);
            log.info("Invalidated {} sessions for principal={}", sessionIds.size(), principalId);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Key helpers
    // ──────────────────────────────────────────────────────────────────────

    private String key(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String sessionsKey(String principalId) {
        return SESSIONS_PREFIX + principalId;
    }
}
