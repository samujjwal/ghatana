package com.ghatana.datacloud.plugins.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Configuration for the PostgreSQL-backed EntityStore provider.
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record PostgresEntityStoreConfig(
    String jdbcUrl,
    String username,
    String password,
    int maxPoolSize,
    int minIdle,
    long connectionTimeoutMs,
    long idleTimeoutMs,
    long maxLifetimeMs
) {
    private static final int DEFAULT_MAX_POOL_SIZE = 16;
    private static final int DEFAULT_MIN_IDLE = 2;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L;
    private static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;
    private static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;

    public PostgresEntityStoreConfig {
        jdbcUrl = normalize(jdbcUrl);
        username = normalize(username);
        password = normalize(password);
        if (maxPoolSize <= 0) {
            maxPoolSize = DEFAULT_MAX_POOL_SIZE;
        }
        if (minIdle < 0) {
            minIdle = DEFAULT_MIN_IDLE;
        }
        if (connectionTimeoutMs <= 0) {
            connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;
        }
        if (idleTimeoutMs <= 0) {
            idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;
        }
        if (maxLifetimeMs <= 0) {
            maxLifetimeMs = DEFAULT_MAX_LIFETIME_MS;
        }
    }

    public boolean isConfigured() {
        return jdbcUrl != null && username != null && password != null;
    }

    public HikariDataSource createDataSource() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                "PostgresEntityStore requires DATACLOUD_DB_URL, DATACLOUD_DB_USER, and DATACLOUD_DB_PASSWORD "
                    + "(or DC_DB_URL, DC_DB_USER, DC_DB_PASSWORD)."
            );
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("datacloud-postgres-entity-store");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setMinimumIdle(Math.min(minIdle, maxPoolSize));
        hikariConfig.setConnectionTimeout(connectionTimeoutMs);
        hikariConfig.setIdleTimeout(idleTimeoutMs);
        hikariConfig.setMaxLifetime(maxLifetimeMs);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("stringtype", "unspecified");
        return new HikariDataSource(hikariConfig);
    }

    public static Optional<PostgresEntityStoreConfig> fromEnvironmentIfPresent() {
        String jdbcUrl = firstPresent("DATACLOUD_DB_URL", "DC_DB_URL");
        String username = firstPresent("DATACLOUD_DB_USER", "DC_DB_USER");
        String password = firstPresent("DATACLOUD_DB_PASSWORD", "DC_DB_PASSWORD");
        if (jdbcUrl == null || username == null || password == null) {
            return Optional.empty();
        }

        return Optional.of(new PostgresEntityStoreConfig(
            jdbcUrl,
            username,
            password,
            envInt("DATACLOUD_DB_POOL_MAX_SIZE", "DC_DB_POOL_MAX_SIZE", DEFAULT_MAX_POOL_SIZE),
            envInt("DATACLOUD_DB_POOL_MIN_IDLE", "DC_DB_POOL_MIN_IDLE", DEFAULT_MIN_IDLE),
            envLong("DATACLOUD_DB_CONN_TIMEOUT_MS", "DC_DB_CONN_TIMEOUT_MS", DEFAULT_CONNECTION_TIMEOUT_MS),
            envLong("DATACLOUD_DB_IDLE_TIMEOUT_MS", "DC_DB_IDLE_TIMEOUT_MS", DEFAULT_IDLE_TIMEOUT_MS),
            envLong("DATACLOUD_DB_MAX_LIFETIME_MS", "DC_DB_MAX_LIFETIME_MS", DEFAULT_MAX_LIFETIME_MS)
        ));
    }

    private static int envInt(String primary, String fallback, int defaultValue) {
        String value = firstPresent(primary, fallback);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static long envLong(String primary, String fallback, long defaultValue) {
        String value = firstPresent(primary, fallback);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String firstPresent(String primary, String fallback) {
        return normalize(Optional.ofNullable(System.getenv(primary)).orElse(System.getenv(fallback)));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}