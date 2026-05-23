package com.ghatana.phr.application.clinical;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service interface for clinical workflows (encounters, medications, allergies, conditions, labs, immunizations, documents).
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing clinical data (PHR-F1-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ClinicalService {

    // ── Encounters ─────────────────────────────────────────────────────────

    Promise<Encounter> createEncounter(PatientOperationContext ctx, CreateEncounterRequest request);
    Promise<Optional<Encounter>> getEncounter(PatientOperationContext ctx, String encounterId);
    Promise<Encounter> updateEncounter(PatientOperationContext ctx, String encounterId, UpdateEncounterRequest request);
    Promise<Encounter> completeEncounter(PatientOperationContext ctx, String encounterId);
    Promise<List<Encounter>> listEncounters(PatientOperationContext ctx, String patientId);

    // ── Medications ────────────────────────────────────────────────────────

    Promise<Medication> prescribeMedication(PatientOperationContext ctx, PrescribeMedicationRequest request);
    Promise<Optional<Medication>> getMedication(PatientOperationContext ctx, String medicationId);
    Promise<Medication> recordAdministration(PatientOperationContext ctx, String medicationId, AdministrationRecord record);
    Promise<List<Medication>> listMedications(PatientOperationContext ctx, String patientId);
    Promise<Medication> requestRefill(PatientOperationContext ctx, String medicationId);

    // ── Allergies ────────────────────────────────────────────────────────

    Promise<Allergy> recordAllergy(PatientOperationContext ctx, RecordAllergyRequest request);
    Promise<Optional<Allergy>> getAllergy(PatientOperationContext ctx, String allergyId);
    Promise<List<Allergy>> listAllergies(PatientOperationContext ctx, String patientId);
    Promise<AllergyCrossCheck> getMedicationCrossCheck(PatientOperationContext ctx, String patientId);

    // ── Conditions ────────────────────────────────────────────────────────

    Promise<Condition> recordCondition(PatientOperationContext ctx, RecordConditionRequest request);
    Promise<Optional<Condition>> getCondition(PatientOperationContext ctx, String conditionId);
    Promise<Condition> resolveCondition(PatientOperationContext ctx, String conditionId);
    Promise<List<Condition>> listConditions(PatientOperationContext ctx, String patientId);

    // ── Labs ─────────────────────────────────────────────────────────────

    Promise<LabOrder> orderLab(PatientOperationContext ctx, OrderLabRequest request);
    Promise<Optional<LabOrder>> getLabOrder(PatientOperationContext ctx, String labId);
    Promise<LabResult> recordLabResult(PatientOperationContext ctx, String labId, LabResult result);
    Promise<Optional<LabResult>> getLabResult(PatientOperationContext ctx, String labId);
    Promise<List<LabOrder>> listLabOrders(PatientOperationContext ctx, String patientId);

    // ── Immunizations ─────────────────────────────────────────────────────

    Promise<Immunization> recordImmunization(PatientOperationContext ctx, RecordImmunizationRequest request);
    Promise<Optional<Immunization>> getImmunization(PatientOperationContext ctx, String immunizationId);
    Promise<List<Immunization>> listImmunizations(PatientOperationContext ctx, String patientId);
    Promise<ImmunizationSchedule> getImmunizationSchedule(PatientOperationContext ctx, String patientId);
    Promise<String> generateCertificate(PatientOperationContext ctx, String patientId);

    // ── Documents ────────────────────────────────────────────────────────

    Promise<Document> uploadDocument(PatientOperationContext ctx, UploadDocumentRequest request);
    Promise<Optional<Document>> getDocument(PatientOperationContext ctx, String documentId);
    Promise<Document> updateDocumentMetadata(PatientOperationContext ctx, String documentId, DocumentMetadata metadata);
    Promise<List<Document>> listDocuments(PatientOperationContext ctx, String patientId);

    // ── Request/Response types ───────────────────────────────────────────

    record CreateEncounterRequest(
        String patientId,
        String encounterType,
        String participant,
        String location
    ) {}

    record UpdateEncounterRequest(
        String encounterType,
        String participant,
        String location
    ) {}

    record Encounter(
        String encounterId,
        String patientId,
        String encounterType,
        String participant,
        String location,
        String status,
        String createdAt,
        String completedAt
    ) {}

    record PrescribeMedicationRequest(
        String patientId,
        String medicationName,
        String dosage,
        String frequency,
        String quantity
    ) {}

    record Medication(
        String medicationId,
        String patientId,
        String medicationName,
        String dosage,
        String frequency,
        String quantity,
        String status,
        String prescribedAt
    ) {}

    record AdministrationRecord(
        String administeredAt,
        String administeredBy,
        String notes
    ) {}

    record RecordAllergyRequest(
        String patientId,
        String allergen,
        String severity,
        String reaction
    ) {}

    record Allergy(
        String allergyId,
        String patientId,
        String allergen,
        String severity,
        String reaction,
        String recordedAt
    ) {}

    record AllergyCrossCheck(
        String patientId,
        List<String> contraindicatedMedications,
        List<String> warnings
    ) {}

    record RecordConditionRequest(
        String patientId,
        String conditionName,
        String code,
        String severity,
        String status
    ) {}

    record Condition(
        String conditionId,
        String patientId,
        String conditionName,
        String code,
        String severity,
        String status,
        String recordedAt,
        String resolvedAt
    ) {}

    record OrderLabRequest(
        String patientId,
        String testType,
        String priority,
        String orderedBy
    ) {}

    record LabOrder(
        String labId,
        String patientId,
        String testType,
        String priority,
        String status,
        String orderedAt,
        String orderedBy
    ) {}

    record LabResult(
        String labId,
        String result,
        String interpretation,
        String referenceRange,
        String reportedAt
    ) {}

    record RecordImmunizationRequest(
        String patientId,
        String vaccine,
        String lotNumber,
        String administeredAt,
        String administeredBy
    ) {}

    record Immunization(
        String immunizationId,
        String patientId,
        String vaccine,
        String lotNumber,
        String administeredAt,
        String administeredBy,
        String recordedAt
    ) {}

    record ImmunizationSchedule(
        String patientId,
        List<ScheduledImmunization> scheduled
    ) {}

    record ScheduledImmunization(
        String vaccine,
        LocalDate dueDate,
        String status
    ) {}

    record UploadDocumentRequest(
        String patientId,
        String documentType,
        String fileName,
        String contentType,
        byte[] content
    ) {}

    record Document(
        String documentId,
        String patientId,
        String documentType,
        String fileName,
        String contentType,
        String uploadedAt,
        String uploadedBy
    ) {}

    record DocumentMetadata(
        String documentType,
        String classification,
        Map<String, String> tags
    ) {}
}
