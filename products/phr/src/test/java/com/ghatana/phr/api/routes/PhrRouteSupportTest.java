package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhrRouteSupport#requireContext(HttpRequest)}.
 *
 * <p>Verifies the fail-closed security contract:
 * <ul>
 *   <li>Tenant, principal, role, persona, and tier headers must be present and non-blank.</li>
 *   <li>Role must be a member of the allowed set.</li>
 *   <li>No implicit default role, persona, or tier is assigned on missing input.</li>
 *   <li>Role is normalised to lower-case.</li>
 * </ul>
 */
@DisplayName("PhrRouteSupport security context extraction")
class PhrRouteSupportTest extends EventloopTestBase {

    @Test
    @DisplayName("valid headers return a correctly populated context")
    void validHeadersReturnContext() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "patient");

        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);

        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.principalId()).isEqualTo("user-42");
        assertThat(ctx.role()).isEqualTo("patient");
        assertThat(ctx.persona()).isEqualTo("patient");
        assertThat(ctx.tier()).isEqualTo("core");
        assertThat(ctx.correlationId()).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    @DisplayName("role is normalised to lower-case")
    void roleIsNormalisedToLowerCase() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "CLINICIAN");

        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);

        assertThat(ctx.role()).isEqualTo("clinician");
    }

    @Test
    @DisplayName("all roles in ALLOWED_ROLES are accepted")
    void allAllowedRolesAreAccepted() {
        for (String role : PhrRouteSupport.ALLOWED_ROLES) {
            HttpRequest request = requestWithHeaders("tenant-1", "principal-1", role);
            assertThat(PhrRouteSupport.requireContext(request).role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("missing X-Tenant-ID throws with descriptive message")
    void missingTenantIdThrows() {
        HttpRequest request = requestWithHeaders(null, "user-42", "patient");

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Tenant-ID");
    }

    @Test
    @DisplayName("blank X-Tenant-ID throws")
    void blankTenantIdThrows() {
        HttpRequest request = requestWithHeaders("   ", "user-42", "patient");

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Tenant-ID");
    }

    @Test
    @DisplayName("missing X-Principal-ID throws")
    void missingPrincipalIdThrows() {
        HttpRequest request = requestWithHeaders("tenant-1", null, "patient");

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Principal-ID");
    }

    @Test
    @DisplayName("missing X-Role throws — no implicit default is assigned")
    void missingRoleThrows() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", null);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Role");
    }

    @Test
    @DisplayName("missing X-Persona throws — no implicit default is assigned")
    void missingPersonaThrows() {
        HttpRequest request = requestWithFullContext("tenant-1", "user-42", "patient", null, "core", null);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Persona");
    }

    @Test
    @DisplayName("missing X-Tier throws — no implicit default is assigned")
    void missingTierThrows() {
        HttpRequest request = requestWithFullContext("tenant-1", "user-42", "patient", "patient", null, null);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("X-Tier");
    }

    @Test
    @DisplayName("unrecognised persona throws")
    void unrecognisedPersonaThrows() {
        HttpRequest request = requestWithFullContext("tenant-1", "user-42", "patient", "auditor", "core", null);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("auditor");
    }

    @Test
    @DisplayName("unrecognised tier throws")
    void unrecognisedTierThrows() {
        HttpRequest request = requestWithFullContext("tenant-1", "user-42", "patient", "patient", "external", null);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("external");
    }

    @Test
    @DisplayName("unrecognised role throws INVALID_ROLE")
    void unrecognisedRoleThrows() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "superuser");

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("superuser");
    }

    @Test
    @DisplayName("correlationId is extracted from X-Correlation-ID header")
    void correlationIdIsExtracted() {
        HttpRequest request = requestWithCorrelation("tenant-1", "user-42", "patient", "corr-xyz");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.correlationId()).isEqualTo("corr-xyz");
    }

    @Test
    @DisplayName("correlationId falls back to generated UUID when header absent")
    void correlationIdFallbackWhenAbsent() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "patient");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.correlationId()).matches("^[0-9a-fA-F-]{36}$");
    }

    @Test
    @DisplayName("requireContext accepts clinician and admin roles")
    void requireContextAcceptsClinicianAndAdmin() {
        for (String role : new String[]{"admin", "clinician"}) {
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(
                requestWithHeaders("tenant-1", "principal-1", role));
            assertThat(ctx.role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("requireContext accepts patient and caregiver roles")
    void requireContextAcceptsPatientAndCaregiver() {
        for (String role : new String[]{"patient", "caregiver"}) {
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(
                requestWithHeaders("tenant-1", "principal-1", role));
            assertThat(ctx.role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("requireContext preserves patient principal for downstream policy")
    void requireContextPreservesPatientPrincipal() {
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(
            requestWithHeaders("tenant-1", "patient-42", "patient"));
        assertThat(ctx.role()).isEqualTo("patient");
        assertThat(ctx.principalId()).isEqualTo("patient-42");
    }

    @Test
    @DisplayName("requireContext preserves privileged roles for downstream policy")
    void requireContextPreservesPrivilegedRoles() {
        for (String role : new String[]{"clinician", "admin"}) {
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(
                requestWithHeaders("tenant-1", "principal-1", role));
            assertThat(ctx.role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("JSON and error responses always include correlation headers")
    void structuredResponsesAlwaysIncludeCorrelationHeaders() throws Exception {
        HttpResponse jsonResponse = runPromise(() -> PhrRouteSupport.jsonResponse(200, Map.of("ok", true)));
        HttpResponse errorResponse = runPromise(() -> PhrRouteSupport.errorResponse(403, "DENIED", "Denied"));

        String jsonCorrelationId = jsonResponse.getHeader(HttpHeaders.of("X-Correlation-ID"));
        String errorCorrelationId = errorResponse.getHeader(HttpHeaders.of("X-Correlation-ID"));
        assertThat(jsonCorrelationId).matches("^[0-9a-fA-F-]{36}$");
        assertThat(errorCorrelationId).matches("^[0-9a-fA-F-]{36}$");
        assertThat(body(errorResponse)).contains("\"correlationId\":\"" + errorCorrelationId + "\"");
    }

    @Test
    @DisplayName("text responses include generated correlation header")
    void textResponsesIncludeCorrelationHeader() {
        HttpResponse response = runPromise(
            () -> PhrRouteSupport.textResponse(200, "ok", "text/plain"));

        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID")))
            .matches("^[0-9a-fA-F-]{36}$");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a mock {@link HttpRequest} with the supplied header values.
     * A {@code null} value means the header is absent from the request.
     */
    private static HttpRequest requestWithHeaders(String tenantId, String principalId, String role) {
        return requestWithCorrelation(tenantId, principalId, role, null);
    }

    private static HttpRequest requestWithCorrelation(
            String tenantId, String principalId, String role, String correlationId) {
        return requestWithFullContext(
            tenantId,
            principalId,
            role,
            role == null ? null : role.strip().toLowerCase(),
            "core",
            correlationId
        );
    }

    private static HttpRequest requestWithFullContext(
            String tenantId, String principalId, String role, String persona, String tier, String correlationId) {
        HttpRequest request = mock(HttpRequest.class);
        // ActiveJ normalises header names to lower-case, so HttpHeaders.of("X-Tenant-ID") and
        // HttpHeaders.of("X-Tenant-Id") resolve to the same HttpHeader key. Stubbing both forms
        // causes the second stub (null) to overwrite the first. Stub only the primary form;
        // firstHeader() will match it regardless of which capitalisation it uses to look up.
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Persona"))).thenReturn(persona);
        when(request.getHeader(HttpHeaders.of("X-Tier"))).thenReturn(tier);
        when(request.getHeader(HttpHeaders.of("X-Correlation-ID"))).thenReturn(correlationId);
        return request;
    }

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }
}
