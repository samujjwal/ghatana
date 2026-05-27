package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FCHV (Female Community Health Volunteer) Community Assignment Service for PHR.
 *
 * <p>Manages the assignment of FCHVs to communities and validates whether
 * an FCHV has access to patients within their assigned community. This is
 * required for proper policy enforcement for FCHV role access.</p>
 *
 * <p>FCHV access is scoped to patients within their assigned community.
 * This service validates community membership before allowing PHI access.</p>
 *
 * @doc.type class
 * @doc.purpose PHR FCHV community assignment validation for policy enforcement
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class FchvCommunityAssignmentService extends PhrServiceBase {

    private static final String FCHV_ASSIGNMENT_DATASET = "phr.fchv.assignments";
    private static final String PATIENT_COMMUNITY_DATASET = "phr.patient.communities";

    public FchvCommunityAssignmentService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "fchv-community-assignment";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            FCHV_ASSIGNMENT_DATASET,
            Map.of("fchvId", "string", "communityId", "string", "assignedAt", "timestamp"),
            Map.of("retention", "10years")
        ).then($ -> createSchema(
            PATIENT_COMMUNITY_DATASET,
            Map.of("patientId", "string", "communityId", "string", "assignedAt", "timestamp"),
            Map.of("retention", "10years")
        )).map($ -> null);
    }

    /**
     * Assigns an FCHV to a community.
     *
     * @param fchvId the FCHV identifier
     * @param communityId the community identifier
     * @return Promise completing when assignment is recorded
     */
    public Promise<Void> assignFchvToCommunity(String fchvId, String communityId) {
        ensureRunning();

        String sanitizedFchvId = PhrInputSanitizationUtils.requireSafeIdentifier(fchvId, "fchvId");
        String sanitizedCommunityId = PhrInputSanitizationUtils.requireSafeIdentifier(communityId, "communityId");

        String id = generateId("fca");
        FchvAssignment assignment = new FchvAssignment(
            id,
            sanitizedFchvId,
            sanitizedCommunityId,
            Instant.now()
        );

        return createRecord(
            FCHV_ASSIGNMENT_DATASET,
            id,
            assignment,
            mutationMetadata(Map.of(
                "fchvId", assignment.fchvId(),
                "communityId", assignment.communityId()
            ), assignment.fchvId()),
            "FchvAssignment",
            1
        ).map($ -> null);
    }

    /**
     * Assigns a patient to a community.
     *
     * @param patientId the patient identifier
     * @param communityId the community identifier
     * @return Promise completing when assignment is recorded
     */
    public Promise<Void> assignPatientToCommunity(String patientId, String communityId) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedCommunityId = PhrInputSanitizationUtils.requireSafeIdentifier(communityId, "communityId");

        String id = generateId("pca");
        PatientCommunityAssignment assignment = new PatientCommunityAssignment(
            id,
            sanitizedPatientId,
            sanitizedCommunityId,
            Instant.now()
        );

        return createRecord(
            PATIENT_COMMUNITY_DATASET,
            id,
            assignment,
            mutationMetadata(Map.of(
                "patientId", assignment.patientId(),
                "communityId", assignment.communityId()
            ), assignment.patientId()),
            "PatientCommunityAssignment",
            1
        ).map($ -> null);
    }

    /**
     * Checks if an FCHV has access to a patient based on community assignment.
     *
     * <p>An FCHV can access a patient if both are assigned to the same community.</p>
     *
     * @param fchvId the FCHV identifier
     * @param patientId the patient identifier
     * @return Promise containing true if FCHV has access to the patient
     */
    public Promise<Boolean> hasCommunityAccess(String fchvId, String patientId) {
        ensureRunning();

        String sanitizedFchvId = PhrInputSanitizationUtils.requireSafeIdentifier(fchvId, "fchvId");
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");

        // Get FCHV's assigned community
        return queryRecords(
            FCHV_ASSIGNMENT_DATASET,
            "fchvId = :fchvId",
            Map.of("fchvId", sanitizedFchvId),
            1,
            0,
            FchvAssignment.class
        ).then(fchvAssignments -> {
            if (fchvAssignments.isEmpty()) {
                return Promise.of(false);
            }
            String fchvCommunityId = fchvAssignments.get(0).communityId();

            // Get patient's assigned community
            return queryRecords(
                PATIENT_COMMUNITY_DATASET,
                "patientId = :patientId",
                Map.of("patientId", sanitizedPatientId),
                1,
                0,
                PatientCommunityAssignment.class
            ).map(patientAssignments -> patientAssignments.stream()
                .anyMatch(pa -> pa.communityId().equals(fchvCommunityId)));
        });
    }

    /**
     * Gets the community ID assigned to an FCHV.
     *
     * @param fchvId the FCHV identifier
     * @return Promise containing the community ID if assigned
     */
    public Promise<Optional<String>> getFchvCommunity(String fchvId) {
        ensureRunning();

        String sanitizedFchvId = PhrInputSanitizationUtils.requireSafeIdentifier(fchvId, "fchvId");

        return queryRecords(
            FCHV_ASSIGNMENT_DATASET,
            "fchvId = :fchvId",
            Map.of("fchvId", sanitizedFchvId),
            1,
            0,
            FchvAssignment.class
        ).map(assignments -> assignments.isEmpty()
            ? Optional.empty()
            : Optional.of(assignments.get(0).communityId()));
    }

    // ==================== Inner Types ====================

    record FchvAssignment(
            String id,
            String fchvId,
            String communityId,
            Instant assignedAt
    ) {}

    record PatientCommunityAssignment(
            String id,
            String patientId,
            String communityId,
            Instant assignedAt
    ) {}
}
