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
 * Imaging and Radiology Service for PHR.
 *
 * <p>Manages radiology orders, DICOM study metadata, and structured radiology report storage.
 * Stores DICOM UIDs as pointers to an external PACS (Picture Archiving and Communication System);
 * actual DICOM binary data is NOT stored in DataCloud. Complies with Nepal Radiological
 * Society standards and FHIR R4 ImagingStudy / DiagnosticReport resources.</p>
 *
 * @doc.type class
 * @doc.purpose PHR imaging — DICOM metadata, radiology orders, structured reports
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ImagingService {

    private static final String ORDER_DATASET = "phr.imaging.orders";
    private static final String STUDY_DATASET = "phr.imaging.studies";
    private static final String REPORT_DATASET = "phr.imaging.reports";
    private static final String AUDIT_DATASET = "phr.imaging.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs an ImagingService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public ImagingService(KernelContext context) {
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
        return "imaging";
    }

    // ==================== Core Operations ====================

    /**
     * Creates a radiology order (imaging request).
     *
     * @param order the imaging order to create
     * @return Promise containing the stored order
     */
    public Promise<ImagingOrder> createOrder(ImagingOrder order) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(order.patientId(), "patientId");
        Objects.requireNonNull(order.modalityCode(), "modalityCode");

        String id = order.id() != null ? order.id() : generateId("imgo");
        ImagingOrder toStore = new ImagingOrder(
                id,
                order.patientId(),
                order.encounterId(),
                order.orderingProviderId(),
                order.modalityCode(),
                order.bodyPart(),
                order.clinicalIndication(),
                OrderStatus.REQUESTED,
                Instant.now(),
                null
        );

        DataWriteRequest request = new DataWriteRequest(
                ORDER_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "ImagingOrder", 1),
                Map.of("patientId", toStore.patientId(), "status", "PENDING")
        );

        return dataCloud.writeData(request)
                .then($ -> audit("CREATE_ORDER", toStore.patientId(),
                        "Imaging order: " + toStore.modalityCode() + " / " + toStore.bodyPart()))
                .map($ -> toStore);
    }

    /**
     * Registers a completed DICOM study against an order.
     *
     * @param study the imaging study metadata
     * @return Promise containing the stored study
     */
    public Promise<ImagingStudy> registerStudy(ImagingStudy study) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(study.patientId(), "patientId");
        Objects.requireNonNull(study.dcmStudyInstanceUid(), "dcmStudyInstanceUid");

        String id = study.id() != null ? study.id() : generateId("imgs");
        ImagingStudy toStore = new ImagingStudy(
                id,
                study.patientId(),
                study.orderId(),
                study.dcmStudyInstanceUid(),
                study.modalityCode(),
                study.pacsLocation(),
                study.seriesCount(),
                study.instanceCount(),
                StudyStatus.COMPLETE,
                Instant.now(),
                study.bodyPart()
        );

        DataWriteRequest request = new DataWriteRequest(
                STUDY_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "ImagingStudy", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "dcmStudyInstanceUid", toStore.dcmStudyInstanceUid()
                )
        );

        // If associated with an order, mark it complete
        Promise<Void> updateOrder = study.orderId() != null
                ? fulfillOrder(study.orderId())
                : Promise.complete();

        return dataCloud.writeData(request)
                .then($ -> updateOrder)
                .then($ -> audit("REGISTER_STUDY", toStore.patientId(),
                        "Study registered: " + toStore.dcmStudyInstanceUid()))
                .map($ -> toStore);
    }

    /**
     * Stores a structured radiology report for a study.
     *
     * @param report the radiology report
     * @return Promise containing the stored report
     */
    public Promise<RadiologyReport> storeReport(RadiologyReport report) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(report.patientId(), "patientId");
        Objects.requireNonNull(report.studyId(), "studyId");

        String id = report.id() != null ? report.id() : generateId("rpt");
        RadiologyReport toStore = new RadiologyReport(
                id,
                report.patientId(),
                report.studyId(),
                report.reportingRadiologistId(),
                report.findings(),
                report.impression(),
                report.recommendations(),
                report.status() != null ? report.status() : ReportStatus.PRELIMINARY,
                Instant.now()
        );

        DataWriteRequest request = new DataWriteRequest(
                REPORT_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "RadiologyReport", 1),
                Map.of("patientId", toStore.patientId(), "studyId", toStore.studyId())
        );

        return dataCloud.writeData(request)
                .then($ -> audit("STORE_REPORT", toStore.patientId(),
                        "Radiology report stored by " + toStore.reportingRadiologistId()))
                .map($ -> toStore);
    }

    /**
     * Returns all imaging orders for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all imaging orders
     */
    public Promise<List<ImagingOrder>> getPatientOrders(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                ORDER_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                500,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), ImagingOrder.class))
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * Returns all imaging studies for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all available studies
     */
    public Promise<List<ImagingStudy>> getPatientStudies(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                STUDY_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                500,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), ImagingStudy.class))
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * Retrieves a single imaging order by ID.
     *
     * @param orderId the order identifier
     * @return Promise containing the order if found
     */
    public Promise<Optional<ImagingOrder>> getOrder(String orderId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(ORDER_DATASET, orderId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable(
                            TypedDataSerializer.fromBytes(result.getData(), ImagingOrder.class));
                });
    }

    // ==================== Private Helpers ====================

    private Promise<Void> fulfillOrder(String orderId) {
        return dataCloud.readData(new DataReadRequest(ORDER_DATASET, orderId, Map.of()))
                .then(result -> {
                    if (result == null || result.getData() == null) return Promise.complete();
                    ImagingOrder existing = TypedDataSerializer.fromBytes(result.getData(), ImagingOrder.class);
                    if (existing == null) return Promise.complete();
                    ImagingOrder fulfilled = new ImagingOrder(
                            existing.id(), existing.patientId(), existing.encounterId(),
                            existing.orderingProviderId(), existing.modalityCode(),
                            existing.bodyPart(), existing.clinicalIndication(),
                            OrderStatus.COMPLETED, existing.orderedAt(), Instant.now()
                    );
                    return dataCloud.writeData(new DataWriteRequest(
                            ORDER_DATASET, orderId,
                            TypedDataSerializer.toBytes(fulfilled, "ImagingOrder", 1),
                            Map.of("status", "FULFILLED")
                    ));
                }).whenException(e -> Promise.complete());
    }

    private Promise<Void> initializeDatasets() {
        Promise<Void> orders = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                ORDER_DATASET,
                Map.of("id", "string", "patientId", "string", "status", "string"),
                Map.of("retention", "25years")
        ));

        Promise<Void> studies = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                STUDY_DATASET,
                Map.of("id", "string", "patientId", "string", "dcmStudyInstanceUid", "string"),
                Map.of("retention", "25years")
        ));

        Promise<Void> reports = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                REPORT_DATASET,
                Map.of("id", "string", "patientId", "string", "studyId", "string"),
                Map.of("retention", "25years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(orders, studies, reports, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "ImagingAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        ));
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * A radiology/imaging order.
     *
     * @param id                    unique order ID
     * @param patientId             patient who the order is for
     * @param orderingProviderId    provider placing the order
     * @param modalityCode          DICOM modality code (e.g. "CT", "MR", "CR", "US")
     * @param bodyPart              body part to image (SNOMED or free text)
     * @param clinicalIndication    reason for the order
     * @param status                order lifecycle status
     * @param orderedAt             when the order was placed
     * @param fulfilledAt           when the study was acquired (null if pending)
     */
    public record ImagingOrder(
            String id,
            String patientId,
            String encounterId,
            String orderingProviderId,
            String modalityCode,
            String bodyPart,
            String clinicalIndication,
            OrderStatus status,
            Instant orderedAt,
            Instant fulfilledAt
    ) {}

    /**
     * DICOM study metadata (pointer to PACS).
     *
     * @param id                    unique study record ID (internal, not DICOM UID)
     * @param patientId             patient the study belongs to
     * @param orderId               associated imaging order (may be null for walk-in studies)
     * @param dcmStudyInstanceUid   DICOM Study Instance UID (global unique)
     * @param modalityCode          DICOM modality code
     * @param bodyPart              body part imaged
     * @param seriesCount           number of series in the study
     * @param instanceCount         total number of DICOM instances (slices)
     * @param studyDate             date the study was acquired
     * @param pacsLocation          URI or node identifier in the PACS system
     * @param status                study availability status
     */
    public record ImagingStudy(
            String id,
            String patientId,
            String orderId,
            String dcmStudyInstanceUid,
            String modalityCode,
            String pacsLocation,
            int seriesCount,
            int instanceCount,
            StudyStatus status,
            Instant studyDate,
            String bodyPart
    ) {}

    /**
     * Structured radiology report for an imaging study.
     *
     * @param id                        unique report ID
     * @param patientId                 patient the report belongs to
     * @param studyId                   the imaging study this report interprets
     * @param reportingRadiologistId    provider who authored the report
     * @param findings                  detailed findings section
     * @param impression                summary impression / conclusion
     * @param recommendations           follow-up recommendations
     * @param reportedAt                timestamp when the report was drafted
     * @param status                    report lifecycle status
     */
    public record RadiologyReport(
            String id,
            String patientId,
            String studyId,
            String reportingRadiologistId,
            String findings,
            String impression,
            String recommendations,
            ReportStatus status,
            Instant reportedAt
    ) {}

    /** Imaging order lifecycle status. */
    public enum OrderStatus {
        REQUESTED, PENDING, SCHEDULED, COMPLETED, FULFILLED, CANCELLED
    }

    /** DICOM study availability status. */
    public enum StudyStatus {
        COMPLETE, AVAILABLE, UNAVAILABLE, ENTERED_IN_ERROR
    }

    /** Radiology report lifecycle status. */
    public enum ReportStatus {
        PRELIMINARY, FINAL, AMENDED
    }
}
