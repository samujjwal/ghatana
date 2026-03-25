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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
public class ImmunizationService {

    private static final String IMMUNIZATION_DATASET = "phr.immunizations";
    private static final String SCHEDULE_DATASET = "phr.immunization.schedules";
    private static final String AUDIT_DATASET = "phr.immunization.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs an ImmunizationService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public ImmunizationService(KernelContext context) {
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
        return "immunization";
    }

    // ==================== Core Operations ====================

    /**
     * Records an administered vaccine dose.
     *
     * @param immunization the immunization record to persist
     * @return Promise containing the stored record with generated ID
     */
    public Promise<ImmunizationRecord> recordImmunization(ImmunizationRecord immunization) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(immunization.patientId(), "patientId");
        Objects.requireNonNull(immunization.cvxCode(), "cvxCode");

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

        DataWriteRequest request = new DataWriteRequest(
                IMMUNIZATION_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "ImmunizationRecord", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "cvxCode", toStore.cvxCode(),
                        "status", toStore.status().name()
                )
        );

        return dataCloud.writeData(request)
                .then($ -> audit("ADMINISTER", toStore.patientId(),
                        "Vaccine administered: " + toStore.vaccineName()))
                .map($ -> toStore);
    }

    /**
     * Returns all immunizations for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the full immunization history
     */
    public Promise<List<ImmunizationRecord>> getImmunizationHistory(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                IMMUNIZATION_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                1000,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), ImmunizationRecord.class))
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * Retrieves a specific immunization record.
     *
     * @param immunizationId the immunization record identifier
     * @return Promise containing the record if found
     */
    public Promise<Optional<ImmunizationRecord>> getImmunization(String immunizationId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(IMMUNIZATION_DATASET, immunizationId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((ImmunizationRecord) TypedDataSerializer.fromBytes(result.getData(), ImmunizationRecord.class));
                })
                ;
    }

    /**
     * Creates a vaccination schedule entry (due date for an upcoming dose).
     *
     * @param schedule the vaccination schedule to persist
     * @return Promise containing the stored schedule
     */
    public Promise<VaccinationSchedule> createSchedule(VaccinationSchedule schedule) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

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

        DataWriteRequest request = new DataWriteRequest(
                SCHEDULE_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "VaccinationSchedule", 1),
                Map.of("patientId", toStore.patientId(), "dueDate", toStore.dueDate().toString(), "status", toStore.status().name())
        );

        return dataCloud.writeData(request).map($ -> toStore);
    }

    /**
     * Returns due vaccination schedules for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing schedules ordered by due date
     */
    public Promise<List<VaccinationSchedule>> getDueSchedules(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                SCHEDULE_DATASET,
                "patientId = :patientId AND status = :status",
                Map.of("patientId", patientId, "status", "PENDING"),
                500,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), VaccinationSchedule.class))
                        .filter(Objects::nonNull)
                        .filter(s -> s.status() == ScheduleStatus.PENDING)
                        .sorted((a, b) -> a.dueDate().compareTo(b.dueDate()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> imm = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                IMMUNIZATION_DATASET,
                Map.of("id", "string", "patientId", "string", "cvxCode", "string",
                        "administeredAt", "timestamp", "status", "string"),
                Map.of("retention", "permanent")
        ));

        Promise<Void> sched = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                SCHEDULE_DATASET,
                Map.of("id", "string", "patientId", "string", "dueDate", "date"),
                Map.of("retention", "10years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(imm, sched, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "ImmunizationAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        ));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

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
