package com.ghatana.platform.security.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsRegistry;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis-based session manager for storing and retrieving HTTP session state.
 * <p>
 * RedisSessionManager provides distributed session storage using Redis as the backing
 * store. This enables session sharing across multiple application instances, automatic
 * expiration, and high availability.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Distributed Storage</b>: Redis for cross-instance session sharing</li>
 *   <li><b>Auto-Expiration</b>: TTL-based session expiration (configurable)</li>
 *   <li><b>Metrics Integration</b>: Tracks session operations (create, load, save, errors)</li>
 *   <li><b>JSON Serialization</b>: Jackson ObjectMapper for session state</li>
 *   <li><b>Promise-Based</b>: ActiveJ Promise for async operations</li>
 *   <li><b>Query Support</b>: Find sessions by userId or tenantId</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Initialize
 * JedisPool jedisPool = new JedisPool("localhost", 6379);
 * ObjectMapper objectMapper = new ObjectMapper();
 * MetricsRegistry metrics = new MetricsRegistry(meterRegistry);
 * 
 * RedisSessionManager sessionManager = new RedisSessionManager(
 *     jedisPool,
 *     objectMapper,
 *     "session:",  // Key prefix
 *     Duration.ofMinutes(30),  // Default TTL
 *     metrics
 * );
 *
 * // Create session
 * sessionManager.createSession()
 *     .whenResult(session -> {
 *         session.setUserId("user-123");
 *         session.setTenantId("tenant-456");
 *         session.setAttribute("cart", cartItems);
 *         sessionManager.saveSession(session);
 *     });
 *
 * // Load session
 * sessionManager.getSession("session-abc123")
 *     .whenResult(optionalSession -> {
 *         if (optionalSession.isPresent()) {
 *             SessionState session = optionalSession.get();
 *             Object cart = session.getAttribute("cart");
 *         } else {
 *             System.out.println("Session expired or not found");
 *         }
 *     });
 *
 * // Find user sessions
 * sessionManager.findSessionsByUserId("user-123")
 *     .whenResult(sessionIds -> {
 *         System.out.println("User has " + sessionIds.size() + " sessions");
 *     });
 *
 * // Cleanup expired sessions
 * sessionManager.deleteExpiredSessions()
 *     .whenResult(count -> {
 *         System.out.println("Deleted " + count + " expired sessions");
 *     });
 * }</pre>
 * 
 * <h2>Redis Key Structure</h2>
 * Sessions stored as: <code>{keyPrefix}{sessionId}</code>
 * <ul>
 *   <li>Example: <code>session:abc123-def456-ghi789</code></li>
 *   <li>TTL set to maxInactiveInterval seconds</li>
 *   <li>Automatically extended on access</li>
 * </ul>
 * 
 * <h2>Metrics Tracked</h2>
 * <ul>
 *   <li><b>session.created.total</b>: Counter for sessions created</li>
 *   <li><b>session.loaded.total</b>: Counter for successful session loads</li>
 *   <li><b>session.expired.total</b>: Counter for expired sessions detected</li>
 *   <li><b>session.errors.total</b>: Counter for session operation errors</li>
 *   <li><b>session.load.duration</b>: Timer for session load latency</li>
 *   <li><b>session.save.duration</b>: Timer for session save latency</li>
 * </ul>
 * 
 * <h2>Expiration Handling</h2>
 * <ul>
 *   <li>Sessions expire after maxInactiveInterval seconds of inactivity</li>
 *   <li>TTL automatically extended on getSession() (access() called)</li>
 *   <li>Redis automatically deletes expired keys (passive + active eviction)</li>
 *   <li>Application-level check: isExpired() in getSession()</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * Thread-safe via Jedis connection pooling. Each operation gets a connection from
 * pool and returns it after use.
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 90
 * @testing Integration
 * @thread_safety Thread-safe (Jedis pool)
 * @performance O(1) get/set, O(n) for findSessions
 * @see SessionManager
 * @see SessionState
 
 *
 * @doc.type class
 * @doc.purpose Redis session manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public class RedisSessionManager implements SessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisSessionManager.class);
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration defaultTtl;
    
    // Metrics
    private final Counter sessionCreatedCounter;
    private final Counter sessionLoadedCounter;
    private final Counter sessionExpiredCounter;
    private final Counter sessionErrorCounter;
    private final Timer sessionLoadTimer;
    private final Timer sessionSaveTimer;
    
    /**
     * Create a new RedisSessionManager.
     */
    public RedisSessionManager(JedisPool jedisPool, ObjectMapper objectMapper, 
                             String keyPrefix, Duration defaultTtl, MetricsRegistry metricsRegistry) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.defaultTtl = defaultTtl;
        
        // Initialize metrics
        if (metricsRegistry != null) {
            this.sessionCreatedCounter = metricsRegistry.customCounter(
                "session.created.total", "Sessions created");
            this.sessionLoadedCounter = metricsRegistry.customCounter(
                "session.loaded.total", "Sessions loaded");
            this.sessionExpiredCounter = metricsRegistry.customCounter(
                "session.expired.total", "Sessions expired");
            this.sessionErrorCounter = metricsRegistry.customCounter(
                "session.errors.total", "Session errors");
            this.sessionLoadTimer = metricsRegistry.customTimer(
                "session.load.duration", "Session load duration");
            this.sessionSaveTimer = metricsRegistry.customTimer(
                "session.save.duration", "Session save duration");
        } else {
            this.sessionCreatedCounter = null;
            this.sessionLoadedCounter = null;
            this.sessionExpiredCounter = null;
            this.sessionErrorCounter = null;
            this.sessionLoadTimer = null;
            this.sessionSaveTimer = null;
        }
        
        logger.info("RedisSessionManager initialized with key prefix: {}", keyPrefix);
    }
    
    @Override
    public Promise<SessionState> createSession() {
        return runBlocking(() -> {
            SessionState session = new SessionState();
            session.setMaxInactiveInterval(defaultTtl.getSeconds());
            
            try {
                saveSession(session);
                
                if (sessionCreatedCounter != null) {
                    sessionCreatedCounter.increment();
                }
                
                return session;
            } catch (Exception e) {
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Optional<SessionState>> getSession(String sessionId) {
        return runBlocking(() -> {
            Timer.Sample sample = null;
            if (sessionLoadTimer != null) {
                sample = Timer.start();
            }
            
            try (Jedis jedis = jedisPool.getResource()) {
                String key = getKey(sessionId);
                String json = jedis.get(key);
                
                if (json == null) {
                    return Optional.empty();
                }
                
                SessionState session = deserializeSession(json);
                
                // Check if session has expired
                if (session.isExpired()) {
                    jedis.del(key);
                    
                    if (sessionExpiredCounter != null) {
                        sessionExpiredCounter.increment();
                    }
                    
                    return Optional.empty();
                }
                
                // Update last accessed time and extend TTL
                session.access();
                jedis.expire(key, (int) session.getMaxInactiveInterval());
                
                if (sessionLoadedCounter != null) {
                    sessionLoadedCounter.increment();
                }
                
                return Optional.of(session);
                
            } catch (Exception e) {
                logger.error("Error loading session: {}", sessionId, e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                return Optional.empty();
            } finally {
                if (sample != null && sessionLoadTimer != null) {
                    sample.stop(sessionLoadTimer);
                }
            }
        });
    }
    
    @Override
    public Promise<Void> saveSession(SessionState session) {
        return runBlocking(() -> {
            Timer.Sample sample = null;
            if (sessionSaveTimer != null) {
                sample = Timer.start();
            }
            
            try (Jedis jedis = jedisPool.getResource()) {
                String key = getKey(session.getId());
                String json = serializeSession(session);
                
                // Update last accessed time
                session.access();
                
                // Save session with TTL
                SetParams params = new SetParams().ex((int) session.getMaxInactiveInterval());
                jedis.set(key, json, params);
                
                return null;
            } catch (Exception e) {
                logger.error("Error saving session: {}", session.getId(), e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                throw e;
            } finally {
                if (sample != null && sessionSaveTimer != null) {
                    sample.stop(sessionSaveTimer);
                }
            }
        });
    }
    
    @Override
    public Promise<Boolean> deleteSession(String sessionId) {
        return runBlocking(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String key = getKey(sessionId);
                Long result = jedis.del(key);
                return result > 0;
            } catch (Exception e) {
                logger.error("Error deleting session: {}", sessionId, e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                return false;
            }
        });
    }
    
    @Override
    public Promise<Set<String>> findSessionsByUserId(String userId) {
        return runBlocking(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // Get all session keys
                Set<String> keys = jedis.keys(keyPrefix + "*");
                
                // Filter sessions by user ID
                return keys.stream()
                    .map(key -> {
                        try {
                            String json = jedis.get(key);
                            if (json == null) {
                                return null;
                            }
                            
                            SessionState session = deserializeSession(json);
                            if (userId.equals(session.getUserId())) {
                                return session.getId();
                            }
                            
                            return null;
                        } catch (Exception e) {
                            logger.error("Error deserializing session: {}", key, e);
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            } catch (Exception e) {
                logger.error("Error finding sessions by user ID: {}", userId, e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Set<String>> findSessionsByTenantId(String tenantId) {
        return runBlocking(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // Get all session keys
                Set<String> keys = jedis.keys(keyPrefix + "*");
                
                // Filter sessions by tenant ID
                return keys.stream()
                    .map(key -> {
                        try {
                            String json = jedis.get(key);
                            if (json == null) {
                                return null;
                            }
                            
                            SessionState session = deserializeSession(json);
                            if (tenantId.equals(session.getTenantId())) {
                                return session.getId();
                            }
                            
                            return null;
                        } catch (Exception e) {
                            logger.error("Error deserializing session: {}", key, e);
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toSet());
            } catch (Exception e) {
                logger.error("Error finding sessions by tenant ID: {}", tenantId, e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                throw e;
            }
        });
    }
    
    @Override
    public Promise<Long> deleteExpiredSessions() {
        return runBlocking(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // Get all session keys
                Set<String> keys = jedis.keys(keyPrefix + "*");
                long deletedCount = 0;
                
                // Check each session for expiration
                for (String key : keys) {
                    String json = jedis.get(key);
                    if (json == null) {
                        continue;
                    }
                    
                    try {
                        SessionState session = deserializeSession(json);
                        if (session.isExpired()) {
                            jedis.del(key);
                            deletedCount++;
                            
                            if (sessionExpiredCounter != null) {
                                sessionExpiredCounter.increment();
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error checking session expiration: {}", key, e);
                    }
                }
                
                return deletedCount;
            } catch (Exception e) {
                logger.error("Error deleting expired sessions", e);
                
                if (sessionErrorCounter != null) {
                    sessionErrorCounter.increment();
                }
                
                throw e;
            }
        });
    }
    
    /**
     * Get the Redis key for a session ID.
     */
    private String getKey(String sessionId) {
        return keyPrefix + sessionId;
    }
    
    /**
     * Serialize a session to JSON.
     */
    private String serializeSession(SessionState session) throws JsonProcessingException {
        return objectMapper.writeValueAsString(session);
    }
    
    /**
     * Deserialize a session from JSON.
     */
    private SessionState deserializeSession(String json) throws IOException {
        return objectMapper.readValue(json, SessionState.class);
    }

    private static <T> Promise<T> runBlocking(java.util.concurrent.Callable<T> supplier) {
        try {
            return Promise.of(supplier.call());
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
