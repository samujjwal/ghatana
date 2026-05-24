package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immunization Records Service for PHR.
 *
 * <p>Tracks patient vaccination history (WHO-standard CVX codes), due-date scheduling,
 * adverse event reporting, and national immunization programme (NIP) compliance as
 * required by Nepal's Child Vaccination Programme and adult booster schedules.</p>
 *
 * @doc.type class
 * @doc.purpose PHR immunization tracking — CVX-coded vaccinations with schedule management
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ImmunizationService extends PhrServiceBase {

    private static final String IMMUNIZATION_DATASET = "phr.immunizations";
    private static final String SCHEDULE_DATASET = "phr.immunization.schedules";

    public ImmunizationService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "immunization";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> imm = createSchema(
            IMMUNIZATION_DATASET,
            Map.of("id", "string", "patientId", "string", "cvxCode", "string",
                "administeredAt", "timestamp", "status", "string"),
            Map.of("retention", "permanent")
        );

        Promise<Void> sched = createSchema(
            SCHEDULE_DATASET,
            Map.of("id", "string", "patientId", "string", "dueDate", "date"),
            Map.of("retention", "10years")
        );

        return imm.then($ -> sched);
    }

    // ==================== Core Operations ====================

    /**
     * Records an administered vaccine dose.
     *
     * @param immunization the immunization record to persist
     * @return Promise containing the stored record with generated ID
     */
    public Promise<ImmunizationRecord> recordImmunization(ImmunizationRecord immunization) {
        ensureRunning();

        validateRequired(immunization.patientId(), "patientId");
        validateRequired(immunization.cvxCode(), "cvxCode");

        String id = immunization.id() != null ? immunization.id() : generateId("imm");
        ImmunizationRecord toStore = new ImmunizationRecord(
            id,
            immunization.patientId(),
            immunization.encounterId(),
            immunization.cvxCode(),
            immunization.vaccineName(),
            immunization.administeredBy(),
            immunization.administeredAt() != null ? immunization.administeredAt() : Instant.now(),
            Instant.now(),
            immunization.lotNumber(),
            immunization.expiresAt(),
            immunization.route(),
            immunization.seriesName(),
            immunization.doseNumber(),
            immunization.adverseEvent(),
            immunization.notes(),
            ImmunizationStatus.ADMINISTERED
        );

        return createRecord(
            IMMUNIZATION_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "cvxCode", toStore.cvxCode(),
                "status", toStore.status().name()
            ), toStore.administeredBy()),
            "ImmunizationRecord",
            1
        ).then(stored -> audit("ADMINISTER", stored.patientId(),
            "Vaccine administered: " + stored.vaccineName())
            .map($ -> stored));
    }

    /**
     * Returns all immunizations for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the full immunization history
     */
    public Promise<List<ImmunizationRecord>> getImmunizationHistory(String patientId) {
        ensureRunning();

        return queryRecords(
            IMMUNIZATION_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            ImmunizationRecord.class
        );
    }

    public Promise<Optional<ImmunizationRecord>> getImmunization(String immunizationId) {
        ensureRunning();
        return readRecord(IMMUNIZATION_DATASET, immunizationId, ImmunizationRecord.class);
    }

    public Promise<VaccinationSchedule> createSchedule(VaccinationSchedule schedule) {
        ensureRunning();

        String id = schedule.id() != null ? schedule.id() : generateId("schd");
        VaccinationSchedule toStore = new VaccinationSchedule(
            id,
            schedule.patientId(),
            schedule.cvxCode(),
            schedule.vaccineName(),
            schedule.seriesName(),
            schedule.doseNumber(),
            schedule.dueDate(),
            ScheduleStatus.PENDING,
            schedule.notes()
        );

        return createRecord(
            SCHEDULE_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "dueDate", toStore.dueDate().toString(),
                "status", toStore.status().name()
            ), "system"),
            "VaccinationSchedule",
            1
        ).map($ -> toStore);
    }

    public Promise<List<VaccinationSchedule>> getDueSchedules(String patientId) {
        ensureRunning();

        return queryRecords(
            SCHEDULE_DATASET,
            "patientId = :patientId AND status = :status",
            Map.of("patientId", patientId, "status", "PENDING"),
            500,
            0,
            VaccinationSchedule.class
        ).map(schedules -> schedules.stream()
            .filter(s -> s.status() == ScheduleStatus.PENDING)
            .sorted((a, b) -> a.dueDate().compareTo(b.dueDate()))
            .toList());
    }

    // ==================== Private Helpers ====================

    // ==================== Inner Types ====================

    /**
     * A single administered vaccine dose.
     *
     * @param id                unique identifier
     * @param patientId         patient who received the vaccine
     * @param administeredBy    provider or clinic identifier
     * @param cvxCode           CVX vaccine code (CDC standard)
     * @param vaccineName       human-readable vaccine name (e.g. "BCG", "OPV", "Hepatitis B")
     * @param lotNumber         vaccine lot number for traceability
     * @param manufacturer      vaccine manufacturer name
     * @param administeredAt    date and time of administration
     * @param administrationSite body site (e.g. "left deltoid")
     * @param route             route of administration (e.g. "intramuscular", "oral")
     * @param doseQuantity      dose quantity with unit (e.g. "0.5 mL")
     * @param doseNumber        dose number in the series (1-based)
     * @param seriesTotal       total doses required to complete the series
     * @param status            current status of this record
     * @param notes             free-text clinical notes
     */
    public record ImmunizationRecord(
            String id,
            String patientId,
            String encounterId,
            String cvxCode,
            String vaccineName,
            String administeredBy,
            Instant administeredAt,
            Instant recordedAt,
            String lotNumber,
            LocalDate expiresAt,
            String route,
            String seriesName,
            int doseNumber,
            boolean adverseEvent,
            String notes,
            ImmunizationStatus status
    ) {}

    /**
     * A scheduled future vaccination dose.
     *
     * @param id          unique schedule identifier
     * @param patientId   patient for whom the dose is scheduled
     * @param cvxCode     CVX vaccine code
     * @param vaccineName human-readable vaccine name
     * @param dueDate     date the dose is due
     * @param doseNumber  dose number in the series
     * @param seriesTotal total doses in the series
     * @param status      schedule status
     * @param notes       optional reminder notes
     */
    public record VaccinationSchedule(
            String id,
            String patientId,
            String cvxCode,
            String vaccineName,
            String seriesName,
            int doseNumber,
            Instant dueDate,
            ScheduleStatus status,
            String notes
    ) {
        /** Returns {@code true} when the schedule due date is now or in the past. */
        public boolean isDue() {
            return !Instant.now().isBefore(dueDate);
        }

        /** Returns {@code true} when the schedule is overdue (past due date and still pending). */
        public boolean isOverdue() {
            return status == ScheduleStatus.PENDING && Instant.now().isAfter(dueDate);
        }
    }

    /** Lifecycle status of a recorded immunization. */
    public enum ImmunizationStatus {
        /** Vaccine was successfully administered. */
        ADMINISTERED,
        /** Vaccine was successfully completed. */
        COMPLETED,
        /** Administration was recorded in error. */
        ENTERED_IN_ERROR,
        /** Administration was not completed (patient refused, etc.). */
        NOT_DONE
    }

    /** Status of a vaccination schedule entry. */
    public enum ScheduleStatus {
        /** Dose is scheduled but not yet administered. */
        PENDING,
        /** Dose has been administered (schedule fulfilled). */
        FULFILLED,
        /** Schedule was cancelled. */
        CANCELLED
    }
}
