package com.ghatana.datacloud.infrastructure.storage;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for KeyValueConnector.
 *
 * <p><b>Purpose</b><br>
 * Immutable configuration value object for Redis-backed KeyValueConnector.
 * Consolidates connection parameters, TTL settings, and namespace configuration.
 * Reuses configuration patterns from libs:redis-cache RedisCacheConfig.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration value object in infrastructure layer. Used by:
 * - {@link KeyValueConnector} for Redis connection setup
 * - Application configuration loading
 * - Environment-specific configuration overrides
 *
 * <p><b>Configuration Components</b><br>
 * <ul>
 *   <li><b>Connection Settings</b>: host, port, password, database, timeout</li>
 *   <li><b>Cache Behavior</b>: ttlSeconds, keyPrefix</li>
 * </ul>
 *
 * <p><b>Environment Variables</b><br>
 * Configuration can be loaded from environment variables:
 * <ul>
 *   <li>{@code REDIS_HOST} - Redis server hostname</li>
 *   <li>{@code REDIS_PORT} - Redis server port</li>
 *   <li>{@code REDIS_PASSWORD} - Redis authentication password</li>
 *   <li>{@code REDIS_DATABASE} - Redis database number (0-15)</li>
 *   <li>{@code REDIS_TTL_SECONDS} - Default TTL for cached entities</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic configuration (localhost, defaults)
 * KeyValueConnectorConfig config = KeyValueConnectorConfig.builder()
 *     .build();
 *
 * // Production configuration with authentication
 * KeyValueConnectorConfig config = KeyValueConnectorConfig.builder()
 *     .host("redis.ghatana.prod")
 *     .port(6380)
 *     .password(System.getenv("REDIS_PASSWORD"))
 *     .database(1)
 *     .timeout(Duration.ofSeconds(10))
 *     .ttlSeconds(3600) // 1 hour TTL
 *     .keyPrefix("prod:entities:")
 *     .build();
 *
 * // From environment variables
 * KeyValueConnectorConfig config = KeyValueConnectorConfig.fromEnvironment();
 *
 * // Modify existing configuration
 * KeyValueConnectorConfig newConfig = config.toBuilder()
 *     .database(3)
 *     .keyPrefix("staging:")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - all fields final. Safe to share across threads.
 *
 * @see KeyValueConnector
 * @see com.ghatana.core.cache.redis.RedisCacheConfig
 * @doc.type class
 * @doc.purpose Configuration for KeyValueConnector with Redis connection settings
 * @doc.layer product
 * @doc.pattern Value Object, Configuration
 */
public final class KeyValueConnectorConfig {

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final Duration timeout;
    private final int ttlSeconds;
    private final String keyPrefix;

    private KeyValueConnectorConfig(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.database = builder.database;
        this.timeout = builder.timeout;
        this.ttlSeconds = builder.ttlSeconds;
        this.keyPrefix = builder.keyPrefix;
    }

    /**
     * Redis server hostname.
     *
     * @return hostname (default: "localhost")
     */
    public String getHost() {
        return host;
    }

    /**
     * Redis server port.
     *
     * @return port number (default: 6379)
     */
    public int getPort() {
        return port;
    }

    /**
     * Redis authentication password.
     *
     * @return password or null if not set
     */
    public String getPassword() {
        return password;
    }

    /**
     * Redis database number (0-15).
     *
     * @return database number (default: 0)
     */
    public int getDatabase() {
        return database;
    }

    /**
     * Connection and operation timeout.
     *
     * @return timeout duration (default: 5 seconds)
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Default TTL for cached entities in seconds.
     *
     * @return TTL in seconds (default: 3600 = 1 hour)
     */
    public int getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Key prefix for namespace isolation.
     *
     * @return key prefix (default: "dc:kv:")
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * Creates a new builder with default values.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with this config's values.
     *
     * @return builder with current values
     */
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

    /**
     * Creates configuration from environment variables.
     * Falls back to defaults if environment variables are not set.
     *
     * @return configuration from environment
     */
    public static KeyValueConnectorConfig fromEnvironment() {
        Builder builder = builder();

        String host = System.getenv("REDIS_HOST");
        if (host != null && !host.isEmpty()) {
            builder.host(host);
        }

        String port = System.getenv("REDIS_PORT");
        if (port != null && !port.isEmpty()) {
            try {
                builder.port(Integer.parseInt(port));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isEmpty()) {
            builder.password(password);
        }

        String database = System.getenv("REDIS_DATABASE");
        if (database != null && !database.isEmpty()) {
            try {
                builder.database(Integer.parseInt(database));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String ttl = System.getenv("REDIS_TTL_SECONDS");
        if (ttl != null && !ttl.isEmpty()) {
            try {
                builder.ttlSeconds(Integer.parseInt(ttl));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String prefix = System.getenv("REDIS_KEY_PREFIX");
        if (prefix != null && !prefix.isEmpty()) {
            builder.keyPrefix(prefix);
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return "KeyValueConnectorConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database=" + database +
                ", timeout=" + timeout +
                ", ttlSeconds=" + ttlSeconds +
                ", keyPrefix='" + keyPrefix + '\'' +
                '}';
    }

    /**
     * Builder for KeyValueConnectorConfig.
     */
    public static final class Builder {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
        private Duration timeout = Duration.ofSeconds(5);
        private int ttlSeconds = 3600; // 1 hour default
        private String keyPrefix = "dc:kv:";

        private Builder() {
        }

        /**
         * Sets the Redis server hostname.
         *
         * @param host hostname (required, non-null)
         * @return this builder
         */
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        /**
         * Sets the Redis server port.
         *
         * @param port port number (1-65535)
         * @return this builder
         */
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }

        /**
         * Sets the Redis authentication password.
         *
         * @param password password or null for no authentication
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the Redis database number.
         *
         * @param database database number (0-15)
         * @return this builder
         */
        public Builder database(int database) {
            if (database < 0 || database > 15) {
                throw new IllegalArgumentException("database must be between 0 and 15");
            }
            this.database = database;
            return this;
        }

        /**
         * Sets the connection and operation timeout.
         *
         * @param timeout timeout duration (null uses default of 5 seconds)
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout != null ? timeout : Duration.ofSeconds(5);
            return this;
        }

        /**
         * Sets the default TTL for cached entities.
         *
         * @param ttlSeconds TTL in seconds (0 = no expiration, not recommended)
         * @return this builder
         */
        public Builder ttlSeconds(int ttlSeconds) {
            if (ttlSeconds < 0) {
                throw new IllegalArgumentException("ttlSeconds cannot be negative");
            }
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        /**
         * Sets the key prefix for namespace isolation.
         *
         * @param keyPrefix prefix string (null uses default)
         * @return this builder
         */
        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix != null ? keyPrefix : "dc:kv:";
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return immutable configuration
         */
        public KeyValueConnectorConfig build() {
            return new KeyValueConnectorConfig(this);
        }
    }
}
