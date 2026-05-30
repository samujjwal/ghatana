package com.ghatana.phr.kernel.service;

import io.activej.promise.Promise;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Extension methods for PatientRecordService for dashboard and route family completion.
 *
 * <p>These methods provide convenience methods for the dashboard and other route families
 * that need aggregated patient record data.</p>
 *
 * @doc.type class
 * @doc.purpose Extension methods for PatientRecordService
 * @doc.layer product
 * @doc.pattern Service Extension
 */
public final class PatientRecordServiceExtensions {

    private final PatientRecordService patientRecordService;
    private final LabResultService labResultService;

    public PatientRecordServiceExtensions(PatientRecordService patientRecordService) {
        this(patientRecordService, null);
    }

    public PatientRecordServiceExtensions(PatientRecordService patientRecordService, LabResultService labResultService) {
        this.patientRecordService = patientRecordService;
        this.labResultService = labResultService;
    }

    /**
     * Gets recent observations for a patient.
     *
     * @param patientId the patient identifier
     * @param limit the maximum number of observations to return
     * @return Promise containing list of recent observations
     */
    public Promise<List<Observation>> getRecentObservations(String patientId, int limit) {
        if (labResultService == null) {
            return Promise.of(List.of());
        }
        int normalizedLimit = Math.max(0, limit);
        return labResultService.getPatientObservations(patientId)
            .map(observations -> observations.stream()
                .sorted(Comparator.comparing(
                    LabResultService.LabObservation::resultedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .limit(normalizedLimit)
                .map(Observation::fromLabObservation)
                .toList());
    }

    /**
     * Gets active conditions for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing list of active conditions
     */
    public Promise<List<Condition>> getActiveConditions(String patientId) {
        return patientRecordService.getPatient(patientId)
            .map(patient -> patient
                .map(PatientRecordService.Patient::getMedicalHistory)
                .map(PatientRecordService.MedicalHistory::getConditions)
                .orElse(List.of()))
            .map(conditions -> IntStream.range(0, conditions.size())
                .mapToObj(index -> Condition.fromMedicalHistory(index, conditions.get(index)))
                .toList());
    }

    public static class Observation {
        private final String severity;

        public Observation(String severity) {
            this.severity = severity;
        }

        public String getSeverity() {
            return severity;
        }

        private static Observation fromLabObservation(LabResultService.LabObservation observation) {
            return new Observation(severityFromInterpretation(observation.interpretation()));
        }

        private static String severityFromInterpretation(String interpretation) {
            if (interpretation == null || interpretation.isBlank() || "N".equalsIgnoreCase(interpretation)) {
                return "normal";
            }
            return switch (interpretation.toUpperCase()) {
                case "HH", "LL", "AA", "CRITICAL" -> "critical";
                default -> "abnormal";
            };
        }
    }

    public static class Condition {
        private final String id;
        private final String code;
        private final String display;
        private final String status;
        private final String onsetDate;
        private final String chronicity;

        public Condition(String id, String code, String display, String status, String onsetDate, String chronicity) {
            this.id = id;
            this.code = code;
            this.display = display;
            this.status = status;
            this.onsetDate = onsetDate;
            this.chronicity = chronicity;
        }

        public String getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public String getDisplay() {
            return display;
        }

        public String getStatus() {
            return status;
        }

        public String getOnsetDate() {
            return onsetDate;
        }

        public String getChronicity() {
            return chronicity;
        }

        private static Condition fromMedicalHistory(int index, String display) {
            String normalizedDisplay = Objects.toString(display, "").trim();
            return new Condition(
                "condition-" + index,
                normalizedDisplay,
                normalizedDisplay,
                "active",
                null,
                "unknown"
            );
        }
    }
}
