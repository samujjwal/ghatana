/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

import java.time.Instant;

/**
 * Canary deployment internal state.
 *
 * @param canaryId Canary ID
 * @param applicationName Application name
 * @param environment Environment
 * @param version Version
 * @param status Status
 * @param progress Progress percentage
 * @param currentTrafficPercentage Current traffic percentage
 * @param startedAt Start timestamp
 * @param completedAt Completion timestamp
 *
 * @doc.type record
 * @doc.purpose Canary deployment state
 * @doc.layer product
 * @doc.pattern DTO
 */
public record CanaryDeployment(
        String canaryId,
        String applicationName,
        String environment,
        String version,
        String status,
        int progress,
        int currentTrafficPercentage,
        Instant startedAt,
        Instant completedAt
) {}
