/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fluent builder for {@link AgentRelease}.
 *
 * <p>Provides sensible defaults and a validated {@link #build()} method that
 * enforces required fields. Use this class when constructing releases
 * programmatically (e.g., in tests or provisioning flows). Direct record
 * construction is also allowed when all fields are known.
 *
 * @doc.type class
 * @doc.purpose Fluent builder for AgentRelease with validation and sensible defaults
 * @doc.layer platform
 * @doc.pattern Builder
 */
public final class AgentReleaseBuilder {

    private String agentReleaseId = UUID.randomUUID().toString();
    private String agentId;
    private String specVersion = "1.0.0";
    private String releaseVersion;
    private AgentReleaseState state = AgentReleaseState.DRAFT;
    private String specDigest;
    private String policyPackId;
    private String policyPackDigest;
    private String evaluationPackId;
    private String evaluationPackDigest;
    private String memoryContractId;
    private List<String> compatibleRuntimeVersions = new ArrayList<>();
    private String signingReference;
    private String toolContractVersion;
    private String telemetryContractVersion;
    private String explanationContractVersion;
    private String redactionProfileId;
    private String threatModelId;
    private Set<String> dataClassesHandled = new HashSet<>();
    private Set<String> permittedPurposes = new HashSet<>();
    private String capabilityMaturityProfile;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private String createdBy;

    /** Creates a new builder. */
    public AgentReleaseBuilder() {}

    public AgentReleaseBuilder agentReleaseId(String id)          { this.agentReleaseId = id; return this; }
    public AgentReleaseBuilder agentId(String agentId)            { this.agentId = agentId; return this; }
    public AgentReleaseBuilder specVersion(String v)              { this.specVersion = v; return this; }
    public AgentReleaseBuilder releaseVersion(String v)           { this.releaseVersion = v; return this; }
    public AgentReleaseBuilder state(AgentReleaseState s)         { this.state = s; return this; }
    public AgentReleaseBuilder specDigest(String d)               { this.specDigest = d; return this; }
    public AgentReleaseBuilder policyPackId(String id)            { this.policyPackId = id; return this; }
    public AgentReleaseBuilder policyPackDigest(String d)         { this.policyPackDigest = d; return this; }
    public AgentReleaseBuilder evaluationPackId(String id)        { this.evaluationPackId = id; return this; }
    public AgentReleaseBuilder evaluationPackDigest(String d)     { this.evaluationPackDigest = d; return this; }
    public AgentReleaseBuilder memoryContractId(String id)        { this.memoryContractId = id; return this; }
    public AgentReleaseBuilder compatibleRuntimeVersions(List<String> v) { this.compatibleRuntimeVersions = new ArrayList<>(v); return this; }
    public AgentReleaseBuilder addCompatibleRuntime(String v)     { this.compatibleRuntimeVersions.add(v); return this; }
    public AgentReleaseBuilder signingReference(String ref)       { this.signingReference = ref; return this; }
    public AgentReleaseBuilder toolContractVersion(String v)      { this.toolContractVersion = v; return this; }
    public AgentReleaseBuilder telemetryContractVersion(String v)  { this.telemetryContractVersion = v; return this; }
    public AgentReleaseBuilder explanationContractVersion(String v){ this.explanationContractVersion = v; return this; }
    public AgentReleaseBuilder redactionProfileId(String id)      { this.redactionProfileId = id; return this; }
    public AgentReleaseBuilder threatModelId(String id)           { this.threatModelId = id; return this; }
    public AgentReleaseBuilder dataClassesHandled(Set<String> d)  { this.dataClassesHandled = new HashSet<>(d); return this; }
    public AgentReleaseBuilder addDataClass(String d)             { this.dataClassesHandled.add(d); return this; }
    public AgentReleaseBuilder permittedPurposes(Set<String> p)   { this.permittedPurposes = new HashSet<>(p); return this; }
    public AgentReleaseBuilder addPermittedPurpose(String p)      { this.permittedPurposes.add(p); return this; }
    public AgentReleaseBuilder capabilityMaturityProfile(String p){ this.capabilityMaturityProfile = p; return this; }
    public AgentReleaseBuilder createdAt(Instant t)               { this.createdAt = t; return this; }
    public AgentReleaseBuilder updatedAt(Instant t)               { this.updatedAt = t; return this; }
    public AgentReleaseBuilder createdBy(String principal)        { this.createdBy = principal; return this; }

    /**
     * Builds and returns the {@link AgentRelease}.
     *
     * @throws IllegalStateException if any required field is missing
     */
    public AgentRelease build() {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalStateException("agentId is required");
        }
        if (releaseVersion == null || releaseVersion.isBlank()) {
            throw new IllegalStateException("releaseVersion is required");
        }
        return new AgentRelease(
                agentReleaseId, agentId, specVersion, releaseVersion,
                state, specDigest,
                policyPackId, policyPackDigest,
                evaluationPackId, evaluationPackDigest,
                memoryContractId, List.copyOf(compatibleRuntimeVersions),
                signingReference, toolContractVersion,
                telemetryContractVersion, explanationContractVersion,
                redactionProfileId, threatModelId,
                Set.copyOf(dataClassesHandled), Set.copyOf(permittedPurposes),
                capabilityMaturityProfile,
                createdAt, updatedAt, createdBy);
    }
}
