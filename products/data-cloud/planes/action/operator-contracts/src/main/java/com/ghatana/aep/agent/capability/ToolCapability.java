/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.agent.capability;

import java.util.Map;
import java.util.Set;

/**
 * WS9-4: Defines a tool capability that an agent can invoke.
 *
 * <p>Tools are external functions or services that agents can call to perform
 * specific actions (e.g., database queries, API calls, file operations).
 *
 * @doc.type record
 * @doc.purpose Tool capability definition for agent tool invocation
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ToolCapability(
        String toolId,
        String displayName,
        String description,
        String inputSchema,
        String outputSchema,
        Set<String> requiredPermissions,
        Map<String, String> metadata
) {
    public ToolCapability {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (requiredPermissions == null) {
            requiredPermissions = Set.of();
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    /**
     * Returns true if this tool requires the given permission.
     */
    public boolean requiresPermission(String permission) {
        return requiredPermissions.contains(permission);
    }
}
