package com.ghatana.phr.application.record;

import com.ghatana.phr.application.patient.PatientOperationContext;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of RecordService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides record summary and timeline operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class RecordServiceImpl implements RecordService {

    private final PatientRecordService patientRecordService;
    private final AppointmentService appointmentService;
    private final MedicationService medicationService;
    private final LabResultService labResultService;
    private final ImmunizationService immunizationService;
    private final DocumentService documentService;
    private final ConsentManagementService consentService;

    public RecordServiceImpl() {
        this(null, null, null, null, null, null, null);
    }

    public RecordServiceImpl(
            PatientRecordService patientRecordService,
            AppointmentService appointmentService,
            MedicationService medicationService,
            LabResultService labResultService,
            ImmunizationService immunizationService,
            DocumentService documentService,
            ConsentManagementService consentService) {
        this.patientRecordService = patientRecordService;
        this.appointmentService = appointmentService;
        this.medicationService = medicationService;
        this.labResultService = labResultService;
        this.immunizationService = immunizationService;
        this.documentService = documentService;
        this.consentService = consentService;
    }

    @Override
    public Promise<RecordSummary> getRecordSummary(PatientOperationContext ctx, String patientId) {
        Map<String, Object> demographics = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        return appendPatientSummary(patientId, demographics)
            .then($ -> countAppointments(patientId, counts))
            .then($ -> countMedications(patientId, counts))
            .then($ -> countLabs(patientId, counts))
            .then($ -> countImmunizations(patientId, counts))
            .then($ -> countDocuments(ctx, patientId, counts))
            .then($ -> countConsents(patientId, counts))
            .map($ -> new RecordSummary(patientId, demographics, counts, Instant.now().toString()));
    }

    @Override
    public Promise<RecordTimeline> getRecordTimeline(PatientOperationContext ctx, String patientId) {
        List<TimelineEntry> entries = new ArrayList<>();

        return appendPatientRecord(patientId, entries)
            .then($ -> appendAppointments(patientId, entries))
            .then($ -> appendMedications(patientId, entries))
            .then($ -> appendLabs(patientId, entries))
            .then($ -> appendImmunizations(patientId, entries))
            .then($ -> appendDocuments(ctx, patientId, entries))
            .then($ -> appendConsents(patientId, entries))
            .map($ -> {
                List<TimelineEntry> sorted = entries.stream()
                    .sorted(Comparator.comparing(TimelineEntry::timestamp).reversed())
                    .toList();
                return new RecordTimeline(patientId, sorted, Instant.now().toString());
            });
    }

    @Override
    public Promise<List<TimelineEntry>> getTimelineByCategory(PatientOperationContext ctx, String patientId, String category) {
        return getRecordTimeline(ctx, patientId)
            .map(timeline -> timeline.entries().stream()
                .filter(e -> e.category().equals(category))
                .toList());
    }

    private Promise<Void> appendPatientSummary(String patientId, Map<String, Object> demographics) {
        if (patientRecordService == null) {
            return Promise.complete();
        }
        return patientRecordService.getPatient(patientId)
            .map(patient -> {
                patient.ifPresent(value -> {
                    PatientRecordService.Demographics demo = value.getDemographics();
                    if (demo != null) {
                        demographics.put("givenName", demo.getGivenName());
                        demographics.put("familyName", demo.getFamilyName());
                        demographics.put("dateOfBirth", demo.getDateOfBirth());
                        demographics.put("gender", demo.getGender());
                    }
                });
                return null;
            });
    }

    private Promise<Void> countAppointments(String patientId, Map<String, Integer> counts) {
        if (appointmentService == null) return Promise.complete();
        return appointmentService.getPatientAppointments(patientId, null).map(items -> putCount(counts, "appointments", items.size()));
    }

    private Promise<Void> countMedications(String patientId, Map<String, Integer> counts) {
        if (medicationService == null) return Promise.complete();
        return medicationService.getPrescriptionHistory(patientId).map(items -> putCount(counts, "medications", items.size()));
    }

    private Promise<Void> countLabs(String patientId, Map<String, Integer> counts) {
        if (labResultService == null) return Promise.complete();
        return labResultService.getPatientObservations(patientId).map(items -> putCount(counts, "labs", items.size()));
    }

    private Promise<Void> countImmunizations(String patientId, Map<String, Integer> counts) {
        if (immunizationService == null) return Promise.complete();
        return immunizationService.getImmunizationHistory(patientId).map(items -> putCount(counts, "immunizations", items.size()));
    }

    private Promise<Void> countDocuments(PatientOperationContext ctx, String patientId, Map<String, Integer> counts) {
        if (documentService == null) return Promise.complete();
        return documentService.getPatientDocuments(patientId, ctx.userId()).map(items -> putCount(counts, "documents", items.size()));
    }

    private Promise<Void> countConsents(String patientId, Map<String, Integer> counts) {
        if (consentService == null) return Promise.complete();
        return consentService.getPatientGrants(patientId).map(items -> putCount(counts, "consents", items.size()));
    }

    private Promise<Void> appendPatientRecord(String patientId, List<TimelineEntry> entries) {
        if (patientRecordService == null) {
            return Promise.complete();
        }
        return patientRecordService.getPatient(patientId)
            .map(patient -> {
                patient.ifPresent(value -> {
                    Instant timestamp = firstInstant(value.getUpdatedAt(), value.getCreatedAt());
                    if (timestamp != null) {
                        entries.add(new TimelineEntry(
                            "patient-record:" + value.getId(),
                            timestamp.toString(),
                            "document",
                            "Patient record",
                            "Patient profile updated",
                            details(
                                "resourceType", "Patient",
                                "source", "patient-record-service"
                            )
                        ));
                    }
                });
                return null;
            });
    }

    private Promise<Void> appendAppointments(String patientId, List<TimelineEntry> entries) {
        if (appointmentService == null) return Promise.complete();
        return appointmentService.getPatientAppointments(patientId, null)
            .map(items -> {
                items.forEach(appointment -> entries.add(new TimelineEntry(
                    "appointment:" + appointment.getId(),
                    appointment.getScheduledTime().toString(),
                    "visit",
                    appointment.getAppointmentType(),
                    "Appointment " + appointment.getStatus().toLowerCase(),
                    details(
                        "resourceType", "Appointment",
                        "status", appointment.getStatus(),
                        "providerId", appointment.getProviderId(),
                        "source", "appointment-service"
                    )
                )));
                return null;
            });
    }

    private Promise<Void> appendMedications(String patientId, List<TimelineEntry> entries) {
        if (medicationService == null) return Promise.complete();
        return medicationService.getPrescriptionHistory(patientId)
            .map(items -> {
                items.forEach(rx -> entries.add(new TimelineEntry(
                    "medication:" + rx.id(),
                    rx.prescribedAt().toString(),
                    "medication",
                    rx.status().name(),
                    safeDescription("Medication", rx.medicationName()),
                    details(
                        "resourceType", "MedicationRequest",
                        "status", rx.status().name(),
                        "medicationCode", rx.medicationCode(),
                        "source", "medication-service"
                    )
                )));
                return null;
            });
    }

    private Promise<Void> appendLabs(String patientId, List<TimelineEntry> entries) {
        if (labResultService == null) return Promise.complete();
        return labResultService.getPatientObservations(patientId)
            .map(items -> {
                items.forEach(obs -> entries.add(new TimelineEntry(
                    "lab:" + obs.id(),
                    obs.resultedAt().toString(),
                    "lab",
                    obs.status().name(),
                    safeDescription("Lab result", obs.testName()),
                    details(
                        "resourceType", "Observation",
                        "status", obs.status().name(),
                        "loincCode", obs.loincCode(),
                        "abnormal", obs.isAbnormal(),
                        "source", "lab-result-service"
                    )
                )));
                return null;
            });
    }

    private Promise<Void> appendImmunizations(String patientId, List<TimelineEntry> entries) {
        if (immunizationService == null) return Promise.complete();
        return immunizationService.getImmunizationHistory(patientId)
            .map(items -> {
                items.forEach(imm -> entries.add(new TimelineEntry(
                    "immunization:" + imm.id(),
                    imm.administeredAt().toString(),
                    "immunization",
                    imm.status().name(),
                    safeDescription("Immunization", imm.vaccineName()),
                    details(
                        "resourceType", "Immunization",
                        "status", imm.status().name(),
                        "cvxCode", imm.cvxCode(),
                        "doseNumber", imm.doseNumber(),
                        "source", "immunization-service"
                    )
                )));
                return null;
            });
    }

    private Promise<Void> appendDocuments(PatientOperationContext ctx, String patientId, List<TimelineEntry> entries) {
        if (documentService == null) return Promise.complete();
        return documentService.getPatientDocuments(patientId, ctx.userId())
            .map(items -> {
                items.forEach(doc -> entries.add(new TimelineEntry(
                    "document:" + doc.getId(),
                    firstInstant(doc.getUpdatedAt(), doc.getCreatedAt()).toString(),
                    "document",
                    doc.getDocumentType(),
                    doc.getTitle(),
                    details(
                        "resourceType", "DocumentReference",
                        "documentType", doc.getDocumentType(),
                        "contentType", doc.getContentType(),
                        "source", "document-service"
                    )
                )));
                return null;
            });
    }

    private Promise<Void> appendConsents(String patientId, List<TimelineEntry> entries) {
        if (consentService == null) return Promise.complete();
        return consentService.getPatientGrants(patientId)
            .map(items -> {
                items.forEach(grant -> entries.add(new TimelineEntry(
                    "consent:" + grant.getId(),
                    grant.getCreatedAt().toString(),
                    "consent",
                    grant.getStatus(),
                    "Consent " + grant.getStatus().toLowerCase(),
                    details(
                        "resourceType", "Consent",
                        "status", grant.getStatus(),
                        "recipientId", grant.getRecipientId(),
                        "source", "consent-service"
                    )
                )));
                return null;
            });
    }

    private static Void putCount(Map<String, Integer> counts, String key, int count) {
        counts.put(key, count);
        return null;
    }

    private static Instant firstInstant(Instant first, Instant fallback) {
        return first != null ? first : fallback;
    }

    private static String safeDescription(String prefix, String value) {
        return value == null || value.isBlank() ? prefix : prefix + ": " + value;
    }

    private static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            Object key = keyValues[i];
            Object value = keyValues[i + 1];
            if (key != null && value != null) {
                values.put(key.toString(), value);
            }
        }
        return values;
    }
}
