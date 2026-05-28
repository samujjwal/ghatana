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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrDocumentImagingRoutes}.
 *
 * <p>Verifies that document and imaging endpoints enforce access control:
 * <ul>
 *   <li>Patient may access their own documents.</li>
 *   <li>Clinician may access documents via consent.</li>
 *   <li>400 is returned when required context headers are absent.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Document imaging routes enforcement matrix: verifies access control for document and imaging APIs
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrDocumentImagingRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrDocumentImagingRoutesTest extends EventloopTestBase {

    @Mock
    private DocumentService documentService;

    @Mock
    private ImagingService imagingService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private TreatmentRelationshipService treatmentRelationshipService;

    @Mock
    private FchvCommunityAssignmentService fchvCommunityAssignmentService;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrPolicyEvaluator policyEvaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvCommunityAssignmentService
        );
        servlet = new PhrDocumentImagingRoutes(
            eventloop(), documentService, imagingService, consentService, policyEvaluator
        ).getServlet();

        lenient().when(consentService.validateAccess(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(
                true, "GRANT_VALID", "grant-42")));
        lenient().when(treatmentRelationshipService.hasActiveTreatmentRelationship(anyString(), anyString()))
            .thenReturn(Promise.of(true));
        lenient().when(fchvCommunityAssignmentService.hasCommunityAccess(anyString(), anyString()))
            .thenReturn(Promise.of(true));
        lenient().when(documentService.getPatientDocuments(anyString(), anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(documentService.getDocument(anyString(), anyString()))
            .thenReturn(Promise.of(Optional.empty()));
        lenient().when(imagingService.getPatientOrders(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(imagingService.getPatientStudies(anyString()))
            .thenReturn(Promise.of(List.of()));
    }

    @Nested
    @DisplayName("GET /documents — list documents")
    class ListDocuments {

        @Test
        @DisplayName("200 — patient may list their own documents")
        void patientMayListOwnDocuments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — clinician with consent may list patient documents")
        void clinicianWithConsentMayListDocuments() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/documents/", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/documents/?patientId=patient-1").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("GET /imaging — list imaging studies")
    class ListImagingStudies {

        @Test
        @DisplayName("200 — patient may list their own imaging studies")
        void patientMayListOwnStudies() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/imaging/studies?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/imaging/studies", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}
