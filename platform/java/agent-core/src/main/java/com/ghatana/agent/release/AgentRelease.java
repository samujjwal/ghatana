/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.util.List;
import java.util.Set;

/**
 * Immutable, versioned, signed release of an agent spec.
 *
 * <p>An {@code AgentRelease} is the deployable unit. It ties a specific version of
 * an agent spec to its governance artifacts (policy pack, evaluation pack, memory
 * contract) and tracks its lifecycle state through the
 * {@link AgentReleaseState} state machine.
 *
 * <p>To construct an instance use the {@link AgentReleaseBuilder} or the plain
 * canonical constructor provided by the Java record syntax.
 *
 * @param agentReleaseId              UUID, immutable primary key
 * @param agentId                     links back to AgentSpec.agentId
 * @param specVersion                 mirrors {@code AgentSpec.agentSpecVersion} (e.g., {@code "2.0.0"})
 * @param releaseVersion              semver string (e.g., {@code "1.3.2"})
 * @param state                       current lifecycle state
 * @param specDigest                  SHA-256 of the spec YAML at release time
 * @param policyPackId                ID of the attached PolicyPack
 * @param policyPackDigest            SHA-256 of the PolicyPack
 * @param evaluationPackId            ID of the EvaluationPack that passed this release
 * @param evaluationPackDigest        SHA-256 of the EvaluationPack
 * @param memoryContractId            ID of the MemoryContract
 * @param compatibleRuntimeVersions   runtime version constraints (e.g., {@code ["aep-runtime:2.x"]})
 * @param signingReference            Sigstore bundle or attestation reference (nullable)
 * @param toolContractVersion         version of ToolContract at release time
 * @param telemetryContractVersion    version of AgentTelemetryContract expected
 * @param explanationContractVersion  version of explanation contract
 * @param redactionProfileId          ID of the redaction profile
 * @param threatModelId               ID of the threat model
 * @param dataClassesHandled          data classes processed by this agent
 * @param permittedPurposes           permitted processing purposes
 * @param capabilityMaturityProfile   capability maturity level (e.g., {@code "L3"})
 * @param createdAt                   creation timestamp
 * @param updatedAt                   last-update timestamp
 * @param createdBy                   principal that created the release
 *
 * @doc.type record
 * @doc.purpose Immutable, versioned, signed release of an agent spec
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record AgentRelease(
        String agentReleaseId,
        String agentId,
        String specVersion,
        String releaseVersion,
        AgentReleaseState state,
        String specDigest,
        String policyPackId,
        String policyPackDigest,
        String evaluationPackId,
        String evaluationPackDigest,
        String memoryContractId,
        List<String> compatibleRuntimeVersions,
        String signingReference,
        String toolContractVersion,
        String telemetryContractVersion,
        String explanationContractVersion,
        String redactionProfileId,
        String threatModelId,
        Set<String> dataClassesHandled,
        Set<String> permittedPurposes,
        String capabilityMaturityProfile,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        String createdBy
) {
    public AgentRelease {
        if (agentReleaseId == null || agentReleaseId.isBlank()) {
            throw new IllegalArgumentException("agentReleaseId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        // TX-2: every AgentRelease must declare its redactionProfileId and threatModelId
        if (redactionProfileId == null || redactionProfileId.isBlank()) {
            throw new IllegalArgumentException("redactionProfileId must not be blank (TX-2)");
        }
        if (threatModelId == null || threatModelId.isBlank()) {
            throw new IllegalArgumentException("threatModelId must not be blank (TX-2)");
        }
        if (permittedPurposes == null || permittedPurposes.isEmpty()) {
            throw new IllegalArgumentException("permittedPurposes must not be empty (TX-2)");
        }
        // TX-5: capability maturity must be declared
        if (capabilityMaturityProfile == null || capabilityMaturityProfile.isBlank()) {
            throw new IllegalArgumentException("capabilityMaturityProfile must not be blank (TX-5)");
        }
        compatibleRuntimeVersions = List.copyOf(compatibleRuntimeVersions);
        dataClassesHandled        = Set.copyOf(dataClassesHandled);
        permittedPurposes         = Set.copyOf(permittedPurposes);
    }

    /**
     * Returns a new {@code AgentRelease} with the state transitioned to {@code newState}.
     *
     * @param newState  the target release state
     * @param updatedAt the timestamp of the transition
     * @return a new record with updated state and updatedAt
     * @throws IllegalStateException if the transition is not permitted
     */
    public AgentRelease withState(AgentReleaseState newState, java.time.Instant updatedAt) {
        if (!this.state.canTransitionTo(newState)) {
            throw new IllegalStateException(
                    "Cannot transition AgentRelease " + agentReleaseId
                    + " from " + this.state + " to " + newState
                    + ". Allowed: " + this.state.allowedTransitions());
        }
        return new AgentRelease(
                agentReleaseId, agentId, specVersion, releaseVersion,
                newState, specDigest,
                policyPackId, policyPackDigest,
                evaluationPackId, evaluationPackDigest,
                memoryContractId, compatibleRuntimeVersions,
                signingReference, toolContractVersion,
                telemetryContractVersion, explanationContractVersion,
                redactionProfileId, threatModelId,
                dataClassesHandled, permittedPurposes, capabilityMaturityProfile,
                createdAt, updatedAt, createdBy);
    }

    /**
     * Returns {@code true} if this release is eligible for dispatch.
     *
     * @return {@code true} iff the current state is dispatchable
     * @see AgentReleaseState#isDispatchable()
     */
    public boolean isDispatchable() {
        return state.isDispatchable();
    }
}
