package com.ghatana.platform.security;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for SecurityGateway.
 *
 * <p><b>Purpose</b><br>
 * Provides configuration settings for the security gateway including:
 * <ul>
 *   <li>Token validation settings (expiry, clock skew)</li>
 *   <li>Policy evaluation settings (cache TTL)</li>
 *   <li>Audit settings (enabled/disabled, async mode)</li>
 *   <li>Rate limiting settings</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SecurityGatewayConfig config = SecurityGatewayConfig.builder()
 *     .tokenClockSkew(Duration.ofSeconds(30))
 *     .policyCacheTtl(Duration.ofMinutes(5))
 *     .auditEnabled(true)
 *     .auditAsync(true)
 *     .rateLimitRequestsPerMinute(100)
 *     .build();
 *
 * SecurityGateway gateway = SecurityGateway.builder()
 *     .config(config)
 *     // ... other components
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Security gateway configuration
 * @doc.layer core
 * @doc.pattern Configuration Builder
 */
public class SecurityGatewayConfig {

    private final Duration tokenClockSkew;
    private final Duration policyCacheTtl;
    private final boolean auditEnabled;
    private final boolean auditAsync;
    private final int rateLimitRequestsPerMinute;
    private final Duration rateLimitWindow;
    private final boolean strictMode;

    private SecurityGatewayConfig(Builder builder) {
        this.tokenClockSkew = builder.tokenClockSkew;
        this.policyCacheTtl = builder.policyCacheTtl;
        this.auditEnabled = builder.auditEnabled;
        this.auditAsync = builder.auditAsync;
        this.rateLimitRequestsPerMinute = builder.rateLimitRequestsPerMinute;
        this.rateLimitWindow = builder.rateLimitWindow;
        this.strictMode = builder.strictMode;
    }

    /**
     * Creates a new builder for SecurityGatewayConfig.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns default configuration.
     *
     * @return default configuration instance
     */
    public static SecurityGatewayConfig defaults() {
        return builder().build();
    }

    // ============================================================
    // Getters
    // ============================================================

    /**
     * Returns the allowed clock skew for token validation.
     *
     * @return clock skew duration
     */
    public Duration getTokenClockSkew() {
        return tokenClockSkew;
    }

    /**
     * Returns the TTL for policy cache.
     *
     * @return cache TTL duration
     */
    public Duration getPolicyCacheTtl() {
        return policyCacheTtl;
    }

    /**
     * Returns whether audit logging is enabled.
     *
     * @return true if audit is enabled
     */
    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    /**
     * Returns whether audit logging is asynchronous.
     *
     * @return true if audit is async
     */
    public boolean isAuditAsync() {
        return auditAsync;
    }

    /**
     * Returns the rate limit in requests per minute.
     *
     * @return requests per minute limit
     */
    public int getRateLimitRequestsPerMinute() {
        return rateLimitRequestsPerMinute;
    }

    /**
     * Returns the rate limit window duration.
     *
     * @return rate limit window
     */
    public Duration getRateLimitWindow() {
        return rateLimitWindow;
    }

    /**
     * Returns whether strict mode is enabled.
     * In strict mode, any validation failure results in denial.
     *
     * @return true if strict mode is enabled
     */
    public boolean isStrictMode() {
        return strictMode;
    }

    // ============================================================
    // Builder
    // ============================================================

    /**
     * Builder for SecurityGatewayConfig.
     */
    public static class Builder {
        private Duration tokenClockSkew = Duration.ofSeconds(30);
        private Duration policyCacheTtl = Duration.ofMinutes(5);
        private boolean auditEnabled = true;
        private boolean auditAsync = true;
        private int rateLimitRequestsPerMinute = 100;
        private Duration rateLimitWindow = Duration.ofMinutes(1);
        private boolean strictMode = true;

        private Builder() {}

        /**
         * Sets the allowed clock skew for token validation.
         * Default: 30 seconds
         *
         * @param tokenClockSkew the clock skew duration
         * @return this builder
         */
        public Builder tokenClockSkew(Duration tokenClockSkew) {
            this.tokenClockSkew = Objects.requireNonNull(tokenClockSkew);
            return this;
        }

        /**
         * Sets the TTL for policy cache.
         * Default: 5 minutes
         *
         * @param policyCacheTtl the cache TTL
         * @return this builder
         */
        public Builder policyCacheTtl(Duration policyCacheTtl) {
            this.policyCacheTtl = Objects.requireNonNull(policyCacheTtl);
            return this;
        }

        /**
         * Enables or disables audit logging.
         * Default: enabled
         *
         * @param auditEnabled true to enable audit
         * @return this builder
         */
        public Builder auditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
            return this;
        }

        /**
         * Sets whether audit logging is asynchronous.
         * Default: async (true)
         *
         * @param auditAsync true for async audit
         * @return this builder
         */
        public Builder auditAsync(boolean auditAsync) {
            this.auditAsync = auditAsync;
            return this;
        }

        /**
         * Sets the rate limit in requests per minute.
         * Default: 100
         *
         * @param rateLimitRequestsPerMinute requests per minute
         * @return this builder
         */
        public Builder rateLimitRequestsPerMinute(int rateLimitRequestsPerMinute) {
            if (rateLimitRequestsPerMinute <= 0) {
                throw new IllegalArgumentException("rateLimitRequestsPerMinute must be positive");
            }
            this.rateLimitRequestsPerMinute = rateLimitRequestsPerMinute;
            return this;
        }

        /**
         * Sets the rate limit window duration.
         * Default: 1 minute
         *
         * @param rateLimitWindow the window duration
         * @return this builder
         */
        public Builder rateLimitWindow(Duration rateLimitWindow) {
            this.rateLimitWindow = Objects.requireNonNull(rateLimitWindow);
            return this;
        }

        /**
         * Enables or disables strict mode.
         * Default: enabled
         *
         * @param strictMode true to enable strict mode
         * @return this builder
         */
        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        /**
         * Builds the SecurityGatewayConfig instance.
         *
         * @return new SecurityGatewayConfig instance
         */
        public SecurityGatewayConfig build() {
            return new SecurityGatewayConfig(this);
        }
    }

    @Override
    public String toString() {
        return "SecurityGatewayConfig{" +
                "tokenClockSkew=" + tokenClockSkew +
                ", policyCacheTtl=" + policyCacheTtl +
                ", auditEnabled=" + auditEnabled +
                ", auditAsync=" + auditAsync +
                ", rateLimitRequestsPerMinute=" + rateLimitRequestsPerMinute +
                ", rateLimitWindow=" + rateLimitWindow +
                ", strictMode=" + strictMode +
                '}';
    }
}
