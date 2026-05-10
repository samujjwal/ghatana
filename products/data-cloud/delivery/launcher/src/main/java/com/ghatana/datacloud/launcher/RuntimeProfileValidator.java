package com.ghatana.datacloud.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Centralized runtime profile validator that enforces fail-closed behaviour
 * for all non-local/non-test deployments.
 *
 * <p>DC-P0-001: Every production-sensitive dependency must be validated at
 * startup time. No handler is allowed to silently fall back to noop, in-memory,
 * or heuristic behaviour in non-local profiles. This class is the single
 * authority for those rules so that scattered per-handler fallback comments
 * can be removed and trusted to this gate.
 *
 * <h2>Profile semantics</h2>
 * <ul>
 *   <li>{@code LOCAL} — in-memory, no external infra, all dependencies optional.</li>
 *   <li>{@code SOVEREIGN} — file-backed H2, no external LLM, external LLM
 *       completion disabled, but audit/auth/policy still required when
 *       strict-tenant mode is on.</li>
 *   <li>{@code STAGING}, {@code PRODUCTION} — full fail-closed: auth, audit, policy,
 *       durable idempotency, durable entity store, durable event store,
 *       transaction manager, metrics, and trace export are all required.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Centralized runtime profile fail-closed validation gate
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class RuntimeProfileValidator {

    private static final Logger log = LoggerFactory.getLogger(RuntimeProfileValidator.class);

    /** Dependency spec submitted by callers via the {@link Builder}. */
    private final String deploymentProfile;
    private final boolean strictTenantResolution;
    private final boolean authConfigured;
    private final boolean auditConfigured;
    private final boolean policyEngineConfigured;
    private final boolean durableEntityStore;
    private final boolean durableEventStore;
    private final boolean durableIdempotencyStore;
    private final boolean transactionManagerConfigured;
    private final boolean metricsConfigured;
    private final boolean traceExportConfigured;
    private final boolean completionServiceConfigured;

    private RuntimeProfileValidator(Builder b) {
        this.deploymentProfile = b.deploymentProfile;
        this.strictTenantResolution = b.strictTenantResolution;
        this.authConfigured = b.authConfigured;
        this.auditConfigured = b.auditConfigured;
        this.policyEngineConfigured = b.policyEngineConfigured;
        this.durableEntityStore = b.durableEntityStore;
        this.durableEventStore = b.durableEventStore;
        this.durableIdempotencyStore = b.durableIdempotencyStore;
        this.transactionManagerConfigured = b.transactionManagerConfigured;
        this.metricsConfigured = b.metricsConfigured;
        this.traceExportConfigured = b.traceExportConfigured;
        this.completionServiceConfigured = b.completionServiceConfigured;
    }

    /**
     * Runs all profile-specific validation rules. Accumulates every violation
     * and throws a single {@link IllegalStateException} listing them all so
     * that operators can fix every gap in one deployment cycle.
     *
     * @throws IllegalStateException if any required dependency is absent for
     *                               the configured deployment profile
     */
    public void validate() {
        List<String> violations = new ArrayList<>();

        boolean isLocalProfile = isLocal(deploymentProfile);
        boolean isSovereignProfile = isSovereign(deploymentProfile);
        boolean isProductionLike = isProductionLike(deploymentProfile);

        if (isLocalProfile && !strictTenantResolution) {
            log.info("[DC-P0-001] LOCAL profile active — dependency validation skipped");
            return;
        }

        // Auth is required for all non-local profiles and any strict-tenant mode.
        if (!authConfigured) {
            violations.add(
                "DC-P0-001: Authentication (API key or JWT) is required for profile '"
                + deploymentProfile + "'. Configure DATACLOUD_API_KEYS or DATACLOUD_JWT_SECRET.");
        }

        // Audit is required for non-local.
        if (!auditConfigured) {
            violations.add(
                "DC-P0-001: Audit service is required for profile '" + deploymentProfile
                + "'. Configure DATACLOUD_AUDIT_ENABLED=true.");
        }

        // Policy engine is required for non-local.
        if (!policyEngineConfigured) {
            violations.add(
                "DC-P0-001: Policy engine is required for profile '" + deploymentProfile
                + "'. Configure DATACLOUD_POLICY_ENGINE_URL.");
        }

        // Production and staging require the full durable stack.
        if (isProductionLike) {
            if (!durableEntityStore) {
                violations.add(
                    "DC-P0-001: Durable entity store is required for profile '" + deploymentProfile
                    + "'. Configure DATACLOUD_DB_ENABLED=true with DATACLOUD_DB_URL.");
            }
            if (!durableEventStore) {
                violations.add(
                    "DC-P0-001: Durable event store is required for profile '" + deploymentProfile
                    + "'. Configure DATACLOUD_KAFKA_ENABLED=true with DATACLOUD_KAFKA_BOOTSTRAP.");
            }
            if (!durableIdempotencyStore) {
                violations.add(
                    "DC-P0-001: Durable idempotency store is required for profile '" + deploymentProfile
                    + "'. Ensure database is configured (DATACLOUD_DB_ENABLED=true).");
            }
            if (!transactionManagerConfigured) {
                violations.add(
                    "DC-P0-001: Transaction manager is required for profile '" + deploymentProfile
                    + "'. Multi-step writes must be atomic. Ensure DATACLOUD_DB_ENABLED=true.");
            }
            if (!metricsConfigured) {
                violations.add(
                    "DC-P0-001: Metrics collector is required for profile '" + deploymentProfile
                    + "'. Configure DATACLOUD_METRICS_ENABLED=true.");
            }
            if (!traceExportConfigured) {
                violations.add(
                    "DC-P0-001: Trace export service is required for profile '" + deploymentProfile
                    + "'. Configure CLICKHOUSE_HOST for span persistence.");
            }
            if (!completionServiceConfigured) {
                violations.add(
                    "DC-P0-001: AI completion service is required for profile '" + deploymentProfile
                    + "'. Configure AI_PROVIDER / OPENAI_API_KEY / OLLAMA_HOST.");
            }
        }

        if (!violations.isEmpty()) {
            String message = buildViolationMessage(violations, deploymentProfile);
            log.error("[DC-P0-001] Runtime profile validation FAILED for profile '{}': {} violation(s) found",
                deploymentProfile, violations.size());
            for (String v : violations) {
                log.error("[DC-P0-001]   -> {}", v);
            }
            throw new IllegalStateException(message);
        }

        log.info(
            "[DC-P0-001] Runtime profile validation PASSED for profile '{}' — "
            + "auth={}, audit={}, policy={}, entityStore={}, eventStore={}, "
            + "idempotency={}, txManager={}, metrics={}, traceExport={}, completionService={}",
            deploymentProfile,
            authConfigured, auditConfigured, policyEngineConfigured,
            durableEntityStore, durableEventStore,
            durableIdempotencyStore, transactionManagerConfigured,
            metricsConfigured, traceExportConfigured, completionServiceConfigured);
    }

    /**
     * Snapshot of the current validation posture for inclusion in
     * {@code /api/v1/surfaces} Runtime Truth output.
     *
     * @return an unmodifiable map of dependency name → pass/fail boolean
     */
    public Map<String, Object> toPostureSnapshot() {
        boolean productionLike = isProductionLike(deploymentProfile);
        return Map.ofEntries(
            Map.entry("profile", deploymentProfile),
            Map.entry("strictTenantResolution", strictTenantResolution),
            Map.entry("authenticationConfigured", authConfigured),
            Map.entry("auditConfigured", auditConfigured),
            Map.entry("policyConfigured", policyEngineConfigured),
            Map.entry("entityStoreDurable", durableEntityStore),
            Map.entry("coreEventStoreDurable", durableEventStore),
            Map.entry("idempotencyStoreDurable", productionLike ? durableIdempotencyStore : "n/a"),
            Map.entry("transactionManager", productionLike ? transactionManagerConfigured : "n/a"),
            Map.entry("metricsConfigured", productionLike ? metricsConfigured : "n/a"),
            Map.entry("traceConfigured", productionLike ? traceExportConfigured : "n/a"),
            Map.entry("aiCompletion", productionLike ? completionServiceConfigured : "n/a")
        );
    }

    // -------------------------------------------------------------------------
    // Static helpers
    // -------------------------------------------------------------------------

    static boolean isLocal(String profile) {
        if (profile == null) return true;
        String lower = profile.trim().toLowerCase();
        return lower.equals("local") || lower.equals("embedded");
    }

    static boolean isSovereign(String profile) {
        if (profile == null) return false;
        return "sovereign".equalsIgnoreCase(profile.trim());
    }

    static boolean isProductionLike(String profile) {
        if (profile == null) return false;
        String lower = profile.trim().toLowerCase();
        return lower.equals("production") || lower.equals("staging");
    }

    private static String buildViolationMessage(List<String> violations, String profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Runtime profile '").append(profile).append("' validation failed with ")
          .append(violations.size()).append(" violation(s):\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append('\n');
        }
        sb.append("Fix all violations before attempting to start in a non-local profile.");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Returns a new builder for constructing a {@link RuntimeProfileValidator}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RuntimeProfileValidator}.
     *
     * @doc.type class
     * @doc.purpose Builder for RuntimeProfileValidator
     * @doc.layer product
     * @doc.pattern Builder
     */
    public static final class Builder {

        private String deploymentProfile = "local";
        private boolean strictTenantResolution = false;
        private boolean authConfigured = false;
        private boolean auditConfigured = false;
        private boolean policyEngineConfigured = false;
        private boolean durableEntityStore = false;
        private boolean durableEventStore = false;
        private boolean durableIdempotencyStore = false;
        private boolean transactionManagerConfigured = false;
        private boolean metricsConfigured = false;
        private boolean traceExportConfigured = false;
        private boolean completionServiceConfigured = false;

        private Builder() {}

        /** Deployment profile label (e.g. "local", "sovereign", "staging", "production"). */
        public Builder deploymentProfile(String deploymentProfile) {
            this.deploymentProfile = deploymentProfile;
            return this;
        }

        /** Whether strict tenant resolution is active (non-local mode). */
        public Builder strictTenantResolution(boolean strictTenantResolution) {
            this.strictTenantResolution = strictTenantResolution;
            return this;
        }

        /** Whether API-key or JWT authentication is configured. */
        public Builder authConfigured(boolean authConfigured) {
            this.authConfigured = authConfigured;
            return this;
        }

        /** Whether a real audit service (not noop) is wired. */
        public Builder auditConfigured(boolean auditConfigured) {
            this.auditConfigured = auditConfigured;
            return this;
        }

        /** Whether a policy engine is wired. */
        public Builder policyEngineConfigured(boolean policyEngineConfigured) {
            this.policyEngineConfigured = policyEngineConfigured;
            return this;
        }

        /** Whether the entity store is backed by durable (non-in-memory) storage. */
        public Builder durableEntityStore(boolean durableEntityStore) {
            this.durableEntityStore = durableEntityStore;
            return this;
        }

        /** Whether the event log store is backed by durable (non-in-memory) storage. */
        public Builder durableEventStore(boolean durableEventStore) {
            this.durableEventStore = durableEventStore;
            return this;
        }

        /** Whether a durable idempotency store is wired for all mutating routes. */
        public Builder durableIdempotencyStore(boolean durableIdempotencyStore) {
            this.durableIdempotencyStore = durableIdempotencyStore;
            return this;
        }

        /** Whether a transaction manager is wired for atomic multi-step writes. */
        public Builder transactionManagerConfigured(boolean transactionManagerConfigured) {
            this.transactionManagerConfigured = transactionManagerConfigured;
            return this;
        }

        /** Whether a real metrics collector (not noop) is wired. */
        public Builder metricsConfigured(boolean metricsConfigured) {
            this.metricsConfigured = metricsConfigured;
            return this;
        }

        /** Whether a trace export service is wired for span persistence. */
        public Builder traceExportConfigured(boolean traceExportConfigured) {
            this.traceExportConfigured = traceExportConfigured;
            return this;
        }

        /** Whether an LLM completion service is wired for AI routes. */
        public Builder completionServiceConfigured(boolean completionServiceConfigured) {
            this.completionServiceConfigured = completionServiceConfigured;
            return this;
        }

        /** Builds the {@link RuntimeProfileValidator}. */
        public RuntimeProfileValidator build() {
            return new RuntimeProfileValidator(this);
        }
    }
}
