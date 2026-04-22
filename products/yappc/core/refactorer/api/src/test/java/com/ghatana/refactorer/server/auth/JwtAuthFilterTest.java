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
@DisplayName("JwtAuthFilter [GH-90000]")
class JwtAuthFilterTest extends EventloopTestBase {

    @Test
    @DisplayName("returns unauthorized without calling next when bearer token is missing [GH-90000]")
    void returnsUnauthorizedWhenBearerTokenMissing() throws Exception { // GH-90000
        AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthFilter filter = new JwtAuthFilter( // GH-90000
                delegate,
                new AccessPolicy(new ServerConfig.TenancyConfig(2, 20, true)), // GH-90000
                tokenProvider);
        AsyncServlet next = mock(AsyncServlet.class); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs [GH-90000]").build();

        HttpResponse response = runPromise(() -> filter.filter(request, next)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        verify(next, never()).serve(request); // GH-90000
    }

    @Test
    @DisplayName("attaches tenant context and delegates to next when token is valid [GH-90000]")
    void attachesTenantContextAndDelegatesToNext() throws Exception { // GH-90000
        AsyncServlet delegate = request -> Promise.of(HttpResponse.ok200().build()); // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        when(tokenProvider.validateToken("valid-token [GH-90000]")).thenReturn(true);
        when(tokenProvider.extractClaims("valid-token [GH-90000]"))
                .thenReturn(Optional.of(Map.of("tenantId", "tenant-1", "region", "us-east-1"))); // GH-90000
        when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]")).thenReturn(Optional.of("user-1 [GH-90000]"));
        when(tokenProvider.getRolesFromToken("valid-token [GH-90000]")).thenReturn(List.of("admin [GH-90000]"));

        JwtAuthFilter filter = new JwtAuthFilter( // GH-90000
                delegate,
                new AccessPolicy(new ServerConfig.TenancyConfig(2, 20, true)), // GH-90000
                tokenProvider);
        AsyncServlet next = request -> {
            TenantContext tenantContext = TenantResolver.require(request); // GH-90000
            assertThat(tenantContext.tenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(tenantContext.subject()).isEqualTo("user-1 [GH-90000]");
            assertThat(tenantContext.roles()).containsExactly("admin [GH-90000]");
            return Promise.of(HttpResponse.ok200().build()); // GH-90000
        };
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs [GH-90000]")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.filter(request, next)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }
}
