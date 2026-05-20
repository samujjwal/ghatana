package com.ghatana.datacloud.launcher.http;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * DC-P1-07: Runtime posture metadata for surface records.
 * Captures auth, durability, audit, policy, metrics, tracing, and health state.
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
    Map<String, DependencyHealth> dependencyHealth  // Health of required dependencies
) {
    public RuntimePosture {
        dependencyHealth = dependencyHealth == null
            ? Map.of()
            : Collections.unmodifiableMap(Map.copyOf(dependencyHealth));
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "authEnabled", authEnabled,
            "durabilityEnabled", durabilityEnabled,
            "auditEnabled", auditEnabled,
            "policyEnabled", policyEnabled,
            "metricsEnabled", metricsEnabled,
            "tracingEnabled", tracingEnabled,
            "eventStoreEnabled", eventStoreEnabled,
            "idempotencyEnabled", idempotencyEnabled,
            "dependencyHealth", dependencyHealth.entrySet().stream()
                .collect(java.util.HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue().toMap()), java.util.HashMap::putAll)
        );
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
                dependencyHealth
            );
        }
    }
}
