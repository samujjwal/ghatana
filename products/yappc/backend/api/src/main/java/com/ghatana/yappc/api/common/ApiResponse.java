/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.common;

import static com.ghatana.yappc.api.common.JsonUtils.toJsonBytes;

import com.ghatana.yappc.api.aep.AepException;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standard API response utilities.
 *
 * <p><b>Purpose</b><br>
 * Provides consistent HTTP response formatting: - Success responses (200, 201) - Error responses
 * (400, 401, 403, 404, 500) - Standard JSON structure
 *
 * @doc.type class
 * @doc.purpose Response utilities
 * @doc.layer api
 * @doc.pattern Utility
 */
public class ApiResponse {

  private static final Logger logger = LoggerFactory.getLogger(ApiResponse.class);

  private static final String CONTENT_TYPE_JSON = "application/json";

  /** Standard error response structure. */
  public record ErrorResponse(String error, String message, String code, String path) {}

  /** Creates 200 OK response with JSON body. */
  public static HttpResponse ok(Object body) {
    return HttpResponse.ok200()
        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
        .withBody(toJsonBytes(body))
        .build();
  }

  /** Creates 201 Created response with JSON body. */
  public static HttpResponse created(Object body) {
    return HttpResponse.ofCode(201)
        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
        .withBody(toJsonBytes(body))
        .build();
  }

  /** Creates 202 Accepted response with JSON body. */
  public static HttpResponse accepted(Object body) {
    return HttpResponse.ofCode(202)
        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
        .withBody(toJsonBytes(body))
        .build();
  }

  /** Creates 204 No Content response. */
  public static HttpResponse noContent() {
    return HttpResponse.ofCode(204).build();
  }

  /** Creates 400 Bad Request response. */
  public static HttpResponse badRequest(String message) {
    return error(400, "BAD_REQUEST", message, null);
  }

  /** Creates 401 Unauthorized response. */
  public static HttpResponse unauthorized(String message) {
    return error(401, "UNAUTHORIZED", message, null);
  }

  /** Creates 403 Forbidden response. */
  public static HttpResponse forbidden(String message) {
    return error(403, "FORBIDDEN", message, null);
  }

  /** Creates 404 Not Found response. */
  public static HttpResponse notFound(String message) {
    return error(404, "NOT_FOUND", message, null);
  }

  /** Creates 409 Conflict response. */
  public static HttpResponse conflict(String message) {
    return error(409, "CONFLICT", message, null);
  }

  /** Creates 500 Internal Server Error response. */
  public static HttpResponse serverError(String message) {
    return error(500, "INTERNAL_ERROR", message, null);
  }

  /** Creates error response with specified code. */
  public static HttpResponse error(int statusCode, String code, String message, String path) {
    ErrorResponse errorResponse = new ErrorResponse(getStatusText(statusCode), message, code, path);
    return HttpResponse.ofCode(statusCode)
        .withHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
        .withBody(toJsonBytes(errorResponse))
        .build();
  }

  /** Converts exception to appropriate HTTP response. */
  public static HttpResponse fromException(Throwable e) {
    logger.error("API error: {}", e.getMessage(), e);

    // Typed API exceptions with explicit status codes
    if (e instanceof ApiException apiEx) {
      return error(apiEx.getStatusCode(), apiEx.getErrorCode(), apiEx.getMessage(), null);
    }
    if (e instanceof TenantContextExtractor.UnauthorizedException) {
      return unauthorized(e.getMessage());
    }
    if (e instanceof TenantContextExtractor.ForbiddenException) {
      return forbidden(e.getMessage());
    }
    if (e instanceof JsonUtils.BadRequestException) {
      return badRequest(e.getMessage());
    }
    if (e instanceof IllegalArgumentException) {
      return badRequest(e.getMessage());
    }
    if (e instanceof AepException) {
      return error(502, "AEP_ERROR", "AEP integration error: " + e.getMessage(), null);
    }

    return serverError("An unexpected error occurred");
  }

  /**
   * Wraps async operation with error handling. Uses then() to handle both success and error cases.
   */
  public static Promise<HttpResponse> wrap(Promise<HttpResponse> promise) {
    return promise.then(response -> Promise.of(response), e -> Promise.of(fromException(e)));
  }

  private static String getStatusText(int code) {
    return switch (code) {
      case 200 -> "OK";
      case 201 -> "Created";
      case 204 -> "No Content";
      case 400 -> "Bad Request";
      case 401 -> "Unauthorized";
      case 403 -> "Forbidden";
      case 404 -> "Not Found";
      case 409 -> "Conflict";
      case 422 -> "Unprocessable Entity";
      case 500 -> "Internal Server Error";
      case 502 -> "Bad Gateway";
      case 503 -> "Service Unavailable";
      default -> "Error";
    };
  }
}
