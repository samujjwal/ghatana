/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Platform policy decision.
 *
 * @doc.type record
 * @doc.purpose Represents a policy evaluation decision from platform services
 * @doc.layer platform
 * @doc.pattern Value Object
 */
public record PlatformPolicy(
    String policyId,
    boolean isAllowed,
    List<String> deniedReasons,
    Map<String, Object> context,
    Instant evaluatedAt
) {
    public record PolicyRequest(
        String policyType,
        Map<String, Object> requestData,
        String tenantId,
        String workspaceId,
        String projectId
    ) {}
}
