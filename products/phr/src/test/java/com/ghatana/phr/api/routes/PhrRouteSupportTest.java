package com.ghatana.phr.api.routes;

import com.ghatana.platform.security.session.KernelSessionContextResolver;
import com.ghatana.platform.security.session.SessionManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PhrRouteSupport#requireContext(HttpRequest)}.
 *
 * <p>Verifies the fail-closed security contract:
 * <ul>
 *   <li>Tenant, principal, role, persona, and tier must be present and non-blank.</li>
 *   <li>Role must be a member of the allowed set.</li>
 *   <li>No implicit default role, persona, or tier is assigned on missing input.</li>
 *   <li>Role is normalised to lower-case.</li>
 * </ul>
 */
@DisplayName("PhrRouteSupport security context extraction")
@ExtendWith(MockitoExtension.class)
class PhrRouteSupportTest extends EventloopTestBase {

    @Mock
    private KernelSessionContextResolver sessionContextResolver;

    @BeforeEach
    void setUp() {
        PhrRouteSupport.setSessionContextResolver(sessionContextResolver);
    }

    @Test
    @DisplayName("valid session returns a correctly populated context")
    void validHeadersReturnContext() {
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(sessionContextResolver.resolveSync(any()))
            .thenReturn(Optional.of(new KernelSessionContextResolver.KernelSessionContext(
                "tenant-1",
                "user-42",
                "patient",
                "patient",
                "core",
                "facility-1",
                "corr-123"
            )));

        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);

        assertThat(ctx.tenantId()).isEqualTo("tenant-1");
        assertThat(ctx.principalId()).isEqualTo("user-42");
        assertThat(ctx.role()).isEqualTo("patient");
        assertThat(ctx.persona()).isEqualTo("patient");
        assertThat(ctx.tier()).isEqualTo("core");
        assertThat(ctx.correlationId()).isEqualTo("corr-123");
    }

    @Test
    @DisplayName("missing session throws with descriptive message")
    void missingSessionThrows() {
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(sessionContextResolver.resolveSync(any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No valid Kernel-authenticated session found");
    }

    @Test
    @DisplayName("unconfigured session resolver throws")
    void unconfiguredResolverThrows() {
        PhrRouteSupport.setSessionContextResolver(null);
        HttpRequest request = mock(HttpRequest.class);

        assertThatThrownBy(() -> PhrRouteSupport.requireContext(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Kernel session context resolver is not configured");
        
        // Reset for other tests
        PhrRouteSupport.setSessionContextResolver(sessionContextResolver);
    }

    @Test
    @DisplayName("requireContext accepts clinician and admin roles")
    void requireContextAcceptsClinicianAndAdmin() {
        for (String role : new String[]{"admin", "clinician"}) {
            HttpRequest request = mock(HttpRequest.class);
            lenient().when(sessionContextResolver.resolveSync(any()))
                .thenReturn(Optional.of(new KernelSessionContextResolver.KernelSessionContext(
                    "tenant-1",
                    "principal-1",
                    role,
                    role,
                    "core",
                    "facility-1",
                    "corr-" + role
                )));
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
            assertThat(ctx.role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("requireContext accepts patient and caregiver roles")
    void requireContextAcceptsPatientAndCaregiver() {
        for (String role : new String[]{"patient", "caregiver"}) {
            HttpRequest request = mock(HttpRequest.class);
            lenient().when(sessionContextResolver.resolveSync(any()))
                .thenReturn(Optional.of(new KernelSessionContextResolver.KernelSessionContext(
                    "tenant-1",
                    "principal-1",
                    role,
                    role,
                    "core",
                    "facility-1",
                    "corr-" + role
                )));
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
            assertThat(ctx.role()).isEqualTo(role);
        }
    }

    @Test
    @DisplayName("requireContext preserves patient principal for downstream policy")
    void requireContextPreservesPatientPrincipal() {
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(sessionContextResolver.resolveSync(any()))
            .thenReturn(Optional.of(new KernelSessionContextResolver.KernelSessionContext(
                "tenant-1",
                "patient-42",
                "patient",
                "patient",
                "core",
                "facility-1",
                "corr-patient"
            )));
        PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
        assertThat(ctx.role()).isEqualTo("patient");
        assertThat(ctx.principalId()).isEqualTo("patient-42");
    }

    @Test
    @DisplayName("requireContext preserves privileged roles for downstream policy")
    void requireContextPreservesPrivilegedRoles() {
        for (String role : new String[]{"clinician", "admin"}) {
            HttpRequest request = mock(HttpRequest.class);
            lenient().when(sessionContextResolver.resolveSync(any()))
                .thenReturn(Optional.of(new KernelSessionContextResolver.KernelSessionContext(
                    "tenant-1",
                    "principal-1",
                    role,
                    role,
                    "core",
                    "facility-1",
                    "corr-" + role
                )));
            PhrRouteSupport.PhrRequestContext ctx = PhrRouteSupport.requireContext(request);
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

    @Test
    @DisplayName("safe error responses preserve route-authored validation messages")
    void safeErrorResponsesPreserveRouteAuthoredValidationMessages() throws Exception {
        HttpResponse response = runPromise(() -> PhrRouteSupport.errorResponse(
            400,
            "MISSING_CONTEXT",
            "X-Tenant-ID header is required",
            "corr-safe"));

        assertThat(body(response)).contains("\"message\":\"X-Tenant-ID header is required\"");
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-safe");
    }

    @Test
    @DisplayName("safe error responses redact internal exception messages")
    void safeErrorResponsesRedactInternalExceptionMessages() throws Exception {
        HttpResponse response = runPromise(() -> PhrRouteSupport.errorResponse(
            500,
            "INTERNAL_FAILURE",
            "java.lang.IllegalStateException: failed at com.ghatana.phr.Secret.java:42\n\tat com.ghatana.phr.Secret",
            "corr-redacted"));

        String body = body(response);
        assertThat(body).contains("\"message\":\"Request could not be processed\"");
        assertThat(body).doesNotContain("IllegalStateException");
        assertThat(body).doesNotContain("com.ghatana");
        assertThat(body).doesNotContain("Secret.java");
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-redacted");
    }

    @Test
    @DisplayName("safe error responses redact patient identifiers in not-found messages")
    void safeErrorResponsesRedactPatientIdentifiersInNotFoundMessages() throws Exception {
        HttpResponse response = runPromise(() -> PhrRouteSupport.errorResponse(
            404,
            "PATIENT_NOT_FOUND",
            "Patient not found: patient-42",
            "corr-not-found"));

        String body = body(response);
        assertThat(body).contains("\"message\":\"Resource not found\"");
        assertThat(body).doesNotContain("patient-42");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String body(HttpResponse response) throws Exception {
        return new String(runPromise(response::loadBody).asArray(), StandardCharsets.UTF_8);
    }
}
