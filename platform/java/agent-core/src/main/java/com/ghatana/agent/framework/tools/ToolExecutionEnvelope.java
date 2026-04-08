/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import com.ghatana.agent.framework.governance.ActionClass;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Normalized envelope for a single tool invocation, capturing all audit and trace metadata
 * required for governance evaluation, approval routing, and evidence recording.
 *
 * @param invocationId        unique identifier for this specific invocation (UUID)
 * @param toolId              ID of the tool being invoked (must match {@code ToolContract.toolId})
 * @param toolVersion         version of the tool contract used for this call
 * @param callerAgentId       ID of the agent invoking the tool
 * @param callerReleaseId     ID of the {@code AgentRelease} that issued the call; may be null
 * @param tenantId            tenant scope for this invocation
 * @param actionClass         governance classification resolved for this call
 * @param requestSchemaVersion schema version of the {@code input} payload
 * @param input               tool input arguments (must conform to {@code ToolContract.inputSchema})
 * @param requestedAt         timestamp when the invocation was submitted
 *
 * @doc.type record
 * @doc.purpose Normalized execution envelope for a single tool invocation
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolExecutionEnvelope(
        String invocationId,
        String toolId,
        String toolVersion,
        String callerAgentId,
        String callerReleaseId,
        String tenantId,
        ActionClass actionClass,
        String requestSchemaVersion,
        Map<String, Object> input,
        Instant requestedAt) {

    public ToolExecutionEnvelope {
        Objects.requireNonNull(invocationId,  "invocationId must not be null");
        Objects.requireNonNull(toolId,         "toolId must not be null");
        Objects.requireNonNull(toolVersion,    "toolVersion must not be null");
        Objects.requireNonNull(callerAgentId,  "callerAgentId must not be null");
        Objects.requireNonNull(tenantId,       "tenantId must not be null");
        Objects.requireNonNull(actionClass,    "actionClass must not be null");
        Objects.requireNonNull(requestedAt,    "requestedAt must not be null");

        if (invocationId.isBlank()) {
            throw new IllegalArgumentException("invocationId must not be blank");
        }
        if (toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        if (callerAgentId.isBlank()) {
            throw new IllegalArgumentException("callerAgentId must not be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }

        input = input != null ? Map.copyOf(input) : Map.of();
    }

    /**
     * Creates a new envelope with a fresh UUID invocation ID, using the current instant as
     * {@code requestedAt}. Convenience factory for call sites that don't need custom IDs.
     *
     * @param toolId             tool to invoke
     * @param toolVersion        contract version
     * @param callerAgentId      invoking agent
     * @param callerReleaseId    release if known, else null
     * @param tenantId           tenant scope
     * @param actionClass        governance class
     * @param requestSchemaVersion schema version
     * @param input              input arguments
     * @return a new envelope
     */
    public static ToolExecutionEnvelope of(
            String toolId,
            String toolVersion,
            String callerAgentId,
            String callerReleaseId,
            String tenantId,
            ActionClass actionClass,
            String requestSchemaVersion,
            Map<String, Object> input) {
        return new ToolExecutionEnvelope(
                UUID.randomUUID().toString(),
                toolId, toolVersion,
                callerAgentId, callerReleaseId,
                tenantId, actionClass,
                requestSchemaVersion, input,
                Instant.now());
    }
}
