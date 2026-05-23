package com.ghatana.phr.application.clinical;

import com.ghatana.phr.application.patient.PatientOperationContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of ClinicalService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides clinical data management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class ClinicalServiceImpl implements ClinicalService {

    private final ConcurrentMap<String, Encounter> encounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Medication> medications = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Allergy> allergies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Condition> conditions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LabOrder> labOrders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LabResult> labResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Immunization> immunizations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Document> documents = new ConcurrentHashMap<>();

    @Override
    public Promise<Encounter> createEncounter(PatientOperationContext ctx, CreateEncounterRequest request) {
        String encounterId = "ENC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Encounter encounter = new Encounter(
            encounterId,
            request.patientId(),
            request.encounterType(),
            request.participant(),
            request.location(),
            "IN_PROGRESS",
            Instant.now().toString(),
            null
        );
        encounters.put(encounterId, encounter);
        return Promise.of(encounter);
    }

    @Override
    public Promise<Optional<Encounter>> getEncounter(PatientOperationContext ctx, String encounterId) {
        return Promise.of(Optional.ofNullable(encounters.get(encounterId)));
    }

    @Override
    public Promise<Encounter> updateEncounter(PatientOperationContext ctx, String encounterId, UpdateEncounterRequest request) {
        Encounter existing = encounters.get(encounterId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Encounter not found: " + encounterId));
        }
        Encounter updated = new Encounter(
            encounterId,
            existing.patientId(),
            request.encounterType() != null ? request.encounterType() : existing.encounterType(),
            request.participant() != null ? request.participant() : existing.participant(),
            existing.location(),
            existing.status(),
            existing.createdAt(),
            existing.completedAt()
        );
        encounters.put(encounterId, updated);
        return Promise.of(updated);
    }

    @Override
    public Promise<Encounter> completeEncounter(PatientOperationContext ctx, String encounterId) {
        Encounter existing = encounters.get(encounterId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Encounter not found: " + encounterId));
        }
        Encounter completed = new Encounter(
            encounterId,
            existing.patientId(),
            existing.encounterType(),
            existing.participant(),
            existing.location(),
            "COMPLETED",
            existing.createdAt(),
            Instant.now().toString()
        );
        encounters.put(encounterId, completed);
        return Promise.of(completed);
    }

    @Override
    public Promise<List<Encounter>> listEncounters(PatientOperationContext ctx, String patientId) {
        return Promise.of(encounters.values().stream()
            .filter(e -> e.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<Medication> prescribeMedication(PatientOperationContext ctx, PrescribeMedicationRequest request) {
        String medicationId = "MED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Medication medication = new Medication(
            medicationId,
            request.patientId(),
            request.medicationName(),
            request.dosage(),
            request.frequency(),
            request.quantity(),
            "ACTIVE",
            Instant.now().toString()
        );
        medications.put(medicationId, medication);
        return Promise.of(medication);
    }

    @Override
    public Promise<Optional<Medication>> getMedication(PatientOperationContext ctx, String medicationId) {
        return Promise.of(Optional.ofNullable(medications.get(medicationId)));
    }

    @Override
    public Promise<Medication> recordAdministration(PatientOperationContext ctx, String medicationId, AdministrationRecord record) {
        Medication existing = medications.get(medicationId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Medication not found: " + medicationId));
        }
        // In a real implementation, this would record the administration
        return Promise.of(existing);
    }

    @Override
    public Promise<List<Medication>> listMedications(PatientOperationContext ctx, String patientId) {
        return Promise.of(medications.values().stream()
            .filter(m -> m.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<Medication> requestRefill(PatientOperationContext ctx, String medicationId) {
        Medication existing = medications.get(medicationId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Medication not found: " + medicationId));
        }
        // In a real implementation, this would process the refill request
        return Promise.of(existing);
    }

    @Override
    public Promise<Allergy> recordAllergy(PatientOperationContext ctx, RecordAllergyRequest request) {
        String allergyId = "ALLERGY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Allergy allergy = new Allergy(
            allergyId,
            request.patientId(),
            request.allergen(),
            request.severity(),
            request.reaction(),
            Instant.now().toString()
        );
        allergies.put(allergyId, allergy);
        return Promise.of(allergy);
    }

    @Override
    public Promise<Optional<Allergy>> getAllergy(PatientOperationContext ctx, String allergyId) {
        return Promise.of(Optional.ofNullable(allergies.get(allergyId)));
    }

    @Override
    public Promise<List<Allergy>> listAllergies(PatientOperationContext ctx, String patientId) {
        return Promise.of(allergies.values().stream()
            .filter(a -> a.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<AllergyCrossCheck> getMedicationCrossCheck(PatientOperationContext ctx, String patientId) {
        AllergyCrossCheck crossCheck = new AllergyCrossCheck(
            patientId,
            List.of("Penicillin", "Sulfa"),
            List.of("Aspirin - mild interaction")
        );
        return Promise.of(crossCheck);
    }

    @Override
    public Promise<Condition> recordCondition(PatientOperationContext ctx, RecordConditionRequest request) {
        String conditionId = "COND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Condition condition = new Condition(
            conditionId,
            request.patientId(),
            request.conditionName(),
            request.code(),
            request.severity(),
            request.status(),
            Instant.now().toString(),
            null
        );
        conditions.put(conditionId, condition);
        return Promise.of(condition);
    }

    @Override
    public Promise<Optional<Condition>> getCondition(PatientOperationContext ctx, String conditionId) {
        return Promise.of(Optional.ofNullable(conditions.get(conditionId)));
    }

    @Override
    public Promise<Condition> resolveCondition(PatientOperationContext ctx, String conditionId) {
        Condition existing = conditions.get(conditionId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Condition not found: " + conditionId));
        }
        Condition resolved = new Condition(
            conditionId,
            existing.patientId(),
            existing.conditionName(),
            existing.code(),
            existing.severity(),
            "RESOLVED",
            existing.recordedAt(),
            Instant.now().toString()
        );
        conditions.put(conditionId, resolved);
        return Promise.of(resolved);
    }

    @Override
    public Promise<List<Condition>> listConditions(PatientOperationContext ctx, String patientId) {
        return Promise.of(conditions.values().stream()
            .filter(c -> c.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<LabOrder> orderLab(PatientOperationContext ctx, OrderLabRequest request) {
        String labId = "LAB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LabOrder labOrder = new LabOrder(
            labId,
            request.patientId(),
            request.testType(),
            request.priority(),
            "PENDING",
            Instant.now().toString(),
            request.orderedBy()
        );
        labOrders.put(labId, labOrder);
        return Promise.of(labOrder);
    }

    @Override
    public Promise<Optional<LabOrder>> getLabOrder(PatientOperationContext ctx, String labId) {
        return Promise.of(Optional.ofNullable(labOrders.get(labId)));
    }

    @Override
    public Promise<LabResult> recordLabResult(PatientOperationContext ctx, String labId, LabResult result) {
        labResults.put(labId, result);
        return Promise.of(result);
    }

    @Override
    public Promise<Optional<LabResult>> getLabResult(PatientOperationContext ctx, String labId) {
        return Promise.of(Optional.ofNullable(labResults.get(labId)));
    }

    @Override
    public Promise<List<LabOrder>> listLabOrders(PatientOperationContext ctx, String patientId) {
        return Promise.of(labOrders.values().stream()
            .filter(l -> l.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<Immunization> recordImmunization(PatientOperationContext ctx, RecordImmunizationRequest request) {
        String immunizationId = "IMM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Immunization immunization = new Immunization(
            immunizationId,
            request.patientId(),
            request.vaccine(),
            request.lotNumber(),
            request.administeredAt(),
            request.administeredBy(),
            Instant.now().toString()
        );
        immunizations.put(immunizationId, immunization);
        return Promise.of(immunization);
    }

    @Override
    public Promise<Optional<Immunization>> getImmunization(PatientOperationContext ctx, String immunizationId) {
        return Promise.of(Optional.ofNullable(immunizations.get(immunizationId)));
    }

    @Override
    public Promise<List<Immunization>> listImmunizations(PatientOperationContext ctx, String patientId) {
        return Promise.of(immunizations.values().stream()
            .filter(i -> i.patientId().equals(patientId))
            .toList());
    }

    @Override
    public Promise<ImmunizationSchedule> getImmunizationSchedule(PatientOperationContext ctx, String patientId) {
        ImmunizationSchedule schedule = new ImmunizationSchedule(
            patientId,
            List.of(
                new ScheduledImmunization("Influenza", java.time.LocalDate.now().plusMonths(6), "DUE"),
                new ScheduledImmunization("Tetanus", java.time.LocalDate.now().plusYears(1), "DUE")
            )
        );
        return Promise.of(schedule);
    }

    @Override
    public Promise<String> generateCertificate(PatientOperationContext ctx, String patientId) {
        return Promise.of("CERT-" + UUID.randomUUID().toString());
    }

    @Override
    public Promise<Document> uploadDocument(PatientOperationContext ctx, UploadDocumentRequest request) {
        String documentId = "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Document document = new Document(
            documentId,
            request.patientId(),
            request.documentType(),
            request.fileName(),
            request.contentType(),
            Instant.now().toString(),
            ctx.userId()
        );
        documents.put(documentId, document);
        return Promise.of(document);
    }

    @Override
    public Promise<Optional<Document>> getDocument(PatientOperationContext ctx, String documentId) {
        return Promise.of(Optional.ofNullable(documents.get(documentId)));
    }

    @Override
    public Promise<Document> updateDocumentMetadata(PatientOperationContext ctx, String documentId, DocumentMetadata metadata) {
        Document existing = documents.get(documentId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Document not found: " + documentId));
        }
        // In a real implementation, this would update the metadata
        return Promise.of(existing);
    }

    @Override
    public Promise<List<Document>> listDocuments(PatientOperationContext ctx, String patientId) {
        return Promise.of(documents.values().stream()
            .filter(d -> d.patientId().equals(patientId))
            .toList());
    }
}
