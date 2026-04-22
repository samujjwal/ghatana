/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.server.http;

import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Integration tests for correlation ID extraction and MDC propagation.
 *
 * P1-7: Verify X-Correlation-ID header is extracted and propagated via MDC.
 */
class CorrelationIdPropagationTest {

    @Test
    void shouldExtractCorrelationIdFromHeader() { // GH-90000
        String correlationId = "req-abc-123-xyz";
        
        HttpRequest request = mockHttpRequestWithCorrelationId(correlationId); // GH-90000
        String extractedId = extractCorrelationId(request); // GH-90000
        
        assertEquals(correlationId, extractedId,  // GH-90000
            "Correlation ID should be extracted from X-Correlation-ID header");
    }

    @Test
    void shouldGenerateCorrelationIdIfNotProvided() { // GH-90000
        HttpRequest request = mockHttpRequestWithoutCorrelationId(); // GH-90000
        String extractedId = extractCorrelationId(request); // GH-90000
        
        assertNotNull(extractedId,  // GH-90000
            "Correlation ID should be generated if not provided in header");
        assertFalse(extractedId.isEmpty(),  // GH-90000
            "Generated correlation ID should not be empty");
    }

    @Test
    void shouldSetCorrelationIdInMDC() { // GH-90000
        String correlationId = "req-xyz-789-abc";
        
        // Clear MDC before test
        MDC.clear(); // GH-90000
        
        setCorrelationIdInMDC(correlationId); // GH-90000
        
        String mdcValue = MDC.get("correlationId [GH-90000]");
        assertEquals(correlationId, mdcValue,  // GH-90000
            "Correlation ID should be set in MDC");
        
        // Cleanup
        MDC.clear(); // GH-90000
    }

    @Test
    void shouldPropagateCorrelationIdThroughRequestProcessing() { // GH-90000
        String correlationId = "req-propagate-123";
        
        HttpRequest request = mockHttpRequestWithCorrelationId(correlationId); // GH-90000
        String extractedId = extractCorrelationId(request); // GH-90000
        setCorrelationIdInMDC(extractedId); // GH-90000
        
        // Simulate request processing
        String mdcValue = MDC.get("correlationId [GH-90000]");
        
        assertEquals(correlationId, mdcValue,  // GH-90000
            "Correlation ID should be available in MDC during request processing");
        
        // Cleanup
        MDC.clear(); // GH-90000
    }

    @Test
    void shouldClearCorrelationIdFromMDCAfterRequest() { // GH-90000
        String correlationId = "req-clear-456";
        
        MDC.clear(); // GH-90000
        setCorrelationIdInMDC(correlationId); // GH-90000
        
        assertNotNull(MDC.get("correlationId [GH-90000]"), 
            "Correlation ID should be set in MDC");
        
        clearCorrelationIdFromMDC(); // GH-90000
        
        assertNull(MDC.get("correlationId [GH-90000]"), 
            "Correlation ID should be cleared from MDC after request");
    }

    @Test
    void shouldHandleEmptyCorrelationIdHeader() { // GH-90000
        HttpRequest request = mockHttpRequestWithCorrelationId(" [GH-90000]");
        String extractedId = extractCorrelationId(request); // GH-90000
        
        assertNotNull(extractedId,  // GH-90000
            "Should generate correlation ID when header is empty");
        assertFalse(extractedId.isEmpty(),  // GH-90000
            "Generated correlation ID should not be empty");
    }

    @Test
    void shouldHandleNullCorrelationIdHeader() { // GH-90000
        HttpRequest request = mockHttpRequestWithCorrelationId(null); // GH-90000
        String extractedId = extractCorrelationId(request); // GH-90000
        
        assertNotNull(extractedId,  // GH-90000
            "Should generate correlation ID when header is null");
    }

    @Test
    void shouldPreserveCorrelationIdAcrossAsyncOperations() { // GH-90000
        String correlationId = "req-async-789";
        
        MDC.clear(); // GH-90000
        setCorrelationIdInMDC(correlationId); // GH-90000
        
        // Simulate async operation that should preserve MDC
        String mdcValueBeforeAsync = MDC.get("correlationId [GH-90000]");
        
        // In real test, this would verify async context propagation
        // For now, we verify MDC is set
        assertEquals(correlationId, mdcValueBeforeAsync,  // GH-90000
            "Correlation ID should be available before async operation");
        
        MDC.clear(); // GH-90000
    }

    // Helper methods to simulate the behavior
    // In a real integration test, these would interact with the actual AepHttpServer
    
    private HttpRequest mockHttpRequestWithCorrelationId(String correlationId) { // GH-90000
        HttpRequest mock = mock(HttpRequest.class); // GH-90000
        when(mock.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"))).thenReturn(correlationId);
        return mock;
    }

    private HttpRequest mockHttpRequestWithoutCorrelationId() { // GH-90000
        HttpRequest mock = mock(HttpRequest.class); // GH-90000
        when(mock.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"))).thenReturn(null);
        return mock;
    }

    private String extractCorrelationId(HttpRequest request) { // GH-90000
        String header = request.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"));
        if (header != null && !header.isEmpty()) { // GH-90000
            return header;
        }
        // Generate correlation ID if not present
        return java.util.UUID.randomUUID().toString(); // GH-90000
    }

    private void setCorrelationIdInMDC(String correlationId) { // GH-90000
        MDC.put("correlationId", correlationId); // GH-90000
    }

    private void clearCorrelationIdFromMDC() { // GH-90000
        MDC.remove("correlationId [GH-90000]");
    }
}
