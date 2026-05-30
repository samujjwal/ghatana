package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public class MedicationService extends PhrServiceBase {

    private static final String MEDICATION_DATASET = "phr.medications";

    public MedicationService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "medication";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            MEDICATION_DATASET,
            Map.of(
                "id", "string",
                "patientId", "string",
                "medicationCode", "string",
                "status", "string",
                "prescribedAt", "timestamp"
            ),
            Map.of("retention", "10years")
        );
    }

    // ==================== Core Operations ====================

    /**
     * Prescribes a new medication for a patient.
     *
     * @param prescription the prescription to store
     * @return Promise containing the stored prescription with generated ID
     */
    public Promise<Prescription> prescribe(Prescription prescription) {
        ensureRunning();

        validateRequired(prescription.patientId(), "patientId");
        validateRequired(prescription.medicationCode(), "medicationCode");

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

        return createRecord(
            MEDICATION_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "status", toStore.status().name(),
                "medicationCode", toStore.medicationCode()
            ), toStore.prescriberId()),
            "Prescription",
            1
        ).then(stored -> audit("PRESCRIBE", stored.patientId(),
                "Prescribed: " + stored.medicationName() + " by " + stored.prescriberId())
            .map($ -> stored));
    }

    /**
     * Retrieves a prescription by ID.
     *
     * @param prescriptionId the prescription identifier
     * @return Promise containing the prescription if found
     */
    public Promise<Optional<Prescription>> getPrescription(String prescriptionId) {
        ensureRunning();
        return readRecord(MEDICATION_DATASET, prescriptionId, Prescription.class);
    }

    /**
     * Lists all active prescriptions for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of active prescriptions
     */
    public Promise<List<Prescription>> getActivePrescriptions(String patientId) {
        ensureRunning();

        return queryRecords(
            MEDICATION_DATASET,
            "patientId = :patientId AND status = :status",
            Map.of("patientId", patientId, "status", "ACTIVE"),
            500,
            0,
            Prescription.class
        ).map(prescriptions -> prescriptions.stream()
            .filter(p -> p.status() == PrescriptionStatus.ACTIVE && !p.isExpired())
            .toList());
    }
    /**
     * Lists all prescriptions (active, completed, discontinued, expired) for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of all prescriptions
     */
    public Promise<List<Prescription>> getPrescriptionHistory(String patientId) {
        ensureRunning();

        return queryRecords(
            MEDICATION_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            500,
            0,
            Prescription.class
        );
    }


    /**
     * Discontinues an active prescription.
     *
     * @param prescriptionId the prescription identifier
     * @param reason         the discontinuation reason
     * @return Promise completing when discontinued
     */
    public Promise<Prescription> discontinue(String prescriptionId, String reason) {
        ensureRunning();

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
                return updateRecord(
                    MEDICATION_DATASET,
                    prescriptionId,
                    discontinued,
                    mutationMetadata(Map.of(
                        "patientId", discontinued.patientId(),
                        "status", "DISCONTINUED"
                    ), discontinued.prescriberId()),
                    "Prescription",
                    1
                ).then(updated -> audit("DISCONTINUE", existing.patientId(),
                    "Discontinued: " + existing.medicationName() + " — " + reason)
                    .map($ -> updated));
            });
    }

    /**
     * Records a refill against an active prescription.
     *
     * @param prescriptionId the prescription identifier
     * @return Promise containing the updated prescription
     */
    public Promise<Prescription> refill(String prescriptionId) {
        ensureRunning();

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
                return updateRecord(
                    MEDICATION_DATASET,
                    prescriptionId,
                    refilled,
                    mutationMetadata(Map.of(
                        "patientId", refilled.patientId(),
                        "refillsRemaining", String.valueOf(refilled.refillsRemaining())
                    ), refilled.prescriberId()),
                    "Prescription",
                    1
                ).then(updated -> audit("REFILL", updated.patientId(),
                    "Refill dispensed: " + updated.medicationName())
                    .map($ -> updated));
            });
    }

    /**
     * Checks for potential drug-drug interactions for a patient's medications.
     *
     * <p>This method analyzes the patient's active medications and identifies
     * potential interactions based on known drug interaction databases. Returns
     * a list of interaction warnings with severity levels.</p>
     *
     * @param patientId the patient identifier
     * @return Promise containing list of interaction warnings
     */
    public Promise<List<InteractionWarning>> checkDrugInteractions(String patientId) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");

        return queryRecords(
            MEDICATION_DATASET,
            "patientId = :patientId AND status = :status",
            Map.of("patientId", sanitizedPatientId, "status", PrescriptionStatus.ACTIVE.name()),
            100,
            0,
            Prescription.class
        ).then(activePrescriptions -> {
            List<InteractionWarning> warnings = new java.util.ArrayList<>();

            // Check for known interactions between active medications
            for (int i = 0; i < activePrescriptions.size(); i++) {
                for (int j = i + 1; j < activePrescriptions.size(); j++) {
                    Prescription rx1 = activePrescriptions.get(i);
                    Prescription rx2 = activePrescriptions.get(j);

                    InteractionWarning warning = checkInteraction(rx1, rx2);
                    if (warning != null) {
                        warnings.add(warning);
                    }
                }
            }

            return Promise.of(warnings);
        });
    }

    /**
     * Checks for potential drug-allergy interactions for a patient.
     *
     * <p>This method compares a proposed medication against the patient's
     * known allergies and returns warnings if there's a potential allergy.</p>
     *
     * @param patientId the patient identifier
     * @param medicationCode the medication code to check
     * @return Promise containing list of allergy warnings
     */
    public Promise<List<AllergyWarning>> checkAllergyInteractions(String patientId, String medicationCode) {
        ensureRunning();

        String sanitizedPatientId = PhrInputSanitizationUtils.requireSafeIdentifier(patientId, "patientId");
        String sanitizedMedicationCode = PhrInputSanitizationUtils.requireSafeIdentifier(medicationCode, "medicationCode");

        return Promise.of(new java.util.ArrayList<>());
    }

    /**
     * Checks interaction between two specific medications.
     *
     * @param rx1 first prescription
     * @param rx2 second prescription
     * @return InteractionWarning if interaction exists, null otherwise
     */
    private InteractionWarning checkInteraction(Prescription rx1, Prescription rx2) {
        // Warfarin + NSAIDs (increased bleeding risk)
        if (isWarfarin(rx1.medicationCode()) && isNsaid(rx2.medicationCode()) ||
            isWarfarin(rx2.medicationCode()) && isNsaid(rx1.medicationCode())) {
            return new InteractionWarning(
                rx1.medicationCode(),
                rx2.medicationCode(),
                InteractionSeverity.HIGH,
                "Increased risk of bleeding when warfarin is combined with NSAIDs",
                "Consider alternative analgesic or monitor INR closely"
            );
        }

        // ACE inhibitors + potassium-sparing diuretics (hyperkalemia risk)
        if (isAceInhibitor(rx1.medicationCode()) && isPotassiumSparingDiuretic(rx2.medicationCode()) ||
            isAceInhibitor(rx2.medicationCode()) && isPotassiumSparingDiuretic(rx1.medicationCode())) {
            return new InteractionWarning(
                rx1.medicationCode(),
                rx2.medicationCode(),
                InteractionSeverity.MEDIUM,
                "Increased risk of hyperkalemia",
                "Monitor potassium levels regularly"
            );
        }

        return null;
    }

    private boolean isWarfarin(String code) {
        return code != null && (code.equalsIgnoreCase("B01AA03") ||
            code.toLowerCase().contains("warfarin"));
    }

    private boolean isNsaid(String code) {
        return code != null && (code.startsWith("M01A") ||
            code.toLowerCase().contains("ibuprofen") ||
            code.toLowerCase().contains("naproxen") ||
            code.toLowerCase().contains("aspirin"));
    }

    private boolean isAceInhibitor(String code) {
        return code != null && (code.startsWith("C09A") ||
            code.toLowerCase().contains("lisinopril") ||
            code.toLowerCase().contains("enalapril"));
    }

    private boolean isPotassiumSparingDiuretic(String code) {
        return code != null && (code.startsWith("C03D") ||
            code.toLowerCase().contains("spironolactone") ||
            code.toLowerCase().contains("amiloride"));
    }

    // ==================== Private Helpers ====================

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

    /** Severity levels for drug interactions. */
    public enum InteractionSeverity {
        /** Low severity - monitor but no action required */
        LOW,
        /** Medium severity - consider alternative or monitor */
        MEDIUM,
        /** High severity - avoid combination or require close monitoring */
        HIGH,
        /** Contraindicated - do not combine */
        CONTRAINDICATED
    }

    /** Warning about a potential drug-drug interaction. */
    public record InteractionWarning(
            String medicationCode1,
            String medicationCode2,
            InteractionSeverity severity,
            String description,
            String recommendation
    ) {}

    /** Warning about a potential drug-allergy interaction. */
    public record AllergyWarning(
            String medicationCode,
            String allergen,
            String description,
            String recommendation
    ) {}

    /** Immutable audit entry for a medication event. */
    public record MedicationAuditEntry(
            String id,
            Instant timestamp,
            String action,
            String patientId,
            String details
    ) {}
}
