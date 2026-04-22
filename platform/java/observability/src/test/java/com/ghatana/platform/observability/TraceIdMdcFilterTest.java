package com.ghatana.platform.observability;

import com.ghatana.platform.testing.activej.ActiveJServletTestUtil;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TraceIdMdcFilter Tests [GH-90000]")
class TraceIdMdcFilterTest extends EventloopTestBase {

    @AfterEach
    void tearDownContext() { // GH-90000
        CorrelationContext.clear(); // GH-90000
    }

    @Test
    @DisplayName("Should keep correlation context available for async delegate execution [GH-90000]")
    void shouldKeepCorrelationContextAvailableForAsyncDelegateExecution() { // GH-90000
        TraceIdMdcFilter filter = new TraceIdMdcFilter(); // GH-90000
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); // GH-90000
        AtomicReference<String> observedRequestId = new AtomicReference<>(); // GH-90000
        AtomicReference<String> observedMdcCorrelationId = new AtomicReference<>(); // GH-90000
        AtomicReference<String> observedMdcRequestId = new AtomicReference<>(); // GH-90000

        AsyncServlet servlet = filter.wrap(request -> Promise.ofCallback(callback -> // GH-90000
            eventloop().delay(1, () -> { // GH-90000
                observedCorrelationId.set(CorrelationContext.getCorrelationId()); // GH-90000
                observedRequestId.set(CorrelationContext.getRequestId()); // GH-90000
                observedMdcCorrelationId.set(MDC.get(CorrelationContext.CORRELATION_ID_KEY)); // GH-90000
                observedMdcRequestId.set(MDC.get(CorrelationContext.REQUEST_ID_KEY)); // GH-90000
                callback.set(HttpResponse.ok200().build()); // GH-90000
            })));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/traces") // GH-90000
            .withHeader(HttpHeaders.of("X-Request-Id [GH-90000]"), "req-shared-123")
            .build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve(servlet, request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(observedCorrelationId.get()).isEqualTo("req-shared-123 [GH-90000]");
        assertThat(observedRequestId.get()).isEqualTo("req-shared-123 [GH-90000]");
        assertThat(observedMdcCorrelationId.get()).isEqualTo("req-shared-123 [GH-90000]");
        assertThat(observedMdcRequestId.get()).isEqualTo("req-shared-123 [GH-90000]");
        assertThat(CorrelationContext.getCorrelationId()).isNull(); // GH-90000
        assertThat(CorrelationContext.getRequestId()).isNull(); // GH-90000
        assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isNull(); // GH-90000
        assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should restore outer correlation context after wrapped request completes [GH-90000]")
    void shouldRestoreOuterCorrelationContextAfterWrappedRequestCompletes() { // GH-90000
        TraceIdMdcFilter filter = new TraceIdMdcFilter(); // GH-90000
        CorrelationContext.initialize("outer-corr", "outer-user", "outer-tenant", "outer-req"); // GH-90000

        AsyncServlet servlet = filter.wrap(request -> Promise.of(HttpResponse.ok200().build())); // GH-90000
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/traces").build(); // GH-90000

        HttpResponse response = ActiveJServletTestUtil.serve(servlet, request, runner); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(CorrelationContext.getCorrelationId()).isEqualTo("outer-corr [GH-90000]");
        assertThat(CorrelationContext.getUserId()).isEqualTo("outer-user [GH-90000]");
        assertThat(CorrelationContext.getTenantId()).isEqualTo("outer-tenant [GH-90000]");
        assertThat(CorrelationContext.getRequestId()).isEqualTo("outer-req [GH-90000]");
        assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isEqualTo("outer-corr [GH-90000]");
        assertThat(MDC.get(CorrelationContext.USER_ID_KEY)).isEqualTo("outer-user [GH-90000]");
        assertThat(MDC.get(CorrelationContext.TENANT_ID_KEY)).isEqualTo("outer-tenant [GH-90000]");
        assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isEqualTo("outer-req [GH-90000]");
    }
}
