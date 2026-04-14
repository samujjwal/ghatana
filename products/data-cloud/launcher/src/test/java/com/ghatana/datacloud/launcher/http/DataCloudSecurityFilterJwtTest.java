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
            request -> Promise.of(HttpResponse.ok200().build());

    @BeforeEach
    void setUp() {
        jwtProvider = mock(JwtTokenProvider.class);
        apiKeyResolver = mock(ApiKeyResolver.class);

        when(jwtProvider.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtProvider.validateToken(INVALID_TOKEN)).thenReturn(false);
        when(jwtProvider.getUserIdFromToken(VALID_TOKEN)).thenReturn(Optional.of("jwt-user"));
        when(jwtProvider.getRolesFromToken(VALID_TOKEN)).thenReturn(List.of("viewer"));
        when(jwtProvider.extractClaims(VALID_TOKEN)).thenReturn(Optional.of(Map.of("tenant_id", "tenant-jwt")));
    }

    @Test
    @DisplayName("JWT-authenticated request passes through and resolves tenant from claims")
    void jwtAuthenticatedRequestPassesThrough() {
        AsyncServlet secured = DataCloudSecurityFilter.builder()
                .jwtProvider(jwtProvider)
                .build()
                .apply(OK_DELEGATE);

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(200);
        verify(jwtProvider).extractClaims(VALID_TOKEN);
    }

    @Test
    @DisplayName("invalid JWT request returns 401")
    void invalidJwtRequestReturns401() {
        AsyncServlet secured = DataCloudSecurityFilter.builder()
                .jwtProvider(jwtProvider)
                .build()
                .apply(OK_DELEGATE);

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + INVALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("API key is checked before JWT when both credentials are present")
    void apiKeyCheckedBeforeJwt() {
        when(apiKeyResolver.resolve("bad-key")).thenReturn(Optional.empty());

        AsyncServlet secured = DataCloudSecurityFilter.builder()
                .apiKeyResolver(apiKeyResolver)
                .jwtProvider(jwtProvider)
                .build()
                .apply(OK_DELEGATE);

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/brain/health")
                .withHeader(HttpHeaders.of("X-API-Key"), "bad-key")
                .withHeader(HttpHeaders.of("Authorization"), "Bearer " + VALID_TOKEN)
                .withHeader(HttpHeaders.HOST, "localhost")
                .build();

        int status = runPromise(() -> secured.serve(request).map(HttpResponse::getCode));

        assertThat(status).isEqualTo(401);
        verify(jwtProvider, never()).validateToken(VALID_TOKEN);
    }
}