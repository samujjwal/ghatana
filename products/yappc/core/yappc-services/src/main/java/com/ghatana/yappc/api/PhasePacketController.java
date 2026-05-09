/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.services.phase.PhasePacketService;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static com.ghatana.yappc.api.HttpResponses.badRequest400;
import static com.ghatana.yappc.api.HttpResponses.ok200Json;
import static com.ghatana.yappc.api.HttpResponses.error500;

/**
 * HTTP API controller for phase cockpit packets.
 *
 * <p>Provides backend-driven phase packet data for the phase cockpit,
 * including blockers, evidence, governance records, and available actions.
 *
 * @doc.type class
 * @doc.purpose HTTP API controller for phase cockpit packets
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class PhasePacketController {

    private static final Logger log = LoggerFactory.getLogger(PhasePacketController.class);

    private final ObjectMapper objectMapper;
    private final PhasePacketService phasePacketService;

    public PhasePacketController(
            @NotNull ObjectMapper objectMapper,
            @NotNull PhasePacketService phasePacketService
    ) {
        this.objectMapper = objectMapper;
        this.phasePacketService = phasePacketService;
    }

    public Promise<HttpResponse> getPhasePacket(HttpRequest request) {
        return request.loadBody()
            .then(body -> {
                try {
                    PhasePacketRequest req = objectMapper.readValue(body.getArray(), PhasePacketRequest.class);

                    // Validate required fields
                    if (req.phase() == null || req.phase().isBlank()) {
                        return Promise.of(badRequest400("phase is required"));
                    }
                    if (req.projectId() == null || req.projectId().isBlank()) {
                        return Promise.of(badRequest400("projectId is required"));
                    }

                    // Extract principal for authorization context
                    Principal principal = request.getAttachment(Principal.class);
                    if (principal == null) {
                        return Promise.of(HttpResponse.ofCode(401)
                            .withJson("{\"error\":\"Unauthenticated\"}")
                            .build());
                    }

                    // Validate tenant scope
                    if (req.tenantId() != null && !req.tenantId().equals(principal.getTenantId())) {
                        log.warn("Tenant scope mismatch: principalTenant={}, requestTenant={}",
                            principal.getTenantId(), req.tenantId());
                        return Promise.of(HttpResponse.ofCode(403)
                            .withJson("{\"error\":\"Forbidden: tenant scope mismatch\"}")
                            .build());
                    }

                    // Build phase packet
                    return phasePacketService.buildPhasePacket(
                        req.phase(),
                        req.projectId(),
                        req.workspaceId() != null ? req.workspaceId() : "default-workspace",
                        principal,
                        req.correlationId()
                    )
                    .map(packet -> {
                        try {
                            return ok200Json(objectMapper.writeValueAsString(packet));
                        } catch (Exception e) {
                            log.error("Error serializing phase packet", e);
                            return error500("Internal server error");
                        }
                    });

                } catch (Exception e) {
                    log.error("Error processing phase packet request", e);
                    return Promise.of(badRequest400("Invalid request format"));
                }
            })
            .whenException(e -> log.error("Phase packet request failed", e));
    }

    public record PhasePacketRequest(
            String phase,
            String tenantId,
            String projectId,
            String workspaceId,
            String correlationId
    ) {}
}
