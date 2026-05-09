/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

/**
 * Service for building phase cockpit packets.
 *
 * <p>Provides backend-driven phase packet data including blockers,
 * evidence, governance records, and available actions.
 *
 * @doc.type interface
 * @doc.purpose Service for building phase cockpit packets
 * @doc.layer services
 * @doc.pattern Service
 */
public interface PhasePacketService {

    /**
     * Builds a phase packet for the given phase and project.
     *
     * @param phase the phase name
     * @param projectId the project ID
     * @param workspaceId the workspace ID
     * @param principal the authenticated principal
     * @param correlationId the correlation ID for tracing
     * @return Promise containing the phase packet
     */
    Promise<PhasePacket> buildPhasePacket(
            @NotNull String phase,
            @NotNull String projectId,
            @NotNull String workspaceId,
            @NotNull Principal principal,
            String correlationId
    );
}
