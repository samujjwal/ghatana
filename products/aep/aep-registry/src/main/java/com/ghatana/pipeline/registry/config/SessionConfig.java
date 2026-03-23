package com.ghatana.pipeline.registry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsRegistry;
import com.ghatana.platform.security.session.RedisSessionManager;
import com.ghatana.platform.security.session.SessionFilter;
import com.ghatana.platform.security.session.SessionManager;
import io.activej.config.Config;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

import static io.activej.config.converter.ConfigConverters.ofInteger;
import static io.activej.config.converter.ConfigConverters.ofString;

/**
 * ActiveJ module for session management configuration.
 *
 * <p>Purpose: Provides dependency injection bindings for Redis-backed session
 * management including JedisPool configuration, SessionManager, and SessionFilter.
 * Enables stateful user sessions across service requests.</p>
 *
 * @doc.type class
 * @doc.purpose Configures Redis-backed session management infrastructure
 * @doc.layer product
 * @doc.pattern Configuration
 * @since 2.0.0
 */
public class SessionConfig extends AbstractModule {
    
    private static final Logger LOG = LoggerFactory.getLogger(SessionConfig.class);
    
    @Provides
    JedisPool jedisPool(Config config) {
        String host = config.get(ofString(), "redis.host", "localhost");
        int port = config.get(ofInteger(), "redis.port", 6379);
        String password = config.get(ofString(), "redis.password", System.getenv("REDIS_PASSWORD"));
        int maxTotal = config.get(ofInteger(), "redis.pool.max.total", 20);
        int maxIdle = config.get(ofInteger(), "redis.pool.max.idle", 10);
        int minIdle = config.get(ofInteger(), "redis.pool.min.idle", 5);
        long maxWaitMillis = config.get(ofInteger(), "redis.pool.max.wait.millis", 5000);
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWaitMillis);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        
        LOG.info("Configuring Redis connection pool for {}:{}", host, port);
        
        if (password != null && !password.isEmpty()) {
            return new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            return new JedisPool(poolConfig, host, port, 2000);
        }
    }
    
    @Provides
    SessionManager sessionManager(JedisPool jedisPool, ObjectMapper objectMapper, MetricsRegistry metricsRegistry) {
        String keyPrefix = "pipeline-registry:session:";
        Duration defaultTtl = Duration.ofMinutes(30);
        
        LOG.info("Configuring Redis session manager with TTL: {}", defaultTtl);
        
        return new RedisSessionManager(jedisPool, objectMapper, keyPrefix, defaultTtl, metricsRegistry);
    }
    
    @Provides
    SessionFilter sessionFilter(SessionManager sessionManager) {
        return SessionFilter.builder()
            .sessionManager(sessionManager)
            .createIfMissing(true)
            .requireSession(false) // Don't require session for all requests
            .persistSession(true)
            .build();
    }
}
