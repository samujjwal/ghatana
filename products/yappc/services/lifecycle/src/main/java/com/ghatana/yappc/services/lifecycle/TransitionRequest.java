/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

/**
 * Input parameters for a lifecycle phase advance request.
 *
 * @param projectId   unique identifier of the YAPPC project
 * @param fromPhase   current phase of the project (e.g., {@code "intent"})
 * @param toPhase     requested target phase (e.g., {@code "context"})
 * @param tenantId    tenant owning the project
 * @param requestedBy user ID or agent ID requesting the transition
 *
 * @doc.type class
 * @doc.purpose Value object carrying phase advance request parameters
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TransitionRequest(
    String projectId,
    String fromPhase,
    String toPhase,
    String tenantId,
    String requestedBy
) {}
