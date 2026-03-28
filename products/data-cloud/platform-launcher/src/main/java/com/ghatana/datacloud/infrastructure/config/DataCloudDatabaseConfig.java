package com.ghatana.datacloud.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Explicit HikariCP connection pool configuration for Data-Cloud.
 *
 * <p>Addresses FINDING-DC-M6: the HikariCP library was on the classpath but the
 * connection pool was being bootstrapped via implicit JPA defaults with no
 * connection-validation, leak-detection, or lifecycle tuning.
 *
 * <p>All constants below are the explicit production defaults. Every value can be
 * overridden through the matched environment variable, allowing per-environment
 * tuning without rebuilding.
 *
 * <p><b>Environment Variables</b></p>
 * <ul>
 *   <li>{@code DC_DB_URL}           — JDBC URL (required)</li>
 *   <li>{@code DC_DB_USER}          — database username (required)</li>
 *   <li>{@code DC_DB_PASSWORD}      — database password (required)</li>
 *   <li>{@code DC_DB_POOL_MIN_IDLE} — minimum idle connections (default: {@value #DEFAULT_MIN_IDLE})</li>
 *   <li>{@code DC_DB_POOL_MAX_SIZE} — maximum pool size (default: {@value #DEFAULT_MAX_POOL_SIZE})</li>
 *   <li>{@code DC_DB_CONN_TIMEOUT_MS}       — connection-acquisition timeout ms (default: {@value #DEFAULT_CONNECTION_TIMEOUT_MS})</li>
 *   <li>{@code DC_DB_IDLE_TIMEOUT_MS}       — idle connection eviction timeout ms (default: {@value #DEFAULT_IDLE_TIMEOUT_MS})</li>
 *   <li>{@code DC_DB_MAX_LIFETIME_MS}       — maximum connection lifetime ms (default: {@value #DEFAULT_MAX_LIFETIME_MS})</li>
 *   <li>{@code DC_DB_KEEPALIVE_INTERVAL_MS} — keep-alive probe interval ms (default: {@value #DEFAULT_KEEPALIVE_INTERVAL_MS})</li>
 *   <li>{@code DC_DB_LEAK_DETECTION_MS}     — connection-leak detection threshold ms; 0 = disabled (default: {@value #DEFAULT_LEAK_DETECTION_THRESHOLD_MS})</li>
 *   <li>{@code DC_DB_VALIDATION_TIMEOUT_MS} — connection-test timeout ms (default: {@value #DEFAULT_VALIDATION_TIMEOUT_MS})</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Explicit HikariCP connection pool configuration for Data-Cloud database access
 * @doc.layer product
 * @doc.pattern Configuration, Value Object
 */
public final class DataCloudDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DataCloudDatabaseConfig.class);

    // ── Explicit defaults (FINDING-DC-L7: no implicit defaults) ─────────────────

    /** Minimum idle connections maintained by the pool. */
    public static final int DEFAULT_MIN_IDLE = 2;

    /** Maximum total connections in the pool. */
    public static final int DEFAULT_MAX_POOL_SIZE = 20;

    /** Maximum ms to wait when acquiring a new connection from the pool. */
    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L;

    /** Maximum ms a connection can remain idle before being evicted. */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;

    /** Maximum ms a connection is allowed to live; forces refresh to pick up DB-side changes. */
    public static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;

    /** Interval ms between keep-alive probes on idle connections. Prevents silent staleness. */
    public static final long DEFAULT_KEEPALIVE_INTERVAL_MS = 60_000L;

    /**
     * Threshold ms after which an unreturned connection is logged as a potential leak.
     * Set to {@code 0} to disable.
     */
    public static final long DEFAULT_LEAK_DETECTION_THRESHOLD_MS = 60_000L;

    /** Maximum ms allowed for the pool to validate a connection via test query. */
    public static final long DEFAULT_VALIDATION_TIMEOUT_MS = 5_000L;

    /** JDBC validation query used to confirm the connection is alive. */
    static final String CONNECTION_TEST_QUERY = "SELECT 1";

    /** Pool name used in metrics and thread naming. */
    static final String POOL_NAME = "data-cloud-db-pool";

    // ── Configuration fields ──────────────────────────────────────────────────

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int minIdle;
    private final int maxPoolSize;
    private final long connectionTimeoutMs;
    private final long idleTimeoutMs;
    private final long maxLifetimeMs;
    private final long keepAliveIntervalMs;
    private final long leakDetectionThresholdMs;
    private final long validationTimeoutMs;

    private DataCloudDatabaseConfig(Builder builder) {
        this.jdbcUrl                   = Objects.requireNonNull(builder.jdbcUrl,  "DC_DB_URL must be set");
        this.username                  = Objects.requireNonNull(builder.username, "DC_DB_USER must be set");
        this.password                  = Objects.requireNonNull(builder.password, "DC_DB_PASSWORD must be set");
        this.minIdle                   = builder.minIdle;
        this.maxPoolSize               = builder.maxPoolSize;
        this.connectionTimeoutMs       = builder.connectionTimeoutMs;
        this.idleTimeoutMs             = builder.idleTimeoutMs;
        this.maxLifetimeMs             = builder.maxLifetimeMs;
        this.keepAliveIntervalMs       = builder.keepAliveIntervalMs;
        this.leakDetectionThresholdMs  = builder.leakDetectionThresholdMs;
        this.validationTimeoutMs       = builder.validationTimeoutMs;
    }

    /**
     * Builds a {@code DataCloudDatabaseConfig} from environment variables,
     * falling back to the constants defined above.
     *
     * @return configured instance
     * @throws NullPointerException if required environment variables are absent
     */
    public static DataCloudDatabaseConfig fromEnvironment() {
        return builder()
                .jdbcUrl(System.getenv("DC_DB_URL"))
                .username(System.getenv("DC_DB_USER"))
                .password(System.getenv("DC_DB_PASSWORD"))
                .minIdle(envInt("DC_DB_POOL_MIN_IDLE", DEFAULT_MIN_IDLE))
                .maxPoolSize(envInt("DC_DB_POOL_MAX_SIZE", DEFAULT_MAX_POOL_SIZE))
                .connectionTimeoutMs(envLong("DC_DB_CONN_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS))
                .idleTimeoutMs(envLong("DC_DB_IDLE_TIMEOUT_MS", DEFAULT_IDLE_TIMEOUT_MS))
                .maxLifetimeMs(envLong("DC_DB_MAX_LIFETIME_MS", DEFAULT_MAX_LIFETIME_MS))
                .keepAliveIntervalMs(envLong("DC_DB_KEEPALIVE_INTERVAL_MS", DEFAULT_KEEPALIVE_INTERVAL_MS))
                .leakDetectionThresholdMs(envLong("DC_DB_LEAK_DETECTION_MS", DEFAULT_LEAK_DETECTION_THRESHOLD_MS))
                .validationTimeoutMs(envLong("DC_DB_VALIDATION_TIMEOUT_MS", DEFAULT_VALIDATION_TIMEOUT_MS))
                .build();
    }

    /**
     * Applies this configuration to a new {@link HikariDataSource}.
     *
     * @return fully-configured HikariCP data source ready for use
     */
    public HikariDataSource createDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(POOL_NAME);
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);

        // Pool sizing
        hikariConfig.setMinimumIdle(minIdle);
        hikariConfig.setMaximumPoolSize(maxPoolSize);

        // Timeouts and lifetimes
        hikariConfig.setConnectionTimeout(connectionTimeoutMs);
        hikariConfig.setIdleTimeout(idleTimeoutMs);
        hikariConfig.setMaxLifetime(maxLifetimeMs);
        hikariConfig.setKeepaliveTime(keepAliveIntervalMs);

        // Connection validation — FINDING-DC-M6
        hikariConfig.setConnectionTestQuery(CONNECTION_TEST_QUERY);
        hikariConfig.setValidationTimeout(validationTimeoutMs);
        hikariConfig.setLeakDetectionThreshold(leakDetectionThresholdMs);

        // Performance properties
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

        log.info("Creating HikariCP data source: pool={} minIdle={} maxPool={} leakThresholdMs={}",
                POOL_NAME, minIdle, maxPoolSize, leakDetectionThresholdMs);

        return new HikariDataSource(hikariConfig);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String jdbcUrl()                  { return jdbcUrl; }
    public int minIdle()                     { return minIdle; }
    public int maxPoolSize()                 { return maxPoolSize; }
    public long connectionTimeoutMs()        { return connectionTimeoutMs; }
    public long idleTimeoutMs()              { return idleTimeoutMs; }
    public long maxLifetimeMs()              { return maxLifetimeMs; }
    public long keepAliveIntervalMs()        { return keepAliveIntervalMs; }
    public long leakDetectionThresholdMs()   { return leakDetectionThresholdMs; }
    public long validationTimeoutMs()        { return validationTimeoutMs; }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link DataCloudDatabaseConfig}.
     */
    public static final class Builder {
        private String jdbcUrl;
        private String username;
        private String password;
        private int    minIdle                  = DEFAULT_MIN_IDLE;
        private int    maxPoolSize              = DEFAULT_MAX_POOL_SIZE;
        private long   connectionTimeoutMs      = DEFAULT_CONNECTION_TIMEOUT_MS;
        private long   idleTimeoutMs            = DEFAULT_IDLE_TIMEOUT_MS;
        private long   maxLifetimeMs            = DEFAULT_MAX_LIFETIME_MS;
        private long   keepAliveIntervalMs      = DEFAULT_KEEPALIVE_INTERVAL_MS;
        private long   leakDetectionThresholdMs = DEFAULT_LEAK_DETECTION_THRESHOLD_MS;
        private long   validationTimeoutMs      = DEFAULT_VALIDATION_TIMEOUT_MS;

        public Builder jdbcUrl(String jdbcUrl)                               { this.jdbcUrl = jdbcUrl; return this; }
        public Builder username(String username)                             { this.username = username; return this; }
        public Builder password(String password)                             { this.password = password; return this; }
        public Builder minIdle(int minIdle)                                  { this.minIdle = minIdle; return this; }
        public Builder maxPoolSize(int maxPoolSize)                          { this.maxPoolSize = maxPoolSize; return this; }
        public Builder connectionTimeoutMs(long connectionTimeoutMs)         { this.connectionTimeoutMs = connectionTimeoutMs; return this; }
        public Builder idleTimeoutMs(long idleTimeoutMs)                     { this.idleTimeoutMs = idleTimeoutMs; return this; }
        public Builder maxLifetimeMs(long maxLifetimeMs)                     { this.maxLifetimeMs = maxLifetimeMs; return this; }
        public Builder keepAliveIntervalMs(long keepAliveIntervalMs)         { this.keepAliveIntervalMs = keepAliveIntervalMs; return this; }
        public Builder leakDetectionThresholdMs(long leakDetectionThresholdMs) { this.leakDetectionThresholdMs = leakDetectionThresholdMs; return this; }
        public Builder validationTimeoutMs(long validationTimeoutMs)         { this.validationTimeoutMs = validationTimeoutMs; return this; }

        public DataCloudDatabaseConfig build() {
            return new DataCloudDatabaseConfig(this);
        }
    }

    // ── Env helpers ───────────────────────────────────────────────────────────

    private static int envInt(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.strip());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for env var {}: '{}', using default {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    private static long envLong(String key, long defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Long.parseLong(val.strip());
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for env var {}: '{}', using default {}", key, val, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return "DataCloudDatabaseConfig{" +
               "jdbcUrl='" + jdbcUrl + '\'' +
               ", minIdle=" + minIdle +
               ", maxPoolSize=" + maxPoolSize +
               ", connectionTimeoutMs=" + connectionTimeoutMs +
               ", idleTimeoutMs=" + idleTimeoutMs +
               ", maxLifetimeMs=" + maxLifetimeMs +
               ", leakDetectionThresholdMs=" + leakDetectionThresholdMs +
               '}';
    }
}
