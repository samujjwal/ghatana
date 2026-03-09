package com.ghatana.requirements.api.http.filter;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.requirements.api.security.JwtService;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.HttpHeaders;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * JWT authentication filter for HTTP requests.
 *
 * <p><b>Purpose:</b> Validates JWT tokens in request headers and enforces
 * authentication requirements.
 *
 * <p><b>Behavior:</b>
 * <ul>
 *   <li>Checks Authorization header for "Bearer {token}"
 *   <li>Validates JWT signature and expiration
 *   <li>Returns 401 if token is missing or invalid
 *   <li>Passes request through if valid
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 *   GET /api/v1/projects
 *   Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose JWT authentication filter
 * @doc.layer product
 * @doc.pattern Filter
 * @since 1.0.0
 */
public final class AuthenticationFilter {
  private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
  
  private final JwtService jwtService;

  /**
   * Create authentication filter with JWT service.
   *
   * @param jwtService JWT validation service
   */
  public AuthenticationFilter(JwtService jwtService) {
    this.jwtService = Objects.requireNonNull(jwtService, "JWT service is required");
  }

  /**
   * Wrap an async servlet with authentication.
   *
   * @param servlet the servlet to wrap
   * @return wrapped servlet that requires authentication
   */
  public Function<HttpRequest, Promise<HttpResponse>> wrap(AsyncServlet servlet) {
    return request -> handle(request, servlet);
  }

  /**
   * Handle authentication for a request.
   *
   * @param request the HTTP request
   * @param servlet the next handler
   * @return promise of response
   */
  private Promise<HttpResponse> handle(HttpRequest request, AsyncServlet servlet) {
    // Check if authentication is required for this path
    if (!jwtService.requiresAuthentication(request)) {
      logger.debug("Public endpoint accessed: {}", request.getPath());
      try {
        return servlet.serve(request);
      } catch (Exception e) {
        logger.error("Error serving request", e);
        return Promise.of(
            ResponseBuilder.internalServerError()
                .json(Map.of("error", "Internal server error"))
                .build());
      }
    }

    try {
      // Validate JWT and extract user principal
      User principal = jwtService.extractPrincipal(request);
      
      // Attach principal to request for downstream use
      request.attach("userPrincipal", principal);
      
      logger.debug("Request authenticated for user: {}", principal.getUsername());

      return servlet.serve(request);
      
    } catch (JwtService.JwtValidationException e) {
      logger.warn("JWT validation failed: {}", e.getMessage());
      return Promise.of(
          ResponseBuilder.unauthorized()
              .json(Map.of(
                  "error", "Invalid or expired authentication token",
                  "code", "INVALID_JWT"
              ))
              .build());
              
    } catch (Exception e) {
      logger.error("Unexpected error during authentication", e);
      return Promise.of(
          ResponseBuilder.internalServerError()
              .json(Map.of("error", "Authentication error"))
              .build());
    }
  }

  /**
   * Convert an async handler function to be wrapped by this filter.
   *
   * @param handler the handler function
   * @return async servlet that can be wrapped
   */
  public AsyncServlet toServlet(Function<HttpRequest, Promise<HttpResponse>> handler) {
    return handler::apply;
  }
}