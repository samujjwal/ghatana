package com.ghatana.datacloud.launcher.http;

import java.util.Collections;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Captures runtime posture metadata including auth, durability, audit, policy, metrics, tracing, and dependency health state.
 * @doc.layer product
 * @doc.pattern Data Transfer Object
 */
public record RuntimePosture(
    boolean authEnabled,           // Authentication is configured and operational
    boolean durabilityEnabled,     // Durable persistence (not in-memory only)
    boolean auditEnabled,        // Audit sink is configured and operational
    boolean policyEnabled,       // Policy engine is configured and operational
    boolean metricsEnabled,      // Metrics collection is enabled
    boolean tracingEnabled,      // Distributed tracing is enabled
    boolean eventStoreEnabled,   // Event store is durable and operational
    boolean idempotencyEnabled,  // Idempotency store is durable
    Map<String, DependencyHealth> dependencyHealth,  // Health of required dependencies
    Map<String, Object> details
) {
    public RuntimePosture {
        dependencyHealth = dependencyHealth == null
            ? Map.of()
            : Collections.unmodifiableMap(Map.copyOf(dependencyHealth));
        details = details == null
            ? Map.of()
            : Collections.unmodifiableMap(Map.copyOf(details));
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("authEnabled", authEnabled);
        result.put("durabilityEnabled", durabilityEnabled);
        result.put("auditEnabled", auditEnabled);
        result.put("policyEnabled", policyEnabled);
        result.put("metricsEnabled", metricsEnabled);
        result.put("tracingEnabled", tracingEnabled);
        result.put("eventStoreEnabled", eventStoreEnabled);
        result.put("idempotencyEnabled", idempotencyEnabled);
        result.put(
            "dependencyHealth",
            dependencyHealth.entrySet().stream()
                .collect(java.util.LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().toMap()), java.util.LinkedHashMap::putAll)
        );
        result.putAll(details);
        return Collections.unmodifiableMap(result);
    }

    public static RuntimePostureBuilder builder() {
        return new RuntimePostureBuilder();
    }

    public static class RuntimePostureBuilder {
        private boolean authEnabled;
        private boolean durabilityEnabled;
        private boolean auditEnabled;
        private boolean policyEnabled;
        private boolean metricsEnabled;
        private boolean tracingEnabled;
        private boolean eventStoreEnabled;
        private boolean idempotencyEnabled;
        private Map<String, DependencyHealth> dependencyHealth = Map.of();
        private Map<String, Object> details = Map.of();

        public RuntimePostureBuilder authEnabled(boolean authEnabled) {
            this.authEnabled = authEnabled;
            return this;
        }

        public RuntimePostureBuilder durabilityEnabled(boolean durabilityEnabled) {
            this.durabilityEnabled = durabilityEnabled;
            return this;
        }

        public RuntimePostureBuilder auditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
            return this;
        }

        public RuntimePostureBuilder policyEnabled(boolean policyEnabled) {
            this.policyEnabled = policyEnabled;
            return this;
        }

        public RuntimePostureBuilder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        public RuntimePostureBuilder tracingEnabled(boolean tracingEnabled) {
            this.tracingEnabled = tracingEnabled;
            return this;
        }

        public RuntimePostureBuilder eventStoreEnabled(boolean eventStoreEnabled) {
            this.eventStoreEnabled = eventStoreEnabled;
            return this;
        }

        public RuntimePostureBuilder idempotencyEnabled(boolean idempotencyEnabled) {
            this.idempotencyEnabled = idempotencyEnabled;
            return this;
        }

        public RuntimePostureBuilder dependencyHealth(Map<String, DependencyHealth> dependencyHealth) {
            this.dependencyHealth = dependencyHealth;
            return this;
        }

        public RuntimePostureBuilder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public RuntimePosture build() {
            return new RuntimePosture(
                authEnabled,
                durabilityEnabled,
                auditEnabled,
                policyEnabled,
                metricsEnabled,
                tracingEnabled,
                eventStoreEnabled,
                idempotencyEnabled,
                dependencyHealth,
                details
            );
        }
    }
}
