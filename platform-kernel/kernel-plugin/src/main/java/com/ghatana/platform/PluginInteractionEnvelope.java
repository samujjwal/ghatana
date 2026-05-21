package com.ghatana.platform.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Broker envelope for request/response and event plugin interactions.
 *
 * @param <Req> payload type
 *
 * @doc.type record
 * @doc.purpose Carries scoped interaction metadata across plugin boundaries
 * @doc.layer core
 * @doc.pattern Envelope
 */
public record PluginInteractionEnvelope<Req>(
        @NotNull String schemaVersion,
        @NotNull String interactionId,
        @NotNull String contractId,
        @NotNull String callerPluginId,
        @Nullable String targetPluginId,
        @Nullable String productUnitId,
        @Nullable String tenantId,
        @Nullable String workspaceId,
        @Nullable String runId,
        @Nullable String lifecyclePhase,
        @NotNull String correlationId,
        @NotNull Instant requestedAt,
        @NotNull Req payload
) {
    public PluginInteractionEnvelope(
            @NotNull String schemaVersion,
            @NotNull String interactionId,
            @NotNull String contractId,
            @NotNull String callerPluginId,
            @Nullable String targetPluginId,
            @Nullable String productUnitId,
            @Nullable String tenantId,
            @Nullable String workspaceId,
            @Nullable String runId,
            @NotNull String correlationId,
            @NotNull Instant requestedAt,
            @NotNull Req payload) {
        this(
                schemaVersion,
                interactionId,
                contractId,
                callerPluginId,
                targetPluginId,
                productUnitId,
                tenantId,
                workspaceId,
                runId,
                null,
                correlationId,
                requestedAt,
                payload);
    }
}
