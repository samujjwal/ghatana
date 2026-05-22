package com.ghatana.kernel.plugin;

import java.util.Map;

/**
 * Request for plugin interaction handling.
 *
 * <p>This class mirrors {@link com.ghatana.kernel.interaction.ProductInteractionRequest}
 * but for plugin interactions. It contains the request payload and context.</p>
 *
 * @doc.type class
 * @doc.purpose Request wrapper for plugin interaction handling
 * @doc.layer kernel
 * @doc.pattern Value Object
 */
public record PluginInteractionRequest<T>(
    String interactionId,
    String providerPluginId,
    String consumerPluginId,
    String tenantId,
    String workspaceId,
    String runId,
    String contractId,
    Map<String, String> policyContext,
    T payload
) {
    public PluginInteractionRequest {
        if (interactionId == null || interactionId.isBlank()) {
            throw new IllegalArgumentException("interactionId must not be blank");
        }
        if (providerPluginId == null || providerPluginId.isBlank()) {
            throw new IllegalArgumentException("providerPluginId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (contractId == null || contractId.isBlank()) {
            throw new IllegalArgumentException("contractId must not be blank");
        }
        if (policyContext == null) {
            policyContext = Map.of();
        }
    }
}
