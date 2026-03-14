/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates Data-Cloud startup configuration before the server accepts traffic.
 *
 * <p>Performs semantic validation on environment variables and command-line
 * arguments that cannot be expressed with simple type constraints. Calling
 * {@link #validate()} on an invalid configuration throws an
 * {@link IllegalStateException} with a human-readable summary of every violation
 * found, allowing operators to fix all problems in a single restart cycle rather
 * than discovering them one at a time.
 *
 * <h3>Validated properties</h3>
 * <ul>
 *   <li>{@code DATACLOUD_HTTP_PORT} — must be 1-65535 if set</li>
 *   <li>{@code DATACLOUD_GRPC_PORT} — must be 1-65535 if set; must not equal HTTP port</li>
 *   <li>{@code DATACLOUD_MAX_CONNECTIONS} — must be a positive integer if set</li>
 *   <li>{@code DATACLOUD_INSTANCE_ID} — must not be blank if set</li>
 *   <li>{@code DATACLOUD_DB_URL} — required when {@code DATACLOUD_DB_ENABLED=true}</li>
 *   <li>{@code DATACLOUD_DB_USER} / {@code DATACLOUD_DB_PASSWORD} — required with DB URL</li>
 *   <li>{@code DATACLOUD_KAFKA_BOOTSTRAP} — required when {@code DATACLOUD_KAFKA_ENABLED=true}</li>
 *   <li>{@code DATACLOUD_CLICKHOUSE_HOST} — required when {@code DATACLOUD_CLICKHOUSE_ENABLED=true}</li>
 *   <li>{@code DATACLOUD_OPENSEARCH_HOST} — required when {@code DATACLOUD_OPENSEARCH_ENABLED=true}</li>
 * </ul>
 *
 * <h3>Usage (at startup)</h3>
 * <pre>{@code
 * DataCloudConfigValidator.fromEnvironment().validate(); // throws if invalid
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Fail-fast startup configuration validation for Data-Cloud
 * @doc.layer product
 * @doc.pattern Validator
 * @since 2.0.0
 */
public final class DataCloudConfigValidator {

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;
    private static final int DEFAULT_HTTP_PORT = 8090;
    private static final int DEFAULT_GRPC_PORT = 9090;

    private final String httpPortStr;
    private final String grpcPortStr;
    private final String maxConnectionsStr;
    private final String instanceId;
    private final boolean dbEnabled;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final boolean kafkaEnabled;
    private final String kafkaBootstrap;
    private final boolean clickhouseEnabled;
    private final String clickhouseHost;
    private final boolean opensearchEnabled;
    private final String opensearchHost;

    private DataCloudConfigValidator(Builder builder) {
        this.httpPortStr        = builder.httpPortStr;
        this.grpcPortStr        = builder.grpcPortStr;
        this.maxConnectionsStr  = builder.maxConnectionsStr;
        this.instanceId         = builder.instanceId;
        this.dbEnabled          = builder.dbEnabled;
        this.dbUrl              = builder.dbUrl;
        this.dbUser             = builder.dbUser;
        this.dbPassword         = builder.dbPassword;
        this.kafkaEnabled       = builder.kafkaEnabled;
        this.kafkaBootstrap     = builder.kafkaBootstrap;
        this.clickhouseEnabled  = builder.clickhouseEnabled;
        this.clickhouseHost     = builder.clickhouseHost;
        this.opensearchEnabled  = builder.opensearchEnabled;
        this.opensearchHost     = builder.opensearchHost;
    }

    /**
     * Creates a validator pre-populated from the current JVM environment variables.
     *
     * @return a ready-to-call {@code DataCloudConfigValidator}
     */
    public static DataCloudConfigValidator fromEnvironment() {
        return builder()
                .httpPortStr(System.getenv("DATACLOUD_HTTP_PORT"))
                .grpcPortStr(System.getenv("DATACLOUD_GRPC_PORT"))
                .maxConnectionsStr(System.getenv("DATACLOUD_MAX_CONNECTIONS"))
                .instanceId(System.getenv("DATACLOUD_INSTANCE_ID"))
                .dbEnabled("true".equalsIgnoreCase(System.getenv("DATACLOUD_DB_ENABLED")))
                .dbUrl(System.getenv("DATACLOUD_DB_URL"))
                .dbUser(System.getenv("DATACLOUD_DB_USER"))
                .dbPassword(System.getenv("DATACLOUD_DB_PASSWORD"))
                .kafkaEnabled("true".equalsIgnoreCase(System.getenv("DATACLOUD_KAFKA_ENABLED")))
                .kafkaBootstrap(System.getenv("DATACLOUD_KAFKA_BOOTSTRAP"))
                .clickhouseEnabled("true".equalsIgnoreCase(System.getenv("DATACLOUD_CLICKHOUSE_ENABLED")))
                .clickhouseHost(System.getenv("DATACLOUD_CLICKHOUSE_HOST"))
                .opensearchEnabled("true".equalsIgnoreCase(System.getenv("DATACLOUD_OPENSEARCH_ENABLED")))
                .opensearchHost(System.getenv("DATACLOUD_OPENSEARCH_HOST"))
                .build();
    }

    /**
     * Runs all validation rules. Accumulates every violation and throws a single
     * {@link IllegalStateException} whose message lists them all.
     *
     * @throws IllegalStateException if any configuration rule is violated
     */
    public void validate() {
        List<String> violations = new ArrayList<>();

        // Port validation
        int httpPort = validatePort(httpPortStr, "DATACLOUD_HTTP_PORT", DEFAULT_HTTP_PORT, violations);
        int grpcPort = validatePort(grpcPortStr, "DATACLOUD_GRPC_PORT", DEFAULT_GRPC_PORT, violations);

        if (violations.isEmpty() && httpPortStr != null && grpcPortStr != null && httpPort == grpcPort) {
            violations.add("DATACLOUD_HTTP_PORT and DATACLOUD_GRPC_PORT must not be the same (" + httpPort + ")");
        }

        // Max connections
        if (maxConnectionsStr != null) {
            try {
                int v = Integer.parseInt(maxConnectionsStr.trim());
                if (v <= 0) {
                    violations.add("DATACLOUD_MAX_CONNECTIONS must be a positive integer; got: " + maxConnectionsStr);
                }
            } catch (NumberFormatException ex) {
                violations.add("DATACLOUD_MAX_CONNECTIONS is not a valid integer: " + maxConnectionsStr);
            }
        }

        // Instance ID
        if (instanceId != null && instanceId.isBlank()) {
            violations.add("DATACLOUD_INSTANCE_ID must not be blank when set");
        }

        // Database
        if (dbEnabled) {
            requireNonBlank(dbUrl,      "DATACLOUD_DB_URL",      violations);
            requireNonBlank(dbUser,     "DATACLOUD_DB_USER",     violations);
            requireNonBlank(dbPassword, "DATACLOUD_DB_PASSWORD", violations);
        }

        // Kafka
        if (kafkaEnabled) {
            requireNonBlank(kafkaBootstrap, "DATACLOUD_KAFKA_BOOTSTRAP", violations);
        }

        // ClickHouse
        if (clickhouseEnabled) {
            requireNonBlank(clickhouseHost, "DATACLOUD_CLICKHOUSE_HOST", violations);
        }

        // OpenSearch
        if (opensearchEnabled) {
            requireNonBlank(opensearchHost, "DATACLOUD_OPENSEARCH_HOST", violations);
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(buildMessage(violations));
        }
    }

    // ==================== Helpers ====================

    private static int validatePort(
            String raw, String name, int defaultPort, List<String> violations) {
        if (raw == null) return defaultPort;
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < MIN_PORT || port > MAX_PORT) {
                violations.add(name + " must be between " + MIN_PORT + " and " + MAX_PORT + "; got: " + raw);
                return defaultPort;
            }
            return port;
        } catch (NumberFormatException ex) {
            violations.add(name + " is not a valid port number: " + raw);
            return defaultPort;
        }
    }

    private static void requireNonBlank(String value, String name, List<String> violations) {
        if (value == null || value.isBlank()) {
            violations.add(name + " is required but not set");
        }
    }

    private static String buildMessage(List<String> violations) {
        StringBuilder sb = new StringBuilder("Data-Cloud startup configuration is invalid (")
                .append(violations.size())
                .append(" violation").append(violations.size() > 1 ? "s" : "").append("):\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  [").append(i + 1).append("] ").append(violations.get(i)).append('\n');
        }
        return sb.toString();
    }

    // ==================== Builder ====================

    /**
     * Returns a builder for constructing a validator from programmatic configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DataCloudConfigValidator}.
     *
     * @doc.type class
     * @doc.purpose Fluent builder for config validator test construction
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {
        private String httpPortStr;
        private String grpcPortStr;
        private String maxConnectionsStr;
        private String instanceId;
        private boolean dbEnabled;
        private String dbUrl;
        private String dbUser;
        private String dbPassword;
        private boolean kafkaEnabled;
        private String kafkaBootstrap;
        private boolean clickhouseEnabled;
        private String clickhouseHost;
        private boolean opensearchEnabled;
        private String opensearchHost;

        private Builder() {}

        public Builder httpPortStr(String v)        { this.httpPortStr = v;       return this; }
        public Builder grpcPortStr(String v)        { this.grpcPortStr = v;       return this; }
        public Builder maxConnectionsStr(String v)  { this.maxConnectionsStr = v; return this; }
        public Builder instanceId(String v)         { this.instanceId = v;        return this; }
        public Builder dbEnabled(boolean v)         { this.dbEnabled = v;         return this; }
        public Builder dbUrl(String v)              { this.dbUrl = v;             return this; }
        public Builder dbUser(String v)             { this.dbUser = v;            return this; }
        public Builder dbPassword(String v)         { this.dbPassword = v;        return this; }
        public Builder kafkaEnabled(boolean v)      { this.kafkaEnabled = v;      return this; }
        public Builder kafkaBootstrap(String v)     { this.kafkaBootstrap = v;    return this; }
        public Builder clickhouseEnabled(boolean v) { this.clickhouseEnabled = v; return this; }
        public Builder clickhouseHost(String v)     { this.clickhouseHost = v;    return this; }
        public Builder opensearchEnabled(boolean v) { this.opensearchEnabled = v; return this; }
        public Builder opensearchHost(String v)     { this.opensearchHost = v;    return this; }

        public DataCloudConfigValidator build() {
            return new DataCloudConfigValidator(this);
        }
    }
}
