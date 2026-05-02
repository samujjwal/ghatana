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

@DisplayName("TraceIdMdcFilter Tests")
class TraceIdMdcFilterTest extends EventloopTestBase {

    @AfterEach
    void tearDownContext() { 
        CorrelationContext.clear(); 
    }

    @Test
    @DisplayName("Should keep correlation context available for async delegate execution")
    void shouldKeepCorrelationContextAvailableForAsyncDelegateExecution() { 
        TraceIdMdcFilter filter = new TraceIdMdcFilter(); 
        AtomicReference<String> observedCorrelationId = new AtomicReference<>(); 
        AtomicReference<String> observedRequestId = new AtomicReference<>(); 
        AtomicReference<String> observedMdcCorrelationId = new AtomicReference<>(); 
        AtomicReference<String> observedMdcRequestId = new AtomicReference<>(); 

        AsyncServlet servlet = filter.wrap(request -> Promise.ofCallback(callback -> 
            eventloop().delay(1, () -> { 
                observedCorrelationId.set(CorrelationContext.getCorrelationId()); 
                observedRequestId.set(CorrelationContext.getRequestId()); 
                observedMdcCorrelationId.set(MDC.get(CorrelationContext.CORRELATION_ID_KEY)); 
                observedMdcRequestId.set(MDC.get(CorrelationContext.REQUEST_ID_KEY)); 
                callback.set(HttpResponse.ok200().build()); 
            })));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/traces") 
            .withHeader(HttpHeaders.of("X-Request-Id"), "req-shared-123")
            .build(); 

        HttpResponse response = ActiveJServletTestUtil.serve(servlet, request, runner); 

        assertThat(response.getCode()).isEqualTo(200); 
        assertThat(observedCorrelationId.get()).isEqualTo("req-shared-123");
        assertThat(observedRequestId.get()).isEqualTo("req-shared-123");
        assertThat(observedMdcCorrelationId.get()).isEqualTo("req-shared-123");
        assertThat(observedMdcRequestId.get()).isEqualTo("req-shared-123");
        assertThat(CorrelationContext.getCorrelationId()).isNull(); 
        assertThat(CorrelationContext.getRequestId()).isNull(); 
        assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isNull(); 
        assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isNull(); 
    }

    @Test
    @DisplayName("Should restore outer correlation context after wrapped request completes")
    void shouldRestoreOuterCorrelationContextAfterWrappedRequestCompletes() { 
        TraceIdMdcFilter filter = new TraceIdMdcFilter(); 
        CorrelationContext.initialize("outer-corr", "outer-user", "outer-tenant", "outer-req"); 

        AsyncServlet servlet = filter.wrap(request -> Promise.of(HttpResponse.ok200().build())); 
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/traces").build(); 

        HttpResponse response = ActiveJServletTestUtil.serve(servlet, request, runner); 

        assertThat(response.getCode()).isEqualTo(200); 
        assertThat(CorrelationContext.getCorrelationId()).isEqualTo("outer-corr");
        assertThat(CorrelationContext.getUserId()).isEqualTo("outer-user");
        assertThat(CorrelationContext.getTenantId()).isEqualTo("outer-tenant");
        assertThat(CorrelationContext.getRequestId()).isEqualTo("outer-req");
        assertThat(MDC.get(CorrelationContext.CORRELATION_ID_KEY)).isEqualTo("outer-corr");
        assertThat(MDC.get(CorrelationContext.USER_ID_KEY)).isEqualTo("outer-user");
        assertThat(MDC.get(CorrelationContext.TENANT_ID_KEY)).isEqualTo("outer-tenant");
        assertThat(MDC.get(CorrelationContext.REQUEST_ID_KEY)).isEqualTo("outer-req");
    }
}
