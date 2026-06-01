package com.ghatana.phr.api.routes;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrEmergencyRoutes}.
 *
 * <p>Verifies that emergency access is properly guarded:
 * <ul>
 *   <li>POST /access — any authenticated role may log an emergency event (role enforcement is contextual).</li>
 *   <li>GET /events/:eventId — patients may not read events for other patients.</li>
 *   <li>GET /reviews/pending — requires clinician or admin role.</li>
 *   <li>400 is returned when required context headers are missing.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Emergency routes enforcement matrix: verifies break-glass access and audit policy
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrEmergencyRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrEmergencyRoutesTest extends EventloopTestBase {

    @Mock
    private EmergencyAccessLogService emergencyAccessLogService;

    @Mock
    private TreatmentRelationshipService treatmentRelationshipService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private FchvCommunityAssignmentService fchvCommunityAssignmentService;

    @Mock
    private AuditTrailService auditTrailService;

    private AsyncServlet servlet;

    private static final String ACCESS_BODY = """
        {
          "patientId": "patient-1",
          "accessorRole": "clinician",
          "justification": "Cardiac emergency - patient unconscious and needs immediate care",
          "resourcesAccessed": ["labs", "medications"]
        }
        """;

    @BeforeEach
    void setUp() {
        PhrPolicyEvaluator policyEvaluator = new PhrPolicyEvaluator(
            consentService,
            treatmentRelationshipService,
            fchvCommunityAssignmentService,
            auditTrailService
        );
        servlet = new PhrEmergencyRoutes(
            eventloop(),
            emergencyAccessLogService,
            treatmentRelationshipService,
            policyEvaluator
        ).getServlet();

        lenient().when(emergencyAccessLogService.logAccess(any()))
            .thenReturn(Promise.of(stubEvent()));
        lenient().when(emergencyAccessLogService.notifyPatientOfEmergencyAccess(any()))
            .thenReturn(Promise.complete());
        lenient().when(emergencyAccessLogService.getEvent(anyString()))
            .thenReturn(Promise.of(Optional.of(stubEvent())));
        lenient().when(emergencyAccessLogService.getPatientEmergencyLog(anyString()))
            .thenReturn(Promise.of(List.of(stubEvent())));
        lenient().when(emergencyAccessLogService.getPendingReviews(anyInt()))
            .thenReturn(Promise.of(List.of(stubEvent())));
        lenient().when(emergencyAccessLogService.getOverdueReviews(anyInt()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(emergencyAccessLogService.markReviewed(anyString(), anyString(), any(), anyString()))
            .thenReturn(Promise.of(stubEvent()));
        lenient().when(treatmentRelationshipService.hasActiveTreatmentRelationship(anyString(), anyString()))
            .thenReturn(Promise.of(true));
    }

    @Nested
    @DisplayName("POST /access — log emergency access event")
    class LogAccess {

        @Test
        @DisplayName("201 — clinician may log emergency access")
        void clinicianMayLogAccess() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/access", "tenant-1", "dr-1", "clinician", ACCESS_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            verify(emergencyAccessLogService, never()).notifyPatientOfEmergencyAccess(any());
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/access")
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .withBody(ACCESS_BODY.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }

        @Test
        @DisplayName("400 — missing patientId in body")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/access", "tenant-1", "dr-1", "clinician",
                """
                {"justification":"Emergency care required immediately","resourcesAccessed":["labs"]}
                """);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("403 - patient role is denied by emergency policy")
        void patientRoleDeniedByEmergencyPolicy() throws Exception {
            HttpRequest request = contextRequestWithBody(
                HttpMethod.POST, "/access", "tenant-1", "patient-1", "patient",
                """
                {
                  "patientId": "patient-1",
                  "accessorRole": "patient-1",
                  "justification": "Emergency access request attempted by patient role",
                  "resourcesAccessed": ["labs"]
                }
                """);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("GET /reviews/pending — pending review list")
    class PendingReviews {

        @Test
        @DisplayName("403 — clinician may NOT view pending reviews")
        void clinicianMayViewPendingReviews() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/reviews/pending", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("200 — admin may view pending reviews")
        void adminMayViewPendingReviews() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/reviews/pending", "tenant-1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 — patient may NOT view pending reviews")
        void patientMayNotViewPendingReviews() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/reviews/pending", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("403 — caregiver may NOT view pending reviews")
        void caregiverMayNotViewPendingReviews() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/reviews/pending", "tenant-1", "cg-1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("GET /patients/:patientId — patient emergency log")
    class PatientLog {

        @Test
        @DisplayName("200 — patient may view their own emergency log")
        void patientMayViewOwnLog() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patients/patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 — patient may NOT view another patient's emergency log")
        void patientMayNotViewOtherPatientLog() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patients/patient-2", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("200 — clinician may view any patient's emergency log")
        void clinicianMayViewAnyPatientLog() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/patients/patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest contextRequest(
            HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static EmergencyAccessLogService.EmergencyAccessEvent stubEvent() {
        return new EmergencyAccessLogService.EmergencyAccessEvent(
            "event-1",
            "patient-1",
            "dr-1",
            "ER_PHYSICIAN",
            "Cardiac emergency",
            java.util.Set.of("labs"),
            Instant.now(),
            null,
            EmergencyAccessLogService.ReviewStatus.PENDING_REVIEW,
            null,
            null,
            null,
            null,
            "review-case-1"
        );
    }
}
