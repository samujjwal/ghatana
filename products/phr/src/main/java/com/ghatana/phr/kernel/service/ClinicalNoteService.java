package com.ghatana.phr.kernel.service;

import com.ghatana.kernel.context.KernelContext;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
public class ClinicalNoteService extends PhrServiceBase {

    private static final String NOTE_DATASET = "phr.clinical.notes";

    public ClinicalNoteService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "clinical-notes";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            NOTE_DATASET,
            Map.of("id", "string", "patientId", "string", "type", "string",
                "status", "string", "createdAt", "timestamp"),
            Map.of("retention", "25years")
        );
    }

    // ==================== Core Operations ====================

    /**
     * Creates a new SOAP note for a clinical encounter.
     *
     * @param note the SOAP note to persist
     * @return Promise containing the stored note with generated ID
     */
    public Promise<SoapNote> createSoapNote(SoapNote note) {
        ensureRunning();

        String patientId = PhrInputSanitizationUtils.requireSafeIdentifier(note.patientId(), "patientId");
        String authorId = PhrInputSanitizationUtils.requireSafeIdentifier(note.authorId(), "authorId");
        String encounterId = note.encounterId() == null
            ? null
            : PhrInputSanitizationUtils.requireSafeIdentifier(note.encounterId(), "encounterId");
        String subjective = PhrInputSanitizationUtils.sanitizeRequiredText(note.subjective(), "subjective", 4000);
        String objective = PhrInputSanitizationUtils.sanitizeRequiredText(note.objective(), "objective", 4000);
        String assessment = PhrInputSanitizationUtils.sanitizeRequiredText(note.assessment(), "assessment", 4000);
        String plan = PhrInputSanitizationUtils.sanitizeRequiredText(note.plan(), "plan", 4000);

        String id = note.id() != null ? note.id() : generateId("note");
        SoapNote toStore = new SoapNote(
            id,
            patientId,
            encounterId,
            authorId,
            subjective,
            objective,
            assessment,
            plan,
            NoteStatus.DRAFT,
            null,
            null,
            Instant.now()
        );

        return createRecord(
            NOTE_DATASET,
            id,
            toStore,
            Map.of(
                "patientId", toStore.patientId(),
                "type", "SOAP",
                "status", toStore.status().name()
            ),
            "SoapNote",
            1
        ).then(stored -> audit("CREATE_SOAP_NOTE", stored.patientId(),
            "SOAP note created by " + stored.authorId())
            .map($ -> stored));
    }

    /**
     * Finalizes (signs) a draft SOAP note, making it immutable.
     *
     * @param noteId   the note identifier
     * @param signedBy the provider ID signing the note
     * @return Promise containing the finalized note
     */
    public Promise<SoapNote> signNote(String noteId, String signedBy) {
        ensureRunning();

        String sanitizedNoteId = PhrInputSanitizationUtils.requireSafeIdentifier(noteId, "noteId");
        String sanitizedSignedBy = PhrInputSanitizationUtils.requireSafeIdentifier(signedBy, "signedBy");

        return getSoapNote(sanitizedNoteId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.<SoapNote>ofException(
                        new IllegalStateException("Note not found: " + sanitizedNoteId));
                }
                SoapNote existing = opt.get();
                if (existing.status() == NoteStatus.FINAL) {
                    return Promise.of(existing); // Idempotent
                }
                SoapNote signed = new SoapNote(
                    existing.id(), existing.patientId(), existing.encounterId(),
                    existing.authorId(), existing.subjective(), existing.objective(),
                    existing.assessment(), existing.plan(),
                    NoteStatus.FINAL, sanitizedSignedBy, Instant.now(), existing.createdAt()
                );
                return updateRecord(
                    NOTE_DATASET,
                    sanitizedNoteId,
                    signed,
                    Map.of("status", "FINAL", "signedBy", sanitizedSignedBy),
                    "SoapNote",
                    1
                ).then(updated -> audit("SIGN_NOTE", updated.patientId(),
                    "SOAP note signed by " + sanitizedSignedBy)
                    .map($ -> updated));
            });
    }

    /**
     * Retrieves a SOAP note by ID.
     *
     * @param noteId the note identifier
     * @return Promise containing the note if found
     */
    public Promise<Optional<SoapNote>> getSoapNote(String noteId) {
        ensureRunning();
        return readRecord(NOTE_DATASET, noteId, SoapNote.class);
    }

    /**
     * Returns all clinical notes for a patient.
     *
     * @param patientId the patient identifier
     * @return Promise containing all notes in reverse chronological order
     */
    public Promise<List<SoapNote>> getPatientNotes(String patientId) {
        ensureRunning();

        return queryRecords(
            NOTE_DATASET,
            "patientId = :patientId",
            Map.of("patientId", patientId),
            1000,
            0,
            SoapNote.class
        ).map(notes -> notes.stream()
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
        ensureRunning();

        return queryRecords(
            NOTE_DATASET,
            "encounterId = :encounterId",
            Map.of("encounterId", encounterId),
            100,
            0,
            SoapNote.class
        );
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
            NoteStatus status,
            String signedBy,
            Instant signedAt,
            Instant createdAt
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
