/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import com.ghatana.agent.framework.governance.ActionClass;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Normalized, schema-driven descriptor for any tool — regardless of whether it runs
 * in-process, sandboxed, remotely, or via the Model Context Protocol.
 *
 * <p>A {@code ToolContract} encapsulates all governance metadata that the execution
 * layer needs to decide whether a tool call is permitted, which approval path to use,
 * and how to record it in the audit trail.
 *
 * <p>Immutable: use {@link ToolContractBuilder} to construct instances
 * (or the compact constructor for property-based tests).
 *
 * @param toolId          globally unique tool identifier
 * @param toolVersion     semantic version of this tool contract
 * @param name            human-readable tool name
 * @param description     human-readable purpose of the tool
 * @param actionClass     governance classification (risk/reversibility category)
 * @param requiresApproval whether this tool requires a human approval gate before execution
 * @param isReversible    whether the tool's side effects can be undone
 * @param inputSchema     JSON Schema definition for the tool's input (Map form)
 * @param outputSchema    JSON Schema definition for the tool's output (Map form)
 * @param policyTags      governance/policy tags (e.g., {@code "pii-allowed"}, {@code "external-call"})
 * @param transport       how the tool is invoked ({@link ToolTransport})
 * @param remoteEndpoint  the remote URL for {@code REMOTE} and {@code MCP} transports; null otherwise
 * @param metadata        arbitrary string key-value metadata for extension points
 *
 * @doc.type record
 * @doc.purpose Normalized, schema-driven tool descriptor for in-process, sandboxed, remote, and MCP tools
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ToolContract(
        String toolId,
        String toolVersion,
        String name,
        String description,
        ActionClass actionClass,
        boolean requiresApproval,
        boolean isReversible,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Set<String> policyTags,
        ToolTransport transport,
        String remoteEndpoint,
        Map<String, String> metadata) {

    public ToolContract {
        Objects.requireNonNull(toolId, "toolId must not be null");
        Objects.requireNonNull(toolVersion, "toolVersion must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(actionClass, "actionClass must not be null");
        Objects.requireNonNull(transport, "transport must not be null");

        if (toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        // Defensive copies for mutability protection
        inputSchema  = inputSchema  != null ? Map.copyOf(inputSchema)  : Map.of();
        outputSchema = outputSchema != null ? Map.copyOf(outputSchema) : Map.of();
        policyTags   = policyTags   != null ? Set.copyOf(policyTags)   : Set.of();
        metadata     = metadata     != null ? Map.copyOf(metadata)     : Map.of();
    }

    /**
     * Returns {@code true} if this tool contract requires a remote endpoint to be set
     * ({@link ToolTransport#REMOTE} or {@link ToolTransport#MCP}).
     *
     * @return whether a remote endpoint is expected
     */
    public boolean requiresRemoteEndpoint() {
        return transport.isExternal();
    }

    /**
     * Returns a new {@link ToolContractBuilder} pre-populated with this record's values,
     * suitable for producing a modified copy.
     *
     * @return a mutable builder initialized from this contract
     */
    public ToolContractBuilder toBuilder() {
        return new ToolContractBuilder()
                .toolId(toolId)
                .toolVersion(toolVersion)
                .name(name)
                .description(description)
                .actionClass(actionClass)
                .requiresApproval(requiresApproval)
                .isReversible(isReversible)
                .inputSchema(inputSchema)
                .outputSchema(outputSchema)
                .policyTags(policyTags)
                .transport(transport)
                .remoteEndpoint(remoteEndpoint)
                .metadata(metadata);
    }
}
