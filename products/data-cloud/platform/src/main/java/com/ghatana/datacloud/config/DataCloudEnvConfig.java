/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Environment variable reader for Data-Cloud configuration.
 *
 * <p>Centralises all environment-based configuration access for the entire
 * Data-Cloud module. No class in data-cloud should call {@link System#getenv(String)}
 * directly; use an instance of this class instead.
 *
 * <h2>Supported Variables</h2>
 * <table border="1">
 *   <tr><th>Variable</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>DC_DEPLOYMENT_MODE</td><td>EMBEDDED</td><td>EMBEDDED | STANDALONE | DISTRIBUTED</td></tr>
 *   <tr><td>DC_SERVER_URL</td><td>(none)</td><td>Base URL for STANDALONE/DISTRIBUTED clients</td></tr>
 *   <tr><td>DC_CLUSTER_URLS</td><td>(none)</td><td>Comma-separated URLs for DISTRIBUTED clients</td></tr>
 *   <tr><td>DATACLOUD_PG_URL</td><td>jdbc:postgresql://localhost:5432/datacloud</td><td>PostgreSQL JDBC URL (warm tier)</td></tr>
 *   <tr><td>DATACLOUD_PG_USER</td><td>datacloud</td><td>PostgreSQL username (warm tier)</td></tr>
 *   <tr><td>DATACLOUD_PG_PASSWORD</td><td>(empty)</td><td>PostgreSQL password (warm tier)</td></tr>
 *   <tr><td>DATACLOUD_PG_POOL_SIZE</td><td>10</td><td>HikariCP max pool size (warm tier)</td></tr>
 *   <tr><td>REDIS_HOST</td><td>localhost</td><td>Redis hostname (hot-tier)</td></tr>
 *   <tr><td>REDIS_PORT</td><td>6379</td><td>Redis port</td></tr>
 *   <tr><td>S3_REGION</td><td>us-east-1</td><td>AWS region for S3 cold-tier archive</td></tr>
 *   <tr><td>DC_S3_ARCHIVE_BUCKET</td><td>dc-archive</td><td>S3 bucket for cold-tier</td></tr>
 *   <tr><td>ICEBERG_CATALOG_URI</td><td>(empty)</td><td>Iceberg REST catalog URI (optional)</td></tr>
 *   <tr><td>ICEBERG_WAREHOUSE</td><td>(empty)</td><td>Iceberg warehouse path</td></tr>
 *   <tr><td>DATACLOUD_HTTP_AUTH_TOKEN</td><td>(empty)</td><td>Bearer token for remote client auth</td></tr>
 *   <tr><td>APP_ENV</td><td>production</td><td>deployment environment</td></tr>
 * </table>
 *
 * @doc.type class
 * @doc.purpose Centralised environment variable configuration reader for Data-Cloud
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class DataCloudEnvConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudEnvConfig.class);

    // ========================================================================
    //  Variable name constants
    // ========================================================================

    public static final String DC_DEPLOYMENT_MODE     = "DC_DEPLOYMENT_MODE";
    public static final String DC_SERVER_URL          = "DC_SERVER_URL";
    public static final String DC_CLUSTER_URLS        = "DC_CLUSTER_URLS";
    public static final String DATACLOUD_PG_URL       = "DATACLOUD_PG_URL";
    public static final String DATACLOUD_PG_USER      = "DATACLOUD_PG_USER";
    public static final String DATACLOUD_PG_PASSWORD  = "DATACLOUD_PG_PASSWORD";
    public static final String DATACLOUD_PG_POOL_SIZE = "DATACLOUD_PG_POOL_SIZE";
    public static final String REDIS_HOST             = "REDIS_HOST";
    public static final String REDIS_PORT             = "REDIS_PORT";
    public static final String S3_REGION              = "S3_REGION";
    public static final String DC_S3_ARCHIVE_BUCKET   = "DC_S3_ARCHIVE_BUCKET";
    public static final String ICEBERG_CATALOG_URI    = "ICEBERG_CATALOG_URI";
    public static final String ICEBERG_WAREHOUSE      = "ICEBERG_WAREHOUSE";
    public static final String DATACLOUD_HTTP_AUTH_TOKEN = "DATACLOUD_HTTP_AUTH_TOKEN";
    public static final String APP_ENV                = "APP_ENV";

    private final Map<String, String> env;

    /** Creates an instance reading from real system environment variables. */
    public static DataCloudEnvConfig fromSystem() {
        return new DataCloudEnvConfig(System.getenv());
    }

    /**
     * Creates an instance reading from the supplied map.
     * Primarily for testing without modifying system env.
     *
     * @param env environment map
     */
    public static DataCloudEnvConfig fromMap(Map<String, String> env) {
        return new DataCloudEnvConfig(Map.copyOf(env));
    }

    private DataCloudEnvConfig(Map<String, String> env) {
        this.env = env;
    }

    /**
     * Returns the value of {@code key}, or {@code defaultValue} if absent or blank.
     *
     * @param key          environment variable name
     * @param defaultValue fallback value
     * @return resolved value
     */
    public String get(String key, String defaultValue) {
        String value = env.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * Returns the integer value of {@code key}, or {@code defaultValue} if absent.
     *
     * @param key          environment variable name
     * @param defaultValue fallback integer
     * @return resolved integer
     * @throws IllegalStateException if the variable is set to a non-integer value
     */
    public int getInt(String key, int defaultValue) {
        String value = env.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "Environment variable '" + key + "' must be an integer, but got: " + value, e);
        }
    }

    /**
     * Returns the value of {@code key}, throwing if absent or blank.
     *
     * @param key environment variable name
     * @return value
     * @throws IllegalStateException if the variable is not set
     */
    public String require(String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Required environment variable '" + key + "' is not set.");
        }
        return value;
    }

    /** @return true if {@code APP_ENV} is {@code development} (case-insensitive) */
    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(get(APP_ENV, "production"));
    }

    // ========================================================================
    //  Typed accessors
    // ========================================================================

    /** Deployment mode: EMBEDDED, STANDALONE, or DISTRIBUTED. */
    public String deploymentMode()    { return get(DC_DEPLOYMENT_MODE, "EMBEDDED").toUpperCase(); }

    /** Server URL for STANDALONE mode. */
    public String serverUrl()         { return get(DC_SERVER_URL, ""); }

    /** Comma-separated cluster URLs for DISTRIBUTED mode. */
    public String clusterUrls()       { return get(DC_CLUSTER_URLS, ""); }

    /** PostgreSQL JDBC URL for warm-tier event log. */
    public String pgUrl()             { return get(DATACLOUD_PG_URL, "jdbc:postgresql://localhost:5432/datacloud"); }

    /** PostgreSQL username for warm-tier event log. */
    public String pgUser()            { return get(DATACLOUD_PG_USER, "datacloud"); }

    /** PostgreSQL password for warm-tier event log. */
    public String pgPassword()        { return get(DATACLOUD_PG_PASSWORD, ""); }

    /** HikariCP max pool size for warm-tier event log. */
    public int    pgPoolSize()        { return getInt(DATACLOUD_PG_POOL_SIZE, 10); }

    /** Redis hostname for hot-tier storage. */
    public String redisHost()         { return get(REDIS_HOST, "localhost"); }

    /** Redis port for hot-tier storage. */
    public int    redisPort()         { return getInt(REDIS_PORT, 6379); }

    /** AWS region for S3 cold-tier archive. */
    public String s3Region()          { return get(S3_REGION, "us-east-1"); }

    /** S3 bucket name for cold-tier archive. */
    public String s3ArchiveBucket()   { return get(DC_S3_ARCHIVE_BUCKET, "dc-archive"); }

    /** Iceberg REST catalog URI (empty string if not configured). */
    public String icebergCatalogUri() { return get(ICEBERG_CATALOG_URI, ""); }

    /** Iceberg warehouse path (empty string if not configured). */
    public String icebergWarehouse()  { return get(ICEBERG_WAREHOUSE, ""); }

    /** Bearer token for Data-Cloud remote client auth. */
    public String authToken()         { return get(DATACLOUD_HTTP_AUTH_TOKEN, ""); }
}
