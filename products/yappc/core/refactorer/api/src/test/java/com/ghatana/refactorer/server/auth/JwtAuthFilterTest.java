package com.ghatana.refactorer.server.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.refactorer.server.config.ServerConfig;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies JWT auth filter chaining and tenant attachment behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest extends EventloopTestBase {

    @Test
    @DisplayName("returns unauthorized without calling next when bearer token is missing")
    void returnsUnauthorizedWhenBearerTokenMissing() throws Exception {
        AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        JwtAuthFilter filter = new JwtAuthFilter(
                delegate,
                new AccessPolicy(new ServerConfig.TenancyConfig(2, 20, true)),
                tokenProvider);
        AsyncServlet next = mock(AsyncServlet.class);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs").build();

        HttpResponse response = runPromise(() -> filter.filter(request, next));

        assertThat(response.getCode()).isEqualTo(401);
        verify(next, never()).serve(request);
    }

    @Test
    @DisplayName("attaches tenant context and delegates to next when token is valid")
    void attachesTenantContextAndDelegatesToNext() throws Exception {
        AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build());
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.extractClaims("valid-token"))
                .thenReturn(Optional.of(Map.of("tenantId", "tenant-1", "region", "us-east-1")));
        when(tokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-1"));
        when(tokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("admin"));

        JwtAuthFilter filter = new JwtAuthFilter(
                delegate,
                new AccessPolicy(new ServerConfig.TenancyConfig(2, 20, true)),
                tokenProvider);
        AsyncServlet next = request -> {
            TenantContext tenantContext = TenantResolver.require(request);
            assertThat(tenantContext.tenantId()).isEqualTo("tenant-1");
            assertThat(tenantContext.subject()).isEqualTo("user-1");
            assertThat(tenantContext.roles()).containsExactly("admin");
            return Promise.of(HttpResponse.ok200().build());
        };
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        HttpResponse response = runPromise(() -> filter.filter(request, next));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
