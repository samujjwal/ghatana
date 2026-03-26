package com.ghatana.datacloud.infrastructure.storage;

import java.util.Objects;

/**
 * Immutable configuration for {@link OpenSearchConnector}.
 *
 * <pre>{@code
 * OpenSearchConfig config = OpenSearchConfig.builder()
 *     .host("opensearch.search.svc.cluster.local")
 *     .port(9200)
 *     .username("admin")
 *     .password("admin")
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Immutable configuration for the OpenSearch full-text search connector
 * @doc.layer product
 * @doc.pattern ValueObject, Configuration
 */
public record OpenSearchConfig(
        String host,
        int port,
        String scheme,
        String username,
        String password,
        int connectTimeoutMs,
        int responseTimeoutMs,
        int maxConnections,
        boolean verifyTls
) {
    public OpenSearchConfig {
        Objects.requireNonNull(host,   "host required");
        Objects.requireNonNull(scheme, "scheme required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be 1-65535");
        scheme = scheme.toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("scheme must be 'http' or 'https'");
        }
        connectTimeoutMs  = connectTimeoutMs  > 0 ? connectTimeoutMs  : 5_000;
        responseTimeoutMs = responseTimeoutMs > 0 ? responseTimeoutMs : 30_000;
        maxConnections    = maxConnections    > 0 ? maxConnections    : 32;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host;
        private int port = 9200;
        private String scheme = "http";
        private String username;
        private String password;
        private int connectTimeoutMs  = 5_000;
        private int responseTimeoutMs = 30_000;
        private int maxConnections    = 32;
        private boolean verifyTls     = true;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder responseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder verifyTls(boolean verifyTls) {
            this.verifyTls = verifyTls;
            return this;
        }

        public OpenSearchConfig build() {
            return new OpenSearchConfig(
                    host, port, scheme, username, password,
                    connectTimeoutMs, responseTimeoutMs, maxConnections, verifyTls);
        }
    }
}
