package com.ghatana.security.interceptor;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.security.audit.SecurityAuditLogger;
import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Tests shared rate-limiter enforcement in the security interceptor
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SecurityInterceptor Tests")
class SecurityInterceptorTest extends EventloopTestBase {

    @Test
    @DisplayName("should reject requests after the configured limit")
    void shouldRejectRequestsAfterConfiguredLimit() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                1,
                60
        );

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("admin"));
        when(policyService.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        HttpRequest first = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/secure/resource")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1")
                .build();
        HttpRequest second = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/secure/resource")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1")
                .build();

        HttpResponse firstResponse = runPromise(() -> interceptor.intercept(first, request -> null));
        HttpResponse secondResponse = runPromise(() -> interceptor.intercept(second, request -> null));

        assertThat(firstResponse.getCode()).isEqualTo(200);
        assertThat(secondResponse.getCode()).isEqualTo(429);
        assertThat(secondResponse.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        verify(auditLogger).logRateLimitExceeded("10.0.0.1", "/api/secure/resource", "GET");
    }

    @Test
    @DisplayName("should attach shared rate-limit headers to successful responses")
    void shouldAttachRateLimitHeadersToSuccessfulResponses() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                5,
                60
        );

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("reader"));
        when(policyService.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/orders")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.2")
                .build();

        HttpResponse response = runPromise(() -> interceptor.intercept(request, next -> null));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-RateLimit-Limit"))).isEqualTo("5");
        assertThat(response.getHeader(HttpHeaders.of("X-RateLimit-Remaining"))).isNotBlank();
        assertThat(response.getHeader(HttpHeaders.of("X-RateLimit-Reset"))).isNotBlank();
        verify(auditLogger, never()).logRateLimitExceeded(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("should preserve provided correlation ID on authorized responses")
    void shouldPreserveProvidedCorrelationId() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                5,
                60
        );

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("reader"));
        when(policyService.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/orders")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-security-123")
                .build();

        HttpResponse response = runPromise(() -> interceptor.intercept(request, next -> null));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("corr-security-123");
    }

    @Test
    @DisplayName("should generate correlation ID for unauthorized responses when request is missing one")
    void shouldGenerateCorrelationIdForUnauthorizedResponses() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                0,
                60
        );

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/orders")
                .build();

        HttpResponse response = runPromise(() -> interceptor.intercept(request, next -> null));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isNotBlank();
    }

    @Test
    @DisplayName("should pass tenant header into authorization principal")
    void shouldPassTenantHeaderIntoAuthorizationPrincipal() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                0,
                60
        );

        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-1"));
        when(jwtTokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("admin"));
        when(policyService.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/admin/users")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant-42")
                .build();

        HttpResponse response = runPromise(() -> interceptor.intercept(request, next -> null));

        assertThat(response.getCode()).isEqualTo(200);

        ArgumentCaptor<Principal> principalCaptor = ArgumentCaptor.forClass(Principal.class);
        verify(policyService).isAuthorized(principalCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(principalCaptor.getValue().getTenantId()).isEqualTo("tenant-42");
        assertThat(principalCaptor.getValue().getName()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("per-user rate limit is enforced when the same user connects from different IPs")
    void shouldEnforcePerUserRateLimitAcrossDifferentClientIps() {
        PolicyService policyService = mock(PolicyService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        SecurityAuditLogger auditLogger = mock(SecurityAuditLogger.class);

        // Limit of 1 request per minute — so the second request from the same user must be rejected
        // regardless of whether it arrives from a different client IP.
        SecurityInterceptor interceptor = new SecurityInterceptor(
                policyService,
                jwtTokenProvider,
                auditLogger,
                1,
                60
        );

        when(jwtTokenProvider.validateToken("token-alice")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("token-alice")).thenReturn(Optional.of("alice"));
        when(jwtTokenProvider.getRolesFromToken("token-alice")).thenReturn(List.of("reader"));
        when(policyService.isAuthorized(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        // First request from IP 10.0.0.1 — should succeed
        HttpRequest firstIp = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/data")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer token-alice")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.1")
                .build();
        // Second request from a completely different IP 10.0.0.2 — same user, must still be rejected
        HttpRequest secondIp = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/data")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer token-alice")
                .withHeader(HttpHeaders.of("X-Forwarded-For"), "10.0.0.2")
                .build();

        HttpResponse first = runPromise(() -> interceptor.intercept(firstIp, request -> null));
        HttpResponse second = runPromise(() -> interceptor.intercept(secondIp, request -> null));

        assertThat(first.getCode()).isEqualTo(200);
        assertThat(second.getCode()).isEqualTo(429);
        assertThat(second.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        verify(auditLogger).logRateLimitExceeded("alice", "/api/data", "GET");
    }
}
