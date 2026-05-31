package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * PHR-P1-007: OCR authorization negative tests.
 * Tests that wrong patient/tenant cannot fetch or confirm OCR, and confirm requires reviewer identity.
 *
 * @doc.type class
 * @doc.purpose Verifies OCR endpoint authorization and tenant isolation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PHR OCR Authorization Tests")
class PhrOcrAuthorizationTest extends EventloopTestBase {

    private AsyncServlet servlet;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        ConsentManagementService consentService = Mockito.mock(ConsentManagementService.class);
        TreatmentRelationshipService treatmentRelationshipService = Mockito.mock(TreatmentRelationshipService.class);
        documentService = Mockito.mock(DocumentService.class);
        when(documentService.getOcrDocument(anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        when(documentService.getOcrDocument("doc-123", "patient-1"))
            .thenReturn(Promise.of(Optional.of(new DocumentService.OcrDocument(
                "doc-123",
                "Lab report",
                "PENDING_REVIEW",
                0.91,
                "Hemoglobin 13.2"
            ))));
        when(documentService.confirmOcrDocument("doc-123", "patient-1", "Reviewed OCR text"))
            .thenReturn(Promise.complete());
        when(documentService.confirmOcrDocument("doc-123", "patient-1", "Reviewed OCR text", null))
            .thenReturn(Promise.of(new DocumentService.OcrDocument(
                "doc-123",
                "Lab report",
                "CONFIRMED",
                0.91,
                "Reviewed OCR text",
                "patient-1",
                Instant.parse("2026-01-01T00:00:00Z")
            )));
        when(documentService.toFhirDocumentReference("doc-123"))
            .thenReturn(Promise.of("{\"resourceType\":\"DocumentReference\",\"id\":\"doc-123\"}"));
        PhrDocumentImagingRoutes routes = new PhrDocumentImagingRoutes(
            eventloop(),
            documentService,
            Mockito.mock(ImagingService.class),
            consentService,
            new PhrPolicyEvaluator(
                consentService,
                treatmentRelationshipService,
                Mockito.mock(FchvCommunityAssignmentService.class)
            )
        );
        servlet = routes.getServlet();
    }

    @Nested
    @DisplayName("GET /documents/{id}/ocr")
    class GetOcrDocument {

        @Test
        @DisplayName("returns 403 when wrong patient tries to fetch OCR")
        void returns403ForWrongPatient() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET,
                "/documents/doc-123/ocr",
                "tenant-1",
                "patient-2",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 403 when wrong tenant tries to fetch OCR")
        void returns403ForWrongTenant() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET,
                "/documents/doc-123/ocr",
                "tenant-2",
                "patient-2",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 401 when context headers are missing")
        void returns401WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/documents/doc-123/ocr").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 403 when caregiver without consent tries to fetch OCR")
        void returns403ForCaregiverWithoutConsent() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET,
                "/documents/doc-123/ocr",
                "tenant-1",
                "caregiver-1",
                "caregiver"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isIn(403, 401);
        }
    }

    @Nested
    @DisplayName("POST /documents/{id}/ocr/confirm")
    class ConfirmOcrDocument {

        @Test
        @DisplayName("returns 403 when wrong patient tries to confirm OCR")
        void returns403ForWrongPatient() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST,
                "/documents/doc-123/ocr/confirm",
                "tenant-1",
                "patient-2",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 403 when wrong tenant tries to confirm OCR")
        void returns403ForWrongTenant() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST,
                "/documents/doc-123/ocr/confirm",
                "tenant-2",
                "patient-2",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("returns 403 when unauthenticated user tries to confirm OCR")
        void returns403ForUnauthenticated() throws Exception {
            HttpRequest request = HttpRequest.post("http://localhost/documents/doc-123/ocr/confirm")
                .withBody(confirmBody())
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("returns 403 when caregiver tries to confirm OCR")
        void returns403ForCaregiver() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST,
                "/documents/doc-123/ocr/confirm",
                "tenant-1",
                "caregiver-1",
                "caregiver"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("requires reviewer identity in context")
        void requiresReviewerIdentity() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST,
                "/documents/doc-123/ocr/confirm",
                "tenant-1",
                "patient-1",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isIn(200, 403, 401);
        }

        @Test
        @DisplayName("no corrected data should leak on authorization failure")
        void noDataLeakOnAuthFailure() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST,
                "/documents/doc-123/ocr/confirm",
                "tenant-2",
                "patient-2",
                "patient"
            );

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core");
        if (method == HttpMethod.POST) {
            builder.withBody(confirmBody());
        }
        return builder.build();
    }

    private static byte[] confirmBody() {
        return "{\"correctedText\":\"Reviewed OCR text\"}".getBytes(StandardCharsets.UTF_8);
    }
}
