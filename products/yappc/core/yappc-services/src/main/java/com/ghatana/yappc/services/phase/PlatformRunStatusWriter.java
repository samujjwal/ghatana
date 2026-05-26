/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Writes platform run status updates from Kernel/AEP execution events.
 *
 * @doc.type interface
 * @doc.purpose Persist YAPPC platform run status truth for phase packets
 * @doc.layer services
 * @doc.pattern Port
 */
public interface PlatformRunStatusWriter {

    /**
     * Persists a normalized run status record.
     *
     * @param record normalized platform run status record
     * @return promise completing after the record is persisted
     */
    Promise<Void> record(@NotNull PlatformRunStatusRecord record);

    /**
     * Normalizes and persists a Data Cloud/AEP event as a platform run status record.
     *
     * @param tenantId tenant scope for the event
     * @param event canonical Data Cloud event envelope
     * @return promise completing after the event is persisted
     */
    Promise<Void> ingestEvent(@NotNull String tenantId, @NotNull DataCloudClient.Event event);

    /**
     * Canonical persisted run status record for {@code yappc_platform_runs}.
     */
    record PlatformRunStatusRecord(
            String tenantId,
            String workspaceId,
            String projectId,
            String phase,
            String runId,
            String status,
            String platform,
            Instant startedAt,
            Instant completedAt,
            String traceId,
            List<String> evidenceIds,
            String sourceEventType,
            String correlationId
    ) {
    }
}

