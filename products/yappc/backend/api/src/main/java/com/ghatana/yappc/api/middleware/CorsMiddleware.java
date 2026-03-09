/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.middleware;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CORS Middleware for handling Cross-Origin Resource Sharing.
 *
 * <p>Wraps an AsyncServlet and adds appropriate CORS headers to all responses.
 *
 * @doc.type class
 * @doc.purpose CORS header management
 * @doc.layer api
 * @doc.pattern Middleware, Decorator
 */
public class CorsMiddleware implements AsyncServlet {

  private static final Logger logger = LoggerFactory.getLogger(CorsMiddleware.class);
  private final AsyncServlet delegate;
  private final String allowOrigin;
  private final String allowMethods;
  private final String allowHeaders;
  private final String maxAge;

  /**
   * Create CORS middleware with default settings.
   *
   * @param delegate the underlying servlet to wrap
   */
  public CorsMiddleware(AsyncServlet delegate) {
    this(
        delegate,
        "*",
        "GET, POST, PUT, DELETE, OPTIONS",
        "Content-Type, Authorization, X-Tenant-Id, X-Persona",
        "3600");
  }

  /**
   * Create CORS middleware with custom settings.
   *
   * @param delegate the underlying servlet to wrap
   * @param allowOrigin the origin(s) to allow (e.g., "*" or "http://localhost:5173")
   * @param allowMethods comma-separated list of allowed HTTP methods
   * @param allowHeaders comma-separated list of allowed headers
   * @param maxAge cache duration in seconds
   */
  public CorsMiddleware(
      AsyncServlet delegate,
      String allowOrigin,
      String allowMethods,
      String allowHeaders,
      String maxAge) {
    this.delegate = delegate;
    this.allowOrigin = allowOrigin;
    this.allowMethods = allowMethods;
    this.allowHeaders = allowHeaders;
    this.maxAge = maxAge;
  }

  @Override
  public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
    // Handle CORS preflight OPTIONS request before routing
    // OPTIONS requests should always return 200 with CORS headers, regardless of path
    HttpMethod method = request.getMethod();
    if (method == HttpMethod.OPTIONS) {
      logger.debug("Handling CORS preflight OPTIONS request for path: {}", request.getPath());
      HttpResponse optionsResponse = HttpResponse.ok200().build();
      return Promise.of(addCorsHeaders(optionsResponse));
    }

    return delegate
        .serve(request)
        .then(
            (response, e) -> {
              HttpResponse finalResponse;
              if (e == null) {
                finalResponse = response;
              } else {
                logger.error("Unhandled exception after middleware chain", e);
                finalResponse =
                    HttpResponse.ofCode(500)
                        .withBody("{\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\",\"code\":\"INTERNAL_ERROR\"}")
                        .build();
              }
              return Promise.of(addCorsHeaders(finalResponse));
            });
  }

  private HttpResponse addCorsHeaders(HttpResponse response) {
    var builder =
        HttpResponse.ofCode(response.getCode())
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowOrigin)
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowMethods)
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders)
            .withHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, maxAge)
            .withHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

    // Copy all original headers from the response
    for (var headerEntry : response.getHeaders()) {
      try {
        // Avoid duplicating CORS headers if they are already present (though builder usually
        // appends)
        // We prioritize our middleware configuration
        if (!isCorsHeader(headerEntry.getKey().toString())) {
          builder.withHeader(headerEntry.getKey(), headerEntry.getValue());
        }
      } catch (RuntimeException e) {
        logger.warn("Failed to copy header: {}", headerEntry.getKey(), e);
      }
    }

    // Copy the body (skip for empty responses)
    try {
      var body = response.getBody();
      if (body != null) {
        builder.withBody(body);
      }
    } catch (IllegalStateException e) {
      // Body already consumed or not available, that's fine for most responses
      logger.debug("Body not available for response: {}", e.getMessage());
    }

    return builder.build();
  }

  private boolean isCorsHeader(String headerName) {
    return headerName.equalsIgnoreCase("Access-Control-Allow-Origin")
        || headerName.equalsIgnoreCase("Access-Control-Allow-Methods")
        || headerName.equalsIgnoreCase("Access-Control-Allow-Headers")
        || headerName.equalsIgnoreCase("Access-Control-Max-Age")
        || headerName.equalsIgnoreCase("Access-Control-Allow-Credentials");
  }
}
