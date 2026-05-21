package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Broker audit record emitted for plugin interaction dispatch and delivery.
 *
 * @doc.type record
 * @doc.purpose Observable audit evidence for plugin-to-plugin interactions
 * @doc.layer core
 * @doc.pattern Evidence
 */
public record PluginInteractionAuditRecord(
        @NotNull String interactionId,
        @NotNull String contractId,
        @NotNull String schemaVersion,
        @NotNull String callerPluginId,
        @Nullable String targetPluginId,
        @Nullable String topic,
        @Nullable String tenantId,
        @Nullable String workspaceId,
        @Nullable String lifecyclePhase,
        @NotNull String correlationId,
        @NotNull String outcome,
        @NotNull String reasonCode,
        @NotNull Instant observedAt
) {
}
