package com.ghatana.kernel.plugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Envelope for plugin interaction events.
 *
 * <p>This class mirrors {@link com.ghatana.kernel.interaction.ProductInteractionEventEnvelope}
 * but for plugin interactions. It contains all metadata and payload for a plugin-to-plugin event.</p>
 *
 * @doc.type class
 * @doc.purpose Event envelope for plugin interactions
 * @doc.layer kernel
 * @doc.pattern Value Object
 */
public record PluginInteractionEventEnvelope<T>(
    String eventId,
    String schemaVersion,
    String contractId,
    String contractVersion,
    String pluginId,
    List<String> consumerPluginIds,
    String pluginUnitId,
    String tenantId,
    String workspaceId,
    String runId,
    String correlationId,
    String topic,
    Instant publishedAt,
    Map<String, String> policyContext,
    T payload
) {
    public PluginInteractionEventEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        if (contractId == null || contractId.isBlank()) {
            throw new IllegalArgumentException("contractId must not be blank");
        }
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("publishedAt must not be null");
        }
        if (policyContext == null) {
            policyContext = Map.of();
        }
        if (consumerPluginIds == null) {
            consumerPluginIds = List.of();
        }
    }
}
