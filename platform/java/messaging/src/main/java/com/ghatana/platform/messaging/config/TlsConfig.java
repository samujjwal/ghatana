/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.config;


/**
 * Shared TLS (Transport Layer Security) configuration used across all connector types.
 *
 * @doc.type record
 * @doc.purpose Shared TLS configuration for connector security
 * @doc.layer infrastructure
 * @doc.pattern ValueObject
 */
public record TlsConfig(
        boolean enabled,
        String keystorePath,
        String keystorePassword,
        String truststorePath,
        String[] enabledProtocols
) {
    /** Convenience instance with TLS disabled (useful for local/dev environments). */
    public static final TlsConfig DISABLED = new TlsConfig(false, null, null, null, new String[0]);

    /** Default TLS-enabled configuration using system truststore. */
    public static final TlsConfig DEFAULT_ENABLED =
        new TlsConfig(true, null, null, null, new String[]{"TLSv1.2", "TLSv1.3"});

    public TlsConfig {
        enabledProtocols = enabledProtocols == null ? new String[0] : enabledProtocols.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enabled = false;
        private String keystorePath;
        private String keystorePassword;
        private String truststorePath;
        private String[] enabledProtocols = {"TLSv1.2", "TLSv1.3"};

        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder keystorePath(String path) { this.keystorePath = path; return this; }
        public Builder keystorePassword(String password) { this.keystorePassword = password; return this; }
        public Builder truststorePath(String path) { this.truststorePath = path; return this; }
        public Builder enabledProtocols(String... protocols) { this.enabledProtocols = protocols; return this; }

        public TlsConfig build() {
            return new TlsConfig(enabled, keystorePath, keystorePassword, truststorePath, enabledProtocols);
        }
    }
}
