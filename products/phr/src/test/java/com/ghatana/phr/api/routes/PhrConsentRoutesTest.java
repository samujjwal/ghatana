package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrConsentRoutes}.
 *
 * <p>Verifies the role-based access control invariants for all consent endpoints:
 * <ul>
 *   <li>Patient may create and revoke their own consent grants.</li>
 *   <li>Admin may create and revoke consent grants for any patient.</li>
 *   <li>Clinician may view/check grants but may NOT create or revoke.</li>
 *   <li>Caregiver may view grants for patients they have access to but may NOT create or revoke.</li>
 *   <li>400 is returned when required context headers are missing.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Consent enforcement matrix: verifies RBAC policy for all consent routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrConsentRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrConsentRoutesTest extends EventloopTestBase {

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private TreatmentRelationshipService treatmentRelationshipService;

    @Mock
    private FchvCommunityAssignmentService fchvCommunityAssignmentService;

    private AsyncServlet servlet;

    private static final String GRANT_BODY = """
        {
          "patientId": "patient-1",
          "recipientId": "clinician-1",
          "scope": {
            "resourceTypes": ["labs"],
            "allDocuments": false,
            "specificDocumentIds": [],
            "actions": ["read"]
          },
          "expiresAt": "2099-01-01T00:00:00Z"
        }
        """;

    @BeforeEach
    void setUp() {
        servlet = new PhrConsentRoutes(
            eventloop(),
            consentService,
            new PhrPolicyEvaluator(consentService, treatmentRelationshipService, fchvCommunityAssignmentService)
        ).getServlet();

        // Stub read-only operations used across multiple tests — lenient because
        // not every test invokes every read path.
        lenient().when(consentService.getPatientGrants(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(consentService.validateAccess(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(
                true, "GRANT_VALID", "grant-42")));
    }

    // ── POST /grants ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /grants — create consent grant")
    class CreateGrant {

        @BeforeEach
        void stubCreateGrant() {
            lenient().when(consentService.createGrant(any()))
                .thenReturn(Promise.of(stubGrant()));
        }

        @Test
        @DisplayName("201 — patient may create their own grant")
        void patientMayCreateOwnGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants", "tenant-1", "patient-1", "patient", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            verify(consentService).createGrant(any());
        }

        @Test
        @DisplayName("201 — admin may create grant for any patient")
        void adminMayCreateGrantForAnyPatient() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants", "tenant-1", "admin-1", "admin", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("403 — clinician may NOT create consent grants")
        void clinicianMayNotCreateGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants", "tenant-1", "dr-1", "clinician", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("403 — caregiver may NOT create consent grants")
        void caregiverMayNotCreateGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants", "tenant-1", "cg-1", "caregiver", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 — missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.post("http://localhost/grants")
                .withBody(GRANT_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    // ── POST /grants/:grantId/revoke ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /grants/:grantId/revoke — revoke consent grant")
    class RevokeGrant {

        @BeforeEach
        void stubRevokeGrant() {
            lenient().when(consentService.revokeGrant(anyString()))
                .thenReturn(Promise.of((Void) null));
        }

        @Test
        @DisplayName("200 — patient may revoke their own grant")
        void patientMayRevokeOwnGrant() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST, "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(consentService).revokeGrant("grant-42");
        }

        @Test
        @DisplayName("200 — admin may revoke any grant")
        void adminMayRevokeGrant() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST, "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 — clinician may NOT revoke consent grants")
        void clinicianMayNotRevoke() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST, "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("403 — caregiver may NOT revoke consent grants")
        void caregiverMayNotRevoke() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST, "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "cg-1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.POST, "/grants/grant-42/revoke", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    // ── GET /check — consent validation ───────────────────────────────────────

    @Nested
    @DisplayName("GET /check — consent validation (read-only)")
    class CheckConsent {

        @Test
        @DisplayName("200 — patient may check consent access")
        void patientMayCheckConsent() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs",
                "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — clinician may check consent access")
        void clinicianMayCheckConsent() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs",
                "tenant-1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — check uses explicit action scope when provided")
        void checkUsesExplicitActionScope() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs&action=WRITE",
                "tenant-1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(consentService).validateAccess("patient-1", "clinician-1", "labs", "WRITE");
        }

        @Test
        @DisplayName("400 — missing required query parameters")
        void returns400WhenQueryParamsMissing() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/check", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    // ── GET / — list grants ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET / — list consent grants (read-only)")
    class ListGrants {

        @Test
        @DisplayName("200 — patient may list own grants")
        void patientMayListOwnGrants() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 — admin may list grants for any patient")
        void adminMayListGrants() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?patientId=patient-1", "tenant-1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 — clinician may NOT list patient grants directly")
        void clinicianMayNotListPatientGrants() throws Exception {
            HttpRequest request = contextRequest(
                HttpMethod.GET, "/?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 — missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "patient-1", "patient");

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

    private static HttpRequest contextRequestWithBody(
            HttpMethod method, String path, String tenantId, String principalId, String role, String body) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .build();
    }

    private static ConsentManagementService.ConsentGrant stubGrant() {
        return new ConsentManagementService.ConsentGrant(
            "grant-42",
            "patient-1",
            "clinician-1",
            new ConsentManagementService.ConsentScope(
                Set.of("labs"), false, Set.of(), Set.of("read"), Map.of()),
            "ACTIVE",
            Instant.now(),
            Instant.now().plusSeconds(86400),
            null,
            null
        );
    }
}
