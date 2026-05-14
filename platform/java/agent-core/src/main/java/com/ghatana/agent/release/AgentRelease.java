/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
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
 * @param tenantId                    tenant that owns this release (required)
 * @param specVersion                 mirrors {@code AgentSpec.agentSpecVersion} (e.g., {@code "2.0.0"})
 * @param releaseVersion              semver string (e.g., {@code "1.3.2"})
 * @param state                       current lifecycle state
 * @param specDigest                  SHA-256 of the spec YAML at release time
 * @param policyPackId                ID of the attached PolicyPack
 * @param policyPackDigest            SHA-256 of the PolicyPack
 * @param evaluationPackId            ID of the EvaluationPack that passed this release
 * @param evaluationPackDigest        SHA-256 of the EvaluationPack
 * @param memoryContractId            ID of the MemoryContract
 * @param masteryPolicyPackId         ID of the MasteryPolicyPack (required for CANARY/ACTIVE)
 * @param learningContractId          ID of the LearningContract or embedded digest
 * @param versionCompatibilityPolicyId ID of the VersionCompatibilityPolicy (replaces raw runtime version list)
 * @param freshnessPolicyId           ID of the FreshnessPolicy
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
        String tenantId,
        String specVersion,
        String releaseVersion,
        AgentReleaseState state,
        String specDigest,
        String policyPackId,
        String policyPackDigest,
        String evaluationPackId,
        String evaluationPackDigest,
        String memoryContractId,
        String masteryPolicyPackId,
        String learningContractId,
        String versionCompatibilityPolicyId,
        String freshnessPolicyId,
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
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
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
        // Governance artifacts mandatory for response-serving releases
        if (evaluationPackId == null || evaluationPackId.isBlank()) {
            throw new IllegalArgumentException("evaluationPackId must not be blank (governance gate)");
        }
        if (memoryContractId == null || memoryContractId.isBlank()) {
            throw new IllegalArgumentException("memoryContractId must not be blank (governance gate)");
        }
        // masteryPolicyPackId required for response-serving states (CANARY, ACTIVE)
        if (state != null && state.isResponseServing()
                && (masteryPolicyPackId == null || masteryPolicyPackId.isBlank())) {
            throw new IllegalArgumentException(
                    "masteryPolicyPackId must not be blank for response-serving state: " + state + " (governance gate)");
        }
        // learningContractId required for response-serving states (Phase 6.2)
        if (state != null && state.isResponseServing()
                && (learningContractId == null || learningContractId.isBlank())) {
            throw new IllegalArgumentException(
                    "learningContractId must not be blank for response-serving state: " + state + " (governance gate - Phase 6.2)");
        }
        compatibleRuntimeVersions = List.copyOf(compatibleRuntimeVersions);
        dataClassesHandled        = Set.copyOf(dataClassesHandled);
        permittedPurposes         = Set.copyOf(permittedPurposes);
    }

    /**
     * Returns skill-level evaluation pack references from release metadata.
     * Maps skill IDs to their evaluation pack IDs for skill-specific evaluation.
     *
     * @return map of skill ID to evaluation pack ID
     */
    public Map<String, String> skillEvaluationPackRefs() {
        // In a full implementation, this would be stored as a separate field or in metadata
        // For now, return empty map - this is a placeholder for Phase 6.2
        return Map.of();
    }

    /**
     * Returns mastery policy pack references from release metadata.
     * Maps skill IDs to their mastery policy pack IDs for skill-specific mastery governance.
     *
     * @return map of skill ID to mastery policy pack ID
     */
    public Map<String, String> masteryPolicyPackRefs() {
        // In a full implementation, this would be stored as a separate field or in metadata
        // For now, return empty map - this is a placeholder for Phase 6.2
        return Map.of();
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
                agentReleaseId, agentId, tenantId, specVersion, releaseVersion,
                newState, specDigest,
                policyPackId, policyPackDigest,
                evaluationPackId, evaluationPackDigest,
                memoryContractId,
                masteryPolicyPackId, learningContractId,
                versionCompatibilityPolicyId, freshnessPolicyId,
                compatibleRuntimeVersions,
                signingReference, toolContractVersion,
                telemetryContractVersion, explanationContractVersion,
                redactionProfileId, threatModelId,
                dataClassesHandled, permittedPurposes, capabilityMaturityProfile,
                createdAt, updatedAt, createdBy);
    }

    /**
     * Returns {@code true} if this release may execute internally.
     *
     * @return {@code true} iff the current state is runnable
     * @see AgentReleaseState#isRunnable()
     */
    public boolean isRunnable() {
        return state.isRunnable();
    }

    /**
     * Returns {@code true} if this release may serve responses to callers.
     *
     * @return {@code true} iff the current state is response-serving
     * @see AgentReleaseState#isResponseServing()
     */
    public boolean isResponseServing() {
        return state.isResponseServing();
    }

    /**
     * Stable SHA-256 digest over governance-critical release fields.
     * Changes when mastery policy, learning contract, spec, policy pack, or evaluation pack change.
     *
     * @return hex-encoded SHA-256 digest
     */
    public String releaseDigest() {
        String canonical = String.join("\n",
                "agentId=" + agentId,
                "specVersion=" + specVersion,
                "releaseVersion=" + releaseVersion,
                "specDigest=" + (specDigest != null ? specDigest : ""),
                "policyPackId=" + (policyPackId != null ? policyPackId : ""),
                "evaluationPackId=" + (evaluationPackId != null ? evaluationPackId : ""),
                "memoryContractId=" + (memoryContractId != null ? memoryContractId : ""),
                "masteryPolicyPackId=" + (masteryPolicyPackId != null ? masteryPolicyPackId : ""),
                "learningContractId=" + (learningContractId != null ? learningContractId : ""),
                "versionCompatibilityPolicyId=" + (versionCompatibilityPolicyId != null ? versionCompatibilityPolicyId : ""),
                "freshnessPolicyId=" + (freshnessPolicyId != null ? freshnessPolicyId : ""));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
