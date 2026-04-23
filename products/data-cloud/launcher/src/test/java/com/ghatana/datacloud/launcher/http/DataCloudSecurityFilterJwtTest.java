package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.governance.security.ApiKeyResolver;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataCloudSecurityFilter JWT authentication")
class DataCloudSecurityFilterJwtTest extends EventloopTestBase {

    private static final String VALID_TOKEN = "jwt-token-valid";
    private static final String INVALID_TOKEN = "jwt-token-invalid";

    private JwtTokenProvider jwtProvider;
    private ApiKeyResolver apiKeyResolver;

    private static final AsyncServlet OK_DELEGATE =
            request -> Promise.of(HttpResponse.ok200().build()); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        jwtProvider = mock(JwtTokenProvider.class); // GH-90000
        apiKeyResolver = mock(ApiKeyResolver.class); // GH-90000

        when(jwtProvider.validateToken(VALID_TOKEN)).thenReturn(true); // GH-90000
        when(jwtProvider.validateToken(INVALID_TOKEN)).thenReturn(false); // GH-90000
        when(jwtProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(Optional.of("jwt-user"));
        when(jwtProvider.getRolesFromToken(VALID_TOKEN)).thenReturn(List.of("admin"));
        when(jwtProvider.extractClaims(VALID_TOKEN)).thenReturn(Optional.of(Map.of("tenant_id", "tenant-jwt"))); // GH-90000
    }

    @Test
    @DisplayName("JWT-authenticated request passes through and resolves tenant from claims")
    void jwtAuthenticatedRequestPassesThrough() { // GH-90000
        AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                .jwtProvider(jwtProvider) // GH-90000
                .build() // GH-90000
                .apply(OK_DELEGATE); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

        assertThat(status).isEqualTo(200); // GH-90000
        verify(jwtProvider).extractClaims(VALID_TOKEN); // GH-90000
    }

        @Test
        @DisplayName("cookie-backed JWT request passes through when Authorization header is absent")
        void cookieBackedJwtRequestPassesThrough() { // GH-90000
                AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                                .jwtProvider(jwtProvider) // GH-90000
                                .build() // GH-90000
                                .apply(OK_DELEGATE); // GH-90000

                HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                                .withHeader(HttpHeaders.COOKIE, DataCloudSecurityFilter.AUTH_TOKEN_COOKIE + "=" + VALID_TOKEN) // GH-90000
                                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                                .build(); // GH-90000

                int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

                assertThat(status).isEqualTo(200); // GH-90000
                verify(jwtProvider).validateToken(VALID_TOKEN); // GH-90000
        }

    @Test
    @DisplayName("invalid JWT request returns 401")
    void invalidJwtRequestReturns401() { // GH-90000
        AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                .jwtProvider(jwtProvider) // GH-90000
                .build() // GH-90000
                .apply(OK_DELEGATE); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + INVALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

        assertThat(status).isEqualTo(401); // GH-90000
    }

        @Test
        @DisplayName("invalid JWT cookie returns 401")
        void invalidJwtCookieReturns401() { // GH-90000
                AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                                .jwtProvider(jwtProvider) // GH-90000
                                .build() // GH-90000
                                .apply(OK_DELEGATE); // GH-90000

                HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                                .withHeader(HttpHeaders.COOKIE, DataCloudSecurityFilter.AUTH_TOKEN_COOKIE + "=" + INVALID_TOKEN) // GH-90000
                                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                                .build(); // GH-90000

                int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

                assertThat(status).isEqualTo(401); // GH-90000
        }

    @Test
    @DisplayName("JWT request with mismatched tenant header returns 403")
    void jwtRequestWithMismatchedTenantHeaderReturns403() { // GH-90000
        AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                .jwtProvider(jwtProvider) // GH-90000
                .build() // GH-90000
                .apply(OK_DELEGATE); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-other")
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

        assertThat(status).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("JWT request with mismatched tenant query parameter returns 403")
    void jwtRequestWithMismatchedTenantQueryReturns403() { // GH-90000
        AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                .jwtProvider(jwtProvider) // GH-90000
                .build() // GH-90000
                .apply(OK_DELEGATE); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health?tenantId=tenant-other")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

        assertThat(status).isEqualTo(403); // GH-90000
    }

    @Test
    @DisplayName("API key is checked before JWT when both credentials are present")
    void apiKeyCheckedBeforeJwt() { // GH-90000
        when(apiKeyResolver.resolve("bad-key")).thenReturn(Optional.empty());

        AsyncServlet secured = DataCloudSecurityFilter.builder() // GH-90000
                .apiKeyResolver(apiKeyResolver) // GH-90000
                .jwtProvider(jwtProvider) // GH-90000
                .build() // GH-90000
                .apply(OK_DELEGATE); // GH-90000

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("X-API-Key"), "bad-key")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost") // GH-90000
                .build(); // GH-90000

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode)); // GH-90000

        assertThat(status).isEqualTo(401); // GH-90000
        verify(jwtProvider, never()).validateToken(VALID_TOKEN); // GH-90000
    }
}
