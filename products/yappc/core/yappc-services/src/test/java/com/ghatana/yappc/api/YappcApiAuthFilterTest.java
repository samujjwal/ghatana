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
    void clearTenantContext() { // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @Test
    @DisplayName("returns 401 when credentials are missing")
    void returns401WhenCredentialsAreMissing() { // GH-90000
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> Optional.empty()); // GH-90000
        AtomicReference<Boolean> delegateReached = new AtomicReference<>(false); // GH-90000
        AsyncServlet delegate = request -> {
            delegateReached.set(true); // GH-90000
            return HttpResponse.ok200().toPromise(); // GH-90000
        };

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/intent:test") // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        assertThat(response.getHeader(HttpHeaders.of("WWW-Authenticate"))).isEqualTo("Bearer realm=\"YAPPC API\"");
        assertThat(delegateReached.get()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("accepts X-API-Key credentials and attaches principal context")
    void acceptsApiKeyAndAttachesPrincipalContext() { // GH-90000
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> Optional.of(new Principal("api-user", List.of("admin"), "tenant-alpha")));
        AtomicReference<String> capturedTenant = new AtomicReference<>(); // GH-90000
        AtomicReference<Principal> capturedPrincipal = new AtomicReference<>(); // GH-90000
        AsyncServlet delegate = request -> {
            capturedTenant.set(TenantContext.getCurrentTenantId()); // GH-90000
            capturedPrincipal.set(request.getAttachment(Principal.class)); // GH-90000
            return HttpResponse.ok200().toPromise(); // GH-90000
        };

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/intent:test") // GH-90000
            .withHeader(HttpHeaders.of("X-API-Key"), "valid-key")
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(capturedTenant.get()).isEqualTo("tenant-alpha");
        assertThat(capturedPrincipal.get()).isNotNull(); // GH-90000
        assertThat(capturedPrincipal.get().getName()).isEqualTo("api-user");
        assertThat(TenantContext.getCurrentTenantId()).isEqualTo("default-tenant");
    }

    @Test
    @DisplayName("accepts bearer credentials through the same resolver path")
    void acceptsBearerCredentials() { // GH-90000
        AtomicReference<String> capturedCredential = new AtomicReference<>(); // GH-90000
        YappcApiAuthFilter filter = new YappcApiAuthFilter(key -> { // GH-90000
            capturedCredential.set(key); // GH-90000
            return Optional.of(new Principal("bearer-user", List.of("admin"), "tenant-beta"));
        });
        AsyncServlet delegate = request -> HttpResponse.ok200().toPromise(); // GH-90000

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/v1/yappc/shape:test") // GH-90000
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer bearer-token") // GH-90000
            .build(); // GH-90000

        HttpResponse response = runPromise(() -> filter.secure(delegate).serve(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(capturedCredential.get()).isEqualTo("bearer-token");
    }
}
