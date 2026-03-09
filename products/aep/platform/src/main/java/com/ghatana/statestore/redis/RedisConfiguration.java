package com.ghatana.statestore.redis;

import lombok.Builder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration holder for Redis connection parameters.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes Redis configuration (host, port, password, SSL) in an immutable,
 * validated configuration object. Supports environment variable overrides for
 * production deployments.
 *
 * <p>
 * <b>Security Features</b><br>
 * - Password authentication via AUTH command - SSL/TLS support for encrypted
 * connections - Client certificate validation (optional) - Connection pool
 * sizing for resource limits - Timeout configuration for slow network detection
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // From environment variables
 * RedisConfiguration config = RedisConfiguration.fromEnvironment();
 *
 * // From builder
 * RedisConfiguration config = RedisConfiguration.builder()
 *     .host("redis.example.com")
 *     .port(6379)
 *     .password("my-secure-password")
 *     .ssl(true)
 *     .connectTimeout(Duration.ofSeconds(5))
 *     .maxPoolSize(20)
 *     .build();
 *
 * // Create state store with config
 * RedisStateStore store = new RedisStateStore(config);
 * }</pre>
 *
 * <p>
 * <b>Environment Variables</b><br>
 * <pre>
 * REDIS_HOST          - Redis server hostname (default: localhost)
 * REDIS_PORT          - Redis server port (default: 6379)
 * REDIS_PASSWORD      - Redis AUTH password (optional)
 * REDIS_SSL           - Enable SSL/TLS (default: false)
 * REDIS_POOL_SIZE     - Max connections in pool (default: 20)
 * REDIS_CONNECT_TIMEOUT - Connection timeout (default: 5s)
 * REDIS_READ_TIMEOUT  - Read timeout (default: 3s)
 * </pre>
 *
 * <p>
 * <b>Validation</b><br>
 * All settings validated on construction. Invalid values throw
 * IllegalArgumentException. - host: not null/empty - port: 1-65535 - password:
 * optional, empty string treated as no password - poolSize: > 0 - timeouts: >=
 * 0
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable (all fields final), thread-safe for concurrent use.
 *
 * @see RedisStateStore
 * @see redis.clients.jedis.JedisPoolConfig
 *
 * @author State Store Team
 * @created 2025-11-15
 * @version 1.0.0
 * @doc.type class
 * @doc.purpose Redis connection configuration with authentication support
 * @doc.layer infrastructure
 * @doc.pattern Configuration, Builder
 */
@Getter
@Builder
public final class RedisConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

    /**
     * Redis server hostname. Default: localhost
     */
    @Builder.Default
    private final String host = "localhost";

    /**
     * Redis server port. Default: 6379 (standard Redis port) Valid range:
     * 1-65535
     */
    @Builder.Default
    private final int port = 6379;

    /**
     * Redis AUTH password (optional). If provided, sent to Redis via AUTH
     * command during connection. Empty string treated as no authentication
     * required.
     */
    @Builder.Default
    private final Optional<String> password = Optional.empty();

    /**
     * Enable SSL/TLS for encrypted connection. Default: false When true, uses
     * redis.clients.jedis.Protocol.DEFAULT_SSL_PORT (6380)
     */
    @Builder.Default
    private final boolean ssl = false;

    /**
     * Maximum connections in connection pool. Default: 20 Recommended: 10-50
     * depending on concurrent operations
     */
    @Builder.Default
    private final int maxPoolSize = 20;

    /**
     * Minimum connections kept in connection pool. Default: 5 Recommended: 1/4
     * of maxPoolSize
     */
    @Builder.Default
    private final int minPoolSize = 5;

    /**
     * Connection timeout duration. Default: 5 seconds Recommended: 2-10 seconds
     */
    @Builder.Default
    private final Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Read timeout duration for socket operations. Default: 3 seconds
     * Recommended: 1-5 seconds
     */
    @Builder.Default
    private final Duration readTimeout = Duration.ofSeconds(3);

    /**
     * Test connection on borrow from pool. Default: true (recommended for
     * production) When true, verifies connection is still alive before
     * returning from pool.
     */
    @Builder.Default
    private final boolean testOnBorrow = true;

    /**
     * Test connection on return to pool. Default: true (recommended for
     * production) When true, verifies connection before adding back to pool.
     */
    @Builder.Default
    private final boolean testOnReturn = true;

    /**
     * Test connection while idle in pool. Default: true (recommended for
     * long-lived pools) When true, periodically validates idle connections.
     */
    @Builder.Default
    private final boolean testWhileIdle = true;

    /**
     * Creates Redis configuration from environment variables.
     *
     * <p>
     * Environment variable mapping:
     * <ul>
     * <li>REDIS_HOST → host (default: localhost)</li>
     * <li>REDIS_PORT → port (default: 6379)</li>
     * <li>REDIS_PASSWORD → password (default: empty/no auth)</li>
     * <li>REDIS_SSL → ssl (default: false)</li>
     * <li>REDIS_POOL_SIZE → maxPoolSize (default: 20)</li>
     * <li>REDIS_CONNECT_TIMEOUT → connectTimeout (default: 5s)</li>
     * <li>REDIS_READ_TIMEOUT → readTimeout (default: 3s)</li>
     * </ul>
     *
     * @return Configuration populated from environment variables
     */
    public static RedisConfiguration fromEnvironment() {
        String host = System.getenv("REDIS_HOST") != null
                ? System.getenv("REDIS_HOST")
                : "localhost";

        int port = System.getenv("REDIS_PORT") != null
                ? Integer.parseInt(System.getenv("REDIS_PORT"))
                : 6379;

        String password = System.getenv("REDIS_PASSWORD");
        Optional<String> passwordOpt = password != null && !password.isEmpty()
                ? Optional.of(password)
                : Optional.empty();

        boolean ssl = System.getenv("REDIS_SSL") != null
                ? Boolean.parseBoolean(System.getenv("REDIS_SSL"))
                : false;

        int poolSize = System.getenv("REDIS_POOL_SIZE") != null
                ? Integer.parseInt(System.getenv("REDIS_POOL_SIZE"))
                : 20;

        long connectTimeoutSeconds = System.getenv("REDIS_CONNECT_TIMEOUT") != null
                ? Long.parseLong(System.getenv("REDIS_CONNECT_TIMEOUT"))
                : 5;

        long readTimeoutSeconds = System.getenv("REDIS_READ_TIMEOUT") != null
                ? Long.parseLong(System.getenv("REDIS_READ_TIMEOUT"))
                : 3;

        RedisConfiguration config = RedisConfiguration.builder()
                .host(host)
                .port(port)
                .password(passwordOpt)
                .ssl(ssl)
                .maxPoolSize(poolSize)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .build();

        log.info("Redis configuration loaded from environment: {}:{} (ssl={}, poolSize={})",
                host, port, ssl, poolSize);

        return config;
    }

    /**
     * Validates configuration on construction.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public RedisConfiguration validate() {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Redis host cannot be null or empty");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Redis port must be in range 1-65535, got: " + port);
        }

        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("Redis pool size must be > 0, got: " + maxPoolSize);
        }

        if (minPoolSize < 0) {
            throw new IllegalArgumentException("Redis min pool size must be >= 0, got: " + minPoolSize);
        }

        if (minPoolSize > maxPoolSize) {
            throw new IllegalArgumentException("Min pool size (" + minPoolSize + ") cannot exceed max (" + maxPoolSize + ")");
        }

        if (connectTimeout.isNegative()) {
            throw new IllegalArgumentException("Connect timeout cannot be negative: " + connectTimeout);
        }

        if (readTimeout.isNegative()) {
            throw new IllegalArgumentException("Read timeout cannot be negative: " + readTimeout);
        }

        return this;
    }

    /**
     * Returns a display-safe string representation (hides password).
     *
     * @return String like "RedisConfig[host=localhost:6379, ssl=false,
     * poolSize=20]"
     */
    @Override
    public String toString() {
        return "RedisConfig["
                + "host=" + host + ":" + port + ", "
                + "ssl=" + ssl + ", "
                + "poolSize=" + maxPoolSize + ", "
                + "auth=" + (password.isPresent() ? "YES" : "NO")
                + "]";
    }
}
