package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataQueryRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataReadRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.DataWriteRequest;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter.QueryResult;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.util.TypedDataSerializer;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Referral Management Service for PHR.
 *
 * <p>Manages provider-to-provider patient referrals including specialist referrals,
 * hospital-to-hospital transfers, and referral acceptance workflows. Tracks referral
 * lifecycle from creation through acceptance, consultation, and closure. Complies with
 * Nepal's Health Referral System guidelines.</p>
 *
 * @doc.type class
 * @doc.purpose PHR referral management — specialist referrals with lifecycle tracking
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ReferralService {

    private static final String REFERRAL_DATASET = "phr.referrals";
    private static final String AUDIT_DATASET = "phr.referral.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a ReferralService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public ReferralService(KernelContext context) {
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
        return "referral";
    }

    // ==================== Core Operations ====================

    /**
     * Creates a new patient referral.
     *
     * @param referral the referral to create
     * @return Promise containing the stored referral with generated ID
     */
    public Promise<Referral> createReferral(Referral referral) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(referral.patientId(), "patientId");
        Objects.requireNonNull(referral.referringProviderId(), "referringProviderId");
        Objects.requireNonNull(referral.specialtyCode(), "specialtyCode");

        String id = referral.id() != null ? referral.id() : generateId("ref");
        Referral toStore = new Referral(
                id,
                referral.patientId(),
                referral.encounterId(),
                referral.referringProviderId(),
                referral.receivingProviderId(),
                referral.specialtyCode(),
                referral.clinicalReason(),
                referral.urgency(),
                ReferralStatus.PENDING,
                Instant.now(),
                null,
                null
        );

        DataWriteRequest request = new DataWriteRequest(
                REFERRAL_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "Referral", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "status", toStore.status().name(),
                        "urgency", toStore.urgency().name()
                )
        );

        return dataCloud.writeData(request)
                .then($ -> audit("CREATE_REFERRAL", toStore.patientId(),
                        "Referral created to specialty: " + toStore.specialtyCode()
                                + " [" + toStore.urgency() + "]"))
                .map($ -> toStore);
    }

    /**
     * Accepts a referral (confirms the receiving provider will see the patient).
     *
     * @param referralId         the referral identifier
     * @param acceptingProviderId the provider accepting the referral
     * @return Promise containing the updated referral
     */
    public Promise<Referral> acceptReferral(String referralId, String acceptingProviderId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getReferral(referralId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<Referral>ofException(
                                new IllegalStateException("Referral not found: " + referralId));
                    }
                    Referral existing = opt.get();
                    if (existing.status() != ReferralStatus.PENDING) {
                        return Promise.<Referral>ofException(
                                new IllegalStateException("Cannot accept referral in status: " + existing.status()));
                    }
                    Referral accepted = new Referral(
                            existing.id(), existing.patientId(), existing.encounterId(),
                            existing.referringProviderId(), acceptingProviderId, existing.specialtyCode(),
                            existing.clinicalReason(), existing.urgency(),
                            ReferralStatus.ACCEPTED, existing.createdAt(), Instant.now(), null
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            REFERRAL_DATASET, referralId,
                            TypedDataSerializer.toBytes(accepted, "Referral", 1),
                            Map.of("status", "ACCEPTED")
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("ACCEPT_REFERRAL", accepted.patientId(),
                                    "Referral accepted by: " + acceptingProviderId))
                            .map($ -> accepted);
                });
    }

    /**
     * Closes a referral after the consultation is complete.
     *
     * @param referralId   the referral identifier
     * @param closureNotes summary of the consultation outcome
     * @return Promise containing the closed referral
     */
    public Promise<Referral> closeReferral(String referralId, String closureNotes) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getReferral(referralId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<Referral>ofException(
                                new IllegalStateException("Referral not found: " + referralId));
                    }
                    Referral existing = opt.get();
                    Referral closed = new Referral(
                            existing.id(), existing.patientId(), existing.encounterId(),
                            existing.referringProviderId(), existing.receivingProviderId(), existing.specialtyCode(),
                            existing.clinicalReason(), existing.urgency(),
                            ReferralStatus.COMPLETED, existing.createdAt(), existing.acceptedAt(), Instant.now()
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            REFERRAL_DATASET, referralId,
                            TypedDataSerializer.toBytes(closed, "Referral", 1),
                            Map.of("status", "COMPLETED")
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("CLOSE_REFERRAL", closed.patientId(), "Referral closed"))
                            .map($ -> closed);
                });
    }

    /**
     * Retrieves a referral by ID.
     *
     * @param referralId the referral identifier
     * @return Promise containing the referral if found
     */
    public Promise<Optional<Referral>> getReferral(String referralId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(REFERRAL_DATASET, referralId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((Referral) TypedDataSerializer.fromBytes(result.getData(), Referral.class));
                })
                ;
    }

    /**
     * Returns all referrals for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all referrals in reverse chronological order
     */
    public Promise<List<Referral>> getPatientReferrals(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                REFERRAL_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                500,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), Referral.class))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> referrals = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                REFERRAL_DATASET,
                Map.of("id", "string", "patientId", "string", "status", "string",
                        "specialtyCode", "string", "createdAt", "timestamp"),
                Map.of("retention", "10years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(referrals, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "ReferralAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        ));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * A patient referral record.
     *
     * @param id                    unique referral identifier
     * @param patientId             patient being referred
     * @param referringProviderId   the provider initiating the referral
     * @param receivingProviderId   the provider receiving the referral (may be null until accepted)
     * @param specialtyCode         target specialty (e.g. "CARDIOLOGY", "ORTHOPEDICS")
     * @param urgency               urgency level
     * @param clinicalReason        clinical reason for the referral
     * @param notes                 additional notes or closure summary
     * @param status                referral lifecycle status
     * @param createdAt             when the referral was created
     * @param acceptedAt            when the referral was accepted (null if pending)
     * @param closedAt              when the referral was closed (null if open)
     */
    public record Referral(
            String id,
            String patientId,
            String encounterId,
            String referringProviderId,
            String receivingProviderId,
            String specialtyCode,
            String clinicalReason,
            ReferralUrgency urgency,
            ReferralStatus status,
            Instant createdAt,
            Instant acceptedAt,
            Instant closedAt
    ) {}

    /** Urgency classification for a referral. */
    public enum ReferralUrgency {
        /** Routine, non-urgent referral. */
        ROUTINE,
        /** Urgent referral requiring prompt attention. */
        URGENT,
        /** Emergency referral requiring immediate action. */
        EMERGENCY
    }

    /** Lifecycle status for a referral. */
    public enum ReferralStatus {
        PENDING, ACCEPTED, DECLINED, COMPLETED, CANCELLED
    }
}
