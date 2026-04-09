package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.service.AbstractDataService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public class LabResultService extends AbstractDataService {

    private static final String RESULT_DATASET = "phr.lab.results";
    private static final String PANEL_DATASET = "phr.lab.panels";

    public LabResultService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "lab-results";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        Promise<Void> results = createSchema(
            RESULT_DATASET,
            Map.of("id", "string", "patientId", "string", "loincCode", "string",
                "resultedAt", "timestamp", "status", "string"),
            Map.of("retention", "25years")
        );

        Promise<Void> panels = createSchema(
            PANEL_DATASET,
            Map.of("id", "string", "patientId", "string", "panelName", "string"),
            Map.of("retention", "25years")
        );

        return results.then($ -> panels);
    }

    // ==================== Core Operations ====================

    /**
     * Records a single lab observation (e.g. a single analyte result).
     *
     * @param observation the lab observation to store
     * @return Promise containing the stored observation with generated ID
     */
    public Promise<LabObservation> recordObservation(LabObservation observation) {
        ensureRunning();

        validateRequired(observation.patientId(), "patientId");
        validateRequired(observation.loincCode(), "loincCode");

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

        return createRecord(
            RESULT_DATASET,
            id,
            toStore,
            Map.of(
                "patientId", toStore.patientId(),
                "loincCode", toStore.loincCode(),
                "status", toStore.status().name()
            ),
            "LabObservation",
            1
        ).then(stored -> audit("RECORD_OBSERVATION", stored.patientId(),
            "Lab result recorded: " + stored.loincDisplay())
            .map($ -> stored));
    }

    /**
     * Records a diagnostic panel (group of related observations, e.g. CBC, LFT).
     *
     * @param panel the lab panel to store
     * @return Promise containing the stored panel
     */
    public Promise<LabPanel> recordPanel(LabPanel panel) {
        ensureRunning();

        validateRequired(panel.patientId(), "patientId");

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
        Promise<LabPanel> panelWrite = createRecord(
            PANEL_DATASET,
            panelId,
            toStore,
            Map.of("patientId", toStore.patientId(), "panelName", toStore.panelName()),
            "LabPanel",
            1
        );

        // Persist each individual observation
        List<Promise<LabObservation>> obsPersist = toStore.observations().stream()
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
                return createRecord(
                    RESULT_DATASET,
                    obsId,
                    withId,
                    Map.of("patientId", withId.patientId(), "panelId", panelId),
                    "LabObservation",
                    1
                );
            })
            .toList();

        return panelWrite
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
        ensureRunning();
        return readRecord(RESULT_DATASET, observationId, LabObservation.class);
    }

    public Promise<List<LabObservation>> getPatientObservations(String patientId) {
        ensureRunning();

        return queryRecords(
            RESULT_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            5000,
            0,
            LabObservation.class
        );
    }

    public Promise<List<LabObservation>> getTrend(String patientId, String loincCode) {
        return getPatientObservations(patientId)
            .map(obs -> obs.stream()
                .filter(o -> loincCode.equals(o.loincCode()))
                .sorted((a, b) -> a.resultedAt().compareTo(b.resultedAt()))
                .toList());
    }

    // ==================== Private Helpers ====================

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
