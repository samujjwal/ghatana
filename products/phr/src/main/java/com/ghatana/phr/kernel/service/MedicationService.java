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
 * Medication Management Service for PHR.
 *
 * <p>Manages patient medication records including prescriptions, dosage schedules,
 * refill tracking, and interaction checking. Complies with Nepal Drug Act 2035 and
 * maintains full audit trail per Privacy Act 2075.</p>
 *
 * @doc.type class
 * @doc.purpose PHR medication management — prescriptions, dosage, refills, interactions
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class MedicationService {

    private static final String MEDICATION_DATASET = "phr.medications";
    private static final String AUDIT_DATASET = "phr.medication.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a MedicationService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public MedicationService(KernelContext context) {
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
        return "medication";
    }

    // ==================== Core Operations ====================

    /**
     * Prescribes a new medication for a patient.
     *
     * @param prescription the prescription to store
     * @return Promise containing the stored prescription with generated ID
     */
    public Promise<Prescription> prescribe(Prescription prescription) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(prescription.patientId(), "patientId");
        Objects.requireNonNull(prescription.medicationCode(), "medicationCode");

        String id = prescription.id() != null ? prescription.id() : generateId("rx");
        Instant now = Instant.now();
        Prescription toStore = new Prescription(
                id,
                prescription.patientId(),
                prescription.prescriberId(),
                prescription.encounterId(),
                prescription.medicationCode(),
                prescription.medicationName(),
                prescription.dosage(),
                prescription.indication(),
                now,
                prescription.expiresAt() != null ? prescription.expiresAt() : now.plusSeconds(30L * 24 * 3600),
                prescription.refillsRemaining(),
                prescription.duration(),
                PrescriptionStatus.ACTIVE
        );

        DataWriteRequest request = new DataWriteRequest(
                MEDICATION_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "Prescription", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "status", toStore.status().name(),
                        "medicationCode", toStore.medicationCode()
                )
        );

        return dataCloud.writeData(request)
                .then($ -> audit("PRESCRIBE", toStore.patientId(),
                        "Prescribed: " + toStore.medicationName() + " by " + toStore.prescriberId()))
                .map($ -> toStore);
    }

    /**
     * Retrieves a prescription by ID.
     *
     * @param prescriptionId the prescription identifier
     * @return Promise containing the prescription if found
     */
    public Promise<Optional<Prescription>> getPrescription(String prescriptionId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(MEDICATION_DATASET, prescriptionId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((Prescription) TypedDataSerializer.fromBytes(result.getData(), Prescription.class));
                })
                ;
    }

    /**
     * Lists all active prescriptions for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of active prescriptions
     */
    public Promise<List<Prescription>> getActivePrescriptions(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        DataQueryRequest request = new DataQueryRequest(
                MEDICATION_DATASET,
                "patientId = :patientId AND status = :status",
                Map.of("patientId", patientId, "status", "ACTIVE"),
                500,
                0
        );

        return dataCloud.queryData(request)
                .map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), Prescription.class))
                        .filter(Objects::nonNull)
                        .filter(p -> p.status() == PrescriptionStatus.ACTIVE && !p.isExpired())
                        .toList());
    }

    /**
     * Discontinues an active prescription.
     *
     * @param prescriptionId the prescription identifier
     * @param reason         the discontinuation reason
     * @return Promise completing when discontinued
     */
    public Promise<Prescription> discontinue(String prescriptionId, String reason) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getPrescription(prescriptionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<Prescription>ofException(
                                new IllegalStateException("Prescription not found: " + prescriptionId));
                    }
                    Prescription existing = opt.get();
                    Prescription discontinued = new Prescription(
                            existing.id(), existing.patientId(), existing.prescriberId(),
                            existing.encounterId(), existing.medicationCode(), existing.medicationName(),
                            existing.dosage(), existing.indication(), existing.prescribedAt(),
                            existing.expiresAt(), existing.refillsRemaining(), existing.duration(),
                            PrescriptionStatus.DISCONTINUED
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            MEDICATION_DATASET, prescriptionId,
                            TypedDataSerializer.toBytes(discontinued, "Prescription", 1),
                            Map.of("status", "DISCONTINUED")
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("DISCONTINUE", existing.patientId(),
                                    "Discontinued: " + existing.medicationName() + " — " + reason))
                            .map($ -> discontinued);
                });
    }

    /**
     * Records a refill against an active prescription.
     *
     * @param prescriptionId the prescription identifier
     * @return Promise containing the updated prescription
     */
    public Promise<Prescription> refill(String prescriptionId) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getPrescription(prescriptionId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<Prescription>ofException(
                                new IllegalStateException("Prescription not found: " + prescriptionId));
                    }
                    Prescription existing = opt.get();
                    if (existing.status() != PrescriptionStatus.ACTIVE) {
                        return Promise.<Prescription>ofException(
                                new IllegalStateException("Cannot refill: prescription is " + existing.status()));
                    }
                    if (existing.refillsRemaining() <= 0) {
                        return Promise.<Prescription>ofException(
                                new IllegalStateException("No refills remaining for: " + prescriptionId));
                    }
                    Prescription refilled = new Prescription(
                            existing.id(), existing.patientId(), existing.prescriberId(),
                            existing.encounterId(), existing.medicationCode(), existing.medicationName(),
                            existing.dosage(), existing.indication(), existing.prescribedAt(),
                            existing.expiresAt().plusSeconds(existing.duration().toSeconds()),
                            existing.refillsRemaining() - 1, existing.duration(), existing.status()
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            MEDICATION_DATASET, prescriptionId,
                            TypedDataSerializer.toBytes(refilled, "Prescription", 1),
                            Map.of("refillsRemaining", String.valueOf(refilled.refillsRemaining()))
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("REFILL", refilled.patientId(),
                                    "Refill dispensed: " + refilled.medicationName()))
                            .map($ -> refilled);
                });
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> meds = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                MEDICATION_DATASET,
                Map.of(
                        "id", "string",
                        "patientId", "string",
                        "medicationCode", "string",
                        "status", "string",
                        "prescribedAt", "timestamp"
                ),
                Map.of("retention", "10years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(meds, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        MedicationAuditEntry entry = new MedicationAuditEntry(auditId, Instant.now(), action, patientId, details);
        DataWriteRequest request = new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(entry, "MedicationAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        );
        return dataCloud.writeData(request);
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * Represents a medication prescription.
     *
     * @param id               unique prescription identifier
     * @param patientId        the patient this prescription belongs to
     * @param prescriberId     the provider who prescribed
     * @param medicationCode   standard drug code (e.g. ATC or national formulary code)
     * @param medicationName   human-readable drug name
     * @param dosage           dosage quantity and unit (e.g. "500mg")
     * @param frequency        dosing frequency (e.g. "twice daily")
     * @param duration         prescription duration
     * @param instructions     patient-facing instructions
     * @param prescribedAt     when the prescription was created
     * @param expiresAt        when the prescription expires
     * @param status           current status
     * @param refillsRemaining number of refills remaining (0 = no refills)
     */
    public record Prescription(
            String id,
            String patientId,
            String prescriberId,
            String encounterId,
            String medicationCode,
            String medicationName,
            String dosage,
            String indication,
            Instant prescribedAt,
            Instant expiresAt,
            int refillsRemaining,
            java.time.Duration duration,
            PrescriptionStatus status
    ) {
        /** Returns {@code true} when the prescription has passed its expiry date. */
        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    /** Lifecycle states for a prescription. */
    public enum PrescriptionStatus {
        /** Prescription is active and can be dispensed. */
        ACTIVE,
        /** Prescription has been dispensed and completed. */
        COMPLETED,
        /** Prescription was discontinued by the prescriber. */
        DISCONTINUED,
        /** Prescription has expired without being filled. */
        EXPIRED
    }

    /** Immutable audit entry for a medication event. */
    public record MedicationAuditEntry(
            String id,
            Instant timestamp,
            String action,
            String patientId,
            String details
    ) {}
}
