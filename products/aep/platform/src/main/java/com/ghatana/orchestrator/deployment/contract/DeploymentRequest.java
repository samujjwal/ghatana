package com.ghatana.orchestrator.deployment.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Contract for pipeline deployment request.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates deployment parameters (pipelineId, tenantId, environment,
 * deployment options) sent from clients to orchestrator deployment endpoint.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DeploymentRequest request = DeploymentRequest.builder()
 *     .pipelineId("fraud-detection-pipeline")
 *     .tenantId("acme-corp")
 *     .environment("production")
 *     .deploymentOptions(Map.of("maxConcurrency", "10", "timeout", "30s"))
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Deployment request contract
 * @doc.layer contracts
 * @doc.pattern Value Object
 */
@Value
@Builder
@AllArgsConstructor
public class DeploymentRequest {

    /**
     * Pipeline identifier to deploy. Non-null, non-empty.
     */
    private final String pipelineId;

    /**
     * Tenant identifier for multi-tenancy isolation. Non-null, non-empty.
     */
    private final String tenantId;

    /**
     * Deployment environment (e.g., staging, production). Non-null, non-empty.
     */
    private final String environment;

    /**
     * Optional deployment options (max concurrency, timeout, etc). Can be empty
     * or null (uses defaults).
     */
    private final Map<String, String> deploymentOptions;

    /**
     * Validate deployment request before processing.
     *
     * @return true if valid (all required fields present), false otherwise
     */
    public boolean isValid() {
        return pipelineId != null && !pipelineId.isEmpty()
                && tenantId != null && !tenantId.isEmpty()
                && environment != null && !environment.isEmpty();
    }

    /**
     * Get deployment option by key with default fallback.
     *
     * @param key the option key
     * @param defaultValue fallback if key not found
     * @return option value or default
     */
    public String getOption(String key, String defaultValue) {
        if (deploymentOptions == null) {
            return defaultValue;
        }
        return deploymentOptions.getOrDefault(key, defaultValue);
    }
}
