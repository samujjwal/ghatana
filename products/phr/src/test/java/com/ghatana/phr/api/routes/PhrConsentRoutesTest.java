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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrConsentRoutes}.
 *
 * @doc.type class
 * @doc.purpose Consent enforcement matrix for PHR consent routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrConsentRoutes - enforcement matrix")
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

        lenient().when(consentService.getPatientGrants(anyString()))
            .thenReturn(Promise.of(List.of()));
        lenient().when(consentService.validateAccess(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(
                true, "GRANT_VALID", "grant-42")));
        lenient().when(consentService.getGrantByIdempotencyKey(anyString()))
            .thenReturn(Promise.of(Optional.empty()));
    }

    @Nested
    @DisplayName("POST /grants - create consent grant")
    class CreateGrant {

        @BeforeEach
        void allowCreateGrant() {
            lenient().when(consentService.createGrant(any()))
                .thenReturn(Promise.of(sampleGrant()));
        }

        @Test
        @DisplayName("201 - patient may create their own grant")
        void patientMayCreateOwnGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "patient-1", "patient", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            verify(consentService).createGrant(any());
        }

        @Test
        @DisplayName("200 - idempotent replay returns matching existing grant")
        void idempotentReplayReturnsExistingGrant() throws Exception {
            lenient().when(consentService.getGrantByIdempotencyKey("idem-consent-1"))
                .thenReturn(Promise.of(Optional.of(sampleGrant("patient-1", "clinician-1", "idem-consent-1"))));
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "patient-1", "patient", GRANT_BODY, "idem-consent-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(consentService, never()).createGrant(any());
        }

        @Test
        @DisplayName("403 - idempotency lookup does not bypass patient ownership")
        void idempotencyLookupDoesNotBypassOwnership() throws Exception {
            lenient().when(consentService.getGrantByIdempotencyKey("idem-consent-2"))
                .thenReturn(Promise.of(Optional.of(sampleGrant("patient-1", "clinician-1", "idem-consent-2"))));
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "patient-2", "patient", GRANT_BODY, "idem-consent-2");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            verify(consentService, never()).getGrantByIdempotencyKey("idem-consent-2");
            verify(consentService, never()).createGrant(any());
        }

        @Test
        @DisplayName("409 - idempotency key reuse must match original consent grant")
        void idempotencyKeyReuseMustMatchOriginalGrant() throws Exception {
            lenient().when(consentService.getGrantByIdempotencyKey("idem-consent-3"))
                .thenReturn(Promise.of(Optional.of(sampleGrant("patient-1", "other-clinician", "idem-consent-3"))));
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "patient-1", "patient", GRANT_BODY, "idem-consent-3");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(409);
            verify(consentService, never()).createGrant(any());
        }

        @Test
        @DisplayName("201 - admin may create grant for any patient")
        void adminMayCreateGrantForAnyPatient() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "admin-1", "admin", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("403 - clinician may not create consent grants")
        void clinicianMayNotCreateGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "dr-1", "clinician", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("403 - caregiver may not create consent grants")
        void caregiverMayNotCreateGrant() throws Exception {
            HttpRequest request = contextRequestWithBody(HttpMethod.POST, "/grants",
                "tenant-1", "cg-1", "caregiver", GRANT_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 - missing context headers")
        void returns400WhenContextMissing() throws Exception {
            HttpRequest request = HttpRequest.post("http://localhost/grants")
                .withBody(GRANT_BODY.getBytes(StandardCharsets.UTF_8))
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /grants/:grantId/revoke - revoke consent grant")
    class RevokeGrant {

        @BeforeEach
        void allowRevokeGrant() {
            lenient().when(consentService.revokeGrant(anyString()))
                .thenReturn(Promise.of((Void) null));
        }

        @Test
        @DisplayName("200 - patient may revoke their own grant")
        void patientMayRevokeOwnGrant() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(consentService).revokeGrant("grant-42");
        }

        @Test
        @DisplayName("200 - admin may revoke any grant")
        void adminMayRevokeGrant() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 - clinician may not revoke consent grants")
        void clinicianMayNotRevoke() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("403 - caregiver may not revoke consent grants")
        void caregiverMayNotRevoke() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "cg-1", "caregiver");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("200 - revoke echoes request correlation ID")
        void revokeEchoesCorrelationId() throws Exception {
            consentService.revokeGrant("grant-42");
            HttpRequest request = contextRequest(HttpMethod.POST,
                "/grants/grant-42/revoke?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }
    }

    @Nested
    @DisplayName("GET /check - consent validation")
    class CheckConsent {

        @Test
        @DisplayName("200 - patient may check consent access")
        void patientMayCheckConsent() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs",
                "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 - clinician may check their own consent access")
        void clinicianMayCheckOwnConsent() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs",
                "tenant-1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 - check uses explicit action scope when provided")
        void checkUsesExplicitActionScope() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs&action=WRITE",
                "tenant-1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(consentService).validateAccess("patient-1", "clinician-1", "labs", "WRITE");
        }

        @Test
        @DisplayName("403 - clinician may not check another accessor grant")
        void clinicianMayNotCheckAnotherAccessorGrant() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/check?patientId=patient-1&accessorId=other-clinician&resourceType=labs",
                "tenant-1", "clinician-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            verify(consentService, never()).validateAccess("patient-1", "other-clinician", "labs", "READ");
        }

        @Test
        @DisplayName("400 - missing required query parameters")
        void returns400WhenQueryParamsMissing() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/check", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("200 - check echoes request correlation ID")
        void checkEchoesCorrelationId() throws Exception {
            consentService.validateAccess("patient-1", "clinician-1", "labs", "READ");
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/check?patientId=patient-1&accessorId=clinician-1&resourceType=labs",
                "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }
    }

    @Nested
    @DisplayName("GET / - list consent grants")
    class ListGrants {

        @Test
        @DisplayName("200 - patient may list own grants")
        void patientMayListOwnGrants() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("200 - admin may list grants for any patient")
        void adminMayListGrants() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/?patientId=patient-1", "tenant-1", "admin-1", "admin");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("403 - clinician may not list patient grants directly")
        void clinicianMayNotListPatientGrants() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/?patientId=patient-1", "tenant-1", "dr-1", "clinician");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
        }

        @Test
        @DisplayName("400 - missing patientId query parameter")
        void returns400WhenPatientIdMissing() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("200 - list echoes request correlation ID")
        void listEchoesCorrelationId() throws Exception {
            consentService.getPatientGrants("patient-1");
            HttpRequest request = contextRequest(HttpMethod.GET,
                "/?patientId=patient-1", "tenant-1", "patient-1", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }
    }

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
        return contextRequestWithBody(method, path, tenantId, principalId, role, body, null);
    }

    private static HttpRequest contextRequestWithBody(
            HttpMethod method,
            String path,
            String tenantId,
            String principalId,
            String role,
            String body,
            String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        if (idempotencyKey != null) {
            builder.withHeader(HttpHeaders.of("X-Idempotency-Key"), idempotencyKey);
        }
        return builder
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static ConsentManagementService.ConsentGrant sampleGrant() {
        return sampleGrant("patient-1", "clinician-1", null);
    }

    private static ConsentManagementService.ConsentGrant sampleGrant(
            String patientId,
            String recipientId,
            String idempotencyKey) {
        return new ConsentManagementService.ConsentGrant(
            "grant-42",
            patientId,
            recipientId,
            new ConsentManagementService.ConsentScope(
                Set.of("labs"), false, Set.of(), Set.of("read"), Map.of()),
            "ACTIVE",
            Instant.now(),
            Instant.parse("2099-01-01T00:00:00Z"),
            null,
            idempotencyKey
        );
    }
}
