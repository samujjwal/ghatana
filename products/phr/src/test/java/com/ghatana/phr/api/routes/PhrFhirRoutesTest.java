package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.api.FhirController;
import com.ghatana.phr.api.PhrApiResponse;
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
import static org.mockito.Mockito.lenient;

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

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        servlet = new PhrFhirRoutes(eventloop(), fhirController).getServlet();

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
                .withBody("{\"resourceType\":\"Patient\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(201);
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
                .withBody("{\"resourceType\":\"Observation\"}".getBytes(StandardCharsets.UTF_8))
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(422);
        }
    }

    private static HttpRequest authenticatedGet(String url) {
        return HttpRequest.get(url)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-health-1")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "patient-1")
            .withHeader(HttpHeaders.of("X-Role"), "patient")
            .build();
    }
}
