package com.ghatana.phr.api;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PhrTenantContextFilter}.
 *
 * @doc.type class
 * @doc.purpose Verifies safe tenant-context rejection responses and tenant isolation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PhrTenantContextFilter")
class PhrTenantContextFilterTest extends EventloopTestBase {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("400 - missing tenant uses safe error envelope with correlation header")
    void missingTenantUsesSafeErrorEnvelopeWithCorrelationHeader() throws Exception {
        PhrTenantContextFilter filter = new PhrTenantContextFilter(request ->
            Promise.of(HttpResponse.ok200().build()));
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/dashboard")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "tenant-corr-1")
            .build();

        HttpResponse response = runPromise(() -> {
            TenantContext.clear();
            return filter.serve(request);
        });

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID"))).isEqualTo("tenant-corr-1");
        assertThat(TenantContext.current()).isEmpty();
    }
}
