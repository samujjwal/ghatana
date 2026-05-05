package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Caregiver Relationship Service for PHR.
 *
 * <p>Manages caregiver–patient relationships including delegation of consent
 * access scope, relationship type, and expiry. A caregiver can view or act on
 * specific PHR data for a patient within the delegated scope. Creation of a
 * caregiver relationship does NOT automatically grant consent access; callers
 * must invoke {@code ConsentManagementService.grantAccess()} separately with the
 * scope defined here as guidance.</p>
 *
 * @doc.type class
 * @doc.purpose PHR caregiver–patient relationship management with consent scoping
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class CaregiverService extends PhrServiceBase {

    private static final String RELATIONSHIP_DATASET = "phr.caregiver.relationships";

    public CaregiverService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "caregiver";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            RELATIONSHIP_DATASET,
            Map.of("id", "string", "caregiverId", "string", "patientId", "string",
                "status", "string", "createdAt", "timestamp"),
            Map.of("retention", "10years")
        );
    }

    // ==================== Core Operations ====================

    /**
     * Creates a caregiver–patient relationship.
     *
     * @param relationship the relationship to create (id may be null)
     * @return Promise containing the stored relationship
     */
    public Promise<CaregiverRelationship> createRelationship(CaregiverRelationship relationship) {
        ensureRunning();

        String caregiverId = PhrInputSanitizationUtils.requireSafeIdentifier(relationship.caregiverId(), "caregiverId");
        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(relationship.patientId(), "patientId");
        if (relationship.relationshipType() == null) {
            return Promise.ofException(new IllegalArgumentException("relationshipType is required"));
        }
        Set<String> sanitizedScope = relationship.consentScope() == null
            ? Set.of()
            : relationship.consentScope().stream()
                .map(scope -> PhrInputSanitizationUtils.requireSafeCode(scope, "consentScope"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        String id = relationship.id() != null ? relationship.id() : generateId("cgv");
        CaregiverRelationship toStore = new CaregiverRelationship(
            id,
            caregiverId,
            patientId,
            relationship.relationshipType(),
            sanitizedScope,
            RelationshipStatus.ACTIVE,
            Instant.now(),
            relationship.expiresAt()
        );

        return createRecord(
            RELATIONSHIP_DATASET,
            id,
            toStore,
            Map.of("caregiverId", toStore.caregiverId(),
                "patientId", toStore.patientId(),
                "status", "ACTIVE"),
            "CaregiverRelationship",
            1
        ).then(stored -> audit("CREATE_RELATIONSHIP", stored.patientId(),
            "Caregiver " + stored.caregiverId() + " linked as " + stored.relationshipType())
            .map($ -> stored));
    }

    public Promise<CaregiverRelationship> revokeRelationship(String relationshipId) {
        ensureRunning();

        return getRelationship(relationshipId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<CaregiverRelationship>ofException(
                        new IllegalStateException("Relationship not found: " + relationshipId));
                }
                CaregiverRelationship existing = opt.get();
                if (existing.status() == RelationshipStatus.REVOKED) {
                    return Promise.<CaregiverRelationship>ofException(
                        new IllegalStateException("Relationship already revoked: " + relationshipId));
                }
                CaregiverRelationship revoked = new CaregiverRelationship(
                    existing.id(), existing.caregiverId(), existing.patientId(),
                    existing.relationshipType(), existing.consentScope(),
                    RelationshipStatus.REVOKED, existing.createdAt(), Instant.now()
                );
                return updateRecord(
                    RELATIONSHIP_DATASET,
                    relationshipId,
                    revoked,
                    Map.of("status", "REVOKED"),
                    "CaregiverRelationship",
                    1
                ).then(updated -> audit("REVOKE_RELATIONSHIP", updated.patientId(),
                    "Relationship revoked")
                    .map($ -> updated));
            });
    }

    public Promise<Optional<CaregiverRelationship>> getRelationship(String relationshipId) {
        ensureRunning();
        return readRecord(RELATIONSHIP_DATASET, relationshipId, CaregiverRelationship.class);
    }

    public Promise<List<CaregiverRelationship>> getActiveCaregiversForPatient(String patientId) {
        ensureRunning();

        return queryRecords(
            RELATIONSHIP_DATASET,
            "patientId = :patientId AND status = :status",
            Map.of("patientId", patientId, "status", "ACTIVE"),
            500,
            0,
            CaregiverRelationship.class
        ).map(relationships -> relationships.stream()
            .filter(rel -> rel.status() == RelationshipStatus.ACTIVE)
            .filter(rel -> rel.expiresAt() == null || rel.expiresAt().isAfter(Instant.now()))
            .toList());
    }

    public Promise<List<CaregiverRelationship>> getPatientsForCaregiver(String caregiverId) {
        ensureRunning();

        return queryRecords(
            RELATIONSHIP_DATASET,
            "caregiverId = :caregiverId AND status = :status",
            Map.of("caregiverId", caregiverId, "status", "ACTIVE"),
            500,
            0,
            CaregiverRelationship.class
        ).map(relationships -> relationships.stream()
            .filter(rel -> rel.status() == RelationshipStatus.ACTIVE)
            .filter(rel -> rel.expiresAt() == null || rel.expiresAt().isAfter(Instant.now()))
            .toList());
    }

    // ==================== Private Helpers ====================

    // ==================== Inner Types ====================

    /**
     * A caregiver–patient relationship record.
     *
     * @param id               unique relationship identifier
     * @param caregiverId      caregiver user identifier
     * @param patientId        patient user identifier
     * @param relationshipType nature of the relationship
     * @param consentScope     set of PHR resource types the caregiver may access
     *                         (empty = no access delegation; use ConsentManagementService for actual access)
     * @param status           relationship status
     * @param createdAt        when the relationship was created
     * @param expiresAt        optional expiry (null = perpetual until revoked)
     */
    public record CaregiverRelationship(
            String id,
            String caregiverId,
            String patientId,
            RelationshipType relationshipType,
            Set<String> consentScope,
            RelationshipStatus status,
            Instant createdAt,
            Instant expiresAt
    ) {
        /** Returns true if the relationship has not expired. */
        public boolean isActive() {
            return status == RelationshipStatus.ACTIVE
                    && (expiresAt == null || expiresAt.isAfter(Instant.now()));
        }
    }

    /** Type of caregiver–patient relationship. */
    public enum RelationshipType {
        PARENT,
        SPOUSE,
        SIBLING,
        LEGAL_GUARDIAN,
        PROFESSIONAL_CAREGIVER,
        POWER_OF_ATTORNEY,
        OTHER
    }

    /** Lifecycle status of a caregiver relationship. */
    public enum RelationshipStatus {
        ACTIVE, SUSPENDED, REVOKED, EXPIRED
    }
}
