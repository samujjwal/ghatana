/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.featurestore.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.time.Duration;

/**
 * Centralised configuration for the feature-store ingest pipeline.
 *
 * <p>Previously the {@code main()} method in {@code FeatureStoreIngestLauncher} contained
 * two separate blocks of nearly identical {@link HikariConfig} setup — one for the
 * EventLogStore pool and one for the FeatureStore pool. This class collapses that
 * duplication into a single, consistent, validated record.
 *
 * <p>Construct via {@link #fromEnv()} to read all values from environment variables.
 *
 * @doc.type class
 * @doc.purpose Centralised, validated configuration for the feature-store ingest launcher
 * @doc.layer product
 * @doc.pattern ValueObject, Configuration
 */
public final class FeatureIngestConfig {

    // ── Environment variable keys ─────────────────────────────────────────────
    public static final String ENV_MODE           = "FEATURE_INGEST_MODE";
    public static final String ENV_DB_URL         = "FEATURE_INGEST_DB_URL";
    public static final String ENV_DB_USER        = "FEATURE_INGEST_DB_USER";
    public static final String ENV_DB_PASSWORD    = "FEATURE_INGEST_DB_PASSWORD";
    public static final String ENV_TENANTS        = "FEATURE_INGEST_TENANTS";
    public static final String ENV_BATCH_SIZE     = "FEATURE_INGEST_BATCH_SIZE";
    public static final String ENV_RETRY_DELAY_MS = "FEATURE_INGEST_RETRY_DELAY_MS";
    public static final String ENV_POLL_DELAY_MS  = "FEATURE_INGEST_POLL_DELAY_MS";
    /**
     * Path to the YAML feature-transform config file.
     * When absent, the pipeline accepts all event types and all fields (pass-through).
     * Example: {@code /etc/feature-ingest/transforms.yaml}
     */
    public static final String ENV_TRANSFORM_CONFIG = "FEATURE_INGEST_TRANSFORM_CONFIG";

    // ── Pool size defaults ────────────────────────────────────────────────────
    private static final int EVENT_LOG_MAX_POOL = 5;
    private static final int EVENT_LOG_MIN_IDLE = 1;
    private static final int FEATURE_STORE_MAX_POOL = 10;
    private static final int FEATURE_STORE_MIN_IDLE = 2;

    public final String mode;
    public final String dbUrl;
    public final String dbUser;
    public final String dbPassword;
    public final String tenants;
    public final int batchSize;
    public final long retryDelayMs;
    public final long pollDelayMs;

    private FeatureIngestConfig(Builder b) {
        this.mode         = b.mode;
        this.dbUrl        = b.dbUrl;
        this.dbUser       = b.dbUser;
        this.dbPassword   = b.dbPassword;
        this.tenants      = b.tenants;
        this.batchSize    = b.batchSize;
        this.retryDelayMs = b.retryDelayMs;
        this.pollDelayMs  = b.pollDelayMs;
    }

    /**
     * Reads all configuration from environment variables, applying defaults where absent.
     */
    public static FeatureIngestConfig fromEnv() {
        return new Builder()
            .mode(env(ENV_MODE, "inmemory"))
            .dbUrl(env(ENV_DB_URL, null))
            .dbUser(env(ENV_DB_USER, "featureingest"))
            .dbPassword(env(ENV_DB_PASSWORD, null))
            .tenants(env(ENV_TENANTS, "default"))
            .batchSize(Integer.parseInt(env(ENV_BATCH_SIZE, "100")))
            .retryDelayMs(Long.parseLong(env(ENV_RETRY_DELAY_MS, "5000")))
            .pollDelayMs(Long.parseLong(env(ENV_POLL_DELAY_MS, "1000")))
            .build();
    }

    /** Returns true if postgres mode is requested and dbUrl is set. */
    public boolean isPostgresMode() {
        return "postgres".equalsIgnoreCase(mode) && dbUrl != null && !dbUrl.isBlank();
    }

    /**
     * Validates required fields for postgres mode.
     *
     * @throws IllegalStateException if required fields are missing in postgres mode
     */
    public void validate() {
        if ("postgres".equalsIgnoreCase(mode) && (dbUrl == null || dbUrl.isBlank())) {
            throw new IllegalStateException(
                "FEATURE_INGEST_MODE=postgres requires FEATURE_INGEST_DB_URL to be set.");
        }
        if ("postgres".equalsIgnoreCase(mode) && (dbPassword == null || dbPassword.isBlank())) {
            throw new IllegalStateException(
                "FEATURE_INGEST_MODE=postgres requires FEATURE_INGEST_DB_PASSWORD to be set.");
        }
        if (batchSize <= 0) {
            throw new IllegalStateException("FEATURE_INGEST_BATCH_SIZE must be positive, got: " + batchSize);
        }
        if (retryDelayMs < 0) {
            throw new IllegalStateException("FEATURE_INGEST_RETRY_DELAY_MS must be non-negative");
        }
    }

    /**
     * Builds a {@link HikariDataSource} for the EventLogStore connection pool.
     *
     * <p>Pool is sized conservatively (5 max, 1 idle) since EventLogStore access
     * is sequential per-tenant and low-concurrency.
     *
     * @throws IllegalStateException if not in postgres mode
     */
    public HikariDataSource buildEventLogStoreDataSource() {
        requirePostgresMode("event-log-store pool");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dbUrl);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPassword);
        cfg.setMaximumPoolSize(EVENT_LOG_MAX_POOL);
        cfg.setMinimumIdle(EVENT_LOG_MIN_IDLE);
        cfg.setConnectionTimeout(Duration.ofSeconds(10).toMillis());
        cfg.setInitializationFailTimeout(-1L);
        cfg.setPoolName("feature-ingest-eventlog-pool");
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
    }

    /**
     * Builds a {@link HikariDataSource} for the FeatureStore connection pool.
     *
     * <p>Pool is sized for concurrent writes (10 max, 2 idle) since feature extraction
     * is CPU-bound and multiple features per event are written in quick succession.
     *
     * @throws IllegalStateException if not in postgres mode
     */
    public HikariDataSource buildFeatureStoreDataSource() {
        requirePostgresMode("feature-store pool");
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(dbUrl);
        cfg.setUsername(dbUser);
        cfg.setPassword(dbPassword);
        cfg.setMaximumPoolSize(FEATURE_STORE_MAX_POOL);
        cfg.setMinimumIdle(FEATURE_STORE_MIN_IDLE);
        cfg.setConnectionTimeout(Duration.ofSeconds(5).toMillis());
        cfg.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        cfg.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        cfg.setInitializationFailTimeout(-1L);
        cfg.setPoolName("feature-ingest-pool");
        cfg.setAutoCommit(true);
        return new HikariDataSource(cfg);
    }

    private void requirePostgresMode(String label) {
        if (!isPostgresMode()) {
            throw new IllegalStateException(
                "Cannot build " + label + " — not in postgres mode or dbUrl is missing");
        }
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static final class Builder {
        private String mode = "inmemory";
        private String dbUrl;
        private String dbUser = "featureingest";
        private String dbPassword = "";
        private String tenants = "default";
        private int batchSize = 100;
        private long retryDelayMs = 5_000L;
        private long pollDelayMs = 1_000L;

        public Builder mode(String mode)                 { this.mode = mode; return this; }
        public Builder dbUrl(String dbUrl)               { this.dbUrl = dbUrl; return this; }
        public Builder dbUser(String dbUser)             { this.dbUser = dbUser; return this; }
        public Builder dbPassword(String dbPassword)     { this.dbPassword = dbPassword; return this; }
        public Builder tenants(String tenants)           { this.tenants = tenants; return this; }
        public Builder batchSize(int batchSize)          { this.batchSize = batchSize; return this; }
        public Builder retryDelayMs(long retryDelayMs)   { this.retryDelayMs = retryDelayMs; return this; }
        public Builder pollDelayMs(long pollDelayMs)     { this.pollDelayMs = pollDelayMs; return this; }

        public FeatureIngestConfig build() {
            return new FeatureIngestConfig(this);
        }
    }
}
