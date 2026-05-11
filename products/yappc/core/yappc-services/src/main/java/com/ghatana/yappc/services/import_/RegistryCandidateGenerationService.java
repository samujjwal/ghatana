/**
 * Registry Candidate Generation Service
 * 
 * Generates registry candidates from residual islands with approval flow.
 * Handles candidate generation, validation, and approval.
 * 
 * @doc.type interface
 * @doc.purpose Registry candidate generation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.api.ComponentRegistryContract;

import java.util.List;

/**
 * Service interface for generating registry candidates.
 */
public interface RegistryCandidateGenerationService {

    /**
     * Generates a registry candidate from a residual island.
     * 
     * @param residualIsland The residual island to convert
     * @param projectId The project ID
     * @return Generated registry candidate
     */
    RegistryCandidate generateCandidate(ResidualIsland residualIsland, String projectId);

    /**
     * Submits a registry candidate for approval.
     * 
     * @param candidate The registry candidate to submit
     * @param submitterId The submitter ID
     * @return Candidate submission result
     */
    CandidateSubmissionResult submitForApproval(RegistryCandidate candidate, String submitterId);

    /**
     * Approves a registry candidate.
     * 
     * @param candidateId The candidate ID
     * @param approverId The approver ID
     * @param reason The approval reason
     */
    void approveCandidate(String candidateId, String approverId, String reason);

    /**
     * Rejects a registry candidate.
     * 
     * @param candidateId The candidate ID
     * @param approverId The approver ID
     * @param reason The rejection reason
     */
    void rejectCandidate(String candidateId, String approverId, String reason);

    /**
     * Gets pending candidates for approval.
     * 
     * @return List of pending candidates
     */
    List<RegistryCandidate> getPendingCandidates();
}

/**
 * Registry candidate.
 */
record RegistryCandidate(
    String candidateId,
    String componentId,
    String componentName,
    String componentType,
    ComponentRegistryContract.RegistryEntry proposedEntry,
    CandidateStatus status,
    String submittedBy,
    java.time.Instant submittedAt,
    String reviewedBy,
    java.time.Instant reviewedAt,
    String reviewReason
) {
    public enum CandidateStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED
    }
}

/**
 * Candidate submission result.
 */
record CandidateSubmissionResult(
    String candidateId,
    boolean success,
    String message,
    java.util.List<String> warnings
) {
    public CandidateSubmissionResult {
        if (warnings == null) {
            warnings = java.util.List.of();
        }
    }
}
