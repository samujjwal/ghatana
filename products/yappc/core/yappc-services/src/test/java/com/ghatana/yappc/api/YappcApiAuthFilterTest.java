package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies YAPPC API auth filter fail-closed behavior and principal propagation for protected HTTP flows
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcApiAuthFilter")
class YappcApiAuthFilterTest extends EventloopTestBase {

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("returns 401 when credentials are missing")
    void returns401WhenCredentialsAreMissing() {
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> Optional.empty());
        AtomicReference<Boolean> delegateReached = new AtomicReference<>(false);
        AsyncServlet delegate = request -> {
            delegateReached.set(true);
            return HttpResponse.ok200().toPromise();
        };

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/intent:test")
            .build();

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.of("WWW-Authenticate"))).isEqualTo("Bearer realm=\"YAPPC API\"");
        assertThat(delegateReached.get()).isFalse();
    }

    @Test
    @DisplayName("accepts X-API-Key credentials and attaches principal context")
    void acceptsApiKeyAndAttachesPrincipalContext() {
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")));
        AtomicReference<String> capturedTenant = new AtomicReference<>();
        AtomicReference<Principal> capturedPrincipal = new AtomicReference<>();
        AsyncServlet delegate = request -> {
            capturedTenant.set(TenantContext.getCurrentTenantId());
            capturedPrincipal.set(request.getAttachment(Principal.class));
            return HttpResponse.ok200().toPromise();
        };

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/intent:test")
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .build();

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(capturedTenant.get()).isEqualTo("tenant-alpha");
        assertThat(capturedPrincipal.get()).isNotNull();
        assertThat(capturedPrincipal.get().getName()).isEqualTo("api-user");
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
    }

    @Test
    @DisplayName("accepts bearer credentials through the same resolver path")
    void acceptsBearerCredentials() {
        AtomicReference<String> capturedCredential = new AtomicReference<>();
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> {
            capturedCredential.set(key);
            return Optional.of(new Principal("bearer-user", List.of("admin"), "tenant-beta"));
        });
        AsyncServlet delegate = request -> HttpResponse.ok200().toPromise();

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/shape:test")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer bearer-token")
            .build();

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(capturedCredential.get()).isEqualTo("bearer-token");
    }
}
