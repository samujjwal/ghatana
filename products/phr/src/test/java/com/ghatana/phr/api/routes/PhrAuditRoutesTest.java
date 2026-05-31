package com.ghatana.phr.api.routes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.phr.kernel.service.ConsentManagementService;
import com.ghatana.phr.kernel.service.FchvCommunityAssignmentService;
import com.ghatana.phr.kernel.service.TreatmentRelationshipService;
import com.ghatana.phr.security.PhrPolicyEvaluator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhrAuditRoutes}.
 *
 * @doc.type class
 * @doc.purpose Verifies patient-scoped access enforcement, pagination, and filter mapping for audit events
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAuditRoutes")
@ExtendWith(MockitoExtension.class)
class PhrAuditRoutesTest extends EventloopTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AuditTrailService auditTrailService;

    @Mock
    private ConsentManagementService consentService;

    @Mock
    private TreatmentRelationshipService treatmentRelationshipService;

    @Mock
    private FchvCommunityAssignmentService fchvCommunityAssignmentService;

    private AsyncServlet servlet;

    @BeforeEach
    void setUp() {
        PhrAuditRoutes routes = new PhrAuditRoutes(
            eventloop(),
            auditTrailService,
            new PhrPolicyEvaluator(
                consentService,
                treatmentRelationshipService,
                fchvCommunityAssignmentService,
                auditTrailService
            )
        );
        servlet = routes.getServlet();
    }

    // -----------------------------------------------------------------------
    // Context enforcement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Context enforcement")
    class ContextEnforcement {

        @Test
        @DisplayName("returns 400 when X-Tenant-ID is missing")
        void returns400ForMissingTenantId() throws Exception {
            HttpResponse response = dispatch("/events", Map.of(
                "X-Principal-ID", "patient-1",
                "X-Role", "patient",
                "X-Persona", "patient",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode body = parseBody(response);
            assertThat(body.path("error").asText()).isEqualTo("MISSING_CONTEXT");
        }

        @Test
        @DisplayName("returns 400 when X-Principal-ID is missing")
        void returns400ForMissingPrincipalId() throws Exception {
            HttpResponse response = dispatch("/events", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Role", "patient",
                "X-Persona", "patient",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode body = parseBody(response);
            assertThat(body.path("error").asText()).isEqualTo("MISSING_CONTEXT");
        }

        @Test
        @DisplayName("returns 400 when X-Role is missing")
        void returns400ForMissingRole() throws Exception {
            HttpResponse response = dispatch("/events", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "patient-1"
            ));

            assertThat(response.getCode()).isEqualTo(400);
            JsonNode body = parseBody(response);
            assertThat(body.path("error").asText()).isEqualTo("MISSING_CONTEXT");
        }
    }

    // -----------------------------------------------------------------------
    // Patient-scoped access enforcement
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Patient-scoped access enforcement")
    class PatientScopedEnforcement {

        @Test
        @DisplayName("patient role receives events scoped to their own principalId")
        void patientRoleReceivesOwnEvents() throws Exception {
            AuditTrailService.AuditTrailEvent ownEvent = buildEvent("evt-1", "patient-1", "access");
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(ownEvent));

            HttpResponse response = dispatch("/events?patientId=patient-1", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "patient-1",
                "X-Role", "patient",
                "X-Persona", "patient",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(200);
            verify(auditTrailService).queryAuditEvents(argWithEntityId("patient-1"));
        }

        @Test
        @DisplayName("patient role cannot query another patient's audit events")
        void patientRoleCannotQueryAnotherPatient() throws Exception {
            HttpResponse response = dispatch("/events?patientId=other-patient", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "patient-1",
                "X-Role", "patient",
                "X-Persona", "patient",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(parseBody(response).path("error").asText()).isEqualTo("AUDIT_SELF_SCOPE_REQUIRED");
        }

        @Test
        @DisplayName("admin role may query using the patientId query parameter")
        void adminRoleCanQueryArbitraryPatientId() throws Exception {
            AuditTrailService.AuditTrailEvent event = buildEvent("evt-2", "patient-42", "access");
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(event));

            HttpResponse response = dispatch("/events?patientId=patient-42", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "admin-1",
                "X-Role", "admin",
                "X-Persona", "admin",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(200);
            verify(auditTrailService).queryAuditEvents(argWithEntityId("patient-42"));
        }

        @Test
        @DisplayName("clinician role may query patient-scoped audit events with treatment relationship")
        void clinicianRoleCanQueryPatientWithTreatmentRelationship() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());
            when(treatmentRelationshipService.hasActiveTreatmentRelationship("clinician-1", "patient-77"))
                .thenReturn(Promise.of(true));

            HttpResponse response = dispatch("/events?patientId=patient-77", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "clinician-1",
                "X-Role", "clinician",
                "X-Persona", "clinician",
                "X-Tier", "core",
                "X-Facility-ID", "facility-1"
            ));

            assertThat(response.getCode()).isEqualTo(200);
            verify(auditTrailService).queryAuditEvents(argWithEntityId("patient-77"));
        }

        @Test
        @DisplayName("clinician role cannot query patient audit events without relationship or consent")
        void clinicianRoleCannotQueryPatientWithoutScope() throws Exception {
            when(treatmentRelationshipService.hasActiveTreatmentRelationship("clinician-1", "patient-77"))
                .thenReturn(Promise.of(false));
            when(consentService.validateAccess("patient-77", "clinician-1", "audit", "READ"))
                .thenReturn(Promise.of(new ConsentManagementService.ConsentValidationResult(false, "denied", null)));

            HttpResponse response = dispatch("/events?patientId=patient-77", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "clinician-1",
                "X-Role", "clinician",
                "X-Persona", "clinician",
                "X-Tier", "core",
                "X-Facility-ID", "facility-1"
            ));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(parseBody(response).path("error").asText()).isEqualTo("CLINICIAN_SCOPE_REQUIRED");
        }

        @Test
        @DisplayName("clinician role must include a patient scope")
        void clinicianRoleRequiresPatientScope() throws Exception {
            HttpResponse response = dispatch("/events", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "clinician-1",
                "X-Role", "clinician",
                "X-Persona", "clinician",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(parseBody(response).path("error").asText()).isEqualTo("AUDIT_PATIENT_SCOPE_REQUIRED");
        }
    }

    // -----------------------------------------------------------------------
    // Response shape
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Response shape")
    class ResponseShape {

        @Test
        @DisplayName("returns paginated envelope with events array, total, page, and pageSize")
        void returnsPaginatedEnvelope() throws Exception {
            AuditTrailService.AuditTrailEvent event = buildEvent("evt-10", "patient-2", "access");
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(event));

            HttpResponse response = dispatch("/events", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("audit-test-corr-1");
            JsonNode body = parseBody(response);
            assertThat(body.has("events")).isTrue();
            assertThat(body.path("events").isArray()).isTrue();
            assertThat(body.has("total")).isTrue();
            assertThat(body.has("page")).isTrue();
            assertThat(body.has("pageSize")).isTrue();
        }

        @Test
        @DisplayName("event DTO contains id, eventType, principal, timestamp, success fields")
        void eventDtoShape() throws Exception {
            AuditTrailService.AuditTrailEvent event = buildEvent("evt-shape", "patient-3", "access");
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(event));

            HttpResponse response = dispatch("/events", adminHeaders("admin-1"));

            JsonNode events = parseBody(response).path("events");
            assertThat(events.size()).isEqualTo(1);
            JsonNode dto = events.get(0);
            assertThat(dto.has("id")).isTrue();
            assertThat(dto.has("eventType")).isTrue();
            assertThat(dto.has("principal")).isTrue();
            assertThat(dto.has("timestamp")).isTrue();
            assertThat(dto.has("success")).isTrue();
        }

        @Test
        @DisplayName("empty result set returns zero total and empty events array")
        void emptyResultSetReturnsZero() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events", adminHeaders("admin-1"));

            JsonNode body = parseBody(response);
            assertThat(body.path("total").asInt()).isEqualTo(0);
            assertThat(body.path("events").size()).isEqualTo(0);
        }

        @Test
        @DisplayName("event detail finds the requested event within tenant-scoped results")
        void eventDetailFindsRequestedEventBeyondFirstResult() throws Exception {
            AuditTrailService.AuditTrailEvent firstEvent = buildEvent("evt-first", "patient-2", "access");
            AuditTrailService.AuditTrailEvent requestedEvent = buildEvent("evt-requested", "patient-2", "access");
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(firstEvent, requestedEvent));

            HttpResponse response = dispatch("/events/evt-requested", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("audit-test-corr-1");
            assertThat(parseBody(response).path("id").asText()).isEqualTo("evt-requested");
            verify(auditTrailService).queryAuditEvents(argWithLimit(1000));
        }
    }

    // -----------------------------------------------------------------------
    // Pagination
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("defaults to page 1 when page parameter is absent")
        void defaultsToPageOne() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events", adminHeaders("admin-1"));

            JsonNode body = parseBody(response);
            assertThat(body.path("page").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("respects explicit page and pageSize parameters")
        void respectsExplicitPageAndPageSize() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?page=2&pageSize=10", adminHeaders("admin-1"));

            JsonNode body = parseBody(response);
            assertThat(body.path("page").asInt()).isEqualTo(2);
            assertThat(body.path("pageSize").asInt()).isEqualTo(10);
        }

        @Test
        @DisplayName("non-numeric page and pageSize defaults to safe values")
        void nonNumericPageFallsBackToDefault() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?page=abc&pageSize=xyz", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            JsonNode body = parseBody(response);
            assertThat(body.path("page").asInt()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Filter mapping
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Event type filter mapping")
    class FilterMapping {

        @Test
        @DisplayName("unknown filter value falls back to all events (no eventType filter on query)")
        void unknownFilterFallsBackToAll() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?filter=UNKNOWN_FILTER", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("filter=all returns all events")
        void filterAllReturnsAll() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?filter=all", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("filter=access passes eventType filter to query")
        void filterAccessPassedToQuery() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?filter=access", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            verify(auditTrailService).queryAuditEvents(argWithEventType("access"));
        }

        @Test
        @DisplayName("filter=consent passes eventType filter to query")
        void filterConsentPassedToQuery() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of());

            HttpResponse response = dispatch("/events?filter=consent", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            verify(auditTrailService).queryAuditEvents(argWithEventType("consent"));
        }
    }

    // -----------------------------------------------------------------------
    // Error handling
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("returns 500 when auditTrailService throws")
        void returns500OnServiceException() throws Exception {
            when(auditTrailService.queryAuditEvents(any())).thenThrow(new RuntimeException("Store unavailable"));

            HttpResponse response = dispatch("/events", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(500);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("audit-test-corr-1");
            JsonNode body = parseBody(response);
            assertThat(body.path("error").asText()).isEqualTo("AUDIT_QUERY_FAILED");
        }
    }

    @Nested
    @DisplayName("Export")
    class Export {

        @Test
        @DisplayName("admin can export audit events as CSV")
        void adminCanExportCsv() {
            when(auditTrailService.queryAuditEvents(any()))
                .thenReturn(List.of(buildEvent("evt-export", "patient-2", "access")));

            HttpResponse response = dispatch("/events/export?format=csv", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("audit-test-corr-1");
        }

        @Test
        @DisplayName("CSV export escapes fields with commas and quotes")
        void csvExportEscapesFields() throws Exception {
            AuditTrailService.AuditTrailEvent event = AuditTrailService.AuditTrailEvent.builder()
                .eventId("evt-export")
                .eventType("access,review")
                .entityId("patient-2")
                .userId("admin,\"one\"")
                .tenantId("tenant-health-1")
                .action("access")
                .timestamp(System.currentTimeMillis())
                .build();
            when(auditTrailService.queryAuditEvents(any())).thenReturn(List.of(event));

            HttpResponse response = dispatch("/events/export?format=csv", adminHeaders("admin-1"));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("audit-test-corr-1");
            String csv = new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
            assertThat(csv).contains("\"access,review\"");
            assertThat(csv).contains("\"admin,\"\"one\"\"\"");
        }

        @Test
        @DisplayName("non-admin export is denied by policy")
        void nonAdminExportDenied() throws Exception {
            HttpResponse response = dispatch("/events/export?format=json", Map.of(
                "X-Tenant-ID", "tenant-health-1",
                "X-Principal-ID", "clinician-1",
                "X-Role", "clinician",
                "X-Persona", "clinician",
                "X-Tier", "core"
            ));

            assertThat(response.getCode()).isEqualTo(403);
            assertThat(parseBody(response).path("error").asText()).isEqualTo("POLICY_DENIED");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HttpResponse dispatch(String path, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.builder(HttpMethod.GET, "http://localhost" + path);
        headers.forEach((name, value) -> builder.withHeader(HttpHeaders.of(name), value));
        return runPromise(() -> servlet.serve(builder.build()));
    }

    private static Map<String, String> adminHeaders(String principalId) {
        return Map.of(
            "X-Tenant-ID", "tenant-health-1",
            "X-Principal-ID", principalId,
            "X-Role", "admin",
            "X-Persona", "admin",
            "X-Tier", "core",
            "X-Correlation-ID", "audit-test-corr-1"
        );
    }

    private JsonNode parseBody(HttpResponse response) throws Exception {
        byte[] bytes = runPromise(response::loadBody).asArray();
        return JSON.readTree(new String(bytes, StandardCharsets.UTF_8));
    }

    private static AuditTrailService.AuditTrailEvent buildEvent(String eventId, String entityId, String eventType) {
        return AuditTrailService.AuditTrailEvent.builder()
            .eventId(eventId)
            .eventType(eventType)
            .entityId(entityId)
            .userId("user-1")
            .tenantId("tenant-health-1")
            .action(eventType)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Mockito argument matcher that checks the {@code entityId} field of the built {@link AuditTrailService.AuditQuery}.
     * Uses a custom matcher rather than captor so the intent is explicit in the test body.
     */
    private static AuditTrailService.AuditQuery argWithEntityId(String expectedEntityId) {
        return org.mockito.ArgumentMatchers.argThat(q -> expectedEntityId.equals(q.getEntityId()));
    }

    private static AuditTrailService.AuditQuery argWithEventType(String expectedEventType) {
        return org.mockito.ArgumentMatchers.argThat(q -> expectedEventType.equals(q.getEventType()));
    }

    private static AuditTrailService.AuditQuery argWithLimit(int expectedLimit) {
        return org.mockito.ArgumentMatchers.argThat(q -> q.getLimit() == expectedLimit);
    }
}
