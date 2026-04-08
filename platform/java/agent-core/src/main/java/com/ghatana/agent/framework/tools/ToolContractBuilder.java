/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

import com.ghatana.agent.framework.governance.ActionClass;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Fluent builder for {@link ToolContract}.
 *
 * <p>Defaults:
 * <ul>
 *   <li>{@code toolId} — {@code UUID.randomUUID().toString()}</li>
 *   <li>{@code toolVersion} — {@code "1.0.0"}</li>
 *   <li>{@code actionClass} — {@link ActionClass#READ}</li>
 *   <li>{@code transport} — {@link ToolTransport#IN_PROCESS}</li>
 *   <li>{@code requiresApproval} — derived from {@code actionClass.isPrivileged()}</li>
 *   <li>{@code isReversible} — derived from {@code !actionClass.isIrreversible()}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Fluent builder for ToolContract
 * @doc.layer platform
 * @doc.pattern Builder
 */
public final class ToolContractBuilder {

    private String toolId         = UUID.randomUUID().toString();
    private String toolVersion    = "1.0.0";
    private String name;
    private String description    = "";
    private ActionClass actionClass = ActionClass.READ;
    private Boolean requiresApproval;   // null ⟹ derived from actionClass
    private Boolean isReversible;       // null ⟹ derived from actionClass
    private Map<String, Object> inputSchema  = new HashMap<>();
    private Map<String, Object> outputSchema = new HashMap<>();
    private Set<String> policyTags           = new HashSet<>();
    private ToolTransport transport          = ToolTransport.IN_PROCESS;
    private String remoteEndpoint;
    private Map<String, String> metadata     = new HashMap<>();

    public ToolContractBuilder toolId(String toolId) {
        this.toolId = Objects.requireNonNull(toolId);
        return this;
    }

    public ToolContractBuilder toolVersion(String toolVersion) {
        this.toolVersion = Objects.requireNonNull(toolVersion);
        return this;
    }

    public ToolContractBuilder name(String name) {
        this.name = Objects.requireNonNull(name);
        return this;
    }

    public ToolContractBuilder description(String description) {
        this.description = Objects.requireNonNull(description);
        return this;
    }

    public ToolContractBuilder actionClass(ActionClass actionClass) {
        this.actionClass = Objects.requireNonNull(actionClass);
        return this;
    }

    public ToolContractBuilder requiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
        return this;
    }

    public ToolContractBuilder isReversible(boolean isReversible) {
        this.isReversible = isReversible;
        return this;
    }

    public ToolContractBuilder inputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = new HashMap<>(Objects.requireNonNull(inputSchema));
        return this;
    }

    public ToolContractBuilder outputSchema(Map<String, Object> outputSchema) {
        this.outputSchema = new HashMap<>(Objects.requireNonNull(outputSchema));
        return this;
    }

    public ToolContractBuilder policyTags(Set<String> policyTags) {
        this.policyTags = new HashSet<>(Objects.requireNonNull(policyTags));
        return this;
    }

    public ToolContractBuilder addPolicyTag(String tag) {
        this.policyTags.add(Objects.requireNonNull(tag));
        return this;
    }

    public ToolContractBuilder transport(ToolTransport transport) {
        this.transport = Objects.requireNonNull(transport);
        return this;
    }

    public ToolContractBuilder remoteEndpoint(String remoteEndpoint) {
        this.remoteEndpoint = remoteEndpoint;
        return this;
    }

    public ToolContractBuilder metadata(Map<String, String> metadata) {
        this.metadata = new HashMap<>(Objects.requireNonNull(metadata));
        return this;
    }

    public ToolContractBuilder addMetadata(String key, String value) {
        this.metadata.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
        return this;
    }

    /**
     * Builds an immutable {@link ToolContract}.
     *
     * @return the new contract
     * @throws IllegalStateException if {@code name} was not set
     */
    public ToolContract build() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("ToolContract requires a non-blank 'name'");
        }
        boolean effectiveRequiresApproval =
                requiresApproval != null ? requiresApproval : actionClass.isPrivileged();
        boolean effectiveIsReversible =
                isReversible != null ? isReversible : !actionClass.isIrreversible();

        return new ToolContract(
                toolId, toolVersion, name, description,
                actionClass,
                effectiveRequiresApproval,
                effectiveIsReversible,
                inputSchema, outputSchema,
                policyTags, transport,
                remoteEndpoint, metadata);
    }
}
