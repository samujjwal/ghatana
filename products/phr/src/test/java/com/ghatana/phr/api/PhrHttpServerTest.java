package com.ghatana.phr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessNotificationSender;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewAuditLogger;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewCase;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewWorkflow;
import com.ghatana.phr.kernel.service.ImmunizationService;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.MedicationService;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.PatientRecordService;
import com.ghatana.phr.kernel.service.PhrTestInfrastructure;
import com.ghatana.phr.kernel.service.PhrNotificationSender;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.IdentityAwareBoundedCache;
import com.ghatana.platform.http.security.RoleEvaluator;
import com.ghatana.platform.http.security.RouteEntitlementEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.api.routes.PhrAdministrativeRoutes;
import com.ghatana.phr.api.routes.PhrClinicalRoutes;
import com.ghatana.phr.api.routes.PhrConsentRoutes;
import com.ghatana.phr.api.routes.PhrDocumentImagingRoutes;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrEmergencyRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import com.ghatana.phr.api.routes.PhrPatientRecordRoutes;
import com.ghatana.phr.api.routes.PhrReleaseReadinessRoutes;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
        DistributedCachePort<String, ConsentManagementService.ConsentCacheEntry> consentCache =
            new TestDistributedCache<>();
        PhrFhirR4Server fhirServer =
            new PhrFhirR4Server(PhrTestInfrastructure.createTestContext(dataCloud));
        runPromise(fhirServer::start);

        FhirController controller = new FhirController(fhirServer);
        PatientRecordService patientRecordService = new PatientRecordService(PhrTestInfrastructure.createTestContext(dataCloud));
        LabResultService labResultService = new LabResultService(PhrTestInfrastructure.createTestContext(dataCloud));
        MedicationService medicationService = new MedicationService(PhrTestInfrastructure.createTestContext(dataCloud));
        ImmunizationService immunizationService = new ImmunizationService(PhrTestInfrastructure.createTestContext(dataCloud));
        AppointmentService appointmentService = new AppointmentService(PhrTestInfrastructure.createTestContext(
            dataCloud,
            Map.of(PhrNotificationSender.class, new NoopNotificationSender())
        ));
        TelemedicineService telemedicineService = new TelemedicineService(PhrTestInfrastructure.createTestContext(
            dataCloud,
            Map.of(PhrNotificationSender.class, new NoopNotificationSender())
        ));
        ReferralService referralService = new ReferralService(PhrTestInfrastructure.createTestContext(dataCloud));
        BillingService billingService = new BillingService(PhrTestInfrastructure.createTestContext(dataCloud));
        DocumentService documentService = new DocumentService(PhrTestInfrastructure.createTestContext(dataCloud));
        ImagingService imagingService = new ImagingService(PhrTestInfrastructure.createTestContext(dataCloud));
        EmergencyAccessLogService emergencyAccessLogService = new EmergencyAccessLogService(
            PhrTestInfrastructure.createTestContext(dataCloud),
            new EmergencyAccessReviewWorkflow(new NoopEmergencyAccessNotificationSender(), new NoopEmergencyAccessAuditLogger())
        );
        ConsentManagementService consentService = new ConsentManagementService(
            PhrTestInfrastructure.createTestContext(
                dataCloud,
                Map.of(PhrNotificationSender.class, new NoopNotificationSender())
            ),
            consentCache
        );
        runPromise(patientRecordService::start);
        runPromise(labResultService::start);
        runPromise(medicationService::start);
        runPromise(immunizationService::start);
        runPromise(appointmentService::start);
        runPromise(telemedicineService::start);
        runPromise(referralService::start);
        runPromise(billingService::start);
        runPromise(documentService::start);
        runPromise(imagingService::start);
        runPromise(emergencyAccessLogService::start);
        runPromise(consentService::start);

        // Create route objects with eventloop
        PhrFhirRoutes fhirRoutes = new PhrFhirRoutes(eventloop(), controller);
        PhrPatientRecordRoutes patientRecordRoutes =
            new PhrPatientRecordRoutes(eventloop(), patientRecordService, consentService);
        PhrConsentRoutes consentRoutes = new PhrConsentRoutes(eventloop(), consentService);
        PhrClinicalRoutes clinicalRoutes = new PhrClinicalRoutes(
            eventloop(),
            labResultService,
            medicationService,
            immunizationService,
            consentService
        );
        PhrEmergencyRoutes emergencyRoutes = new PhrEmergencyRoutes(eventloop(), emergencyAccessLogService);
        PhrAdministrativeRoutes administrativeRoutes = new PhrAdministrativeRoutes(
            eventloop(),
            appointmentService,
            telemedicineService,
            referralService,
            billingService,
            consentService
        );
        PhrDocumentImagingRoutes documentImagingRoutes = new PhrDocumentImagingRoutes(
            eventloop(),
            documentService,
            imagingService,
            consentService
        );
        PhrHealthRoutes healthRoutes = new PhrHealthRoutes(eventloop(), fhirServer);
        PhrReleaseReadinessRoutes releaseReadinessRoutes = new PhrReleaseReadinessRoutes(eventloop());
        RouteEntitlementEvaluator routeEntitlementEvaluator = new RouteEntitlementEvaluator(new RoleEvaluator.FailClosed());
        IdentityAwareBoundedCache<String, Map<String, Object>> entitlementCache = new IdentityAwareBoundedCache<>(1000, 300);
        PhrEntitlementRoutes entitlementRoutes = new PhrEntitlementRoutes(eventloop(), routeEntitlementEvaluator, entitlementCache);

        server = new PhrHttpServer(
            eventloop(),
            fhirRoutes,
            patientRecordRoutes,
            consentRoutes,
            clinicalRoutes,
            emergencyRoutes,
            administrativeRoutes,
            documentImagingRoutes,
            releaseReadinessRoutes,
            entitlementRoutes,
            healthRoutes
        );
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
        @DisplayName("admin role receives release readiness cockpit route")
        void adminRoleReceivesReleaseReadinessRoute() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/route-entitlements", null,
                Map.of("X-Role", "admin"));

            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(routePaths(body.path("routes"))).contains("/release-readiness");
            assertThat(actionIds(body.path("actions"))).contains("view-release-readiness");
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
    // GET /release-readiness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GET /release-readiness")
    class ReleaseReadinessRoute {

        @Test
        @DisplayName("requires tenant and principal headers")
        void requiresTenantAndPrincipalHeaders() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/release-readiness", null);

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("error").asText()).isEqualTo("MISSING_CONTEXT");
        }

        @Test
        @DisplayName("requires admin role")
        void requiresAdminRole() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/release-readiness", null,
                phrHeaders("clinician-1", "clinician"));

            assertThat(response.getCode()).isEqualTo(403);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("error").asText()).isEqualTo("PHR_RELEASE_READINESS_FORBIDDEN");
        }

        @Test
        @DisplayName("returns runtime truth release sections for admin")
        void returnsReleaseReadinessForAdmin() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/release-readiness?environment=staging", null,
                phrHeaders("admin-1", "admin"));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("product").asText()).isEqualTo("phr");
            assertThat(body.path("tenantId").asText()).isEqualTo("tenant-health-1");
            assertThat(body.path("environment").asText()).isEqualTo("staging");
            assertThat(body.path("sections").path("evidenceFreshness").path("runtimeProven").asBoolean()).isTrue();
            assertThat(body.path("sections").path("fhirRuntime").path("details").path("resourcesSupported").isArray()).isTrue();
            assertThat(body.path("sections").path("consentCache").path("runtimeProven").asBoolean()).isTrue();
            assertThat(body.path("sections").path("rollback").path("runtimeProven").asBoolean()).isTrue();
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
        @DisplayName("returns 201 for every claimed FHIR resource")
        void createsEveryClaimedFhirResource() throws Exception {
            Map<String, String> payloads = Map.of(
                "Patient", patientJson(),
                "Observation", observationJson("obs-claimed", "patient-1"),
                "MedicationRequest", medicationRequestJson(),
                "Immunization", immunizationJson(),
                "DiagnosticReport", diagnosticReportJson(),
                "DocumentReference", documentReferenceJson()
            );

            for (Map.Entry<String, String> entry : payloads.entrySet()) {
                HttpResponse response = dispatch(HttpMethod.POST, "/fhir/" + entry.getKey(), entry.getValue());
                assertThat(response.getCode()).as(entry.getKey()).isEqualTo(201);
                assertThat(JSON.readTree(bodyString(response)).path("resourceType").asText()).isEqualTo(entry.getKey());
            }
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

        @Test
        @DisplayName("searches every claimed FHIR resource")
        void searchesEveryClaimedFhirResource() throws Exception {
            Map<String, String> payloads = Map.of(
                "Patient", patientJson(),
                "Observation", observationJson("obs-search", "patient-1"),
                "MedicationRequest", medicationRequestJson(),
                "Immunization", immunizationJson(),
                "DiagnosticReport", diagnosticReportJson(),
                "DocumentReference", documentReferenceJson()
            );

            for (Map.Entry<String, String> entry : payloads.entrySet()) {
                dispatch(HttpMethod.POST, "/fhir/" + entry.getKey(), entry.getValue());
                HttpResponse response = dispatch(HttpMethod.GET, "/fhir/" + entry.getKey(), null);
                JsonNode body = JSON.readTree(bodyString(response));
                assertThat(response.getCode()).as(entry.getKey()).isEqualTo(200);
                assertThat(body.path("resourceType").asText()).isEqualTo("Bundle");
                assertThat(body.path("type").asText()).isEqualTo("searchset");
            }
        }
    }

    // -----------------------------------------------------------------------
    // /patients
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Patient record API")
    class PatientRecordApi {

        @Test
        @DisplayName("requires tenant and principal headers")
        void requiresTenantAndPrincipalHeaders() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/patients/patient-1", null);

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode body = JSON.readTree(bodyString(response));
            assertThat(body.path("error").asText()).isEqualTo("MISSING_CONTEXT");
        }

        @Test
        @DisplayName("creates, reads, searches, updates, and exposes read history for self")
        void patientRecordCrudSearchAndHistory() throws Exception {
            Map<String, String> headers = phrHeaders("patient-1", "patient");

            HttpResponse created = dispatch(HttpMethod.POST, "/patients", patientRecordJson("patient-1", "Route"), headers);
            assertThat(created.getCode()).isEqualTo(201);
            JsonNode createdBody = JSON.readTree(bodyString(created));
            assertThat(createdBody.path("id").asText()).isEqualTo("patient-1");

            HttpResponse read = dispatch(HttpMethod.GET, "/patients/patient-1", null, headers);
            assertThat(read.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(read)).path("demographics").path("givenName").asText()).isEqualTo("Route");

            HttpResponse searched = dispatch(HttpMethod.GET, "/patients?patientId=patient-1", null, headers);
            assertThat(searched.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(searched)).path("count").asInt()).isEqualTo(1);

            HttpResponse updated = dispatch(HttpMethod.PUT, "/patients/patient-1", patientRecordJson("patient-1", "Updated"), headers);
            assertThat(updated.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(updated)).path("demographics").path("givenName").asText()).isEqualTo("Updated");

            HttpResponse history = dispatch(HttpMethod.GET, "/patients/patient-1/history", null, headers);
            assertThat(history.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(history)).path("history").isArray()).isTrue();
        }

        @Test
        @DisplayName("denies cross-principal read without consent")
        void deniesReadWithoutConsent() throws Exception {
            dispatch(HttpMethod.POST, "/patients", patientRecordJson("patient-2", "Private"),
                phrHeaders("patient-2", "patient"));

            HttpResponse response = dispatch(HttpMethod.GET, "/patients/patient-2", null,
                phrHeaders("provider-1", "patient"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("CONSENT_REQUIRED");
        }
    }

    // -----------------------------------------------------------------------
    // /consents
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Consent API")
    class ConsentApi {

        @Test
        @DisplayName("grants, checks, lists, and revokes consent")
        void consentGrantCheckListAndRevoke() throws Exception {
            Map<String, String> patientHeaders = phrHeaders("patient-3", "patient");

            HttpResponse created = dispatch(HttpMethod.POST, "/consents/grants",
                consentGrantJson("patient-3", "provider-1", "patient-records"), patientHeaders);
            assertThat(created.getCode()).isEqualTo(201);
            String grantId = JSON.readTree(bodyString(created)).path("id").asText();

            HttpResponse allowed = dispatch(HttpMethod.GET,
                "/consents/check?patientId=patient-3&accessorId=provider-1&resourceType=patient-records",
                null,
                phrHeaders("provider-1", "clinician"));
            assertThat(allowed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(allowed)).path("allowed").asBoolean()).isTrue();

            HttpResponse listed = dispatch(HttpMethod.GET, "/consents?patientId=patient-3", null, patientHeaders);
            assertThat(listed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(listed)).path("count").asInt()).isEqualTo(1);

            HttpResponse revoked = dispatch(HttpMethod.POST,
                "/consents/grants/" + grantId + "/revoke?patientId=patient-3",
                null,
                patientHeaders);
            assertThat(revoked.getCode()).isEqualTo(200);

            HttpResponse denied = dispatch(HttpMethod.GET,
                "/consents/check?patientId=patient-3&accessorId=provider-1&resourceType=patient-records",
                null,
                phrHeaders("provider-1", "clinician"));
            assertThat(JSON.readTree(bodyString(denied)).path("allowed").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("denies non-owner grant creation")
        void deniesNonOwnerGrantCreation() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/consents/grants",
                consentGrantJson("patient-4", "provider-1", "patient-records"),
                phrHeaders("patient-5", "patient"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("CONSENT_OWNER_REQUIRED");
        }
    }

    // -----------------------------------------------------------------------
    // /clinical
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Clinical API")
    class ClinicalApi {

        @Test
        @DisplayName("records and lists labs, medications, and immunizations for self")
        void recordsAndListsClinicalResources() throws Exception {
            Map<String, String> headers = phrHeaders("patient-6", "patient");

            HttpResponse lab = dispatch(HttpMethod.POST, "/clinical/labs/observations",
                labObservationJson("patient-6"), headers);
            assertThat(lab.getCode()).isEqualTo(201);
            String labId = JSON.readTree(bodyString(lab)).path("id").asText();
            assertThat(dispatch(HttpMethod.GET, "/clinical/labs/observations/" + labId, null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/clinical/labs?patientId=patient-6", null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/clinical/labs/trends?patientId=patient-6&loincCode=2160-0", null, headers).getCode()).isEqualTo(200);

            HttpResponse medication = dispatch(HttpMethod.POST, "/clinical/medications/prescriptions",
                prescriptionJson("patient-6"), headers);
            assertThat(medication.getCode()).isEqualTo(201);
            String prescriptionId = JSON.readTree(bodyString(medication)).path("id").asText();
            assertThat(dispatch(HttpMethod.GET, "/clinical/medications/prescriptions/" + prescriptionId, null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/clinical/medications?patientId=patient-6", null, headers).getCode()).isEqualTo(200);

            HttpResponse immunization = dispatch(HttpMethod.POST, "/clinical/immunizations",
                immunizationRecordJson("patient-6"), headers);
            assertThat(immunization.getCode()).isEqualTo(201);
            String immunizationId = JSON.readTree(bodyString(immunization)).path("id").asText();
            assertThat(dispatch(HttpMethod.GET, "/clinical/immunizations/" + immunizationId, null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/clinical/immunizations?patientId=patient-6", null, headers).getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("denies sensitive clinical read without consent")
        void deniesClinicalReadWithoutConsent() throws Exception {
            dispatch(HttpMethod.POST, "/clinical/labs/observations", labObservationJson("patient-7"),
                phrHeaders("patient-7", "patient"));

            HttpResponse response = dispatch(HttpMethod.GET, "/clinical/labs?patientId=patient-7", null,
                phrHeaders("provider-7", "clinician"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("CONSENT_REQUIRED");
        }

        @Test
        @DisplayName("allows sensitive clinical read with consent")
        void allowsClinicalReadWithConsent() throws Exception {
            dispatch(HttpMethod.POST, "/clinical/medications/prescriptions", prescriptionJson("patient-8"),
                phrHeaders("patient-8", "patient"));
            dispatch(HttpMethod.POST, "/consents/grants",
                consentGrantJson("patient-8", "provider-8", "medications"),
                phrHeaders("patient-8", "patient"));

            HttpResponse response = dispatch(HttpMethod.GET, "/clinical/medications?patientId=patient-8", null,
                phrHeaders("provider-8", "clinician"));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(response)).path("count").asInt()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // /emergency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Emergency break-glass API")
    class EmergencyApi {

        @Test
        @DisplayName("logs emergency access, exposes event, queues review, and completes review")
        void logsAndReviewsEmergencyAccess() throws Exception {
            Map<String, String> clinicianHeaders = phrHeaders("provider-9", "clinician");
            Map<String, String> adminHeaders = phrHeaders("admin-9", "admin");

            HttpResponse logged = dispatch(HttpMethod.POST, "/emergency/access",
                emergencyAccessJson("patient-9", "provider-9"), clinicianHeaders);
            assertThat(logged.getCode()).isEqualTo(201);
            JsonNode loggedBody = JSON.readTree(bodyString(logged));
            String eventId = loggedBody.path("id").asText();
            assertThat(loggedBody.path("reviewStatus").asText()).isEqualTo("PENDING_REVIEW");
            assertThat(loggedBody.path("reviewCaseId").asText()).startsWith("EMR-");

            HttpResponse event = dispatch(HttpMethod.GET, "/emergency/events/" + eventId, null, clinicianHeaders);
            assertThat(event.getCode()).isEqualTo(200);

            HttpResponse patientLog = dispatch(HttpMethod.GET, "/emergency/patients/patient-9", null,
                phrHeaders("patient-9", "patient"));
            assertThat(patientLog.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(patientLog)).path("count").asInt()).isEqualTo(1);

            HttpResponse pending = dispatch(HttpMethod.GET, "/emergency/reviews/pending", null, adminHeaders);
            assertThat(pending.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(pending)).path("count").asInt()).isEqualTo(1);

            HttpResponse reviewed = dispatch(HttpMethod.POST, "/emergency/reviews/" + eventId,
                emergencyReviewJson("REVIEWED", "Clinically justified"), adminHeaders);
            assertThat(reviewed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(reviewed)).path("reviewStatus").asText()).isEqualTo("REVIEWED");
        }

        @Test
        @DisplayName("requires admin role for emergency review queues")
        void requiresAdminForEmergencyReviewQueues() throws Exception {
            HttpResponse response = dispatch(HttpMethod.GET, "/emergency/reviews/pending", null,
                phrHeaders("provider-10", "clinician"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("REVIEWER_REQUIRED");
        }

        @Test
        @DisplayName("denies accessor spoofing for emergency access")
        void deniesAccessorSpoofing() throws Exception {
            HttpResponse response = dispatch(HttpMethod.POST, "/emergency/access",
                emergencyAccessJson("patient-11", "provider-other"), phrHeaders("provider-11", "clinician"));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("INVALID_EMERGENCY_ACCESS");
        }
    }

    // -----------------------------------------------------------------------
    // /admin
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Administrative API")
    class AdministrativeApi {

        @Test
        @DisplayName("schedules and completes telemedicine session")
        void telemedicineLifecycle() throws Exception {
            Map<String, String> headers = phrHeaders("patient-12", "patient");

            HttpResponse scheduled = dispatch(HttpMethod.POST, "/admin/telemedicine/sessions",
                telemedicineSessionJson("patient-12"), headers);
            assertThat(scheduled.getCode()).isEqualTo(201);
            String sessionId = JSON.readTree(bodyString(scheduled)).path("id").asText();

            assertThat(dispatch(HttpMethod.GET, "/admin/telemedicine?patientId=patient-12", null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.POST, "/admin/telemedicine/sessions/" + sessionId + "/start?patientId=patient-12", null, headers).getCode()).isEqualTo(200);
            HttpResponse completed = dispatch(HttpMethod.POST,
                "/admin/telemedicine/sessions/" + sessionId + "/complete?patientId=patient-12&notes=done",
                null,
                headers);
            assertThat(completed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(completed)).path("status").asText()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("creates, accepts, closes, and lists referrals")
        void referralLifecycle() throws Exception {
            Map<String, String> headers = phrHeaders("patient-13", "patient");

            HttpResponse created = dispatch(HttpMethod.POST, "/admin/referrals",
                referralJson("patient-13"), headers);
            assertThat(created.getCode()).isEqualTo(201);
            String referralId = JSON.readTree(bodyString(created)).path("id").asText();

            HttpResponse accepted = dispatch(HttpMethod.POST,
                "/admin/referrals/" + referralId + "/accept?patientId=patient-13",
                null,
                phrHeaders("provider-13", "admin"));
            assertThat(accepted.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(accepted)).path("status").asText()).isEqualTo("ACCEPTED");

            HttpResponse closed = dispatch(HttpMethod.POST,
                "/admin/referrals/" + referralId + "/close?patientId=patient-13&notes=completed",
                null,
                headers);
            assertThat(closed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(closed)).path("status").asText()).isEqualTo("COMPLETED");

            HttpResponse listed = dispatch(HttpMethod.GET, "/admin/referrals?patientId=patient-13", null, headers);
            assertThat(listed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(listed)).path("count").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("creates billing encounter and claim")
        void billingJourney() throws Exception {
            Map<String, String> headers = phrHeaders("patient-14", "patient");

            HttpResponse encounter = dispatch(HttpMethod.POST, "/admin/billing/encounters",
                billingEncounterJson("patient-14"), headers);
            assertThat(encounter.getCode()).isEqualTo(201);
            String encounterId = JSON.readTree(bodyString(encounter)).path("id").asText();

            HttpResponse closed = dispatch(HttpMethod.POST,
                "/admin/billing/encounters/" + encounterId + "/close?patientId=patient-14",
                null,
                headers);
            assertThat(closed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(closed)).path("status").asText()).isEqualTo("CLOSED");

            HttpResponse claim = dispatch(HttpMethod.POST, "/admin/billing/claims",
                insuranceClaimJson("patient-14", encounterId), headers);
            assertThat(claim.getCode()).isEqualTo(201);
            String claimId = JSON.readTree(bodyString(claim)).path("id").asText();

            HttpResponse approved = dispatch(HttpMethod.POST,
                "/admin/billing/claims/" + claimId + "/status?patientId=patient-14",
                "{\"status\":\"APPROVED\",\"note\":\"approved\"}",
                headers);
            assertThat(approved.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(approved)).path("status").asText()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("denies administrative patient data without consent")
        void deniesAdministrativeDataWithoutConsent() throws Exception {
            dispatch(HttpMethod.POST, "/admin/telemedicine/sessions",
                telemedicineSessionJson("patient-15"), phrHeaders("patient-15", "patient"));

            HttpResponse response = dispatch(HttpMethod.GET, "/admin/telemedicine?patientId=patient-15", null,
                phrHeaders("provider-15", "clinician"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("CONSENT_REQUIRED");
        }
    }

    // -----------------------------------------------------------------------
    // /records
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Document and imaging API")
    class DocumentImagingApi {

        @Test
        @DisplayName("uploads document, reads metadata/content/FHIR, changes consent, and deletes")
        void documentLifecycle() throws Exception {
            Map<String, String> headers = phrHeaders("patient-16", "patient");

            HttpResponse uploaded = dispatch(HttpMethod.POST, "/records/documents",
                documentUploadJson("patient-16"), headers);
            assertThat(uploaded.getCode()).isEqualTo(201);
            String documentId = JSON.readTree(bodyString(uploaded)).path("id").asText();

            assertThat(dispatch(HttpMethod.GET, "/records/documents/" + documentId, null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/records/documents/" + documentId + "/content", null, headers).getCode()).isEqualTo(200);
            HttpResponse fhir = dispatch(HttpMethod.GET, "/records/documents/" + documentId + "/fhir", null, headers);
            assertThat(fhir.getCode()).isEqualTo(200);
            assertThat(bodyString(fhir)).contains("DocumentReference");

            HttpResponse listed = dispatch(HttpMethod.GET, "/records/documents?patientId=patient-16", null, headers);
            assertThat(listed.getCode()).isEqualTo(200);
            assertThat(JSON.readTree(bodyString(listed)).path("count").asInt()).isEqualTo(1);

            HttpResponse consent = dispatch(HttpMethod.POST, "/records/documents/" + documentId + "/consent?patientId=patient-16",
                "{\"visibility\":\"private\"}", headers);
            assertThat(consent.getCode()).isEqualTo(200);

            HttpResponse deleted = dispatch(HttpMethod.DELETE, "/records/documents/" + documentId + "?patientId=patient-16", null, headers);
            assertThat(deleted.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("creates imaging order, registers study, stores report, and lists imaging records")
        void imagingJourney() throws Exception {
            Map<String, String> headers = phrHeaders("patient-17", "patient");

            HttpResponse order = dispatch(HttpMethod.POST, "/records/imaging/orders",
                imagingOrderJson("patient-17"), headers);
            assertThat(order.getCode()).isEqualTo(201);
            String orderId = JSON.readTree(bodyString(order)).path("id").asText();
            assertThat(dispatch(HttpMethod.GET, "/records/imaging/orders/" + orderId, null, headers).getCode()).isEqualTo(200);

            HttpResponse study = dispatch(HttpMethod.POST, "/records/imaging/studies",
                imagingStudyJson("patient-17", orderId), headers);
            assertThat(study.getCode()).isEqualTo(201);
            String studyId = JSON.readTree(bodyString(study)).path("id").asText();

            HttpResponse report = dispatch(HttpMethod.POST, "/records/imaging/reports",
                radiologyReportJson("patient-17", studyId), headers);
            assertThat(report.getCode()).isEqualTo(201);

            assertThat(dispatch(HttpMethod.GET, "/records/imaging/orders?patientId=patient-17", null, headers).getCode()).isEqualTo(200);
            assertThat(dispatch(HttpMethod.GET, "/records/imaging/studies?patientId=patient-17", null, headers).getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("denies document list without consent")
        void deniesDocumentReadWithoutConsent() throws Exception {
            dispatch(HttpMethod.POST, "/records/documents", documentUploadJson("patient-18"),
                phrHeaders("patient-18", "patient"));

            HttpResponse response = dispatch(HttpMethod.GET, "/records/documents?patientId=patient-18", null,
                phrHeaders("provider-18", "clinician"));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(JSON.readTree(bodyString(response)).path("error").asText()).isEqualTo("CONSENT_REQUIRED");
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

    private static Map<String, String> phrHeaders(String principalId, String role) {
        return Map.of(
            "X-Tenant-ID", "tenant-health-1",
            "X-Principal-ID", principalId,
            "X-Role", role
        );
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

    private static String immunizationJson() {
        return """
            {
              "resourceType": "Immunization",
              "status": "completed",
              "vaccineCode": {"coding": [{"system": "http://hl7.org/fhir/sid/cvx", "code": "21"}]},
              "patient": {"reference": "Patient/patient-1"},
              "occurrenceDateTime": "2026-05-01T10:00:00Z"
            }
            """;
    }

    private static String diagnosticReportJson() {
        return """
            {
              "resourceType": "DiagnosticReport",
              "status": "final",
              "code": {"coding": [{"system": "http://loinc.org", "code": "58410-2"}]},
              "subject": {"reference": "Patient/patient-1"},
              "result": [{"reference": "Observation/obs-1"}]
            }
            """;
    }

    private static String documentReferenceJson() {
        return """
            {
              "resourceType": "DocumentReference",
              "status": "current",
              "type": {"coding": [{"system": "http://loinc.org", "code": "34133-9"}]},
              "subject": {"reference": "Patient/patient-1"},
              "content": [
                {
                  "attachment": {
                    "contentType": "application/pdf",
                    "url": "https://example.test/document.pdf"
                  }
                }
              ]
            }
            """;
    }

    private static String patientRecordJson(String patientId, String givenName) {
        return """
            {
              "id": "%s",
              "nationalId": "np-%s",
              "demographics": {
                "givenName": "%s",
                "familyName": "Patient",
                "dateOfBirth": "1990-01-01",
                "gender": "unknown"
              }
            }
            """.formatted(patientId, patientId, givenName);
    }

    private static String consentGrantJson(String patientId, String recipientId, String resourceType) {
        return """
            {
              "patientId": "%s",
              "recipientId": "%s",
              "scope": {
                "resourceTypes": ["%s"],
                "actions": ["READ", "WRITE"]
              },
              "status": "ACTIVE",
              "expiresAt": "2027-05-25T00:00:00Z"
            }
            """.formatted(patientId, recipientId, resourceType);
    }

    private static String labObservationJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "loincCode": "2160-0",
              "loincDisplay": "Creatinine",
              "testName": "Serum creatinine",
              "value": 1.1,
              "unit": "mg/dL",
              "performingLabId": "lab-1",
              "status": "FINAL"
            }
            """.formatted(patientId);
    }

    private static String prescriptionJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "prescriberId": "provider-1",
              "medicationCode": "RXNORM:197361",
              "medicationName": "Amoxicillin",
              "dosage": "500mg",
              "indication": "infection",
              "refillsRemaining": 1,
              "duration": "PT720H"
            }
            """.formatted(patientId);
    }

    private static String immunizationRecordJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "cvxCode": "21",
              "vaccineName": "Varicella",
              "administeredBy": "clinic-1",
              "lotNumber": "lot-1",
              "route": "intramuscular",
              "seriesName": "routine",
              "doseNumber": 1,
              "adverseEvent": false
            }
            """.formatted(patientId);
    }

    private static String emergencyAccessJson(String patientId, String accessorId) {
        return """
            {
              "patientId": "%s",
              "accessorId": "%s",
              "accessorRole": "ER_PHYSICIAN",
              "justification": "Patient unconscious in emergency department",
              "resourcesAccessed": ["medications", "labs", "allergies"]
            }
            """.formatted(patientId, accessorId);
    }

    private static String emergencyReviewJson(String status, String notes) {
        return """
            {
              "status": "%s",
              "notes": "%s"
            }
            """.formatted(status, notes);
    }

    private static String telemedicineSessionJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "providerId": "provider-tele",
              "scheduledAt": "2027-05-25T10:00:00Z",
              "durationMinutes": 30,
              "platform": "CUSTOM",
              "joinUrl": "https://telemedicine.example.test/session"
            }
            """.formatted(patientId);
    }

    private static String referralJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "referringProviderId": "provider-ref",
              "specialtyCode": "CARDIOLOGY",
              "clinicalReason": "Specialist consultation required",
              "urgency": "ROUTINE"
            }
            """.formatted(patientId);
    }

    private static String billingEncounterJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "providerId": "provider-bill",
              "facilityId": "facility-1",
              "serviceLines": [
                {
                  "serviceCode": "CONSULT",
                  "description": "Consultation",
                  "quantity": 1,
                  "unitPrice": 1200.00,
                  "currency": "NPR"
                }
              ],
              "totalAmount": 1200.00,
              "currency": "NPR"
            }
            """.formatted(patientId);
    }

    private static String insuranceClaimJson(String patientId, String encounterId) {
        return """
            {
              "patientId": "%s",
              "encounterId": "%s",
              "insurerId": "insurer-1",
              "policyNumber": "POLICY-1",
              "claimedAmount": 1200.00,
              "currency": "NPR"
            }
            """.formatted(patientId, encounterId);
    }

    private static String documentUploadJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "documentType": "LAB_REPORT",
              "title": "Lab report",
              "description": "Uploaded lab report",
              "contentType": "application/pdf",
              "content": "SGVsbG8=",
              "contentHash": "sha256-test",
              "visibility": "shared-with-provider"
            }
            """.formatted(patientId);
    }

    private static String imagingOrderJson(String patientId) {
        return """
            {
              "patientId": "%s",
              "orderingProviderId": "provider-img",
              "modalityCode": "CT",
              "bodyPart": "HEAD",
              "clinicalIndication": "Head trauma"
            }
            """.formatted(patientId);
    }

    private static String imagingStudyJson(String patientId, String orderId) {
        return """
            {
              "patientId": "%s",
              "orderId": "%s",
              "dcmStudyInstanceUid": "1.2.840.113619.2.55.3.604688435.781.1599758237.467",
              "modalityCode": "CT",
              "pacsLocation": "pacs://study/1",
              "seriesCount": 1,
              "instanceCount": 12,
              "bodyPart": "HEAD"
            }
            """.formatted(patientId, orderId);
    }

    private static String radiologyReportJson(String patientId, String studyId) {
        return """
            {
              "patientId": "%s",
              "studyId": "%s",
              "reportingRadiologistId": "radiologist-1",
              "findings": "No acute intracranial abnormality.",
              "impression": "Normal CT head.",
              "recommendations": "Clinical follow-up.",
              "status": "FINAL"
            }
            """.formatted(patientId, studyId);
    }

    private static final class TestDistributedCache<K, V> implements DistributedCachePort<K, V> {
        private final ConcurrentHashMap<K, V> values = new ConcurrentHashMap<>();

        @Override
        public Promise<Optional<V>> get(K key) {
            return Promise.of(Optional.ofNullable(values.get(key)));
        }

        @Override
        public Promise<Void> put(K key, V value) {
            values.put(key, value);
            return Promise.complete();
        }

        @Override
        public Promise<Void> put(K key, V value, Duration ttl) {
            values.put(key, value);
            return Promise.complete();
        }

        @Override
        public Promise<V> getOrLoad(K key, Function<K, Promise<V>> loader) {
            V cached = values.get(key);
            if (cached != null) {
                return Promise.of(cached);
            }
            return loader.apply(key)
                .then(value -> {
                    values.put(key, value);
                    return Promise.of(value);
                });
        }

        @Override
        public Promise<Void> invalidate(K key) {
            values.remove(key);
            return Promise.complete();
        }

        @Override
        public Promise<Void> invalidateAll() {
            values.clear();
            return Promise.complete();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }

    private static final class NoopNotificationSender implements PhrNotificationSender {
        @Override
        public Promise<Void> scheduleAppointmentReminder(AppointmentReminderNotification notification) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> cancelAppointmentReminder(AppointmentReminderNotification notification) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyConsentChange(ConsentChangeNotification notification) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyTelemedicineSession(TelemedicineSessionNotification notification) {
            return Promise.complete();
        }
    }

    private static final class NoopEmergencyAccessNotificationSender implements EmergencyAccessNotificationSender {
        @Override
        public Promise<Void> notifyComplianceLead(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> scheduleMandatoryReview(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> notifyEscalation(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }

    private static final class NoopEmergencyAccessAuditLogger implements EmergencyAccessReviewAuditLogger {
        @Override
        public Promise<Void> logReviewQueued(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }

        @Override
        public Promise<Void> logReviewCompleted(
                EmergencyAccessReviewCase reviewCase,
                EmergencyAccessLogService.EmergencyAccessEvent event) {
            return Promise.complete();
        }
    }
}
