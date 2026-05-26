package com.ghatana.phr.api.routes;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhrRouteSupport#requireContext(HttpRequest)}.
 *
 * <p>Verifies the fail-closed security contract:
 * <ul>
 *   <li>All three headers must be present and non-blank.</li>
 *   <li>Role must be a member of the allowed set.</li>
 *   <li>No implicit default role is assigned on missing input.</li>
 *   <li>Role is normalised to lower-case.</li>
 * </ul>
 */
@DisplayName("PhrRouteSupport security context extraction")
class PhrRouteSupportTest {

    @Test
    @DisplayName("valid headers return a correctly populated context")
    void validHeadersReturnContext() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "patient");

        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);

        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.principalId()).isEqualTo("user-42");
        assertThat(ctx.role()).isEqualTo("patient");
        assertThat(ctx.correlationId()).isEqualTo("no-correlation-id");
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
            HttpRequest request = requestWithHeaders("t1", "p1", role);
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
    @DisplayName("correlationId falls back to no-correlation-id when header absent")
    void correlationIdFallbackWhenAbsent() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "patient");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.correlationId()).isEqualTo("no-correlation-id");
    }

    @Test
    @DisplayName("hasClinicalRole returns true for clinician and admin")
    void hasClinicalRoleForClinicianAndAdmin() {
        for (String role : new String[]{"admin", "clinician"}) {
            PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "p1", role, "corr");
            assertThat(PhrRouteSupport.hasClinicalRole(ctx)).isTrue();
        }
    }

    @Test
    @DisplayName("hasClinicalRole returns false for patient and caregiver")
    void hasClinicalRoleReturnsFalseForNonClinical() {
        for (String role : new String[]{"patient", "caregiver"}) {
            PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "p1", role, "corr");
            assertThat(PhrRouteSupport.hasClinicalRole(ctx)).isFalse();
        }
    }

    @Test
    @DisplayName("canAccessPatientRecordForRole — patient may only access own record")
    void patientCanOnlyAccessOwnRecord() {
        PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "patient-42", "patient", "corr");
        assertThat(PhrRouteSupport.canAccessPatientRecordForRole(ctx, "patient-42")).isTrue();
        assertThat(PhrRouteSupport.canAccessPatientRecordForRole(ctx, "patient-99")).isFalse();
    }

    @Test
    @DisplayName("canAccessPatientRecordForRole — clinician and admin access any record")
    void clinicianAndAdminAccessAnyRecord() {
        for (String role : new String[]{"clinician", "admin"}) {
            PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "p1", role, "corr");
            assertThat(PhrRouteSupport.canAccessPatientRecordForRole(ctx, "any-patient")).isTrue();
        }
    }

    @Test
    @DisplayName("isPrivileged returns true for clinician and admin (deprecated bridge)")
    void isPrivilegedForAdminAndClinician() {
        for (String role : new String[]{"admin", "clinician"}) {
            PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "p1", role, "corr");
            assertThat(PhrRouteSupport.isPrivileged(ctx)).isTrue();
        }
    }

    @Test
    @DisplayName("isPrivileged returns false for patient and caregiver (deprecated bridge)")
    void isNotPrivilegedForPatientAndCaregiver() {
        for (String role : new String[]{"patient", "caregiver"}) {
            PhrRouteSupport.PhrRequestContext ctx = new PhrRouteSupport.PhrRequestContext("t1", "p1", role, "corr");
            assertThat(PhrRouteSupport.isPrivileged(ctx)).isFalse();
        }
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
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Tenant-Id"))).thenReturn(null);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Principal-Id"))).thenReturn(null);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Correlation-ID"))).thenReturn(correlationId);
        when(request.getHeader(HttpHeaders.of("X-Correlation-Id"))).thenReturn(null);
        when(request.getHeader(HttpHeaders.of("X-Request-ID"))).thenReturn(null);
        return request;
    }
}
