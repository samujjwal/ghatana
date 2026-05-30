package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.Map;

/**
 * Treatment Relationship Service for PHR.
 *
 * <p>Validates whether a provider has an active treatment relationship with a patient,
 * which is required for emergency break-glass access. A treatment relationship exists
 * when there is an active encounter or the provider is assigned to the same facility
 * as the patient within a reasonable time window.</p>
 *
 * @doc.type class
 * @doc.purpose PHR treatment relationship validation for emergency access policy gates
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class TreatmentRelationshipService extends PhrServiceBase {

    private static final String ENCOUNTER_DATASET = "phr.clinical.encounters";
    private static final String PATIENT_FACILITY_DATASET = "phr.patient.facilities";
    private static final String PROVIDER_FACILITY_DATASET = "phr.provider.facilities";
    private static final java.time.Duration TREATMENT_WINDOW = java.time.Duration.ofDays(30);

    public TreatmentRelationshipService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "treatment-relationship";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        // Encounters are managed by ClinicalService, we only need facility datasets
        return Promises.all(
            createSchema(
                PATIENT_FACILITY_DATASET,
                Map.of("patientId", "string", "facilityId", "string", "assignedAt", "timestamp"),
                Map.of("retention", "10years")
            ),
            createSchema(
                PROVIDER_FACILITY_DATASET,
                Map.of("providerId", "string", "facilityId", "string", "assignedAt", "timestamp"),
                Map.of("retention", "10years")
            )
        ).map($ -> null);
    }

    // ==================== Core Operations ====================

    /**
     * Checks if a provider has an active treatment relationship with a patient.
     *
     * <p>A treatment relationship exists if:
     * <ol>
     *   <li>There is an active encounter between the provider and patient, OR</li>
     *   <li>The provider and patient are assigned to the same facility, OR</li>
     *   <li>There was an encounter within the treatment window (default 30 days)</li>
     * </ol>
     *
     * @param providerId the provider identifier
     * @param patientId the patient identifier
     * @return Promise containing true if treatment relationship exists
     */
    public Promise<Boolean> hasActiveTreatmentRelationship(String providerId, String patientId) {
        ensureRunning();

        String sanitizedProviderId = PhrInputSanitizationUtils.requireSafeIdentifier(providerId, "providerId");
        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");

        // Check active encounters first
        return hasActiveEncounter(sanitizedProviderId, sanitizedPatientId)
            .then(hasEncounter -> {
                if (hasEncounter) {
                    return Promise.of(true);
                }
                // Check recent encounters within treatment window
                return hasRecentEncounter(sanitizedProviderId, sanitizedPatientId)
                    .then(hasRecent -> {
                        if (hasRecent) {
                            return Promise.of(true);
                        }
                        // Check same facility assignment
                        return hasSameFacilityAssignment(sanitizedProviderId, sanitizedPatientId);
                    });
            });
    }

    /**
     * Records a patient's facility assignment.
     *
     * @param patientId the patient identifier
     * @param facilityId the facility identifier
     * @return Promise completing when assignment is recorded
     */
    public Promise<Void> assignPatientToFacility(String patientId, String facilityId) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedFacilityId = PhrInputSanitizationUtils.requireSafeIdentifier(facilityId, "facilityId");

        String id = generateId("pfa");
        PatientFacilityAssignment assignment = new PatientFacilityAssignment(
            id,
            sanitizedPatientId,
            sanitizedFacilityId,
            Instant.now()
        );

        return createRecord(
            PATIENT_FACILITY_DATASET,
            id,
            assignment,
            mutationMetadata(Map.of(
                "patientId", assignment.patientId(),
                "facilityId", assignment.facilityId()
            ), assignment.patientId()),
            "PatientFacilityAssignment",
            1
        ).map($ -> null);
    }

    /**
     * Records a provider's facility assignment.
     *
     * @param providerId the provider identifier
     * @param facilityId the facility identifier
     * @return Promise completing when assignment is recorded
     */
    public Promise<Void> assignProviderToFacility(String providerId, String facilityId) {
        ensureRunning();

        String sanitizedProviderId = PhrInputSanitizationUtils.requireSafeIdentifier(providerId, "providerId");
        String sanitizedFacilityId = PhrInputSanitizationUtils.requireSafeIdentifier(facilityId, "facilityId");

        String id = generateId("prfa");
        ProviderFacilityAssignment assignment = new ProviderFacilityAssignment(
            id,
            sanitizedProviderId,
            sanitizedFacilityId,
            Instant.now()
        );

        return createRecord(
            PROVIDER_FACILITY_DATASET,
            id,
            assignment,
            mutationMetadata(Map.of(
                "providerId", assignment.providerId(),
                "facilityId", assignment.facilityId()
            ), assignment.providerId()),
            "ProviderFacilityAssignment",
            1
        ).map($ -> null);
    }

    // ==================== Private Helpers ====================

    private Promise<Boolean> hasActiveEncounter(String providerId, String patientId) {
        // Direct dataset query for active encounters
        return queryRecords(
            ENCOUNTER_DATASET,
            "patientId = :patientId AND participant = :providerId AND status = :status",
            Map.of("patientId", patientId, "providerId", providerId, "status", "IN_PROGRESS"),
            1,
            0,
            EncounterRecord.class
        ).map(encounters -> !encounters.isEmpty());
    }

    private Promise<Boolean> hasRecentEncounter(String providerId, String patientId) {
        Instant cutoff = Instant.now().minus(TREATMENT_WINDOW);

        // Direct dataset query for recent encounters
        return queryRecords(
            ENCOUNTER_DATASET,
            "patientId = :patientId AND participant = :providerId AND createdAt >= :cutoff",
            Map.of("patientId", patientId, "providerId", providerId, "cutoff", cutoff.toString()),
            1,
            0,
            EncounterRecord.class
        ).map(encounters -> !encounters.isEmpty());
    }

    private Promise<Boolean> hasSameFacilityAssignment(String providerId, String patientId) {
        // Get patient's current facility
        return queryRecords(
            PATIENT_FACILITY_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1,
            0,
            PatientFacilityAssignment.class
        ).then(patientAssignments -> {
            if (patientAssignments.isEmpty()) {
                return Promise.of(false);
            }
            String patientFacilityId = patientAssignments.get(0).facilityId();

            // Get provider's current facility
            return queryRecords(
                PROVIDER_FACILITY_DATASET,
                "providerId = :providerId",
                Map.of("providerId", providerId),
                1,
                0,
                ProviderFacilityAssignment.class
            ).map(providerAssignments -> providerAssignments.stream()
                .anyMatch(pa -> pa.facilityId().equals(patientFacilityId)));
        });
    }

    // ==================== Inner Types ====================

    record PatientFacilityAssignment(
            String id,
            String patientId,
            String facilityId,
            Instant assignedAt
    ) {}

    record ProviderFacilityAssignment(
            String id,
            String providerId,
            String facilityId,
            Instant assignedAt
    ) {}

    record EncounterRecord(
            String encounterId,
            String patientId,
            String participant,
            String location,
            String status,
            String createdAt,
            String completedAt
    ) {}
}
