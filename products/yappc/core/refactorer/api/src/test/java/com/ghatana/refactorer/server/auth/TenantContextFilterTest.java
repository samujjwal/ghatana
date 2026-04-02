package com.ghatana.refactorer.server.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @doc.type class
 * @doc.purpose Verifies tenant context extraction delegates and always clears thread-local state
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TenantContextFilter")
class TenantContextFilterTest extends EventloopTestBase {

    @Test
    @DisplayName("extracts tenant from attached context before delegating")
    void extractsTenantFromAttachedContextBeforeDelegating() {
        TenantContextFilter filter = new TenantContextFilter(request -> Promise.of(HttpResponse.ok200().build()));
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs").build();
        TenantResolver.attach(request, TenantContext.of("tenant-ctx", "user-1", Set.of("admin"), Map.of()));
        AsyncServlet next = ignoredRequest -> {
            assertThat(com.ghatana.platform.governance.security.TenantContext.getCurrentTenantId())
                    .isEqualTo("tenant-ctx");
            return Promise.of(HttpResponse.ok200().build());
        };

        HttpResponse response = runPromise(() -> filter.filter(request, next));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(com.ghatana.platform.governance.security.TenantContext.getCurrentTenantId())
            .isEqualTo("default-tenant");
    }

    @Test
    @DisplayName("falls back to header tenant id when no attached context exists")
    void fallsBackToHeaderTenantId() {
        TenantContextFilter filter = new TenantContextFilter(request -> Promise.of(HttpResponse.ok200().build()));
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/jobs")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-header")
                .build();
        AsyncServlet next = ignoredRequest -> {
            assertThat(com.ghatana.platform.governance.security.TenantContext.getCurrentTenantId())
                    .isEqualTo("tenant-header");
            return Promise.of(HttpResponse.ok200().build());
        };

        HttpResponse response = runPromise(() -> filter.filter(request, next));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(com.ghatana.platform.governance.security.TenantContext.getCurrentTenantId())
            .isEqualTo("default-tenant");
    }
}