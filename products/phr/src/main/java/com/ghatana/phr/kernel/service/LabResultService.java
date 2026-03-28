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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lab Result Service for PHR.
 *
 * <p>Manages patient laboratory test results (haematology, biochemistry, microbiology,
 * histopathology, radiology reports). Stores machine-readable LOINC-coded results
 * and human-readable narrative summaries. Complies with Nepal NHSS standards and
 * FHIR R4 DiagnosticReport / Observation resources.</p>
 *
 * @doc.type class
 * @doc.purpose PHR lab result service — LOINC-coded diagnostic results with trend support
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class LabResultService implements KernelLifecycleAware {

    private static final String RESULT_DATASET = "phr.lab.results";
    private static final String PANEL_DATASET = "phr.lab.panels";
    private static final String AUDIT_DATASET = "phr.lab.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a LabResultService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public LabResultService(KernelContext context) {
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
        return "lab-results";
    }

    // ==================== Core Operations ====================

    /**
     * Records a single lab observation (e.g. a single analyte result).
     *
     * @param observation the lab observation to store
     * @return Promise containing the stored observation with generated ID
     */
    public Promise<LabObservation> recordObservation(LabObservation observation) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(observation.patientId(), "patientId");
        Objects.requireNonNull(observation.loincCode(), "loincCode");

        String id = observation.id() != null ? observation.id() : generateId("obs");
        LabObservation toStore = new LabObservation(
                id,
                observation.patientId(),
                observation.encounterId(),
                observation.orderId(),
                observation.loincCode(),
                observation.loincDisplay(),
                observation.testName(),
                observation.value(),
                observation.referenceRangeLow(),
                observation.unit(),
                observation.referenceRange(),
                observation.performingLabId(),
                observation.orderedAt() != null ? observation.orderedAt() : Instant.now(),
                observation.resultedAt() != null ? observation.resultedAt() : Instant.now(),
                observation.status(),
                observation.notes(),
                observation.interpretation()
        );

        DataWriteRequest request = new DataWriteRequest(
                RESULT_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "LabObservation", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "loincCode", toStore.loincCode(),
                        "status", toStore.status().name()
                )
        );

        return dataCloud.writeData(request)
                .then($ -> audit("RECORD_OBSERVATION", toStore.patientId(),
                        "Lab result recorded: " + toStore.loincDisplay()))
                .map($ -> toStore);
    }

    /**
     * Records a diagnostic panel (group of related observations, e.g. CBC, LFT).
     *
     * @param panel the lab panel to store
     * @return Promise containing the stored panel
     */
    public Promise<LabPanel> recordPanel(LabPanel panel) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(panel.patientId(), "patientId");

        String panelId = panel.id() != null ? panel.id() : generateId("pnl");
        LabPanel toStore = new LabPanel(
                panelId,
                panel.patientId(),
                panel.performingLabId(),
                panel.orderingProviderId(),
                panel.panelName(),
                panel.observations(),
                Instant.now(),
                panel.status()
        );

        // Persist the panel header
        DataWriteRequest panelReq = new DataWriteRequest(
                PANEL_DATASET,
                panelId,
                TypedDataSerializer.toBytes(toStore, "LabPanel", 1),
                Map.of("patientId", toStore.patientId(), "panelName", toStore.panelName())
        );

        // Persist each individual observation
        List<Promise<Void>> obsPersist = toStore.observations().stream()
                .map(obs -> {
                    String obsId = obs.id() != null ? obs.id() : generateId("obs");
                    LabObservation withId = new LabObservation(
                            obsId, obs.patientId(), obs.encounterId(), obs.orderId(),
                            obs.loincCode(), obs.loincDisplay(), obs.testName(), obs.value(),
                            obs.referenceRangeLow(), obs.unit(), obs.referenceRange(),
                            obs.performingLabId(),
                            obs.orderedAt() != null ? obs.orderedAt() : Instant.now(),
                            Instant.now(), obs.status(), obs.notes(), obs.interpretation()
                    );
                    return dataCloud.writeData(new DataWriteRequest(
                            RESULT_DATASET, obsId,
                            TypedDataSerializer.toBytes(withId, "LabObservation", 1),
                            Map.of("patientId", withId.patientId(), "panelId", panelId)
                    ));
                })
                .toList();

        return dataCloud.writeData(panelReq)
                .then($ -> Promises.all(obsPersist))
                .then($ -> audit("RECORD_PANEL", toStore.patientId(),
                        "Panel recorded: " + toStore.panelName()))
                .map($ -> toStore);
    }

    /**
     * Retrieves a single lab observation by ID.
     *
     * @param observationId the observation identifier
     * @return Promise containing the observation if found
     */
    public Promise<Optional<LabObservation>> getObservation(String observationId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(RESULT_DATASET, observationId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable((LabObservation) TypedDataSerializer.fromBytes(result.getData(), LabObservation.class));
                })
                ;
    }

    /**
     * Returns all lab observations for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing the list of observations
     */
    public Promise<List<LabObservation>> getPatientObservations(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                RESULT_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                5000,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), LabObservation.class))
                        .filter(Objects::nonNull)
                        .toList());
    }

    /**
     * Returns observations for a specific LOINC code, useful for trending a single analyte.
     *
     * @param patientId  the patient identifier
     * @param loincCode  the LOINC code to trend
     * @return Promise containing observations ordered by result time (earliest first)
     */
    public Promise<List<LabObservation>> getTrend(String patientId, String loincCode) {
        return getPatientObservations(patientId)
                .map(obs -> obs.stream()
                        .filter(o -> loincCode.equals(o.loincCode()))
                        .sorted((a, b) -> a.resultedAt().compareTo(b.resultedAt()))
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> results = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                RESULT_DATASET,
                Map.of("id", "string", "patientId", "string", "loincCode", "string",
                        "resultedAt", "timestamp", "status", "string"),
                Map.of("retention", "25years")
        ));

        Promise<Void> panels = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                PANEL_DATASET,
                Map.of("id", "string", "patientId", "string", "panelName", "string"),
                Map.of("retention", "25years")
        ));

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        ));

        return Promises.all(results, panels, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        DataWriteRequest req = new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "LabAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        );
        return dataCloud.writeData(req);
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * A single lab test result (analyte / observation).
     *
     * @param id                  unique observation ID
     * @param patientId           the patient this result belongs to
     * @param performingLabId     identifier of the performing laboratory
     * @param orderingProviderId  identifier of the ordering physician
     * @param loincCode           LOINC code for the analyte (e.g. "2160-0" for serum creatinine)
     * @param loincDisplay        human-readable analyte name
     * @param value               numeric or textual result value
     * @param unit                UCUM unit (e.g. "mg/dL")
     * @param referenceRangeLow   lower bound of normal reference range (may be null)
     * @param referenceRangeHigh  upper bound of normal reference range (may be null)
     * @param interpretation      result interpretation code (N=normal, H=high, L=low, etc.)
     * @param resultedAt          when the result was produced
     * @param status              result status
     * @param notes               free-text analyst notes
     */
    public record LabObservation(
            String id,
            String patientId,
            String encounterId,
            String orderId,
            String loincCode,
            String loincDisplay,
            String testName,
            BigDecimal value,
            Double referenceRangeLow,
            String unit,
            String referenceRange,
            String performingLabId,
            Instant orderedAt,
            Instant resultedAt,
            ObservationStatus status,
            String notes,
            String interpretation
    ) {
        /** Returns {@code true} when the interpretation code indicates an out-of-range result. */
        public boolean isAbnormal() {
            return interpretation != null && !interpretation.isBlank() && !"N".equalsIgnoreCase(interpretation);
        }
    }

    /**
     * A grouped set of lab observations forming a named panel (e.g. CBC, LFT, Lipid Profile).
     *
     * @param id                 unique panel ID
     * @param patientId          the patient this panel belongs to
     * @param performingLabId    identifier of the performing laboratory
     * @param orderingProviderId identifier of the ordering physician
     * @param panelName          human-readable panel name (e.g. "Complete Blood Count")
     * @param observations       individual observations in this panel
     * @param reportedAt         when the full panel report was finalized
     * @param status             panel status
     */
    public record LabPanel(
            String id,
            String patientId,
            String performingLabId,
            String orderingProviderId,
            String panelName,
            List<LabObservation> observations,
            Instant reportedAt,
            ObservationStatus status
    ) {}

    /** Lab observation lifecycle status. */
    public enum ObservationStatus {
        /** Result is preliminary and may change. */
        PRELIMINARY,
        /** Result is final and verified. */
        FINAL,
        /** Result has been corrected/amended. */
        AMENDED,
        /** Result has been cancelled. */
        CANCELLED
    }
}
