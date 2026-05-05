package com.ghatana.digitalmarketing.application.governance;

import io.activej.promise.Promise;

/**
 * Service port for DMOS kill switch (circuit breaker) operations.
 *
 * <p>Kill switches provide emergency circuit breaking for critical operations.
 * They can be scoped at global, tenant, workspace, or feature level.</p>
 *
 * <p>All methods are async (Promise-based) to support distributed state lookups.</p>
 *
 * @doc.type interface
 * @doc.purpose Emergency circuit breaker for critical DMOS operations (P1-024)
 * @doc.layer product
 * @doc.pattern Service, CircuitBreaker
 */
public interface DmKillSwitchService {

    /**
     * Checks if a kill switch is active for the given feature.
     *
     * <p>Checks in order of specificity: FEATURE → WORKSPACE → TENANT → GLOBAL.
     * The most specific active kill switch wins.</p>
     *
     * @param tenantId  the tenant ID
     * @param workspaceId the workspace ID (can be null for global/tenant-only checks)
     * @param feature   the feature identifier (e.g., "google_ads.publish")
     * @return Promise of true if the kill switch is active (operations should be blocked)
     */
    Promise<Boolean> isKillSwitchActive(String tenantId, String workspaceId, String feature);

    /**
     * Activates a kill switch for the given scope and feature.
     *
     * @param scope     the scope level (GLOBAL, TENANT, WORKSPACE, FEATURE)
     * @param scopeId   the scope ID (tenant ID, workspace ID, or feature name)
     * @param feature   the feature identifier
     * @param reason    human-readable reason for activation
     * @param activatedBy who is activating the kill switch
     * @return Promise that completes when the kill switch is activated
     */
    Promise<Void> activateKillSwitch(
        String scope, String scopeId, String feature, String reason, String activatedBy);

    /**
     * Deactivates a kill switch for the given scope and feature.
     *
     * @param scope     the scope level (GLOBAL, TENANT, WORKSPACE, FEATURE)
     * @param scopeId   the scope ID
     * @param feature   the feature identifier
     * @param deactivatedBy who is deactivating the kill switch
     * @return Promise that completes when the kill switch is deactivated
     */
    Promise<Void> deactivateKillSwitch(
        String scope, String scopeId, String feature, String deactivatedBy);

    /**
     * Records a kill switch check in the audit log.
     *
     * @param tenantId    the tenant ID
     * @param workspaceId the workspace ID
     * @param feature     the feature checked
     * @param wasBlocked  whether the operation was blocked
     * @param correlationId the correlation ID for tracing
     * @return Promise that completes when the audit record is created
     */
    Promise<Void> recordKillSwitchAudit(
        String tenantId, String workspaceId, String feature,
        boolean wasBlocked, String correlationId);

    /**
     * Scope levels for kill switches.
     */
    enum Scope {
        GLOBAL, TENANT, WORKSPACE, FEATURE
    }

    /**
     * Status values for kill switches.
     */
    enum Status {
        ACTIVE, INACTIVE
    }

    /**
     * Pre-defined feature identifiers for DMOS kill switches.
     */
    final class Features {
        private Features() {}

        public static final String GOOGLE_ADS_PUBLISH = "google_ads.publish";
        public static final String GOOGLE_ADS_UPDATE = "google_ads.update";
        public static final String AI_GENERATION = "ai.generation";
        public static final String BUDGET_MODIFICATION = "budget.modification";
        public static final String CAMPAIGN_ACTIVATION = "campaign.activation";
        public static final String CONNECTOR_WRITE = "connector.write";
        public static final String STRATEGY_PUBLISH = "strategy.publish";
    }
}
