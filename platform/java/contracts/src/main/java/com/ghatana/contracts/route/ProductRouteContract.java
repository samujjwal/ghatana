package com.ghatana.contracts.route;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared route contract model for product routes.
 *
 * <p>This model mirrors the TypeScript ProductRouteContract schema in
 * kernel-product-contracts, providing a shared contract definition for
 * both TypeScript and Java ecosystems.
 *
 * @doc.type record
 * @doc.purpose Shared route contract model for TS and Java
 * @doc.layer platform
 * @doc.pattern Data Transfer Object
 */
public record ProductRouteContract(
    String version,
    String schemaVersion,
    String product,
    Map<String, Integer> roleOrder,
    List<ProductRoute> routes
) {
    /**
     * Product route definition.
     */
    public record ProductRoute(
        String path,
        String label,
        String description,
        String group,
        String minimumRole,
        List<String> personas,
        List<String> tiers,
        List<Object> actions,
        List<Object> cards,
        String stability,
        Boolean featureFlag,
        RouteMetadata metadata,
        List<String> surface,
        String i18nKey,
        String descriptionI18nKey,
        String routeType,
        String visibilityReason,
        Boolean emergencyAction,
        String apiContractId,
        String dtoSchemaId,
        List<String> pluginDependencies,
        String auditRequirement,
        String phiSensitivity,
        String cachePolicy,
        String offlinePolicy
    ) {
        /**
         * Route metadata.
         */
        public record RouteMetadata(
            String apiEndpoint,
            String policyId,
            String testId,
            String featureFlag,
            String introducedAt
        ) {}
    }

    /**
     * Route action definition.
     */
    public record RouteAction(
        String id,
        String label,
        String endpoint,
        String method,
        String policyId,
        Boolean idempotent,
        Boolean confirmationRequired,
        String visibility
    ) {}

    /**
     * Route card definition.
     */
    public record RouteCard(
        String id,
        String title,
        String description,
        String icon,
        String badge
    ) {}

    /**
     * Route stability values.
     */
    public enum RouteStability {
        STABLE,
        PREVIEW,
        BLOCKED,
        HIDDEN,
        DEFERRED,
        REMOVED
    }

    /**
     * Route group values.
     */
    public enum RouteGroup {
        CARE,
        GOVERNANCE,
        CLINICAL,
        ADMINISTRATIVE,
        PROFILE,
        DASHBOARD,
        SYSTEM,
        EMERGENCY,
        PROVIDER,
        CAREGIVER,
        FCHV
    }

    /**
     * HTTP method values.
     */
    public enum HttpMethod {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH
    }

    /**
     * Audit requirement values.
     */
    public enum AuditRequirement {
        NONE,
        STANDARD,
        PHI_ACCESS,
        PHI_WRITE,
        EMERGENCY_BREAK_GLASS,
        ADMIN_REVIEW
    }

    /**
     * PHI sensitivity values.
     */
    public enum PhiSensitivity {
        NONE,
        PII,
        PHI,
        RESTRICTED_PHI,
        EMERGENCY_PHI
    }

    /**
     * Cache policy values.
     */
    public enum CachePolicy {
        NO_STORE,
        PRIVATE_SESSION,
        SHORT_LIVED,
        OFFLINE_ENCRYPTED
    }

    /**
     * Offline policy values.
     */
    public enum OfflinePolicy {
        ONLINE_ONLY,
        METADATA_ONLY,
        ENCRYPTED_TTL,
        EMERGENCY_UNAVAILABLE
    }
}
