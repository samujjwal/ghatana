package com.ghatana.aep.security;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies trusted proxy handling and forwarded-header decision metrics
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AepSecurityFilter")
class AepSecurityFilterTest extends EventloopTestBase {

    private static final String EVENTS_URL = "http://localhost/api/v1/events";

    @Test
    @DisplayName("trusted proxy accepts X-Forwarded-For and records acceptance metric")
    void trustedProxyAcceptsForwardedClientIp() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build()));

        AepSecurityFilter filter = new AepSecurityFilter(
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL)
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.7")
            .build();

        HttpResponse response = serve(filter, request);

        assertEquals(200, response.getCode());
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED);
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "untrusted_proxy");
        verify(nextServlet).serve(any());
    }

    @Test
    @DisplayName("untrusted proxy rejects X-Forwarded-For and records rejection reason")
    void untrustedProxyRejectsForwardedClientIp() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build()));

        AepSecurityFilter filter = new AepSecurityFilter(
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "192.168.1.10",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL)
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.7")
            .build();

        HttpResponse response = serve(filter, request);

        assertEquals(200, response.getCode());
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "untrusted_proxy");
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED);
        verify(nextServlet).serve(any());
    }

    @Test
    @DisplayName("trusted proxy records malformed X-Forwarded-For as invalid")
    void malformedForwardedHeaderRecordsInvalidReason() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build()));

        AepSecurityFilter filter = new AepSecurityFilter(
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL)
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "   ")
            .build();

        HttpResponse response = serve(filter, request);

        assertEquals(200, response.getCode());
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED);
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "invalid_forwarded_for");
        verify(nextServlet).serve(any());
    }

    @Test
    @DisplayName("trusted proxy rejects syntactically invalid forwarded address")
    void invalidForwardedAddressIsRejected() throws Exception {
        AsyncServlet nextServlet = mock(AsyncServlet.class);
        MetricsCollector metricsCollector = mock(MetricsCollector.class);
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build()));

        AepSecurityFilter filter = new AepSecurityFilter(
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL)
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "[]")
            .build();

        HttpResponse response = serve(filter, request);

        assertEquals(200, response.getCode());
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "invalid_forwarded_for");
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED);
        verify(nextServlet).serve(any());
    }

    private HttpResponse serve(AsyncServlet filter, HttpRequest request) {
        return runPromise(() -> filter.serve(request));
    }
}