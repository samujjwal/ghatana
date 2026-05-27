package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public class ImagingService extends PhrServiceBase {

    private static final String ORDER_DATASET = "phr.imaging.orders";
    private static final String STUDY_DATASET = "phr.imaging.studies";
    private static final String REPORT_DATASET = "phr.imaging.reports";

    public ImagingService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "imaging";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> orders = createSchema(
            ORDER_DATASET,
            Map.of("id", "string", "patientId", "string", "status", "string"),
            Map.of("retention", "25years")
        );

        Promise<Void> studies = createSchema(
            STUDY_DATASET,
            Map.of("id", "string", "patientId", "string", "dcmStudyInstanceUid", "string"),
            Map.of("retention", "25years")
        );

        Promise<Void> reports = createSchema(
            REPORT_DATASET,
            Map.of("id", "string", "patientId", "string", "studyId", "string"),
            Map.of("retention", "25years")
        );

        return orders.then($ -> studies).then($ -> reports);
    }

    // ==================== Core Operations ====================

    /**
     * Creates a radiology order (imaging request).
     *
     * @param order the imaging order to create
     * @return Promise containing the stored order
     */
    public Promise<ImagingOrder> createOrder(ImagingOrder order) {
        ensureRunning();

        validateRequired(order.patientId(), "patientId");
        validateRequired(order.modalityCode(), "modalityCode");

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

        return createRecord(
            ORDER_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "status", "PENDING"
            ), toStore.orderingProviderId()),
            "ImagingOrder",
            1
        ).then(stored -> audit("CREATE_ORDER", stored.patientId(),
            "Imaging order: " + stored.modalityCode() + " / " + stored.bodyPart())
            .map($ -> stored));
    }

    /**
     * Registers a completed DICOM study against an order.
     *
     * @param study the imaging study metadata
     * @return Promise containing the stored study
     */
    public Promise<ImagingStudy> registerStudy(ImagingStudy study) {
        ensureRunning();

        validateRequired(study.patientId(), "patientId");
        validateRequired(study.dcmStudyInstanceUid(), "dcmStudyInstanceUid");

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

        // If associated with an order, mark it complete
        Promise<ImagingStudy> studyWrite = createRecord(
            STUDY_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "dcmStudyInstanceUid", toStore.dcmStudyInstanceUid()
            ), "system"),
            "ImagingStudy",
            1
        );

        Promise<Void> updateOrder = study.orderId() != null
            ? fulfillOrder(study.orderId())
            : Promise.complete();

        return studyWrite
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
        ensureRunning();

        validateRequired(report.patientId(), "patientId");
        validateRequired(report.studyId(), "studyId");

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

        return createRecord(
            REPORT_DATASET,
            id,
            toStore,
            mutationMetadata(Map.of(
                "patientId", toStore.patientId(),
                "studyId", toStore.studyId()
            ), toStore.reportingRadiologistId()),
            "RadiologyReport",
            1
        ).then(stored -> audit("STORE_REPORT", stored.patientId(),
            "Radiology report stored by " + stored.reportingRadiologistId())
            .map($ -> stored));
    }

    /**
     * Returns all imaging orders for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all imaging orders
     */
    public Promise<List<ImagingOrder>> getPatientOrders(String patientId) {
        ensureRunning();

        return queryRecords(
            ORDER_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            500,
            0,
            ImagingOrder.class
        );
    }

    public Promise<List<ImagingStudy>> getPatientStudies(String patientId) {
        ensureRunning();

        return queryRecords(
            STUDY_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            500,
            0,
            ImagingStudy.class
        );
    }

    public Promise<Optional<ImagingOrder>> getOrder(String orderId) {
        ensureRunning();
        return readRecord(ORDER_DATASET, orderId, ImagingOrder.class);
    }

    public Promise<Optional<ImagingStudy>> getStudy(String studyId) {
        ensureRunning();
        return readRecord(STUDY_DATASET, studyId, ImagingStudy.class);
    }

    // ==================== Private Helpers ====================

    private Promise<Void> fulfillOrder(String orderId) {
        return readRecord(ORDER_DATASET, orderId, ImagingOrder.class)
            .then(opt -> {
                if (opt.isEmpty()) return Promise.complete();
                ImagingOrder existing = opt.get();
                ImagingOrder fulfilled = new ImagingOrder(
                    existing.id(), existing.patientId(), existing.encounterId(),
                    existing.orderingProviderId(), existing.modalityCode(),
                    existing.bodyPart(), existing.clinicalIndication(),
                    OrderStatus.COMPLETED, existing.orderedAt(), Instant.now()
                );
                return updateRecord(
                    ORDER_DATASET,
                    orderId,
                    fulfilled,
                    mutationMetadata(Map.of(
                        "patientId", fulfilled.patientId(),
                        "status", "FULFILLED"
                    ), fulfilled.orderingProviderId()),
                    "ImagingOrder",
                    1
                ).map($ -> null);
            }).whenException(e -> Promise.complete());
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
