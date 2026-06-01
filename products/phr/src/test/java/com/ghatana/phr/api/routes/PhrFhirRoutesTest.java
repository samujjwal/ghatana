package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.api.PhrApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enforcement matrix tests for {@link PhrFhirRoutes}.
 *
 * <p>Verifies that FHIR R4 endpoints delegate to the controller and return
 * appropriate HTTP status codes:
 * <ul>
 *   <li>GET /:resourceType/:id proxies the controller's get response.</li>
 *   <li>GET /:resourceType proxies the controller's search response.</li>
 *   <li>POST /:resourceType proxies the controller's create response.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose FHIR routes enforcement matrix: verifies controller delegation and HTTP status proxying
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrFhirRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrFhirRoutesTest extends EventloopTestBase {

    @Mock
    private FhirController fhirController;

    @Mock
    private PhrPolicyEvaluator policyEvaluator;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrFhirRoutes(eventloop(), fhirController, policyEvaluator).getServlet();

        lenient().when(policyEvaluator.canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.allowed("FHIR_POLICY_ALLOWED", "FHIR access allowed")));
        lenient().when(fhirController.getResource(anyString(), anyString()))
            .thenReturn(Promise.of(PhrApiResponse.fhirJson(200, "{\"resourceType\":\"Patient\"}")));
        lenient().when(fhirController.searchResources(anyString(), any()))
            .thenReturn(Promise.of(PhrApiResponse.fhirJson(200, "{\"resourceType\":\"Bundle\"}")));
        lenient().when(fhirController.createResource(anyString(), anyString()))
            .thenReturn(Promise.of(PhrApiResponse.fhirJson(201, "{\"resourceType\":\"Patient\"}")));
    }

    @Nested
    @DisplayName("GET /:resourceType/:id — retrieve FHIR resource")
    class GetResource {

        @Test
        @DisplayName("200 — retrieves a FHIR resource by type and ID")
        void retrievesFhirResourceById() throws Exception {
            HttpRequest request = authenticatedGet("http://localhost/Patient/patient-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("fhir-Patient"), eq("READ"), eq("tenant-health-1"), any());
            verify(fhirController).getResource("Patient", "patient-1");
        }

        @Test
        @DisplayName("403 — denies before controller when policy rejects the FHIR read")
        void deniesFhirReadWhenPolicyRejects() throws Exception {
            when(policyEvaluator.canAccessPhiResourceAsync(
                    any(), eq("patient-1"), eq("fhir-Patient"), eq("READ"), eq("tenant-health-1"), any()))
                .thenReturn(Promise.of(PhrPolicyEvaluator.PolicyDecision.denied(
                    "FHIR_PATIENT_SCOPE_DENIED", "FHIR patient scope denied")));
            HttpRequest request = authenticatedGet("http://localhost/Patient/patient-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(fhirController, never()).getResource(anyString(), anyString());
        }

        @Test
        @DisplayName("400 — rejects unsupported FHIR resource types before policy")
        void rejectsUnsupportedResourceTypeBeforePolicy() throws Exception {
            HttpRequest request = authenticatedGet("http://localhost/UnsupportedResource/patient-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator, never()).canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fhirController, never()).getResource(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /:resourceType — search FHIR resources")
    class SearchResources {

        @Test
        @DisplayName("200 — searches FHIR resources of the given type")
        void searchesFhirResources() throws Exception {
            HttpRequest request = authenticatedGet("http://localhost/Observation?patient=patient-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("fhir-Observation"), eq("SEARCH"), eq("tenant-health-1"), any());
            verify(fhirController).searchResources(eq("Observation"), any());
        }

        @Test
        @DisplayName("400 — requires patient scope for non-Patient FHIR searches")
        void requiresPatientScopeForSearch() throws Exception {
            HttpRequest request = authenticatedGet("http://localhost/Observation");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator, never()).canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fhirController, never()).searchResources(anyString(), any());
        }

        @Test
        @DisplayName("400 — rejects unsupported FHIR search resource types before controller")
        void rejectsUnsupportedSearchResourceTypeBeforeController() throws Exception {
            HttpRequest request = authenticatedGet("http://localhost/UnsupportedResource?patient=patient-1");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator, never()).canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fhirController, never()).searchResources(anyString(), any());
        }
    }

    @Nested
    @DisplayName("POST /:resourceType — create FHIR resource")
    class CreateResource {

        @Test
        @DisplayName("201 — creates a FHIR resource")
        void createsFhirResource() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/Patient")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/fhir+json")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
                .withHeader(HttpHeaders.of("X-Role"), "patient")
                .withHeader(HttpHeaders.of("X-Persona"), "patient")
                .withHeader(HttpHeaders.of("X-Tier"), "core")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .withBody("{\"resourceType\":\"Patient\",\"id\":\"patient-1\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator).canAccessPhiResourceAsync(
                any(), eq("patient-1"), eq("fhir-Patient"), eq("WRITE"), eq("tenant-health-1"), any());
            verify(fhirController).createResource(eq("Patient"), anyString());
        }

        @Test
        @DisplayName("proxies non-200 controller status codes")
        void proxiesControllerErrorStatusCode() throws Exception {
            lenient().when(fhirController.createResource(anyString(), anyString()))
                .thenReturn(Promise.of(PhrApiResponse.fhirJson(422, "{\"error\":\"invalid\"}")));

            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/Observation")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/fhir+json")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
                .withHeader(HttpHeaders.of("X-Role"), "patient")
                .withHeader(HttpHeaders.of("X-Persona"), "patient")
                .withHeader(HttpHeaders.of("X-Tier"), "core")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .withBody("""
                    {"resourceType":"Observation","subject":{"reference":"Patient/patient-1"}}
                    """.getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(422);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
        }

        @Test
        @DisplayName("400 — rejects FHIR create when body resourceType does not match route")
        void rejectsResourceTypeMismatch() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/Observation")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/fhir+json")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
                .withHeader(HttpHeaders.of("X-Role"), "patient")
                .withHeader(HttpHeaders.of("X-Persona"), "patient")
                .withHeader(HttpHeaders.of("X-Tier"), "core")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .withBody("{\"resourceType\":\"Patient\",\"id\":\"patient-1\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator, never()).canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fhirController, never()).createResource(anyString(), anyString());
        }

        @Test
        @DisplayName("400 — rejects unsupported FHIR create resource types before parsing body")
        void rejectsUnsupportedCreateResourceTypeBeforeParsingBody() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/UnsupportedResource")
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/fhir+json")
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
                .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
                .withHeader(HttpHeaders.of("X-Role"), "patient")
                .withHeader(HttpHeaders.of("X-Persona"), "patient")
                .withHeader(HttpHeaders.of("X-Tier"), "core")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
                .withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
            verify(policyEvaluator, never()).canAccessPhiResourceAsync(
                any(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fhirController, never()).createResource(anyString(), anyString());
        }
    }

    @Test
    @DisplayName("401 — missing context echoes request correlation ID")
    void missingContextEchoesCorrelationId() throws Exception {
        HttpRequest request = HttpRequest.get("http://localhost/Patient/patient-1")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();

        HttpResponse response = runPromise(() -> servlet.serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("test-corr-1");
    }

    private static HttpRequest authenticatedGet(String url) {
        return HttpRequest.get(url)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .withHeader(HttpHeaders.of("X-Persona"), "patient")
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "test-corr-1")
            .build();
    }
}
