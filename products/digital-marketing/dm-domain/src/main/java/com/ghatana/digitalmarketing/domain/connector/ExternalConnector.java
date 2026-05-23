package com.ghatana.digitalmarketing.domain.connector;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * External connector domain entity.
 *
 * @doc.type class
 * @doc.purpose Represents an external connector for Digital Marketing integrations
 * @doc.layer product
 * @doc.pattern Domain Entity
 */
public class ExternalConnector {

    private final String connectorId;
    private final String tenantId;
    private final String name;
    private final ConnectorType connectorType;
    private final ConnectorConfig config;
    private final ConnectorStatus status;
    private final String googleAdsAccountId;
    private final Instant createdAt;
    private final Instant lastValidatedAt;
    private final String createdBy;

    private ExternalConnector(Builder builder) {
        this.connectorId = Objects.requireNonNull(builder.connectorId, "connectorId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.connectorType = Objects.requireNonNull(builder.connectorType, "connectorType must not be null");
        this.config = Objects.requireNonNull(builder.config, "config must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.googleAdsAccountId = builder.googleAdsAccountId;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.lastValidatedAt = builder.lastValidatedAt;
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
    }

    public String connectorId() {
        return connectorId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public ConnectorType connectorType() {
        return connectorType;
    }

    public ConnectorConfig config() {
        return config;
    }

    public ConnectorStatus status() {
        return status;
    }

    public String googleAdsAccountId() {
        return googleAdsAccountId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastValidatedAt() {
        return lastValidatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    /**
     * Validate the connector configuration.
     *
     * @return true if validation succeeds
     */
    public boolean validate() {
        // In a real implementation, this would validate the connector configuration
        // by making a test API call to the external service
        return true;
    }

    /**
     * Link Google Ads account.
     *
     * @param googleAdsId Google Ads account ID
     * @throws IllegalStateException if connector is not valid
     */
    public void linkGoogleAdsAccount(String googleAdsId) {
        if (status != ConnectorStatus.VALID) {
            throw new IllegalStateException("Cannot link Google Ads account to invalid connector");
        }
        // In a real implementation, this would update the Google Ads account ID
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String generateConnectorId() {
        return "CONN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static final class Builder {
        private String connectorId;
        private String tenantId;
        private String name;
        private ConnectorType connectorType;
        private ConnectorConfig config;
        private ConnectorStatus status = ConnectorStatus.PENDING;
        private String googleAdsAccountId;
        private Instant createdAt = Instant.now();
        private Instant lastValidatedAt;
        private String createdBy;

        public Builder connectorId(String connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder connectorType(ConnectorType connectorType) {
            this.connectorType = connectorType;
            return this;
        }

        public Builder config(ConnectorConfig config) {
            this.config = config;
            return this;
        }

        public Builder status(ConnectorStatus status) {
            this.status = status;
            return this;
        }

        public Builder googleAdsAccountId(String googleAdsAccountId) {
            this.googleAdsAccountId = googleAdsAccountId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastValidatedAt(Instant lastValidatedAt) {
            this.lastValidatedAt = lastValidatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public ExternalConnector build() {
            return new ExternalConnector(this);
        }
    }

    public enum ConnectorType {
        GOOGLE_ADS,
        FACEBOOK_ADS,
        LINKEDIN_ADS,
        CRM,
        EMAIL_PROVIDER,
        ANALYTICS
    }

    public enum ConnectorStatus {
        PENDING,
        VALID,
        INVALID,
        BLOCKED
    }

    public record ConnectorConfig(
        String endpoint,
        String apiKey,
        String apiSecret,
        String region,
        Map<String, String> additionalConfig
    ) {
        public ConnectorConfig {
            Objects.requireNonNull(endpoint, "endpoint must not be null");
            Objects.requireNonNull(apiKey, "apiKey must not be null");
        }
    }
}
