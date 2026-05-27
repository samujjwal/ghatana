package com.ghatana.phr.api.routes;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.repository.UserRepository;
import io.activej.http.AsyncServlet;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Enforcement matrix tests for {@link PhrAuthRoutes}.
 *
 * <p>Verifies the login and logout endpoint invariants:
 * <ul>
 *   <li>Valid credentials produce a 200 session envelope.</li>
 *   <li>Invalid credentials produce a 401.</li>
 *   <li>Malformed request bodies produce a 400.</li>
 *   <li>Logout always returns 204 regardless of authentication state.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Auth enforcement matrix: verifies login/logout invariants for PHR auth routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrAuthRoutes — enforcement matrix")
@ExtendWith(MockitoExtension.class)
class PhrAuthRoutesTest extends EventloopTestBase {

    @Mock
    private KernelSecurityManager securityManager;

    @Mock
    private UserRepository userRepository;

    private AsyncServlet servlet;

    private static final String VALID_BODY = """
        {"nationalId":"patient-001","password":"Secure!P@ss1"}
        """;

    @BeforeEach
    void setUp() {
        servlet = new PhrAuthRoutes(eventloop(), securityManager, userRepository, null).getServlet();

        PHRUser user = new PHRUser();
        user.setUserId("patient-001");
        user.setUsername("patient-001");
        user.setTenantId("t1");
        user.setRoles(Set.of("patient"));

        lenient().when(securityManager.validateCredentials(any()))
            .thenReturn(new KernelSecurityManager.ValidationResult(true, null));
        lenient().when(userRepository.findByUsername(anyString()))
            .thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("POST /login")
    class Login {

        @Test
        @DisplayName("200 — valid credentials return session envelope")
        void validCredentialsReturn200() throws Exception {
            HttpRequest request = postRequest("/login", VALID_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("401 — invalid credentials return 401")
        void invalidCredentialsReturn401() throws Exception {
            lenient().when(securityManager.validateCredentials(any()))
                .thenReturn(new KernelSecurityManager.ValidationResult(false, "INVALID"));

            HttpRequest request = postRequest("/login", VALID_BODY);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(401);
        }

        @Test
        @DisplayName("400 — missing nationalId field returns 400")
        void missingNationalIdReturns400() throws Exception {
            HttpRequest request = postRequest("/login", """
                {"password":"Secure!P@ss1"}
                """);

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 — malformed JSON returns 400")
        void malformedJsonReturns400() throws Exception {
            HttpRequest request = postRequest("/login", "not-json");

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("POST /logout")
    class Logout {

        @Test
        @DisplayName("204 — logout always returns 204 regardless of session state")
        void logoutReturns204WithPrincipal() throws Exception {
            HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost/logout")
                .withHeader(io.activej.http.HttpHeaders.of("X-Principal-ID"), "patient-001")
                .build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(204);
        }

        @Test
        @DisplayName("204 — logout without principal header still returns 204")
        void logoutReturns204WithoutPrincipal() throws Exception {
            HttpRequest request = HttpRequest.post("http://localhost/logout").build();

            HttpResponse response = runPromise(() -> servlet.serve(request));

            assertThat(response.getCode()).isEqualTo(204);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static HttpRequest postRequest(String path, String body) {
        return HttpRequest.builder(HttpMethod.POST, "http://localhost" + path)
            .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
            .withBody(body.getBytes(StandardCharsets.UTF_8))
            .build();
    }
}
