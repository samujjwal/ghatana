package com.ghatana.products.yappc.design.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import io.activej.promise.Promise;

/**
 * Figma REST API client.
 *
 * <p><b>Purpose</b><br>
 * HTTP client for Figma REST API v1. Fetches design files,
 * components, and variables for design token extraction.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * FigmaClient client = new FigmaClient(
 *     "figma-api-token",
 *     objectMapper,
 *     metrics
 * );
 *
 * // Get file
 * FigmaFileResponse file = client.getFile("file-id").await();
 *
 * // Get variables (design tokens)
 * FigmaVariablesResponse vars = client.getVariables("file-id").await();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Authenticates with Figma Personal Access Token
 * - Wraps Figma REST API v1 endpoints
 * - Converts HTTP responses to Java models
 * - Tracks metrics for all API calls
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. HttpClient is thread-safe by design.
 *
 * @doc.type class
 * @doc.purpose Figma REST API client
 * @doc.layer product
 * @doc.pattern Client/Adapter
 */
public class FigmaClient {
    private static final Logger logger = LoggerFactory.getLogger(FigmaClient.class);
    private static final String BASE_URL = "https://api.figma.com/v1";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    private final String accessToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metrics;
    
    /**
     * Creates Figma API client
     *
     * @param accessToken Figma Personal Access Token
     * @param objectMapper Jackson ObjectMapper for JSON
     * @param metrics Metrics collector
     */
    public FigmaClient(
            String accessToken,
            ObjectMapper objectMapper,
            MetricsCollector metrics) {
        this.accessToken = Objects.requireNonNull(accessToken, "Access token required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        
        logger.info("[FigmaClient] Initialized with token: {}***", accessToken.substring(0, 8));
    }
    
    /**
     * Get Figma file by ID
     *
     * <p>Fetches complete file structure including all nodes, pages, and components.
     *
     * @param fileKey Figma file key (from file URL)
     * @return Promise with file response
     */
    public Promise<FigmaModels.FigmaFileResponse> getFile(String fileKey) {
        Objects.requireNonNull(fileKey, "File key required");
        
        String url = String.format("%s/files/%s", BASE_URL, fileKey);
        
        return makeRequest(url, FigmaModels.FigmaFileResponse.class)
                .whenComplete(() -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_file",
                        "status", "success");
                })
                .whenException(e -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_file",
                        "status", "error");
                    logger.error("[FigmaClient] Failed to get file: {}", fileKey, e);
                });
    }
    
    /**
     * Get Figma file nodes by IDs
     *
     * <p>Fetches specific nodes from a file. Useful for targeted extraction.
     *
     * @param fileKey Figma file key
     * @param nodeIds Comma-separated node IDs
     * @return Promise with file response (only requested nodes)
     */
    public Promise<FigmaModels.FigmaFileResponse> getFileNodes(String fileKey, String nodeIds) {
        Objects.requireNonNull(fileKey, "File key required");
        Objects.requireNonNull(nodeIds, "Node IDs required");
        
        String url = String.format("%s/files/%s/nodes?ids=%s", BASE_URL, fileKey, nodeIds);
        
        return makeRequest(url, FigmaModels.FigmaFileResponse.class)
                .whenComplete(() -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_file_nodes",
                        "status", "success");
                });
    }
    
    /**
     * Get local variables (design tokens) from file
     *
     * <p>Figma Variables are design tokens (colors, typography, spacing, etc.).
     * This endpoint is the primary source for token extraction.
     *
     * @param fileKey Figma file key
     * @return Promise with variables response
     */
    public Promise<String> getVariables(String fileKey) {
        Objects.requireNonNull(fileKey, "File key required");
        
        String url = String.format("%s/files/%s/variables/local", BASE_URL, fileKey);
        
        return makeRawRequest(url)
                .whenComplete(() -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_variables",
                        "status", "success");
                })
                .whenException(e -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_variables",
                        "status", "error");
                    logger.error("[FigmaClient] Failed to get variables: {}", fileKey, e);
                });
    }
    
    /**
     * Get image fills from file
     *
     * <p>Fetches URLs for images used in file (useful for extracting icons, logos).
     *
     * @param fileKey Figma file key
     * @return Promise with image URLs map
     */
    public Promise<String> getImageFills(String fileKey) {
        Objects.requireNonNull(fileKey, "File key required");
        
        String url = String.format("%s/files/%s/images", BASE_URL, fileKey);
        
        return makeRawRequest(url)
                .whenComplete(() -> {
                    metrics.incrementCounter("figma.api.calls",
                        "endpoint", "get_image_fills",
                        "status", "success");
                });
    }
    
    // ========================================================================
    // HTTP Request Helpers
    // ========================================================================
    
    /**
     * Make HTTP GET request and deserialize response
     *
     * @param url API endpoint URL
     * @param responseClass Response class to deserialize to
     * @param <T> Response type
     * @return Promise with deserialized response
     */
    private <T> Promise<T> makeRequest(String url, Class<T> responseClass) {
        return Promise.ofFuture(makeHttpRequest(url)
                .thenApply(response -> {
                    try {
                        String body = response.body();
                        logger.debug("[FigmaClient] Response: {}", body.substring(0, Math.min(200, body.length())));
                        return objectMapper.readValue(body, responseClass);
                    } catch (IOException e) {
                        throw new FigmaApiException("Failed to parse response", e);
                    }
                }));
    }
    
    /**
     * Make HTTP GET request and return raw response body
     *
     * @param url API endpoint URL
     * @return Promise with raw JSON string
     */
    private Promise<String> makeRawRequest(String url) {
        return Promise.ofFuture(makeHttpRequest(url)
                .thenApply(HttpResponse::body));
    }
    
    /**
     * Execute HTTP GET request
     *
     * @param url API endpoint URL
     * @return Promise with HTTP response
     */
    private CompletableFuture<HttpResponse<String>> makeHttpRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Figma-Token", accessToken)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        
        logger.debug("[FigmaClient] GET {}", url);
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new FigmaApiException(
                                String.format("Figma API error: %d - %s",
                                        response.statusCode(),
                                        response.body())
                        );
                    }
                    return response;
                });
    }
    
    // ========================================================================
    // Exception
    // ========================================================================
    
    /**
     * Exception thrown when Figma API request fails
     *
     * @doc.type exception
     * @doc.purpose Figma API error
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class FigmaApiException extends RuntimeException {
        public FigmaApiException(String message) {
            super(message);
        }
        
        public FigmaApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
