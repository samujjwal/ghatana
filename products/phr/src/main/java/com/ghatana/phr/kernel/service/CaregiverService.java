package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
public class CaregiverService implements KernelLifecycleAware {

    private static final String RELATIONSHIP_DATASET = "phr.caregiver.relationships";
    private static final String AUDIT_DATASET         = "phr.caregiver.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a CaregiverService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public CaregiverService(KernelContext context) {
        this.dataCloud = context.getDependency(DataCloudKernelAdapter.class);
    }

    /** Starts the service and initializes backing datasets. */
    public Promise<Void> start() {
        running = true;
        return initializeDatasets();
    }

    /** Stops the service. */
    public Promise<Void> stop() {
        running = false;
        return Promise.complete();
    }

    /** Returns {@code true} when the service is running. */
    public boolean isHealthy() {
        return running;
    }

    /** Returns the logical service name. */
    public String getName() {
        return "caregiver";
    }

    // ==================== Core Operations ====================

    /**
     * Creates a caregiver–patient relationship.
     *
     * @param relationship the relationship to create (id may be null)
     * @return Promise containing the stored relationship
     */
    public Promise<CaregiverRelationship> createRelationship(CaregiverRelationship relationship) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(relationship.caregiverId(), "caregiverId");
        Objects.requireNonNull(relationship.patientId(), "patientId");
        Objects.requireNonNull(relationship.relationshipType(), "relationshipType");

        String id = relationship.id() != null ? relationship.id() : generateId("cgv");
        CaregiverRelationship toStore = new CaregiverRelationship(
                id,
                relationship.caregiverId(),
                relationship.patientId(),
                relationship.relationshipType(),
                relationship.consentScope() != null ? relationship.consentScope() : Set.of(),
                RelationshipStatus.ACTIVE,
                Instant.now(),
                relationship.expiresAt()
        );

        DataWriteRequest req = new DataWriteRequest(
                RELATIONSHIP_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "CaregiverRelationship", 1),
                Map.of("caregiverId", toStore.caregiverId(),
                        "patientId", toStore.patientId(),
                        "status", "ACTIVE")
        );

        return dataCloud.writeData(req)
                .then($ -> audit("CREATE_RELATIONSHIP", toStore.patientId(),
                        "Caregiver " + toStore.caregiverId() + " linked as "
                                + toStore.relationshipType()))
                .map($ -> toStore);
    }

    /**
     * Revokes a caregiver relationship immediately.
     *
     * @param relationshipId the relationship identifier
     * @param revokedBy      the identity revoking the relationship
     * @return Promise containing the revoked relationship
     */
    public Promise<CaregiverRelationship> revokeRelationship(String relationshipId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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
                    DataWriteRequest req = new DataWriteRequest(
                            RELATIONSHIP_DATASET, relationshipId,
                            TypedDataSerializer.toBytes(revoked, "CaregiverRelationship", 1),
                            Map.of("status", "REVOKED")
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("REVOKE_RELATIONSHIP", revoked.patientId(),
                                    "Relationship revoked"))
                            .map($ -> revoked);
                });
    }

    /**
     * Retrieves a caregiver relationship by ID.
     *
     * @param relationshipId the relationship identifier
     * @return Promise containing the relationship if found
     */
    public Promise<Optional<CaregiverRelationship>> getRelationship(String relationshipId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(RELATIONSHIP_DATASET, relationshipId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable(
                            TypedDataSerializer.fromBytes(result.getData(), CaregiverRelationship.class));
                })
                ;
    }

    /**
     * Returns all active caregivers for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of active caregiver relationships
     */
    public Promise<List<CaregiverRelationship>> getActiveCaregiversForPatient(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                RELATIONSHIP_DATASET,
                "patientId = :patientId AND status = :status",
                Map.of("patientId", patientId, "status", "ACTIVE"),
                500, 0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), CaregiverRelationship.class))
                        .filter(Objects::nonNull)
                        .filter(rel -> rel.status() == RelationshipStatus.ACTIVE)
                        .filter(rel -> rel.expiresAt() == null || rel.expiresAt().isAfter(Instant.now()))
                        .toList());
    }

    /**
     * Returns all active patients a caregiver is responsible for.
     *
     * @param caregiverId the caregiver identifier
     * @return Promise containing the list of active relationships
     */
    public Promise<List<CaregiverRelationship>> getPatientsForCaregiver(String caregiverId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                RELATIONSHIP_DATASET,
                "caregiverId = :caregiverId AND status = :status",
                Map.of("caregiverId", caregiverId, "status", "ACTIVE"),
                500, 0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), CaregiverRelationship.class))
                        .filter(Objects::nonNull)
                        .filter(rel -> rel.status() == RelationshipStatus.ACTIVE)
                        .filter(rel -> rel.expiresAt() == null || rel.expiresAt().isAfter(Instant.now()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> relationships = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                RELATIONSHIP_DATASET,
                Map.of("id", "string", "caregiverId", "string", "patientId", "string",
                        "status", "string", "createdAt", "timestamp"),
                Map.of("retention", "10years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "10years")
        ));

        return Promises.all(relationships, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "CaregiverAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        ));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

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
