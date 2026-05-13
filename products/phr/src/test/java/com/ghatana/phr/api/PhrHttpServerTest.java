package com.ghatana.phr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.RoleEvaluator;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP routing conformance tests for {@link PhrHttpServer}.
 *
 * <p>Exercises the ActiveJ {@link RoutingServlet} wired by
 * {@link PhrHttpServer#getServlet()} end-to-end through the servlet layer.
 * Uses the in-memory FHIR server and stub data-cloud adapter so no real
 * HTTP port is bound.
 *
 * @doc.type class
 * @doc.purpose End-to-end route conformance for PhrHttpServer FHIR and health routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrHttpServer — route conformance")
class PhrHttpServerTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AsyncServlet servlet;
    private PhrHttpServer server;

    @BeforeEach
    void setUp() {
        PhrTestInfrastructure.StubDataCloudAdapter dataCloud =
            new PhrTestInfrastructure.StubDataCloudAdapter();
        PhrFhirR4Server fhirServer =
            new PhrFhirR4Server(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(fhirServer::start);

        FhirController controller = new FhirController(fhirServer);
        
        // Create route objects with eventloop
        PhrFhirRoutes fhirRoutes = new PhrFhirRoutes(eventloop(), controller);
        PhrHealthRoutes healthRoutes = new PhrHealthRoutes(eventloop(), fhirServer);
        RouteEntitlementEvaluator routeEntitlementEvaluator = new RouteEntitlementEvaluator(new RoleEvaluator.FailClosed());
        IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache = new IdentityAwareBoundedCache<>(1000, 300);
        PhrEntitlementRoutes entitlementRoutes = new PhrEntitlementRoutes(eventloop(), routeEntitlementEvaluator, entitlementCache);
        
        server = new PhrHttpServer(eventloop(), fhirRoutes, entitlementRoutes, healthRoutes);
        runPromise(server::start);
        servlet = server.getServlet();
    }

    // -----------------------------------------------------------------------
    // /health
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /health")
    class HealthRoute {

        @Test
        @DisplayName("returns 200 when server is started")
        void healthReturns200WhenStarted() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/health", null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("status").asText()).isEqualTo("UP");
        }

        @Test
        @DisplayName("health response body contains service field")
        void healthResponseContainsServiceField() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/health", null);

            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.has("service")).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // /ready
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /ready")
    class ReadyRoute {

        @Test
        @DisplayName("returns 200 when server and FHIR layer are started")
        void readyReturns200WhenStarted() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/ready", null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("ready").asBoolean()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // GET /route-entitlements
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /route-entitlements")
    class RouteEntitlementRoute {

        @Test
        @DisplayName("returns ProductRouteEntitlement-shaped payload")
        void returnsProductRouteEntitlementShape() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("product").asText()).isEqualTo("phr");
            assertThat(body.path("principalId").asText()).isEqualTo("anonymous");
            assertThat(body.path("tenantId").asText()).isEqualTo("default");
            assertThat(body.path("role").asText()).isEqualTo("patient");
            assertThat(body.path("routes").isArray()).isTrue();
            assertThat(body.path("actions").isArray()).isTrue();
            assertThat(body.path("cards").isArray()).isTrue();
        }

        @Test
        @DisplayName("patient role is denied clinician-only emergency route")
        void patientRoleDoesNotReceiveEmergencyRoute() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null,
                Map.of("X-Role", "patient"));

            JsonNode body = JSON.readTree(bodyString(response));
            JsonNode routes = body.path("routes");
            JsonNode actions = body.path("actions");
            assertThat(routePaths(routes)).doesNotContain("/emergency");
            assertThat(actionIds(actions)).doesNotContain("break-glass-review");
        }

        @Test
        @DisplayName("clinician role receives emergency route")
        void clinicianRoleReceivesEmergencyRoute() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null,
                Map.of("X-Role", "clinician"));

            JsonNode body = JSON.readTree(bodyString(response));
            JsonNode routes = body.path("routes");
            JsonNode actions = body.path("actions");
            assertThat(routePaths(routes)).contains("/emergency");
            assertThat(actionIds(actions)).contains("break-glass-review");
        }

        @Test
        @DisplayName("unknown role fails closed to patient-scoped routes")
        void unknownRoleFailsClosedToPatientRoutes() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null,
                Map.of("X-Role", "superuser"));

            JsonNode routes = JSON.readTree(bodyString(response)).path("routes");
            assertThat(routePaths(routes)).contains("/dashboard", "/records");
            assertThat(routePaths(routes)).doesNotContain("/labs", "/emergency");
        }

        @Test
        @DisplayName("propagates tenant, persona, and tier headers")
        void propagatesEntitlementContextHeaders() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null,
                Map.of(
                    "X-Tenant-ID", "tenant-health-1",
                    "X-Principal-ID", "principal-1",
                    "X-Persona", "clinician",
                    "X-Tier", "clinical"
                ));

            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("tenantId").asText()).isEqualTo("tenant-health-1");
            assertThat(body.path("principalId").asText()).isEqualTo("principal-1");
            assertThat(body.path("persona").asText()).isEqualTo("clinician");
            assertThat(body.path("tier").asText()).isEqualTo("clinical");
        }
    }

    // -----------------------------------------------------------------------
    // POST /fhir/:resourceType
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("POST /fhir/:resourceType")
    class FhirCreateRoute {

        @Test
        @DisplayName("returns 201 and application/json content-type for Patient")
        void createPatientReturns201() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/fhir/Patient", patientJson());

            assertThat(response.getCode()).isEqualTo(201);
            assertThat(response.getHeader(HttpHeaders.of("Content-Type"))).contains("application/json");
        }

        @Test
        @DisplayName("created Patient body contains resourceType=Patient")
        void createPatientBodyHasResourceType() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/fhir/Patient", patientJson());

            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("resourceType").asText()).isEqualTo("Patient");
        }

        @Test
        @DisplayName("returns 201 for MedicationRequest")
        void createMedicationRequestReturns201() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/fhir/MedicationRequest",
                medicationRequestJson());

            assertThat(response.getCode()).isEqualTo(201);
        }

        @Test
        @DisplayName("returns 404 OperationOutcome for unsupported resource type")
        void unsupportedResourceTypeReturnsOperationOutcome() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/fhir/Encounter",
                "{\"resourceType\":\"Encounter\"}");

            assertThat(response.getCode()).isEqualTo(404);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("resourceType").asText()).isEqualTo("OperationOutcome");
        }
    }

    // -----------------------------------------------------------------------
    // GET /fhir/:resourceType/:id
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /fhir/:resourceType/:id")
    class FhirReadRoute {

        @Test
        @DisplayName("reads a previously created Patient by id")
        void readCreatedPatient() throws Exception {
            HttpResponse created = dispatch(HttpMethod.POST, "/fhir/Patient", patientJson());
            String id = JSON.readTree(bodyString(created)).path("id").asText();

            HttpResponse response = dispatch(HttpMethod.GET, "/fhir/Patient/" + id, null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("id").asText()).isEqualTo(id);
            assertThat(body.path("resourceType").asText()).isEqualTo("Patient");
        }

        @Test
        @DisplayName("returns 404 for non-existent resource id")
        void readNonExistentPatientReturns404() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/fhir/Patient/does-not-exist", null);

            assertThat(response.getCode()).isEqualTo(404);
        }
    }

    // -----------------------------------------------------------------------
    // GET /fhir/:resourceType (search)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /fhir/:resourceType (search)")
    class FhirSearchRoute {

        @Test
        @DisplayName("returns a FHIR Bundle searchset")
        void searchReturnsBundle() throws Exception {
            dispatch(HttpMethod.POST, "/fhir/Observation", observationJson("obs-1", "patient-10"));

            HttpResponse response = dispatch(HttpMethod.GET, "/fhir/Observation?subject=patient-10", null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("resourceType").asText()).isEqualTo("Bundle");
            assertThat(body.path("type").asText()).isEqualTo("searchset");
        }

        @Test
        @DisplayName("search with no matches returns empty bundle")
        void searchWithNoMatchesReturnsEmptyBundle() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/fhir/Observation?subject=nobody", null);

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("total").asInt()).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HttpResponse dispatch(HttpMethod method, String path, String jsonBody)
            throws Exception {
        return dispatch(method, path, jsonBody, Map.of());
    }

    private HttpResponse dispatch(HttpMethod method, String path, String jsonBody, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.builder(method, "http://localhost" + path);
        headers.forEach((name, value) -> builder.withHeader(HttpHeaders.of(name), value));
        if (jsonBody != null) {
            builder.withBody(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return runPromise(() -> servlet.serve(builder.build()));
    }

    private static java.util.List<String> routePaths(JsonNode routes) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        routes.forEach(route -> paths.add(route.path("path").asText()));
        return paths;
    }

    private static java.util.List<String> actionIds(JsonNode actions) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        actions.forEach(action -> ids.add(action.path("id").asText()));
        return ids;
    }

    private String bodyString(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String patientJson() {
        return """
            {
              "resourceType": "Patient",
              "name": [{"family": "Test", "given": ["Route"]}],
              "gender": "male",
              "birthDate": "1990-01-01"
            }
            """;
    }

    private static String medicationRequestJson() {
        return """
            {
              "resourceType": "MedicationRequest",
              "status": "active",
              "intent": "order",
              "subject": {"reference": "Patient/patient-1"},
              "medicationCodeableConcept": {
                "coding": [{"system": "http://www.nlm.nih.gov/research/umls/rxnorm", "code": "197361"}]
              }
            }
            """;
    }

    private static String observationJson(String id, String patientId) {
        return """
            {
              "resourceType": "Observation",
              "id": "%s",
              "status": "final",
              "code": {"coding": [{"system": "http://loinc.org", "code": "2160-0"}]},
              "subject": {"reference": "Patient/%s"},
              "valueQuantity": {"value": 95.0, "unit": "mmHg", "system": "http://unitsofmeasure.org"}
            }
            """.formatted(id, patientId);
    }
}
