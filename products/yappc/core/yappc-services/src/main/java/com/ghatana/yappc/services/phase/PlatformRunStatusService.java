/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Reads the latest platform run state for a YAPPC lifecycle phase.
 *
 * @doc.type interface
 * @doc.purpose Resolve Data Cloud, AEP, or Kernel run status for phase packets
 * @doc.layer services
 * @doc.pattern Port
 */
public interface PlatformRunStatusService {

    /**
     * Finds the latest run status for the tenant-scoped phase context.
     *
     * @param tenantId tenant identifier
     * @param workspaceId workspace identifier
     * @param projectId project identifier
     * @param phase lifecycle phase
     * @return latest run status when available
     */
    Promise<Optional<PhasePacket.PlatformRunStatus>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String phase
    );
}
