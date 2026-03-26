package com.ghatana.security.interceptor;

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
}