package com.ghatana.phr.api.routes;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for request context normalization (G4-T06).
 *
 * <p>Verifies that all context fields are properly normalized:
 * <ul>
 *   <li>Tenant ID is stripped of whitespace</li>
 *   <li>Principal ID is stripped of whitespace</li>
 *   <li>Role is stripped and normalized to lower-case</li>
 *   <li>Persona is preserved or defaults to role</li>
 *   <li>Tier is preserved or defaults to core</li>
 *   <li>Facility ID is preserved or null</li>
 *   <li>Correlation ID is stripped or generated</li>
 * </ul>
 */
@DisplayName("PhrRequestContext normalization")
class PhrRequestContextNormalizationTest {

    @Test
    @DisplayName("tenant ID is stripped of whitespace")
    void tenantIdIsStripped() {
        HttpRequest request = requestWithHeaders("  tenant-1  ", "user-42", "patient");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    @DisplayName("principal ID is stripped of whitespace")
    void principalIdIsStripped() {
        HttpRequest request = requestWithHeaders("tenant-1", "  user-42  ", "patient");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.principalId()).isEqualTo("user-42");
    }

    @Test
    @DisplayName("role is stripped of whitespace and normalized to lower-case")
    void roleIsStrippedAndNormalized() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "  CLINICIAN  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.role()).isEqualTo("clinician");
    }

    @Test
    @DisplayName("persona header is preserved when provided")
    void personaHeaderIsPreserved() {
        HttpRequest request = requestWithPersona("tenant-1", "user-42", "clinician", "clinician");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.persona()).isEqualTo("clinician");
    }

    @Test
    @DisplayName("persona defaults to role when not provided")
    void personaDefaultsToRole() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "clinician");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.persona()).isEqualTo("clinician");
    }

    @Test
    @DisplayName("persona is stripped of whitespace")
    void personaIsStripped() {
        HttpRequest request = requestWithPersona("tenant-1", "user-42", "clinician", "  clinician  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.persona()).isEqualTo("clinician");
    }

    @Test
    @DisplayName("tier header is preserved when provided")
    void tierHeaderIsPreserved() {
        HttpRequest request = requestWithTier("tenant-1", "user-42", "clinician", "clinical");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.tier()).isEqualTo("clinical");
    }

    @Test
    @DisplayName("tier defaults to core when not provided")
    void tierDefaultsToCore() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "clinician");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.tier()).isEqualTo("core");
    }

    @Test
    @DisplayName("tier is stripped of whitespace")
    void tierIsStripped() {
        HttpRequest request = requestWithTier("tenant-1", "user-42", "clinician", "  clinical  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.tier()).isEqualTo("clinical");
    }

    @Test
    @DisplayName("facility ID is preserved when provided")
    void facilityIdIsPreserved() {
        HttpRequest request = requestWithFacility("tenant-1", "user-42", "clinician", "facility-123");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.facilityId()).isEqualTo("facility-123");
    }

    @Test
    @DisplayName("facility ID is null when not provided")
    void facilityIdIsNullWhenNotProvided() {
        HttpRequest request = requestWithHeaders("tenant-1", "user-42", "clinician");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.facilityId()).isNull();
    }

    @Test
    @DisplayName("facility ID is stripped of whitespace")
    void facilityIdIsStripped() {
        HttpRequest request = requestWithFacility("tenant-1", "user-42", "clinician", "  facility-123  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.facilityId()).isEqualTo("facility-123");
    }

    @Test
    @DisplayName("correlation ID is stripped of whitespace")
    void correlationIdIsStripped() {
        HttpRequest request = requestWithCorrelation("tenant-1", "user-42", "patient", "  corr-xyz  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.correlationId()).isEqualTo("corr-xyz");
    }

    @Test
    @DisplayName("all context fields are normalized together")
    void allContextFieldsAreNormalized() {
        HttpRequest request = requestWithFullContext(
            "  tenant-1  ", "  user-42  ", "  CLINICIAN  ", 
            "  clinician  ", "  clinical  ", "  facility-123  ", "  corr-xyz  ");
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.principalId()).isEqualTo("user-42");
        assertThat(ctx.role()).isEqualTo("clinician");
        assertThat(ctx.persona()).isEqualTo("clinician");
        assertThat(ctx.tier()).isEqualTo("clinical");
        assertThat(ctx.facilityId()).isEqualTo("facility-123");
        assertThat(ctx.correlationId()).isEqualTo("corr-xyz");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static HttpRequest requestWithHeaders(String tenantId, String principalId, String role) {
        return requestWithCorrelation(tenantId, principalId, role, null);
    }

    private static HttpRequest requestWithCorrelation(
            String tenantId, String principalId, String role, String correlationId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Correlation-ID"))).thenReturn(correlationId);
        return request;
    }

    private static HttpRequest requestWithPersona(
            String tenantId, String principalId, String role, String persona) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Persona"))).thenReturn(persona);
        return request;
    }

    private static HttpRequest requestWithTier(
            String tenantId, String principalId, String role, String tier) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Tier"))).thenReturn(tier);
        return request;
    }

    private static HttpRequest requestWithFacility(
            String tenantId, String principalId, String role, String facilityId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Facility-ID"))).thenReturn(facilityId);
        return request;
    }

    private static HttpRequest requestWithFullContext(
            String tenantId, String principalId, String role, String persona, 
            String tier, String facilityId, String correlationId) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Tenant-ID"))).thenReturn(tenantId);
        when(request.getHeader(HttpHeaders.of("X-Principal-ID"))).thenReturn(principalId);
        when(request.getHeader(HttpHeaders.of("X-Role"))).thenReturn(role);
        when(request.getHeader(HttpHeaders.of("X-Persona"))).thenReturn(persona);
        when(request.getHeader(HttpHeaders.of("X-Tier"))).thenReturn(tier);
        when(request.getHeader(HttpHeaders.of("X-Facility-ID"))).thenReturn(facilityId);
        when(request.getHeader(HttpHeaders.of("X-Correlation-ID"))).thenReturn(correlationId);
        return request;
    }
}
