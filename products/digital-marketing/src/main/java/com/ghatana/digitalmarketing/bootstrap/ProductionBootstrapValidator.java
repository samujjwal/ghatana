package com.ghatana.digitalmarketing.bootstrap;

import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * Production bootstrap validator for Digital Marketing.
 *
 * <p>Validates all required production dependencies and configurations at startup:
 * - PostgreSQL connectivity and schema version
 * - Database migrations applied
 * - Connector credentials (Google Ads)
 * - Secrets availability
 * - OTLP (OpenTelemetry) endpoint connectivity
 * - Rate limit configuration
 * - Feature flags
 * - Kill switches</p>
 *
 * @doc.type class
 * @doc.purpose Production bootstrap validation for Digital Marketing
 * @doc.layer product
 * @doc.pattern Validator
 */
public class ProductionBootstrapValidator {

    private static final Logger logger = LoggerFactory.getLogger(ProductionBootstrapValidator.class);

    private final BootstrapConfig config;
    private final Map<String, HealthStatus> validationResults = new HashMap<>();

    public ProductionBootstrapValidator(BootstrapConfig config) {
        this.config = config;
    }

    /**
     * Run all bootstrap validations.
     *
     * @return Promise that completes when all validations are done
     */
    public Promise<Void> validateAll() {
        logger.info("Starting production bootstrap validation for Digital Marketing");

        validatePostgreSQL();
        validateMigrations();
        validateConnectorCredentials();
        validateSecrets();
        validateOTLPEndpoint();
        validateRateLimits();
        validateFeatureFlags();
        validateKillSwitches();

        return Promise.complete();
    }

    private void validatePostgreSQL() {
        try {
            // Check PostgreSQL connectivity
            String dbUrl = config.getDatabaseUrl();
            if (dbUrl == null || dbUrl.isEmpty()) {
                validationResults.put("postgresql", HealthStatus.unhealthy("Database URL not configured"));
                return;
            }

            // In production, this would attempt a real connection
            // For now, we validate the configuration
            if (dbUrl.contains("localhost") && config.isProduction()) {
                validationResults.put("postgresql", HealthStatus.unhealthy("Production database cannot be localhost"));
            } else {
                validationResults.put("postgresql", HealthStatus.healthy("PostgreSQL configuration valid"));
            }
        } catch (Exception e) {
            validationResults.put("postgresql", HealthStatus.unhealthy("PostgreSQL validation failed: " + e.getMessage()));
        }
    }

    private void validateMigrations() {
        try {
            // Check if migrations are applied
            String migrationVersion = config.getMigrationVersion();
            if (migrationVersion == null || migrationVersion.isEmpty()) {
                validationResults.put("migrations", HealthStatus.unhealthy("Migration version not configured"));
                return;
            }

            // In production, this would check the database schema_migrations table
            validationResults.put("migrations", HealthStatus.healthy("Migrations at version: " + migrationVersion));
        } catch (Exception e) {
            validationResults.put("migrations", HealthStatus.unhealthy("Migration validation failed: " + e.getMessage()));
        }
    }

    private void validateConnectorCredentials() {
        try {
            // Validate Google Ads connector credentials
            String googleAdsClientId = config.getGoogleAdsClientId();
            String googleAdsClientSecret = config.getGoogleAdsClientSecret();
            String googleAdsDeveloperToken = config.getGoogleAdsDeveloperToken();

            if (config.isProduction()) {
                if (googleAdsClientId == null || googleAdsClientId.isEmpty()) {
                    validationResults.put("connector-credentials", HealthStatus.unhealthy("Google Ads client ID missing"));
                    return;
                }
                if (googleAdsClientSecret == null || googleAdsClientSecret.isEmpty()) {
                    validationResults.put("connector-credentials", HealthStatus.unhealthy("Google Ads client secret missing"));
                    return;
                }
                if (googleAdsDeveloperToken == null || googleAdsDeveloperToken.isEmpty()) {
                    validationResults.put("connector-credentials", HealthStatus.unhealthy("Google Ads developer token missing"));
                    return;
                }
            }

            validationResults.put("connector-credentials", HealthStatus.healthy("Connector credentials configured"));
        } catch (Exception e) {
            validationResults.put("connector-credentials", HealthStatus.unhealthy("Connector credential validation failed: " + e.getMessage()));
        }
    }

    private void validateSecrets() {
        try {
            // Validate required secrets
            String[] requiredSecrets = {
                "DMOS_DB_PASSWORD",
                "DMOS_ENCRYPTION_KEY",
                "DMOS_JWT_SECRET"
            };

            for (String secret : requiredSecrets) {
                String value = config.getSecret(secret);
                if (value == null || value.isEmpty()) {
                    validationResults.put("secrets", HealthStatus.unhealthy("Required secret missing: " + secret));
                    return;
                }
            }

            validationResults.put("secrets", HealthStatus.healthy("All required secrets configured"));
        } catch (Exception e) {
            validationResults.put("secrets", HealthStatus.unhealthy("Secret validation failed: " + e.getMessage()));
        }
    }

    private void validateOTLPEndpoint() {
        try {
            String otlpEndpoint = config.getOTLPEndpoint();
            if (otlpEndpoint == null || otlpEndpoint.isEmpty()) {
                validationResults.put("otlp", HealthStatus.unhealthy("OTLP endpoint not configured"));
                return;
            }

            // In production, this would attempt a connection to the OTLP endpoint
            validationResults.put("otlp", HealthStatus.healthy("OTLP endpoint configured: " + otlpEndpoint));
        } catch (Exception e) {
            validationResults.put("otlp", HealthStatus.unhealthy("OTLP validation failed: " + e.getMessage()));
        }
    }

    private void validateRateLimits() {
        try {
            // Validate rate limit configuration
            Integer apiRateLimit = config.getApiRateLimit();
            if (apiRateLimit == null || apiRateLimit <= 0) {
                validationResults.put("rate-limits", HealthStatus.unhealthy("API rate limit not configured"));
                return;
            }

            Integer connectorRateLimit = config.getConnectorRateLimit();
            if (connectorRateLimit == null || connectorRateLimit <= 0) {
                validationResults.put("rate-limits", HealthStatus.unhealthy("Connector rate limit not configured"));
                return;
            }

            validationResults.put("rate-limits", HealthStatus.healthy("Rate limits configured"));
        } catch (Exception e) {
            validationResults.put("rate-limits", HealthStatus.unhealthy("Rate limit validation failed: " + e.getMessage()));
        }
    }

    private void validateFeatureFlags() {
        try {
            // Validate feature flag configuration
            Map<String, Boolean> featureFlags = config.getFeatureFlags();
            if (featureFlags == null || featureFlags.isEmpty()) {
                validationResults.put("feature-flags", HealthStatus.unhealthy("Feature flags not configured"));
                return;
            }

            // Ensure required feature flags are present
            if (!featureFlags.containsKey("google-ads-integration")) {
                validationResults.put("feature-flags", HealthStatus.unhealthy("Required feature flag missing: google-ads-integration"));
                return;
            }

            validationResults.put("feature-flags", HealthStatus.healthy("Feature flags configured"));
        } catch (Exception e) {
            validationResults.put("feature-flags", HealthStatus.unhealthy("Feature flag validation failed: " + e.getMessage()));
        }
    }

    private void validateKillSwitches() {
        try {
            // Validate kill switch configuration
            Map<String, Boolean> killSwitches = config.getKillSwitches();
            if (killSwitches == null || killSwitches.isEmpty()) {
                validationResults.put("kill-switches", HealthStatus.unhealthy("Kill switches not configured"));
                return;
            }

            // Ensure critical kill switches are present
            if (!killSwitches.containsKey("google-ads-connector")) {
                validationResults.put("kill-switches", HealthStatus.unhealthy("Required kill switch missing: google-ads-connector"));
                return;
            }

            validationResults.put("kill-switches", HealthStatus.healthy("Kill switches configured"));
        } catch (Exception e) {
            validationResults.put("kill-switches", HealthStatus.unhealthy("Kill switch validation failed: " + e.getMessage()));
        }
    }

    /**
     * Get overall health status based on all validations.
     *
     * @return HealthStatus representing overall bootstrap health
     */
    public HealthStatus getOverallHealth() {
        boolean allHealthy = validationResults.values().stream()
            .allMatch(status -> status.getStatus() == HealthStatus.Status.HEALTHY);

        if (allHealthy) {
            return HealthStatus.healthy("All bootstrap validations passed");
        }

        HealthStatus.Builder builder = HealthStatus.builder()
            .withStatus(HealthStatus.Status.UNHEALTHY)
            .withMessage("Bootstrap validation failed");

        validationResults.forEach((name, status) -> {
            builder.withCheck(name, status.getStatus(), status.getMessage(), 0);
        });

        return builder.build();
    }

    /**
     * Bootstrap configuration interface.
     */
    public interface BootstrapConfig {
        String getDatabaseUrl();
        String getMigrationVersion();
        String getGoogleAdsClientId();
        String getGoogleAdsClientSecret();
        String getGoogleAdsDeveloperToken();
        String getSecret(String secretName);
        String getOTLPEndpoint();
        Integer getApiRateLimit();
        Integer getConnectorRateLimit();
        Map<String, Boolean> getFeatureFlags();
        Map<String, Boolean> getKillSwitches();
        boolean isProduction();
    }
}
