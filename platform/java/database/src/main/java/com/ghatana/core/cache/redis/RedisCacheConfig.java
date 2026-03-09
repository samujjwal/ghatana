package com.ghatana.core.cache.redis;

import java.time.Duration;
import java.util.Objects;

/**
 * Redis cache configuration value object.
 *
 * <p><b>Purpose</b><br>
 * Immutable configuration for Redis-backed caches consolidating connection
 * parameters, TTL settings, and namespace configuration.
 *
 * <p><b>Architecture Role</b><br>
 * Value object in redis-cache layer for cache initialization. Used by:
 * - {@link AsyncRedisCache} for connection setup
 * - State management for hybrid store configuration
 * - Session storage for distributed cache setup
 * - Pattern catalog for shared cache initialization
 *
 * <p><b>Configuration Components</b><br>
 * @doc.type class
 * @doc.purpose Immutable Redis cache configuration with connection and TTL settings
 * @doc.layer core
 * @doc.pattern Value Object, Configuration
 *
 * 
 * <p><b>Connection Settings</b>
 * - Host: Redis server hostname (default: "localhost")
 * - Port: Redis server port (default: 6379)
 * - Password: Optional authentication password
 * - Database: Redis database number (default: 0, range: 0-15)
 * - Timeout: Connection/operation timeout (default: 5 seconds)
 * 
 * <p><b>Cache Behavior</b>
 * - TTL Seconds: Default time-to-live for entries (default: 86400 = 24 hours)
 * - Key Prefix: Namespace prefix for multi-tenancy (default: empty)
 * 
 * <p><b>TTL Strategy</b>
 * - Default TTL: Applied to all put() operations without explicit TTL
 * - Per-Entry Override: AsyncRedisCache.put(key, value, customTtl)
 * - Infinite TTL: Set ttlSeconds = 0 (not recommended)
 * - Short-Lived: ttlSeconds = 300 (5 minutes) for volatile data
 * - Long-Lived: ttlSeconds = 604800 (7 days) for stable data
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic configuration (localhost, defaults)
 * RedisCacheConfig config = RedisCacheConfig.builder()
 *     .build();
 *
 * // Production configuration with authentication
 * RedisCacheConfig config = RedisCacheConfig.builder()
 *     .host("redis.ghatana.prod")
 *     .port(6380)
 *     .password(System.getenv("REDIS_PASSWORD"))
 *     .database(1)
 *     .timeout(Duration.ofSeconds(10))
 *     .ttlSeconds(3600) // 1 hour TTL
 *     .keyPrefix("prod:events:")
 *     .build();
 *
 * // Multi-tenant configuration with namespace
 * RedisCacheConfig tenantConfig = RedisCacheConfig.builder()
 *     .host("redis.ghatana.local")
 *     .keyPrefix("tenant:" + tenantId + ":")
 *     .ttlSeconds(1800) // 30 minutes
 *     .build();
 *
 * // Short-lived session cache
 * RedisCacheConfig sessionConfig = RedisCacheConfig.builder()
 *     .database(2)
 *     .ttlSeconds(900) // 15 minutes
 *     .keyPrefix("session:")
 *     .build();
 *
 * // Modify existing configuration
 * RedisCacheConfig newConfig = config.toBuilder()
 *     .database(3)
 *     .keyPrefix("staging:")
 *     .build();
 * }</pre>
 *
 * <p><b>Key Prefix Patterns</b><br>
 * - Tenant Isolation: {@code "tenant:{tenantId}:"}
 * - Environment: {@code "prod:"}, {@code "staging:"}, {@code "dev:"}
 * - Feature Namespace: {@code "events:"}, {@code "patterns:"}, {@code "agents:"}
 * - Hierarchical: {@code "prod:tenant:acme:events:"}
 *
 * <p><b>Security Considerations</b><br>
 * - Never hardcode passwords in configuration
 * - Use environment variables or secrets management
 * - Enable Redis AUTH for production
 * - Consider TLS for Redis connections (see {@link com.ghatana.cache.security.RedisTlsConfig})
 * - Use separate databases for different environments
 *
 * <p><b>Builder Pattern</b><br>
 * - Fluent API with sensible defaults
 * - {@code toBuilder()} for configuration modification
 * - Immutable result - thread-safe sharing
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see AsyncRedisCache
 * @see com.ghatana.cache.security.RedisTlsConfig
 * @doc.type class
 * @doc.purpose Redis cache configuration value object
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class RedisCacheConfig {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final Duration timeout;
    private final int ttlSeconds;
    private final String keyPrefix;

    private RedisCacheConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.timeout = builder.timeout;
        this.ttlSeconds = builder.ttlSeconds;
        this.keyPrefix = builder.keyPrefix;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPassword() {
        return password;
    }

    public int getDatabase() {
        return database;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .host(host)
                .port(port)
                .password(password)
                .database(database)
                .timeout(timeout)
                .ttlSeconds(ttlSeconds)
                .keyPrefix(keyPrefix);
    }

    public static final class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private Duration timeout = Duration.ofSeconds(5);
        private int ttlSeconds = 86_400;
        private String keyPrefix = "";

        private Builder() {}

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(5);
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix != null ? keyPrefix : "";
            return this;
        }

        public RedisCacheConfig build() {
            return new RedisCacheConfig(this);
        }
    }
}
