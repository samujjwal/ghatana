package com.ghatana.phr.kernel.service;

import com.ghatana.phr.kernel.service.ClinicalNoteService.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ClinicalNoteService}.
 *
 * @doc.type class
 * @doc.purpose Tests for PHR clinical note service — SOAP notes, sign lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ClinicalNoteService")
class ClinicalNoteServiceTest extends EventloopTestBase {

    private ClinicalNoteService service;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
                new PhrTestInfrastructure.StubDataCloudAdapter();
        service = new ClinicalNoteService(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(service::start);
    }

    @Nested
    @DisplayName("service lifecycle")
    class Lifecycle {

        @Test
        void healthyAfterStart() {
            assertTrue(service.isHealthy());
        }

        @Test
        void serviceName() {
            assertEquals("clinical-notes", service.getName());
        }
    }

    @Nested
    @DisplayName("createSoapNote")
    class CreateTests {

        @Test
        @DisplayName("creates note in DRAFT status")
        void createsAsDraft() {
            SoapNote note = buildNote("patient-1", "enc-1", "author-1", null);

            SoapNote stored = runPromise(() -> service.createSoapNote(note));

            assertNotNull(stored.id());
            assertThat(stored.status()).isEqualTo(NoteStatus.DRAFT);
            assertThat(stored.patientId()).isEqualTo("patient-1");
        }

        @Test
        @DisplayName("rejects null patientId")
        void rejectsNullPatient() {
            SoapNote note = buildNote(null, "enc-1", "author-1", null);
            assertThrows(Exception.class, () -> runPromise(() -> service.createSoapNote(note)));
            clearFatalError();
        }

        @Test
        @DisplayName("sanitizes SOAP note sections")
        void sanitizesSoapSections() {
            SoapNote note = new SoapNote(
                null,
                "patient-1",
                "enc-1",
                "author-1",
                "<script>alert('xss')</script>",
                "<b>Objective</b>",
                "<img src=x onerror=alert(1)>",
                "<iframe>plan</iframe>",
                NoteStatus.DRAFT,
                null,
                null,
                null
            );

            SoapNote stored = runPromise(() -> service.createSoapNote(note));

            assertThat(stored.subjective()).isEqualTo("&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;");
            assertThat(stored.objective()).isEqualTo("&lt;b&gt;Objective&lt;/b&gt;");
            assertThat(stored.assessment()).isEqualTo("&lt;img src=x onerror=alert(1)&gt;");
            assertThat(stored.plan()).isEqualTo("&lt;iframe&gt;plan&lt;/iframe&gt;");
        }
    }

    @Nested
    @DisplayName("signNote")
    class SignTests {

        @Test
        @DisplayName("transitions DRAFT to FINAL")
        void draftToFinal() {
            SoapNote draft = runPromise(() -> service.createSoapNote(buildNote("p1", "e1", "auth", null)));

            SoapNote signed = runPromise(() -> service.signNote(draft.id(), "attending-dr"));

            assertThat(signed.status()).isEqualTo(NoteStatus.FINAL);
            assertThat(signed.signedBy()).isEqualTo("attending-dr");
            assertNotNull(signed.signedAt());
        }

        @Test
        @DisplayName("idempotent for already-FINAL notes")
        void idempotentOnFinal() {
            SoapNote draft = runPromise(() -> service.createSoapNote(buildNote("p1", "e1", "auth", null)));
            SoapNote signed = runPromise(() -> service.signNote(draft.id(), "dr-1"));

            // Sign again — should return same note without error
            SoapNote signedAgain = runPromise(() -> service.signNote(draft.id(), "dr-2"));
            assertThat(signedAgain.status()).isEqualTo(NoteStatus.FINAL);
            assertThat(signedAgain.signedBy()).isEqualTo("dr-1"); // first signer preserved
        }

        @Test
        @DisplayName("throws for unknown note id")
        void throwsForUnknownId() {
            assertThrows(Exception.class, () -> runPromise(() -> service.signNote("ghost", "dr")));
            clearFatalError();
        }
    }

    @Nested
    @DisplayName("getSoapNote")
    class GetTests {

        @Test
        @DisplayName("returns stored note by id")
        void returnsById() {
            SoapNote created = runPromise(() ->
                    service.createSoapNote(buildNote("p1", "e1", "auth", null)));

            Optional<SoapNote> found = runPromise(() -> service.getSoapNote(created.id()));

            assertTrue(found.isPresent());
            assertThat(found.get().id()).isEqualTo(created.id());
        }

        @Test
        @DisplayName("returns empty for unknown id")
        void returnsEmpty() {
            assertTrue(runPromise(() -> service.getSoapNote("no-such-id")).isEmpty());
        }
    }

    @Nested
    @DisplayName("getPatientNotes")
    class PatientNoteTests {

        @Test
        @DisplayName("returns all notes for patient sorted newest-first")
        void sortedNewestFirst() {
            runPromise(() -> service.createSoapNote(buildNote("patient-X", "encounter-1", "dr", null)));
            runPromise(() -> service.createSoapNote(buildNote("patient-X", "encounter-2", "dr", null)));
            runPromise(() -> service.createSoapNote(buildNote("patient-Y", "encounter-3", "dr", null)));

            List<SoapNote> notes = runPromise(() -> service.getPatientNotes("patient-X"));

            assertThat(notes).hasSize(2);
            assertThat(notes).allMatch(n -> "patient-X".equals(n.patientId()));
        }
    }

    // ─────────────────────── Helpers ──────────────────────────────────────────

    private static SoapNote buildNote(String patientId, String encounterId,
                                       String authorId, String id) {
        return new SoapNote(id, patientId, encounterId, authorId,
                "Patient reports headache", "BP 120/80, HR 72",
                "Tension headache", "Rest and ibuprofen",
                NoteStatus.DRAFT, null, null, null);
    }
}
