/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void setUp() { // GH-90000
        optimizer = new HttpRequestOptimizer(true, true); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        optimizer.resetMetrics(); // GH-90000
    }

    // ─── Request optimization ─────────────────────────────────────────────────

    @Test
    @DisplayName("generates optimization hints for request")
    void generatesOptimizationHintsForRequest() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeRequest(request); // GH-90000

        assertThat(hints).isNotNull(); // GH-90000
        assertThat(hints.enableKeepAlive()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("tracks request statistics")
    void tracksRequestStatistics() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); // GH-90000

        HttpRequestOptimizer.OptimizationMetrics metrics = optimizer.getMetrics(); // GH-90000
        assertThat(metrics.totalRequests()).isEqualTo(1); // GH-90000
    }

    // ─── Response optimization ────────────────────────────────────────────────

    @Test
    @DisplayName("generates optimization hints for response")
    void generatesOptimizationHintsForResponse() { // GH-90000
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeResponse(response); // GH-90000

        assertThat(hints).isNotNull(); // GH-90000
        assertThat(hints.enableCompression()).isTrue(); // GH-90000
        assertThat(hints.enableCaching()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("does not enable compression for non-200 responses")
    void doesNotEnableCompressionForNon200Responses() { // GH-90000
        HttpResponse response = HttpResponse.ofCode(404).build(); // GH-90000
        HttpRequestOptimizer.OptimizationHints hints = optimizer.optimizeResponse(response); // GH-90000

        assertThat(hints.enableCompression()).isFalse(); // GH-90000
    }

    // ─── Metrics tracking ─────────────────────────────────────────────────────

    @Test
    @DisplayName("tracks total requests")
    void tracksTotalRequests() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); // GH-90000
        optimizer.optimizeRequest(request); // GH-90000

        assertThat(optimizer.getMetrics().totalRequests()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("tracks optimized requests")
    void tracksOptimizedRequests() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000
        
        optimizer.optimizeRequest(request); // GH-90000
        optimizer.optimizeResponse(response); // GH-90000

        assertThat(optimizer.getMetrics().optimizedRequests()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("calculates optimization rate")
    void calculatesOptimizationRate() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000
        
        optimizer.optimizeRequest(request); // GH-90000
        optimizer.optimizeResponse(response); // GH-90000

        double rate = optimizer.getMetrics().optimizationRate(); // GH-90000
        assertThat(rate).isGreaterThan(0.0); // GH-90000
    }

    @Test
    @DisplayName("tracks path-specific statistics")
    void tracksPathSpecificStatistics() { // GH-90000
        HttpRequest request1 = HttpRequest.get("http://localhost/api/data1").build();
        HttpRequest request2 = HttpRequest.get("http://localhost/api/data2").build();

        optimizer.optimizeRequest(request1); // GH-90000
        optimizer.optimizeRequest(request1); // GH-90000
        optimizer.optimizeRequest(request2); // GH-90000

        assertThat(optimizer.getMetrics().trackedPaths()).isGreaterThan(0); // GH-90000
    }

    // ─── Metrics reset ────────────────────────────────────────────────────────

    @Test
    @DisplayName("resets metrics correctly")
    void resetsMetricsCorrectly() { // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();
        optimizer.optimizeRequest(request); // GH-90000

        optimizer.resetMetrics(); // GH-90000

        assertThat(optimizer.getMetrics().totalRequests()).isEqualTo(0); // GH-90000
        assertThat(optimizer.getMetrics().optimizedRequests()).isEqualTo(0); // GH-90000
    }

    // ─── Configuration ────────────────────────────────────────────────────────

    @Test
    @DisplayName("respects compression enabled setting")
    void respectsCompressionEnabledSetting() { // GH-90000
        HttpRequestOptimizer noCompressionOptimizer = new HttpRequestOptimizer(false, true); // GH-90000
        HttpResponse response = HttpResponse.ok200().build(); // GH-90000
        
        HttpRequestOptimizer.OptimizationHints hints = noCompressionOptimizer.optimizeResponse(response); // GH-90000
        
        assertThat(hints.enableCompression()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("respects keep-alive enabled setting")
    void respectsKeepAliveEnabledSetting() { // GH-90000
        HttpRequestOptimizer noKeepAliveOptimizer = new HttpRequestOptimizer(true, false); // GH-90000
        HttpRequest request = HttpRequest.get("http://localhost/api/data").build();

        HttpRequestOptimizer.OptimizationHints hints = noKeepAliveOptimizer.optimizeRequest(request); // GH-90000
        
        assertThat(hints.enableKeepAlive()).isFalse(); // GH-90000
    }
}
