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
    void trustedProxyAcceptsForwardedClientIp() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build())); // GH-90000

        AepSecurityFilter filter = new AepSecurityFilter( // GH-90000
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.7")
            .build(); // GH-90000

        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED); // GH-90000
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "untrusted_proxy"); // GH-90000
        verify(nextServlet).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("untrusted proxy rejects X-Forwarded-For and records rejection reason")
    void untrustedProxyRejectsForwardedClientIp() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build())); // GH-90000

        AepSecurityFilter filter = new AepSecurityFilter( // GH-90000
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "192.168.1.10",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "203.0.113.7")
            .build(); // GH-90000

        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "untrusted_proxy"); // GH-90000
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED); // GH-90000
        verify(nextServlet).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("trusted proxy records malformed X-Forwarded-For as invalid")
    void malformedForwardedHeaderRecordsInvalidReason() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build())); // GH-90000

        AepSecurityFilter filter = new AepSecurityFilter( // GH-90000
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "   ")
            .build(); // GH-90000

        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED); // GH-90000
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "invalid_forwarded_for"); // GH-90000
        verify(nextServlet).serve(any()); // GH-90000
    }

    @Test
    @DisplayName("trusted proxy rejects syntactically invalid forwarded address")
    void invalidForwardedAddressIsRejected() throws Exception { // GH-90000
        AsyncServlet nextServlet = mock(AsyncServlet.class); // GH-90000
        MetricsCollector metricsCollector = mock(MetricsCollector.class); // GH-90000
        when(nextServlet.serve(any())).thenReturn(Promise.of(HttpResponse.ofCode(200).build())); // GH-90000

        AepSecurityFilter filter = new AepSecurityFilter( // GH-90000
            nextServlet,
            "*",
            "10.0.0.0/8",
            ignored -> "10.1.2.3",
            metricsCollector
        );

        HttpRequest request = HttpRequest.post(EVENTS_URL) // GH-90000
            .withHeader(HttpHeaders.of("X-Forwarded-For"), "[]")
            .build(); // GH-90000

        HttpResponse response = serve(filter, request); // GH-90000

        assertEquals(200, response.getCode()); // GH-90000
        verify(metricsCollector).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_REJECTED, "reason", "invalid_forwarded_for"); // GH-90000
        verify(metricsCollector, never()).incrementCounter(AepSecurityFilter.FORWARDED_HEADER_ACCEPTED); // GH-90000
        verify(nextServlet).serve(any()); // GH-90000
    }

    private HttpResponse serve(AsyncServlet filter, HttpRequest request) { // GH-90000
        return runPromise(() -> filter.serve(request)); // GH-90000
    }
}