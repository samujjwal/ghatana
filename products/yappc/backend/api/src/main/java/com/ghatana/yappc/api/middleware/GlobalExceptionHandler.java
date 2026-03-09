/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.middleware;

import com.ghatana.yappc.api.common.ApiResponse;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global exception handler middleware.
 *
 * <p>Wraps the delegate servlet and catches all unhandled exceptions, converting them to
 * consistent JSON error responses via {@link ApiResponse#fromException(Throwable)}. This
 * eliminates the need for per-endpoint {@code .then(ok, fromException)} error callbacks and
 * prevents raw error messages from leaking to clients.
 *
 * <p><b>Order</b>: Should be applied <em>inside</em> CORS middleware (CORS wraps this, this wraps
 * routing).
 *
 * @doc.type class
 * @doc.purpose Centralized exception-to-response conversion
 * @doc.layer api
 * @doc.pattern Middleware, Decorator
 */
public class GlobalExceptionHandler implements AsyncServlet {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final AsyncServlet delegate;

  public GlobalExceptionHandler(AsyncServlet delegate) {
    this.delegate = delegate;
  }

  @Override
  public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
    try {
      return delegate
          .serve(request)
          .then(
              response -> Promise.of(response),
              e -> {
                logger.error(
                    "Unhandled exception for {} {}: {}",
                    request.getMethod(),
                    request.getPath(),
                    e.getMessage(),
                    e);
                return Promise.of(ApiResponse.fromException(e));
              });
    } catch (Exception e) {
      // Catch synchronous exceptions thrown by servlet.serve() itself
      logger.error(
          "Synchronous exception for {} {}: {}",
          request.getMethod(),
          request.getPath(),
          e.getMessage(),
          e);
      return Promise.of(ApiResponse.fromException(e));
    }
  }
}
