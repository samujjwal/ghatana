/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import java.time.Instant;
import java.util.Map;

/**
 * Platform telemetry event.
 *
 * @doc.type record
 * @doc.purpose Represents a telemetry event from platform services
 * @doc.layer platform
 * @doc.pattern Event
 */
public record PlatformTelemetry(
    String eventId,
    String eventType,
    Map<String, Object> data,
    String tenantId,
    String workspaceId,
    String projectId,
    Instant timestamp
) {}
