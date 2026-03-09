package com.ghatana.yappc.api.deployment;

import java.time.Instant;

/**
 * CanaryDeployment.
 *
 * @doc.type record
 * @doc.purpose canary deployment
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CanaryDeployment(
    String canaryId,
    String applicationName,
    String environment,
    String version,
    String status,
    int currentTrafficPercentage,
    int requestCount,
    Instant startedAt,
    Instant lastHealthCheck
) {}

