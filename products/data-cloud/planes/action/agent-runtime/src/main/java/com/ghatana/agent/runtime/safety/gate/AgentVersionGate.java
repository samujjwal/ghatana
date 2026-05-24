/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that validates version compatibility before dispatch.
 *
 * <p>This gate checks that the agent version is compatible with the
 * current environment, dependency fingerprint, and runtime configuration.
 *
 * @doc.type class
 * @doc.purpose Validates version compatibility before agent dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentVersionGate implements AgentDispatchGate {

    private final VersionContextResolver versionContextResolver;

    public AgentVersionGate(VersionContextResolver versionContextResolver) {
        this.versionContextResolver = Objects.requireNonNull(versionContextResolver, "versionContextResolver must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String agentId = context.agentId();
        String agentVersion = context.agentVersion();

        Map<String, Object> versionContext = versionContextResolver.resolveVersionContext(agentId, agentVersion);

        if (versionContext == null) {
            return GateResult.failure(
                "Version context not found for agent: " + agentId + "@" + agentVersion);
        }

        if (Boolean.FALSE.equals(versionContext.get("isCompatible"))) {
            return GateResult.failure(
                "Agent version not compatible with current environment: " + agentId + "@" + agentVersion);
        }

        if (versionContext.containsKey("dependencyFingerprint")) {
            Map<String, String> expectedFingerprint = requireStringMap(
                versionContext.get("dependencyFingerprint"),
                "dependencyFingerprint");
            Map<String, String> actualFingerprint = versionContextResolver.getRuntimeFingerprint();
            
            if (!expectedFingerprint.equals(actualFingerprint)) {
                return GateResult.failure(
                    "Dependency fingerprint mismatch for agent: " + agentId + "@" + agentVersion);
            }
        }

        return GateResult.success();
    }

    private static Map<String, String> requireStringMap(Object value, String fieldName) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException(fieldName + " must be a map of string values");
        }

        java.util.LinkedHashMap<String, String> typedMap = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String stringValue)) {
                throw new IllegalArgumentException(fieldName + " must be a map of string values");
            }
            typedMap.put(key, stringValue);
        }
        return Map.copyOf(typedMap);
    }

    /**
     * Interface for resolving version context.
     *
     * <p>This is a placeholder for the actual version context resolution logic.
     * A production implementation would query the VersionContextResolver service.
     */
    public interface VersionContextResolver {
        /**
         * Resolves the version context for an agent.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @return version context map, or null if not found
         */
        Map<String, Object> resolveVersionContext(String agentId, String agentVersion);

        /**
         * Gets the current runtime dependency fingerprint.
         *
         * @return dependency fingerprint map
         */
        Map<String, String> getRuntimeFingerprint();
    }
}
