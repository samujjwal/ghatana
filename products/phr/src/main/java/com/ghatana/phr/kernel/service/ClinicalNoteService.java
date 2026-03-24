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
 * Clinical Notes Service for PHR.
 *
 * <p>Manages structured and free-text clinical documentation including SOAP notes
 * (Subjective, Objective, Assessment, Plan), progress notes, discharge summaries,
 * and referral letters. Notes are stored as FHIR R4 Composition-compatible records
 * and comply with the Nepal Medical Council documentation standards.</p>
 *
 * @doc.type class
 * @doc.purpose PHR clinical notes — SOAP notes, progress notes, discharge summaries
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ClinicalNoteService {

    private static final String NOTE_DATASET = "phr.clinical.notes";
    private static final String AUDIT_DATASET = "phr.clinical.audit";

    private final DataCloudKernelAdapter dataCloud;
    private volatile boolean running = false;

    /**
     * Constructs a ClinicalNoteService.
     *
     * @param context kernel context providing DataCloudKernelAdapter
     */
    public ClinicalNoteService(KernelContext context) {
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
        return "clinical-note";
    }

    // ==================== Core Operations ====================

    /**
     * Creates a new SOAP note for a clinical encounter.
     *
     * @param note the SOAP note to persist
     * @return Promise containing the stored note with generated ID
     */
    public Promise<SoapNote> createSoapNote(SoapNote note) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        Objects.requireNonNull(note.patientId(), "patientId");
        Objects.requireNonNull(note.authorId(), "authorId");

        String id = note.id() != null ? note.id() : generateId("note");
        SoapNote toStore = new SoapNote(
                id,
                note.patientId(),
                note.encounterId(),
                note.authorId(),
                note.subjective(),
                note.objective(),
                note.assessment(),
                note.plan(),
                Instant.now(),
                note.lastUpdatedAt() != null ? note.lastUpdatedAt() : Instant.now(),
                NoteStatus.DRAFT
        );

        DataWriteRequest request = new DataWriteRequest(
                NOTE_DATASET,
                id,
                TypedDataSerializer.toBytes(toStore, "SoapNote", 1),
                Map.of(
                        "patientId", toStore.patientId(),
                        "type", "SOAP",
                        "status", toStore.status().name()
                )
        );

        return dataCloud.writeData(request)
                .then($ -> audit("CREATE_SOAP_NOTE", toStore.patientId(),
                        "SOAP note created by " + toStore.authorId()))
                .map($ -> toStore);
    }

    /**
     * Finalizes (signs) a draft SOAP note, making it immutable.
     *
     * @param noteId   the note identifier
     * @param signedBy the provider ID signing the note
     * @return Promise containing the finalized note
     */
    public Promise<SoapNote> signNote(String noteId, String signedBy) {
        if (!running) {
            return Promise.ofException(new IllegalStateException("Service not running"));
        }

        return getSoapNote(noteId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.<SoapNote>ofException(
                                new IllegalStateException("Note not found: " + noteId));
                    }
                    SoapNote existing = opt.get();
                    if (existing.status() == NoteStatus.FINAL) {
                        return Promise.of(existing); // Idempotent
                    }
                    SoapNote signed = new SoapNote(
                            existing.id(), existing.patientId(), existing.encounterId(),
                            existing.authorId(), existing.subjective(), existing.objective(),
                            existing.assessment(), existing.plan(),
                            existing.createdAt(), Instant.now(), NoteStatus.FINAL
                    );
                    DataWriteRequest req = new DataWriteRequest(
                            NOTE_DATASET, noteId,
                            TypedDataSerializer.toBytes(signed, "SoapNote", 1),
                            Map.of("status", "FINAL", "signedBy", signedBy)
                    );
                    return dataCloud.writeData(req)
                            .then($ -> audit("SIGN_NOTE", signed.patientId(),
                                    "SOAP note signed by " + signedBy))
                            .map($ -> signed);
                });
    }

    /**
     * Retrieves a SOAP note by ID.
     *
     * @param noteId the note identifier
     * @return Promise containing the note if found
     */
    public Promise<Optional<SoapNote>> getSoapNote(String noteId) {
        if (!running) {
            return Promise.of(Optional.empty());
        }

        return dataCloud.readData(new DataReadRequest(NOTE_DATASET, noteId, Map.of()))
                .map(result -> {
                    if (result == null || result.getData() == null) return Optional.empty();
                    return Optional.ofNullable(TypedDataSerializer.fromBytes(result.getData(), SoapNote.class));
                })
                .whenException(e -> Promise.of(Optional.empty()));
    }

    /**
     * Returns all clinical notes for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all notes in reverse chronological order
     */
    public Promise<List<SoapNote>> getPatientNotes(String patientId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                NOTE_DATASET,
                "patientId = :patientId",
                Map.of("patientId", patientId),
                1000,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), SoapNote.class))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                        .toList());
    }

    /**
     * Returns notes associated with a specific encounter.
     *
     * @param encounterId the encounter identifier
     * @return Promise containing notes for the encounter
     */
    public Promise<List<SoapNote>> getEncounterNotes(String encounterId) {
        if (!running) {
            return Promise.of(List.of());
        }

        return dataCloud.queryData(new DataQueryRequest(
                NOTE_DATASET,
                "encounterId = :encounterId",
                Map.of("encounterId", encounterId),
                100,
                0
        )).map(QueryResult::getResults)
                .map(results -> results.stream()
                        .map(r -> TypedDataSerializer.fromBytes(r.getData(), SoapNote.class))
                        .filter(Objects::nonNull)
                        .toList());
    }

    // ==================== Private Helpers ====================

    private Promise<Void> initializeDatasets() {
        Promise<Void> notes = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                NOTE_DATASET,
                Map.of("id", "string", "patientId", "string", "type", "string",
                        "status", "string", "createdAt", "timestamp"),
                Map.of("retention", "25years")
        )).whenException(e -> {});

        Promise<Void> audit = dataCloud.createSchema(new DataCloudKernelAdapter.SchemaCreateRequest(
                AUDIT_DATASET,
                Map.of("action", "string", "patientId", "string", "timestamp", "timestamp"),
                Map.of("retention", "25years")
        )).whenException(e -> {});

        return Promises.all(notes, audit).map($ -> null);
    }

    private Promise<Void> audit(String action, String patientId, String details) {
        String auditId = generateId("aud");
        record AuditEntry(String id, Instant ts, String action, String patient, String details) {}
        return dataCloud.writeData(new DataWriteRequest(
                AUDIT_DATASET, auditId,
                TypedDataSerializer.toBytes(
                        new AuditEntry(auditId, Instant.now(), action, patientId, details),
                        "ClinicalNoteAuditEntry", 1),
                Map.of("timestamp", Instant.now().toString())
        )).whenException(e -> {});
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // ==================== Inner Types ====================

    /**
     * A structured SOAP clinical note.
     *
     * @param id            unique note identifier
     * @param patientId     patient this note pertains to
     * @param encounterId   the encounter during which the note was written (may be null)
     * @param authorId      provider / clinician who authored the note
     * @param subjective    patient's reported symptoms and history
     * @param objective     clinician's observations, vitals, and examination findings
     * @param assessment    differential diagnosis and working diagnosis
     * @param plan          treatment plan, orders, follow-up instructions
     * @param createdAt     note creation timestamp
     * @param lastUpdatedAt last modification timestamp
     * @param status        note lifecycle status
     */
    public record SoapNote(
            String id,
            String patientId,
            String encounterId,
            String authorId,
            String subjective,
            String objective,
            String assessment,
            String plan,
            Instant createdAt,
            Instant lastUpdatedAt,
            NoteStatus status
    ) {}

    /** Lifecycle status for clinical notes. */
    public enum NoteStatus {
        /** Note is in draft — can still be edited. */
        DRAFT,
        /** Note has been signed and is final. */
        FINAL,
        /** Note has been amended post-signing. */
        AMENDED,
        /** Note was entered in error. */
        ENTERED_IN_ERROR
    }
}
