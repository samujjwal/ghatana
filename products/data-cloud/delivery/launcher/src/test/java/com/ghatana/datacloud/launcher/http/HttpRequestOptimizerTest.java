/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HTTP request optimization service.
 *
 * @doc.type    class
 * @doc.purpose Tests for HTTP request optimization including hints generation and metrics tracking
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("HttpRequestOptimizerTest")
@Tag("launcher")
class HttpRequestOptimizerTest {

    private HttpRequestOptimizer optimizer;

    @BeforeEach
    void setUp() { 
        optimizer = new HttpRequestOptimizer(true, true); 
    }

    @AfterEach
    void tearDown() { 
        optimizer.resetMetrics(); 
    }

    // ─── Request optimization ─────────────────────────────────────────────────

    @Test
    @DisplayName("generates optimization hints for request")
    void generatesOptimizationHintsForRequest() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeRequest(request); 

        assertThat(hints).isNotNull(); 
        assertThat(hints.enableKeepAlive()).isTrue(); 
    }

    @Test
    @DisplayName("tracks request statistics")
    void tracksRequestStatistics() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); 

        HttpRequestOptimizer.OptimizationMetrics metrics = optimizer.getMetrics(); 
        assertThat(metrics.totalRequests()).isEqualTo(1); 
    }

    // ─── Response optimization ────────────────────────────────────────────────

    @Test
    @DisplayName("generates optimization hints for response")
    void generatesOptimizationHintsForResponse() { 
        HttpResponse response = HttpResponse.ok200().build(); 
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeResponse(response); 

        assertThat(hints).isNotNull(); 
        assertThat(hints.enableCompression()).isTrue(); 
        assertThat(hints.enableCaching()).isTrue(); 
    }

    @Test
    @DisplayName("does not enable compression for non-200 responses")
    void doesNotEnableCompressionForNon200Responses() { 
        HttpResponse response = HttpResponse.ofCode(404).build(); 
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeResponse(response); 

        assertThat(hints.enableCompression()).isFalse(); 
    }

    // ─── Metrics tracking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("tracks total requests")
    void tracksTotalRequests() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); 
        optimizer.optimizeRequest(request); 

        assertThat(optimizer.getMetrics().totalRequests()).isEqualTo(2); 
    }

    @Test
    @DisplayName("tracks optimized requests")
    void tracksOptimizedRequests() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpResponse response = HttpResponse.ok200().build(); 
        
        optimizer.optimizeRequest(request); 
        optimizer.optimizeResponse(response); 

        assertThat(optimizer.getMetrics().optimizedRequests()).isGreaterThan(0); 
    }

    @Test
    @DisplayName("calculates optimization rate")
    void calculatesOptimizationRate() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpResponse response = HttpResponse.ok200().build(); 
        
        optimizer.optimizeRequest(request); 
        optimizer.optimizeResponse(response); 

        double rate = optimizer.getMetrics().optimizationRate(); 
        assertThat(rate).isGreaterThan(0.0); 
    }

    @Test
    @DisplayName("tracks path-specific statistics")
    void tracksPathSpecificStatistics() { 
        HttpRequest request1 = HttpRequest.get("http://localhost/api/data1").build();
        HttpRequest request2 = HttpRequest.get("http://localhost/api/data2").build();

        optimizer.optimizeRequest(request1); 
        optimizer.optimizeRequest(request1); 
        optimizer.optimizeRequest(request2); 

        assertThat(optimizer.getMetrics().trackedPaths()).isGreaterThan(0); 
    }

    // ─── Metrics reset ────────────────────────────────────────────────────────

    @Test
    @DisplayName("resets metrics correctly")
    void resetsMetricsCorrectly() { 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); 

        optimizer.resetMetrics(); 

        assertThat(optimizer.getMetrics().totalRequests()).isEqualTo(0); 
        assertThat(optimizer.getMetrics().optimizedRequests()).isEqualTo(0); 
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    @Test
    @DisplayName("respects compression enabled setting")
    void respectsCompressionEnabledSetting() { 
        HttpRequestOptimizer noCompressionOptimizer = new HttpRequestOptimizer(false, true); 
        HttpResponse response = HttpResponse.ok200().build(); 
        
        HttpRequestOptimizer.OptimizationHints hints = noCompressionOptimizer.optimizeResponse(response); 
        
        assertThat(hints.enableCompression()).isFalse(); 
    }

    @Test
    @DisplayName("respects keep-alive enabled setting")
    void respectsKeepAliveEnabledSetting() { 
        HttpRequestOptimizer noKeepAliveOptimizer = new HttpRequestOptimizer(true, false); 
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();

        HttpRequestOptimizer.OptimizationHints hints = noKeepAliveOptimizer.optimizeRequest(request); 
        
        assertThat(hints.enableKeepAlive()).isFalse(); 
    }
}
