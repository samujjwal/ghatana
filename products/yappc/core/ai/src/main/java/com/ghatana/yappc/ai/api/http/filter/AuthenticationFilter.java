package com.ghatana.yappc.ai.api.http.filter;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.security.model.User;
import com.ghatana.yappc.ai.api.security.JwtService;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT authentication filter for HTTP requests.
 *
 * @doc.type class
 * @doc.purpose JWT authentication filter for YAPPC HTTP endpoints
 * @doc.layer product
 * @doc.pattern Filter
 * @since 1.0.0
 */
public final class AuthenticationFilter {
  private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

  private final JwtService jwtService;

  public AuthenticationFilter(JwtService jwtService) {
    this.jwtService = Objects.requireNonNull(jwtService, "JWT service is required");
  }

  public Function<HttpRequest, Promise<HttpResponse>> wrap(AsyncServlet servlet) {
    return request -> handle(request, servlet);
  }

  private Promise<HttpResponse> handle(HttpRequest request, AsyncServlet servlet) {
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
      User principal = jwtService.extractPrincipal(request);
      request.attach("userPrincipal", principal);
      logger.debug("Request authenticated for user: {}", principal.getUsername());
      return servlet.serve(request);
    } catch (JwtService.JwtValidationException e) {
      logger.warn("JWT validation failed: {}", e.getMessage());
      return Promise.of(
          ResponseBuilder.unauthorized()
              .json(Map.of(
                  "error", "Invalid or expired authentication token",
                  "code", "INVALID_JWT"))
              .build());
    } catch (Exception e) {
      logger.error("Unexpected error during authentication", e);
      return Promise.of(
          ResponseBuilder.internalServerError()
              .json(Map.of("error", "Authentication error"))
              .build());
    }
  }

  public AsyncServlet toServlet(Function<HttpRequest, Promise<HttpResponse>> handler) {
    return handler::apply;
  }
}