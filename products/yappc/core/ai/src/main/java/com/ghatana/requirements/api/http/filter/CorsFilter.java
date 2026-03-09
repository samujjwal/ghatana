package com.ghatana.requirements.api.http.filter;

import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * CORS (Cross-Origin Resource Sharing) filter for HTTP requests.
 *
 * <p><b>Purpose:</b> Handles CORS headers to allow cross-origin requests
 * from web browsers.
 *
 * <p><b>Headers Added:</b>
 * <ul>
 *   <li>{@code Access-Control-Allow-Origin} - Allowed origins
 *   <li>{@code Access-Control-Allow-Methods} - Allowed HTTP methods
 *   <li>{@code Access-Control-Allow-Headers} - Allowed headers
 *   <li>{@code Access-Control-Allow-Credentials} - Credentials flag
 *   <li>{@code Access-Control-Max-Age} - Preflight cache duration
 * </ul>
 *
 * <p><b>Configuration:</b>
 * <ul>
 *   <li>Development: Allow all origins (*)
 *   <li>Production: Configure specific origins via config
 * </ul>
 *
 * @doc.type class
 * @doc.purpose CORS filter
 * @doc.layer product
 * @doc.pattern Filter
 * @since 1.0.0
 */
public final class CorsFilter {
  private static final Logger logger = LoggerFactory.getLogger(CorsFilter.class);

  private final String allowedOrigins;
  private final String allowedMethods;
  private final String allowedHeaders;
  private final long maxAge;

  /**
   * Create a CORS filter with default settings.
   * Development: Allow all origins and methods
   * Production: Should be configured via environment
   */
  public CorsFilter() {
    this("*", "GET,POST,PUT,DELETE,OPTIONS", "Content-Type,Authorization", 3600);
  }

  /**
   * Create a CORS filter with custom settings.
   *
   * @param allowedOrigins comma-separated origins or "*"
   * @param allowedMethods comma-separated HTTP methods
   * @param allowedHeaders comma-separated header names
   * @param maxAge preflight cache duration in seconds
   */
  public CorsFilter(String allowedOrigins, String allowedMethods, String allowedHeaders,
      long maxAge) {
    this.allowedOrigins = Objects.requireNonNull(allowedOrigins);
    this.allowedMethods = Objects.requireNonNull(allowedMethods);
    this.allowedHeaders = Objects.requireNonNull(allowedHeaders);
    this.maxAge = maxAge;
  }

  /**
   * Handle CORS for a request/response cycle.
   *
   * @param request the HTTP request
   * @return promise of response with CORS headers
   */
  public Promise<HttpResponse> handle(HttpRequest request) {
    logger.debug("Processing CORS for {} {}", request.getMethod(), request.getPath());

    // Handle preflight requests
    if ("OPTIONS".equals(request.getMethod())) {
      return Promise.of(createCorsResponse(request));
    }

    // Return null to continue with next handler
    // CORS headers will be added by the servlet
    return null;
  }

  /**
   * Create a response with CORS headers.
   *
   * @param request the HTTP request
   * @return response with CORS headers
   */
  private HttpResponse createCorsResponse(HttpRequest request) {
    String origin = request.getHeader(HttpHeaders.of("Origin"));
    if (origin == null) {
      origin = allowedOrigins;
    }

    return HttpResponse.ok200()
        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), origin)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), allowedMethods)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), allowedHeaders)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), "true")
        .withHeader(HttpHeaders.of("Access-Control-Max-Age"), String.valueOf(maxAge))
        .build();
  }

  /**
   * Add CORS headers to a response builder.
   *
   * @param responseBuilder the response builder to enhance
   * @param request the original request
   * @return response builder with CORS headers
   */
  public HttpResponse.Builder addCorsHeaders(HttpResponse.Builder responseBuilder, HttpRequest request) {
    String origin = request.getHeader(HttpHeaders.of("Origin"));
    if (origin == null) {
      origin = allowedOrigins;
    }

    return responseBuilder
        .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), origin)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Methods"), allowedMethods)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Headers"), allowedHeaders)
        .withHeader(HttpHeaders.of("Access-Control-Allow-Credentials"), "true");
  }
}