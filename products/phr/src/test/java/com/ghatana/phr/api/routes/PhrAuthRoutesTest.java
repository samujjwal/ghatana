package com.ghatana.phr.api.routes;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Enforcement matrix tests for {@link PhrAuthRoutes}.
 *
 * @doc.type class
 * @doc.purpose Auth enforcement matrix for PHR auth routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAuthRoutes - enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrAuthRoutesTest extends EventloopTestBase {

    @Mock
    private KernelSecurityManager securityManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditTrailService auditTrailService;

    private AsyncServlet servlet;

    private static final String VALID_BODY = """
        {"nationalId":"patient-001","password":"Secure!P@ss1"}
        """;

    @BeforeEach
    void setUp() {
        servlet = new PhrAuthRoutes(eventloop(), securityManager, userRepository, auditTrailService).getServlet();

        PHRUser user = new PHRUser();
        user.setUserId("patient-001");
        user.setUsername("patient-001");
        user.setTenantId("t1");
        user.setRoles(Set.of("patient"));

        lenient().when(securityManager.validateCredentials(any()))
            .thenReturn(new KernelSecurityManager.ValidationResult(true, null));
        lenient().when(userRepository.findByUsername(anyString()))
            .thenReturn(Optional.of(user));
        lenient().when(userRepository.findByUserId("patient-001"))
            .thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 - valid credentials return session envelope and audit event")
        void validCredentialsReturn200() throws Exception {
            HttpRequest request = postRequest("/login", VALID_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
            ArgumentCaptor<AuditTrailService.AuditTrailEvent> eventCaptor =
                ArgumentCaptor.forClass(AuditTrailService.AuditTrailEvent.class);
            verify(auditTrailService).recordAuditEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUTH_LOGIN_SUCCESS");
            assertThat(eventCaptor.getValue().getData()).containsEntry("correlationId", "auth-test-corr-1");
        }

        @Test
        @DisplayName("401 - invalid credentials return 401 and audit event")
        void invalidCredentialsReturn401() throws Exception {
            lenient().when(securityManager.validateCredentials(any()))
                .thenReturn(new KernelSecurityManager.ValidationResult(false, "INVALID"));

            HttpRequest request = postRequest("/login", VALID_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
            ArgumentCaptor<AuditTrailService.AuditTrailEvent> eventCaptor =
                ArgumentCaptor.forClass(AuditTrailService.AuditTrailEvent.class);
            verify(auditTrailService).recordAuditEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUTH_LOGIN_FAILED");
            assertThat(eventCaptor.getValue().getData()).containsEntry("correlationId", "auth-test-corr-1");
        }

        @Test
        @DisplayName("400 - missing nationalId field returns 400")
        void missingNationalIdReturns400() throws Exception {
            HttpRequest request = postRequest("/login", """
                {"password":"Secure!P@ss1"}
                """);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }

        @Test
        @DisplayName("400 - malformed JSON returns 400")
        void malformedJsonReturns400() throws Exception {
            HttpRequest request = postRequest("/login", "not-json");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("204 - logout validates session context and writes audit event")
        void logoutReturns204WithValidSession() throws Exception {
            HttpRequest request = contextRequest("/logout", "t1", "patient-001", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(204);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
            ArgumentCaptor<AuditTrailService.AuditTrailEvent> eventCaptor =
                ArgumentCaptor.forClass(AuditTrailService.AuditTrailEvent.class);
            verify(auditTrailService).recordAuditEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getEventType()).isEqualTo("AUTH_LOGOUT");
            assertThat(eventCaptor.getValue().getUserId()).isEqualTo("patient-001");
            assertThat(eventCaptor.getValue().getData()).containsEntry("correlationId", "auth-test-corr-1");
        }

        @Test
        @DisplayName("401 - logout without session context is denied")
        void logoutWithoutContextIsDenied() throws Exception {
            HttpRequest request = HttpRequest.post("http://localhost/logout")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "auth-test-corr-1")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }

        @Test
        @DisplayName("401 - logout with unknown principal is denied")
        void logoutWithUnknownPrincipalIsDenied() throws Exception {
            lenient().when(userRepository.findByUserId("unknown-user"))
                .thenReturn(Optional.empty());
            HttpRequest request = contextRequest("/logout", "t1", "unknown-user", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }
    }

    @Nested
    @DisplayName("GET /me")
    class Me {

        @Test
        @DisplayName("200 - current actor returns correlation header")
        void currentActorReturnsCorrelationHeader() throws Exception {
            HttpRequest request = contextRequest(HttpMethod.GET, "/me", "t1", "patient-001", "patient");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }

        @Test
        @DisplayName("401 - current actor without session context is denied with correlation header")
        void currentActorWithoutContextIsDeniedWithCorrelationHeader() throws Exception {
            HttpRequest request = HttpRequest.get("http://localhost/me")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "auth-test-corr-1")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(401);
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("auth-test-corr-1");
        }
    }

    private static HttpRequest postRequest(String path, String body) {
        return HttpRequest.builder(HttpMethod.POST, "http://localhost" + path)
            .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "auth-test-corr-1")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }

    private static HttpRequest contextRequest(String path, String tenantId, String principalId, String role) {
        return contextRequest(HttpMethod.POST, path, tenantId, principalId, role);
    }

    private static HttpRequest contextRequest(HttpMethod method, String path, String tenantId, String principalId, String role) {
        return HttpRequest.builder(method, "http://localhost" + path)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principalId)
            .withHeader(HttpHeaders.of("X-Role"), role)
            .withHeader(HttpHeaders.of("X-Persona"), role)
            .withHeader(HttpHeaders.of("X-Tier"), "core")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "auth-test-corr-1")
            .build();
    }
}
