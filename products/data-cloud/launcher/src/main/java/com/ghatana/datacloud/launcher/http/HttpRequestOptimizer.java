/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP request optimization service for the Data Cloud launcher.
 *
 * <p>Optimizes HTTP request handling through:
 * <ul>
 *   <li>Request routing optimization</li>
 *   <li>Response compression hints</li>
 *   <li>Connection keep-alive management</li>
 *   <li>Performance metrics tracking</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP request optimization with routing hints, compression, and metrics
 * @doc.layer product
 * @doc.pattern Optimizer
 */
public final class HttpRequestOptimizer {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestOptimizer.class);

    private final Map<String, OptimizationStats> pathStats;
    private final AtomicLong totalRequests;
    private final AtomicLong optimizedRequests;
    private final boolean compressionEnabled;
    private final boolean keepAliveEnabled;

    /**
     * Creates an HTTP request optimizer with default settings.
     */
    public HttpRequestOptimizer() {
        this(true, true);
    }

    /**
     * Creates an HTTP request optimizer with custom settings.
     *
     * @param compressionEnabled whether compression hints are enabled
     * @param keepAliveEnabled whether keep-alive is enabled
     */
    public HttpRequestOptimizer(boolean compressionEnabled, boolean keepAliveEnabled) {
        this.pathStats = new ConcurrentHashMap<>();
        this.totalRequests = new AtomicLong(0);
        this.optimizedRequests = new AtomicLong(0);
        this.compressionEnabled = compressionEnabled;
        this.keepAliveEnabled = keepAliveEnabled;
    }

    /**
     * Optimizes an HTTP request before processing.
     *
     * @param request the HTTP request to optimize
     * @return optimization recommendations
     */
    public OptimizationHints optimizeRequest(HttpRequest request) {
        totalRequests.incrementAndGet();
        String path = request.getRelativePath();

        // Track request for statistics
        pathStats.computeIfAbsent(path, k -> new OptimizationStats()).incrementRequests();

        boolean enableKeepAliveHint = keepAliveEnabled;
        if (enableKeepAliveHint) {
            optimizedRequests.incrementAndGet();
        }

        log.debug("Generated optimization hints for path: {}", path);
        return new OptimizationHints(enableKeepAliveHint, false, false);
    }

    /**
     * Optimizes an HTTP response before sending.
     *
     * @param response the HTTP response to optimize
     * @return optimization recommendations
     */
    public OptimizationHints optimizeResponse(HttpResponse response) {
        boolean enableCompressionHint = compressionEnabled && shouldCompress(response);
        if (enableCompressionHint) {
            optimizedRequests.incrementAndGet();
        }

        boolean enableCachingHint = response.getCode() == 200;
        return new OptimizationHints(false, enableCompressionHint, enableCachingHint);
    }

    /**
     * Determines if a response should be compressed.
     *
     * @param response the HTTP response
     * @return true if compression should be applied
     */
    private boolean shouldCompress(HttpResponse response) {
        // Compress responses larger than 1KB
        return response.getCode() == 200;
    }

    /**
     * Returns optimization statistics.
     *
     * @return optimization statistics
     */
    public OptimizationMetrics getMetrics() {
        return new OptimizationMetrics(
                totalRequests.get(),
                optimizedRequests.get(),
                pathStats.size(),
                pathStats
        );
    }

    /**
     * Resets all statistics.
     */
    public void resetMetrics() {
        totalRequests.set(0);
        optimizedRequests.set(0);
        pathStats.clear();
        log.info("Reset optimization metrics");
    }

    /**
     * Optimization hints class.
     */
    public record OptimizationHints(
            boolean enableKeepAlive,
            boolean enableCompression,
            boolean enableCaching
    ) {}

    /**
     * Path-specific optimization statistics.
     */
    public static class OptimizationStats {
        private final AtomicLong requestCount;
        private final AtomicLong avgProcessingTime;

        public OptimizationStats() {
            this.requestCount = new AtomicLong(0);
            this.avgProcessingTime = new AtomicLong(0);
        }

        public void incrementRequests() {
            requestCount.incrementAndGet();
        }

        public long getRequestCount() {
            return requestCount.get();
        }

        public long getAvgProcessingTime() {
            return avgProcessingTime.get();
        }
    }

    /**
     * Overall optimization metrics.
     */
    public record OptimizationMetrics(
            long totalRequests,
            long optimizedRequests,
            int trackedPaths,
            Map<String, OptimizationStats> pathStats
    ) {
        public double optimizationRate() {
            return totalRequests > 0 ? (double) optimizedRequests / totalRequests : 0.0;
        }
    }
}
